package showroomz.api.admin.home;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.seller.entity.SellerApplication;
import showroomz.domain.member.seller.repository.SellerApplicationRepository;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.service.SettlementClawbackService;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.config.properties.AdminProperties;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 홈 세부(1017 설계서 6절 · 9절) — 마감 있는 큐의 기한 초과 · D-1 · 대기 분리(영업일) · 건별 행 20건 상한과 「외 N건」 ·
 * 세금계산서 발행 마감 5영업일 승격 · 오늘 일정 · 회원 수 · 탈퇴 승인 큐 · 오늘 지표의 어제 같은 시각 · PG 연동 경고 ·
 * 대기 행 보조줄(55 계획서 A3).
 */
@DisplayName("1017 어드민 홈 세부")
class AdminHomeDetailIntegrationTest extends SettlementTestSupport {

    private static final String HOME = "/v1/admin/home";
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM.dd");

    @Autowired private SettlementClawbackService clawbackService;
    @Autowired private SellerApplicationRepository sellerApplications;
    @Autowired private AdminProperties adminProperties;

    @Test
    @DisplayName("AD-01 입점 심사 — 마감(신청 + 3영업일)이 지났으면 기한 초과 · 다음 영업일이면 D-1 · 나머지는 대기 1행")
    void onboardingDeadlines() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate nextBusinessDay = businessCalendar.addBusinessDays(today, 1);
        LocalDate dueSoonCreated = today;
        for (int guard = 0; !businessCalendar.addBusinessDays(dueSoonCreated, 3).equals(nextBusinessDay); guard++) {
            assertThat(guard).as("마감이 다음 영업일인 신청일을 찾지 못했다").isLessThan(30);
            dueSoonCreated = dueSoonCreated.minusDays(1);
        }
        seedCreatorApplication("overdue-app", LocalDateTime.now().minusDays(20));
        seedCreatorApplication("due-soon-app", dueSoonCreated.atTime(9, 0));
        seedCreatorApplication("waiting-app", LocalDateTime.now());

        JsonNode todo = json(adminGet(HOME).andExpect(status().isOk())).get("todo");
        List<JsonNode> rows = rowsOf(todo, "CREATOR_APPLICATION");
        assertThat(rows).extracting(r -> r.get("urgency").asText()).containsExactly("OVERDUE", "DUE_SOON", "WAITING");
        assertThat(rows.get(0).get("badge").asText()).isEqualTo("기한 초과");
        assertThat(rows.get(1).get("badge").asText()).isEqualTo("D-1");
        assertThat(rows.get(1).get("dueText").asText()).isEqualTo("마감 " + nextBusinessDay.format(MD));
        assertThat(rows.get(1).get("title").asText()).isEqualTo("인플루언서 입점 심사 · due-soon-app");
        assertThat(rows.get(2).get("count").asLong()).isEqualTo(1);
        // 55 계획서 A3 — 대기 행 보조줄은 위로 올라간 건별 행을 가리킨다.
        assertThat(rows.get(2).get("detail").asText()).isEqualTo("기한 초과 1건 · 기한 임박 1건은 위에");

        JsonNode badges = json(adminGet("/v1/admin/badges").andExpect(status().isOk()));
        assertThat(badges.at("/onboarding/creator").asLong()).isEqualTo(3);
        assertThat(badges.at("/onboarding/total").asLong()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("AD-02 건별 행 상한 — 1:1 문의 기한 초과 21건은 20행 + 「외 1건」 · 기한 초과 합계는 21")
    void overdueRowsAreFolded() throws Exception {
        for (int i = 0; i < 21; i++) {
            seedInquiry(LocalDateTime.now().minusHours(30).plusMinutes(i), "문의 " + i);
        }

        JsonNode todo = json(adminGet(HOME).andExpect(status().isOk())).get("todo");
        List<JsonNode> rows = rowsOf(todo, "INQUIRY");
        assertThat(rows).hasSize(21);
        assertThat(rows.subList(0, 20)).allMatch(r -> r.get("count").asLong() == 1 && "INQUIRY".equals(r.at("/target/type").asText()));
        JsonNode folded = rows.get(20);
        assertThat(folded.get("title").asText()).isEqualTo("1:1 문의 기한 초과 외 1건");
        assertThat(folded.get("count").asLong()).isEqualTo(1);
        assertThat(folded.at("/target/type").asText()).isEqualTo("QUEUE");
        assertThat(todo.get("overdue").asLong()).isEqualTo(21);
        assertThat(rows.get(0).get("dueText").asText()).isEqualTo("+6시간");
    }

    @Test
    @DisplayName("AD-03 세금계산서 발행 — 마감 지남 = 기한 초과 · 5영업일 안 = 기한 임박 · 나머지는 대기 · 정산 배지 = 정산 요약 배지")
    void taxInvoiceIssuePromotion() throws Exception {
        LocalDate today = LocalDate.now();
        seedBrandInvoice(seedSettlement("마감 지난 공구", 1_000_000, SettlementStatus.PAID), today.minusDays(1));
        seedBrandInvoice(seedSettlement("마감 임박 공구", 1_000_000, SettlementStatus.PAID),
                businessCalendar.addBusinessDays(today, 2));
        seedBrandInvoice(seedSettlement("여유 공구", 1_000_000, SettlementStatus.PAID), today.plusDays(40));

        JsonNode todo = json(adminGet(HOME).andExpect(status().isOk())).get("todo");
        List<JsonNode> rows = rowsOf(todo, "TAX_INVOICE_ISSUE");
        assertThat(rows).extracting(r -> r.get("urgency").asText()).containsExactly("OVERDUE", "DUE_SOON", "WAITING");
        assertThat(rows.get(0).get("title").asText()).startsWith("세금계산서 발행 · STL-");
        assertThat(rows.get(1).get("dueText").asText()).isEqualTo("마감 " + businessCalendar.addBusinessDays(today, 2).format(MD));
        assertThat(rows.get(2).get("count").asLong()).isEqualTo(1);
        // 55 계획서 A3 — 대기 행이 대표하는 문서(여유 공구 1건)의 브랜드 수 · 가장 빠른 마감
        assertThat(rows.get(2).get("detail").asText())
                .isEqualTo("브랜드 1곳 · 홈택스 수동 · " + today.plusDays(40).format(MD) + " 마감");

        JsonNode badges = json(adminGet("/v1/admin/badges").andExpect(status().isOk()));
        assertThat(badges.get("settlements").asLong())
                .isEqualTo(json(adminGet("/v1/admin/settlements/summary")).get("gnbBadge").asLong());
    }

    @Test
    @DisplayName("AD-04 오늘 일정 — 정산 확인 마감 · 조정 협의 기한 · 직권 중단 집행 예정(운영자 집행 필요) · 시각순")
    void todayScheduleKinds() throws Exception {
        LocalDate today = LocalDate.now();
        Settlement reviewing = seedSettlement("확인 마감 공구", 1_000_000, SettlementStatus.REVIEWING);
        jdbc.update("UPDATE settlement SET review_due_at = ? WHERE settlement_id = ?", today.atTime(23, 30), reviewing.getId());
        jdbc.update("INSERT INTO settlement_adjustment (settlement_id, settlement_number, group_buy_id, market_id, creator_id, "
                        + "thread_id, requester_type, status, original_reward_amount, max_reward_amount, opened_at, deadline_at, "
                        + "notice_due_at, created_at, modified_at) VALUES (?, ?, ?, ?, ?, ?, 'CREATOR', 'OPEN', 1000, 2000, ?, ?, ?, ?, ?)",
                reviewing.getId(), reviewing.getSettlementNumber(), reviewing.getGroupBuyId(), brand.marketId(), creator.getId(),
                pairThreadId, LocalDateTime.now().minusDays(3), today.atTime(22, 0), today.atTime(9, 0), LocalDateTime.now(),
                LocalDateTime.now());
        seedNotice(groupBuy.getId(), today.atTime(21, 0).minusDays(1));

        JsonNode schedule = json(adminGet(HOME).andExpect(status().isOk())).get("todaySchedule");
        List<String> kinds = new ArrayList<>();
        schedule.forEach(s -> kinds.add(s.get("kind").asText()));
        assertThat(kinds).containsSubsequence("ADMIN_SUSPENSION_EXECUTE", "ADJUSTMENT_DEADLINE", "SETTLEMENT_REVIEW_DUE");
        for (JsonNode item : schedule) {
            switch (item.get("kind").asText()) {
                case "ADMIN_SUSPENSION_EXECUTE" -> assertThat(item.get("detail").asText()).isEqualTo("운영자 집행 필요");
                case "SETTLEMENT_REVIEW_DUE" -> assertThat(item.at("/target/id").asLong()).isEqualTo(reviewing.getId());
                case "ADJUSTMENT_DEADLINE" -> assertThat(item.get("title").asText()).endsWith(reviewing.getSettlementNumber());
                default -> {
                }
            }
        }
    }

    @Test
    @DisplayName("AD-05 회원 수 · 탈퇴 승인 — 탈퇴한 소비자는 회원 수에서 빠지고 · 브랜드 탈퇴 신청은 회원 메뉴 할 일 · 대기 행")
    void membersAndWithdrawals() throws Exception {
        JsonNode before = json(adminGet("/v1/admin/badges").andExpect(status().isOk())).get("members");
        createConsumer("new-member", "새회원");
        var gone = createConsumer("gone-member", "떠난회원");
        jdbc.update("UPDATE USERS SET STATUS = 'WITHDRAWN' WHERE USER_ID = ?", gone.getId());
        createCreator("새인플루언서", "new-creator");
        jdbc.update("INSERT INTO brand_withdrawal_request (market_id, status, requested_at, requester_name) "
                + "VALUES (?, 'PENDING', ?, '박대표')", brand.marketId(), LocalDateTime.now().minusDays(2));

        JsonNode members = json(adminGet("/v1/admin/badges").andExpect(status().isOk())).get("members");
        assertThat(members.get("userCount").asLong()).isEqualTo(before.get("userCount").asLong() + 1);
        assertThat(members.get("creatorCount").asLong()).isEqualTo(before.get("creatorCount").asLong() + 1);
        assertThat(members.get("todo").asLong()).isEqualTo(1);
        JsonNode row = rowsOf(json(adminGet(HOME)).get("todo"), "WITHDRAWAL").get(0);
        assertThat(row.get("urgency").asText()).isEqualTo("WAITING");
        assertThat(row.get("ageText").asText()).isEqualTo("2일 경과");
    }

    @Test
    @DisplayName("AD-06 오늘 지표 — 어제 같은 시각까지의 주문 · 거래액과 나란히")
    void metricsTodayAgainstYesterday() throws Exception {
        purchase(creamVariant, 1);
        Purchase yesterday = purchase(creamVariant, 2);
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = ?", LocalDate.now().minusDays(1).atStartOfDay(),
                yesterday.orderId());

        JsonNode items = json(adminGet(HOME).andExpect(status().isOk())).at("/metricsToday/items");
        JsonNode orders = null;
        JsonNode amount = null;
        for (JsonNode item : items) {
            if ("ORDERS".equals(item.get("key").asText())) {
                orders = item;
            } else if ("PAID_AMOUNT".equals(item.get("key").asText())) {
                amount = item;
            }
        }
        assertThat(orders.get("today").asLong()).isEqualTo(1);
        assertThat(orders.get("yesterdaySameTime").asLong()).isEqualTo(1);
        assertThat(amount.get("yesterdaySameTime").asLong()).isGreaterThan(amount.get("today").asLong());
    }

    @Test
    @DisplayName("AD-07 연동 — 결제 운영 지표가 임계를 넘으면 PG 경고(문구 · 영향 큐 · 장애 시작 = 걸린 건의 가장 이른 시각) · 택배 추적 꺼짐 · 전자서명 수동")
    void paymentIntegrationDegraded() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        LocalDateTime failedSince = LocalDateTime.now().minusHours(5).withNano(0);
        jdbc.update("UPDATE payment SET status = 'CANCEL_FAILED', cancel_requested_at = ? WHERE payment_id = ?",
                failedSince, purchase.paymentId());
        Purchase later = purchase(creamVariant, 1);
        jdbc.update("UPDATE payment SET status = 'CANCEL_FAILED', cancel_requested_at = ? WHERE payment_id = ?",
                failedSince.plusHours(2), later.paymentId());

        JsonNode integrations = json(adminGet(HOME).andExpect(status().isOk())).get("integrations");
        JsonNode pg = integrations.get(0);
        assertThat(pg.get("key").asText()).isEqualTo("PG");
        assertThat(pg.get("status").asText()).isEqualTo("DEGRADED");
        assertThat(pg.get("message").asText()).contains("운영자 처리 대기(CANCEL_FAILED) 2건");
        assertThat(pg.get("affectedQueues").get(0).asText()).isEqualTo("환불 실패");
        // 55 계획서 A4 — since 는 메모리가 아니라 데이터(가장 이른 건)에서 구한다.
        assertThat(LocalDateTime.parse(pg.get("since").asText().replace("Z", ""))).isEqualTo(failedSince);
        assertThat(integrations.get(1).get("status").asText()).isEqualTo("DISABLED");
        assertThat(integrations.get(2).get("message").asText()).isEqualTo("수동");
    }

    @Test
    @DisplayName("AD-08 대기 행 보조줄(55 계획서 A3) — 배송 예외 · 처리 지연은 유형별 건수 · 변경 요청은 요청자별 건수 · 고정 문구는 대조 · 신고")
    void waitingRowDetails() throws Exception {
        OrderDeliveryGroup stalled = shippingGroup(nextTrackingNumber());
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ? "
                + "WHERE delivery_group_id = ?", LocalDateTime.now().minusDays(8), stalled.getId());
        OrderDeliveryGroup late = purchase(creamVariant, 1).group();
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(2), late.getId());
        seedChangeRequest("CR-BRAND-1", "BRAND", "BUSINESS_INFO", brand.marketId(), null);
        seedChangeRequest("CR-BRAND-2", "BRAND", "SETTLEMENT_ACCOUNT", brand.marketId(), null);
        seedChangeRequest("CR-CREATOR-1", "CREATOR", "CREATOR_ACCOUNT", null, creator.getId());

        JsonNode todo = json(adminGet(HOME).andExpect(status().isOk())).get("todo");
        assertThat(rowsOf(todo, "DELIVERY_EXCEPTION").get(0).get("detail").asText()).isEqualTo("추적 정지 1");
        assertThat(rowsOf(todo, "PROCESSING_DELAY").get(0).get("detail").asText()).isEqualTo("발송 기한 경과 1");
        JsonNode changeRequest = rowsOf(todo, "CHANGE_REQUEST").get(0);
        assertThat(changeRequest.get("count").asLong()).isEqualTo(3);
        assertThat(changeRequest.get("detail").asText()).isEqualTo("브랜드 2 · 인플루언서 1");

        JsonNode summary = json(adminGet("/v1/admin/change-requests/summary").andExpect(status().isOk()));
        assertThat(summary.get("pendingCount").asLong()).isEqualTo(3);
        assertThat(summary.get("brandPendingCount").asLong()).isEqualTo(2);
        assertThat(summary.get("creatorPendingCount").asLong()).isEqualTo(1);

        // 인플루언서 요청이 끝나면 0인 쪽은 빠진다.
        jdbc.update("UPDATE brand_change_request SET status = 'APPROVED' WHERE request_code = 'CR-CREATOR-1'");
        assertThat(rowsOf(json(adminGet(HOME)).get("todo"), "CHANGE_REQUEST").get(0).get("detail").asText())
                .isEqualTo("브랜드 2");
    }

    @Test
    @DisplayName("AD-09 차감 미회수 보조줄(55 계획서 A3) — 1건이면 「차감 번호 · 미회수액」")
    void clawbackDetail() throws Exception {
        OrderDeliveryGroup creamGroup = confirmedGroup(creamVariant, 1);
        endGroupBuy();
        confirm(generate(LocalDateTime.now().withNano(0)));
        operatorRefund(creamGroup, "RECALL", 27_200);
        jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE user_id = ?", creator.getUser().getId());
        assertThat(clawbackService.markUnrecoverable(LocalDateTime.now())).isEqualTo(1);
        long unrecoverable = jdbc.queryForObject("SELECT SUM(amount) FROM settlement_clawback WHERE status = 'UNRECOVERABLE'",
                Long.class);

        JsonNode row = rowsOf(json(adminGet(HOME).andExpect(status().isOk())).get("todo"), "CLAWBACK_UNRECOVERABLE").get(0);
        assertThat(row.get("count").asLong()).isEqualTo(1);
        assertThat(row.get("detail").asText()).isEqualTo("CLW-0001 · " + String.format("%,d원", unrecoverable));
    }

    @Test
    @DisplayName("[55 A3-03] 입점 심사 · 오픈 승인 대기 행 보조줄 — 위로 올라간 건별 행 수를 「기한 초과 N건 · 기한 임박 N건은 위에」로 적고 "
            + "0인 쪽은 빼며, 올라간 행이 없으면 보조줄이 없다")
    void deadlinedWaitingRowDetails() throws Exception {
        // 오픈 승인 — 기한 초과 2 · 대기 1(기한 임박 없음)
        for (int i = 0; i < 2; i++) {
            GroupBuy overdue = seedIn(GroupBuyStatus.PREPARING);
            seedPost(overdue.getId(), GroupBuyPostReviewStatus.PENDING, false);
            jdbc.update("UPDATE group_buy_post SET submitted_at = ? WHERE group_buy_id = ?",
                    LocalDateTime.now().minusDays(14 + i), overdue.getId());
        }
        GroupBuy fresh = seedIn(GroupBuyStatus.PREPARING);
        seedPost(fresh.getId(), GroupBuyPostReviewStatus.PENDING, false);
        // 브랜드 입점 심사 — 기한 임박(D-1) 1 · 대기 1(기한 초과 없음)
        seedSellerApplication("due-soon-brand", "임박브랜드", dueSoonCreatedDate().atTime(9, 0));
        seedSellerApplication("waiting-brand", "여유브랜드", LocalDateTime.now());
        // 인플루언서 입점 심사 — 대기 1뿐
        seedCreatorApplication("only-waiting-app", LocalDateTime.now());

        JsonNode todo = json(adminGet(HOME).andExpect(status().isOk())).get("todo");

        List<JsonNode> openReview = rowsOf(todo, "OPEN_REVIEW");
        assertThat(openReview).extracting(r -> r.get("urgency").asText()).containsExactly("OVERDUE", "OVERDUE", "WAITING");
        assertThat(openReview.get(2).get("title").asText()).isEqualTo("오픈 승인");
        assertThat(openReview.get(2).get("count").asLong()).isEqualTo(1);
        assertThat(openReview.get(2).get("detail").asText()).isEqualTo("기한 초과 2건은 위에");

        List<JsonNode> brandApplication = rowsOf(todo, "BRAND_APPLICATION");
        assertThat(brandApplication).extracting(r -> r.get("urgency").asText()).containsExactly("DUE_SOON", "WAITING");
        assertThat(brandApplication.get(0).get("title").asText()).isEqualTo("브랜드 입점 심사 · 임박브랜드");
        assertThat(brandApplication.get(1).get("count").asLong()).isEqualTo(1);
        assertThat(brandApplication.get(1).get("detail").asText()).isEqualTo("기한 임박 1건은 위에");

        List<JsonNode> creatorApplication = rowsOf(todo, "CREATOR_APPLICATION");
        assertThat(creatorApplication).extracting(r -> r.get("urgency").asText()).containsExactly("WAITING");
        assertThat(textOrNull(creatorApplication.get(0), "detail")).as("위로 올라간 행이 없으면 보조줄 없음").isNull();
    }

    @Test
    @DisplayName("[55 A3-04] 세금계산서 발행 대기 행 보조줄 — 「브랜드 N곳 · 홈택스 수동 · MM.dd 마감」은 대기 문서만 센다(같은 브랜드는 1곳 · "
            + "기한 임박으로 올라간 문서의 브랜드 · 마감은 빠진다) · 마감이 없으면 마감 문구를 뺀다")
    void taxInvoiceWaitingRowDetail() throws Exception {
        LocalDate today = LocalDate.now();
        BrandFixture.Brand mood = fixture.createBrand("mood-tax@showroomz.test", "무드코스메틱");
        BrandFixture.Brand pure = fixture.createBrand("pure-tax@showroomz.test", "퓨어랩");
        Settlement glowLater = seedSettlement("글로우 여유 공구 1", 1_000_000, SettlementStatus.PAID);
        Settlement glowEarlier = seedSettlement("글로우 여유 공구 2", 1_000_000, SettlementStatus.PAID);
        Settlement moodWaiting = settlementOf(mood, "무드 여유 공구");
        Settlement pureUrgent = settlementOf(pure, "퓨어 임박 공구");
        seedBrandInvoice(glowLater, today.plusDays(40));
        seedBrandInvoice(glowEarlier, today.plusDays(30));
        seedBrandInvoice(moodWaiting, today.plusDays(50));
        LocalDate urgentDue = businessCalendar.addBusinessDays(today, 2);
        seedBrandInvoice(pureUrgent, urgentDue);

        List<JsonNode> rows = rowsOf(json(adminGet(HOME).andExpect(status().isOk())).get("todo"), "TAX_INVOICE_ISSUE");
        assertThat(rows).extracting(r -> r.get("urgency").asText()).containsExactly("DUE_SOON", "WAITING");
        assertThat(rows.get(0).get("dueText").asText()).isEqualTo("마감 " + urgentDue.format(MD));
        JsonNode waiting = rows.get(1);
        assertThat(waiting.get("count").asLong()).isEqualTo(3);
        // 글로우랩 2건은 1곳 · 무드 1곳 — 퓨어랩(기한 임박 행)은 세지 않고, 마감도 퓨어랩의 더 이른 날이 아니라 대기 문서 중 가장 이른 날
        assertThat(waiting.get("detail").asText())
                .isEqualTo("브랜드 2곳 · 홈택스 수동 · " + today.plusDays(30).format(MD) + " 마감");

        // 대기 문서에 마감이 없으면 마감 문구만 빠진다
        jdbc.update("UPDATE settlement_tax_document SET due_date = NULL WHERE settlement_id IN (?, ?, ?)",
                glowLater.getId(), glowEarlier.getId(), moodWaiting.getId());
        List<JsonNode> undated = rowsOf(json(adminGet(HOME)).get("todo"), "TAX_INVOICE_ISSUE");
        assertThat(undated).extracting(r -> r.get("urgency").asText()).containsExactly("DUE_SOON", "WAITING");
        assertThat(undated.get(1).get("detail").asText()).isEqualTo("브랜드 2곳 · 홈택스 수동");
    }

    // ------------------------------------------------------------------ 픽스처

    private void seedChangeRequest(String code, String requesterType, String type, Long marketId, Long creatorId) {
        LocalDateTime requestedAt = LocalDateTime.now().minusDays(1);
        jdbc.update("INSERT INTO brand_change_request (request_code, requester_type, market_id, creator_id, type, status, "
                        + "evidence_file_url, evidence_file_name, evidence_file_size, requested_at, version, created_at, "
                        + "modified_at) VALUES (?, ?, ?, ?, ?, 'PENDING', 'https://cdn.showroomz.test/evidence.jpg', "
                        + "'evidence.jpg', 1000, ?, 0, ?, ?)",
                code, requesterType, marketId, creatorId, type, requestedAt, requestedAt, requestedAt);
    }

    private static List<JsonNode> rowsOf(JsonNode todo, String queue) {
        List<JsonNode> rows = new ArrayList<>();
        todo.get("rows").forEach(r -> {
            if (queue.equals(r.get("queue").asText())) {
                rows.add(r);
            }
        });
        return rows;
    }

    private void seedCreatorApplication(String accountId, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO CREATOR_APPLICATION (USER_ID, SNS_TYPE, CHANNEL_URL, ACCOUNT_ID, STATUS, created_at, modified_at) "
                        + "VALUES (?, 'INSTAGRAM', ?, ?, 'PENDING', ?, ?)", consumer.getId(),
                "https://instagram.com/" + accountId, accountId, createdAt, createdAt);
    }

    private void seedInquiry(LocalDateTime createdAt, String content) {
        jdbc.update("INSERT INTO one_to_one_inquiry (user_id, type, content, status, created_at, modified_at) "
                + "VALUES (?, 'DELIVERY', ?, 'WAITING', ?, ?)", consumer.getId(), content, createdAt, createdAt);
    }

    private void seedBrandInvoice(Settlement settlement, LocalDate dueDate) {
        jdbc.update("INSERT INTO settlement_tax_document (settlement_id, type, status, supply_amount, vat_amount, total_amount, "
                        + "due_date, created_at) VALUES (?, 'BRAND_TAX_INVOICE', 'PENDING_ISSUE', 100000, 10000, 110000, ?, ?)",
                settlement.getId(), dueDate, LocalDateTime.now().minusDays(3));
    }

    /** {@code owner} 몫 정산 — 시드 브랜드의 종료 공구로 만든 뒤 마켓만 옮긴다(보조줄의 브랜드 수는 정산의 market_id 로 센다). */
    private Settlement settlementOf(BrandFixture.Brand owner, String title) {
        Settlement s = seedSettlement(title, 1_000_000, SettlementStatus.PAID);
        jdbc.update("UPDATE settlement SET market_id = ? WHERE settlement_id = ?", owner.marketId(), s.getId());
        return s;
    }

    /** 입점 신청 대기 — 신청 화면이 만드는 행을 그대로 두고 신청 시각만 옮긴다. */
    private void seedSellerApplication(String account, String marketName, LocalDateTime createdAt) {
        BrandFixture.Brand applicant = fixture.createBrand(account + "@showroomz.test", marketName);
        SellerApplication application = sellerApplications.save(
                SellerApplication.createFrom(applicant.seller(), marketName, "02-1234-5678"));
        jdbc.update("UPDATE seller_application SET created_at = ? WHERE seller_application_id = ?", createdAt,
                application.getId());
    }

    /** 마감(신청 + 입점 SLA 영업일)이 다음 영업일이 되는 신청일 — 기한 임박(D-1) 행을 만든다. */
    private LocalDate dueSoonCreatedDate() {
        int sla = adminProperties.getOnboardingSlaBusinessDays();
        LocalDate nextBusinessDay = businessCalendar.addBusinessDays(LocalDate.now(), 1);
        LocalDate created = LocalDate.now();
        for (int guard = 0; !businessCalendar.addBusinessDays(created, sla).equals(nextBusinessDay); guard++) {
            assertThat(guard).as("마감이 다음 영업일인 신청일을 찾지 못했다").isLessThan(30);
            created = created.minusDays(1);
        }
        return created;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
