package showroomz.api.admin.home;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.health.IntegrationHealthRegistry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 홈(1017 설계서 6절) — 할 일 합계 = 사이드바 배지 합 · 모듈 요약과 같은 건수 · 급한 정도(실패 · 기한 초과 · 집행 대기 · 기한 임박 · 대기) ·
 * 오늘 처리 완료 · 오늘 일정 · 오늘 지표 · 연동 상태(택배 추적 장애 중 추적 정지 집계 제외).
 */
class AdminHomeIntegrationTest extends SettlementTestSupport {

    private static final String HOME = "/v1/admin/home";
    private static final String BADGES = "/v1/admin/badges";
    private static final String CONTRACT_SUMMARY = "/v1/admin/contracts/summary";

    @Autowired
    private IntegrationHealthRegistry healthRegistry;
    @Autowired
    private OperatorChannelService operatorChannels;

    @AfterEach
    void resetTracking() {
        healthRegistry.recordTrackingSuccess(LocalDateTime.now());
    }

    @Test
    @DisplayName("A-01 합계 = 사이드바 배지 합 · 배지 = 모듈 요약(공구 · 정산 · 1:1 문의 · 환불) · 연동 3종")
    void totalsMatchBadgesAndModuleSummaries() throws Exception {
        GroupBuy review = seedIn(GroupBuyStatus.PREPARING);
        seedPost(review.getId(), GroupBuyPostReviewStatus.PENDING, false);
        seedInquiry(LocalDateTime.now().minusHours(2), "배송이 아직 안 왔어요");
        Long taskId = requestOperatorRefund(confirmedGroup(creamVariant, 1));

        JsonNode home = json(adminGet(HOME).andExpect(status().isOk()));
        JsonNode badges = json(adminGet(BADGES).andExpect(status().isOk()));
        long badgeSum = badges.at("/onboarding/total").asLong() + badges.at("/members/todo").asLong()
                + badges.get("contracts").asLong() + badges.get("groupBuys").asLong() + badges.get("transactions").asLong()
                + badges.get("settlements").asLong() + badges.at("/cs/total").asLong() + badges.at("/posts/total").asLong();
        JsonNode todo = home.get("todo");
        assertThat(todo.get("total").asLong()).isEqualTo(badgeSum).isPositive();
        assertThat(todo.get("failed").asLong() + todo.get("overdue").asLong() + todo.get("dueSoon").asLong()
                + todo.get("waiting").asLong()).isEqualTo(todo.get("total").asLong());
        long rowSum = 0;
        for (JsonNode row : todo.get("rows")) {
            rowSum += row.get("count").asLong();
        }
        assertThat(rowSum).isEqualTo(todo.get("total").asLong());

        assertThat(badges.get("groupBuys").asLong())
                .isEqualTo(json(adminGet("/v1/admin/group-buys/summary")).get("actionRequiredCount").asLong()).isEqualTo(1);
        assertThat(badges.get("contracts").asLong())
                .isEqualTo(json(adminGet(CONTRACT_SUMMARY)).get("actionRequiredCount").asLong());
        assertThat(badges.get("settlements").asLong())
                .isEqualTo(json(adminGet("/v1/admin/settlements/summary")).get("gnbBadge").asLong());
        assertThat(badges.at("/cs/inquiries").asLong())
                .isEqualTo(json(adminGet("/v1/admin/inquiries/summary")).get("unansweredCount").asLong()).isEqualTo(1);
        assertThat(badges.at("/members/userCount").asLong()).isPositive();

        // 사유 환불 집행 대기는 건별 행(집행 대기)이다.
        JsonNode refundRow = rowOf(todo, "REFUND_EXECUTION");
        assertThat(refundRow.get("urgency").asText()).isEqualTo("EXECUTION_PENDING");
        assertThat(refundRow.at("/target/id").asLong()).isEqualTo(taskId);

        List<String> integrations = new ArrayList<>();
        home.get("integrations").forEach(i -> integrations.add(i.get("key").asText() + "=" + i.get("status").asText()));
        assertThat(integrations).containsExactly("PG=OK", "DELIVERY_TRACKING=DISABLED", "E_SIGN=NOT_INTEGRATED");
    }

    @Test
    @DisplayName("A-02 급한 정도 — 환불 실패(건별 · 실패 시각) · 1:1 문의 24시간 초과(건별 기한 초과) + 기한 내(대기 1행) · 오픈 승인 기한 초과")
    void urgencyRows() throws Exception {
        Long taskId = requestOperatorRefund(confirmedGroup(creamVariant, 1));
        jdbc.update("UPDATE order_refund_task SET status = 'FAILED', failed_at = ?, last_error = '이미 취소된 결제' "
                + "WHERE refund_task_id = ?", LocalDateTime.now().minusHours(3), taskId);
        seedInquiry(LocalDateTime.now().minusHours(30), "환불은 언제 되나요");
        seedInquiry(LocalDateTime.now().minusHours(1), "교환 문의");
        GroupBuy review = seedIn(GroupBuyStatus.PREPARING);
        seedPost(review.getId(), GroupBuyPostReviewStatus.PENDING, false);
        jdbc.update("UPDATE group_buy_post SET submitted_at = ? WHERE group_buy_id = ?",
                LocalDateTime.now().minusDays(14), review.getId());

        JsonNode todo = json(adminGet(HOME).andExpect(status().isOk())).get("todo");
        assertThat(todo.get("failed").asLong()).isEqualTo(1);
        JsonNode first = todo.at("/rows/0");
        assertThat(first.get("urgency").asText()).isEqualTo("FAILED");
        assertThat(first.get("queue").asText()).isEqualTo("REFUND_FAILED");
        assertThat(first.get("detail").asText()).startsWith("이미 취소된 결제");
        assertThat(first.get("ageText").asText()).isEqualTo("3시간 경과");

        List<String> inquiryRows = new ArrayList<>();
        todo.get("rows").forEach(r -> {
            if (r.get("queue").asText().equals("INQUIRY")) {
                inquiryRows.add(r.get("urgency").asText() + ":" + r.get("count").asLong());
            }
        });
        assertThat(inquiryRows).containsExactly("OVERDUE:1", "WAITING:1");
        JsonNode openReview = rowOf(todo, "OPEN_REVIEW");
        assertThat(openReview.get("urgency").asText()).isEqualTo("OVERDUE");
        assertThat(openReview.get("badge").asText()).isEqualTo("기한 초과");
        assertThat(openReview.at("/target/id").asLong()).isEqualTo(review.getId());
        assertThat(todo.get("overdue").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("A-03 오늘 처리 완료 — 처리 시각 열(문의 답변 · 환불 집행)이 오늘인 행 합")
    void processedToday() throws Exception {
        long before = json(adminGet(HOME)).at("/todo/processedToday").asLong();
        operatorRefund(confirmedGroup(creamVariant, 1), "POST_CONFIRM_DEFECT", 1_000);
        jdbc.update("INSERT INTO one_to_one_inquiry (user_id, type, content, status, answer_content, answered_at, "
                        + "created_at, modified_at) VALUES (?, 'DELIVERY', '답변된 문의', 'ANSWERED', '안내드렸습니다', ?, ?, ?)",
                consumer.getId(), LocalDateTime.now(), LocalDateTime.now().minusDays(1), LocalDateTime.now());

        JsonNode todo = json(adminGet(HOME).andExpect(status().isOk())).get("todo");
        assertThat(todo.get("processedToday").asLong()).isEqualTo(before + 2);
        assertThat(todo.get("note").asText()).contains("배송 예외");
    }

    @Test
    @DisplayName("A-04 오늘 일정 · 오늘 지표 — 오픈 · 종료 · 정산 지급(배치 시각) · 주문 · 거래액 · 가입")
    void scheduleAndMetrics() throws Exception {
        LocalDate today = LocalDate.now();
        GroupBuy opening = seedIn(GroupBuyStatus.READY);
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?", today.atTime(23, 59), opening.getId());
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?", today.atTime(23, 58), groupBuy.getId());
        Settlement settlement = seedSettlement("지급 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ? "
                + "AND payee = 'BRAND'", today, settlement.getId());
        purchase(creamVariant, 2);

        JsonNode home = json(adminGet(HOME).andExpect(status().isOk()));
        List<String> kinds = new ArrayList<>();
        home.get("todaySchedule").forEach(s -> kinds.add(s.get("kind").asText() + "|" + s.at("/target/id").asText()));
        assertThat(kinds).contains("GROUP_BUY_OPEN|" + opening.getId(), "GROUP_BUY_END|" + groupBuy.getId(),
                "SETTLEMENT_PAYOUT|null");

        Map<String, Long> today2 = new java.util.HashMap<>();
        home.at("/metricsToday/items").forEach(i -> today2.put(i.get("key").asText(), i.get("today").asLong()));
        assertThat(today2.get("ORDERS")).isGreaterThanOrEqualTo(1);
        assertThat(today2.get("PAID_AMOUNT")).isGreaterThanOrEqualTo(CREAM_PRICE * 2L);
        assertThat(today2.get("SIGNUPS")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("A-05 택배 추적 장애 중 — 추적 정지를 배송 예외 집계에서 뺀다 · 복구되면 돌아온다")
    void trackingDegradedExcludesStalled() throws Exception {
        OrderDeliveryGroup shipping = shippingGroup(nextTrackingNumber());
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ? "
                + "WHERE delivery_group_id = ?", LocalDateTime.now().minusDays(8), shipping.getId());
        long withStalled = json(adminGet("/v1/admin/order-exceptions/summary")).at("/kindCounts/TRACKING_STALLED").asLong();
        assertThat(withStalled).isEqualTo(1);

        healthRegistry.recordTrackingBlocked(LocalDateTime.now(), "조회 차단(103)");
        assertThat(json(adminGet("/v1/admin/order-exceptions/summary")).at("/kindCounts/TRACKING_STALLED").asLong())
                .isZero();

        healthRegistry.recordTrackingSuccess(LocalDateTime.now());
        assertThat(json(adminGet("/v1/admin/order-exceptions/summary")).at("/kindCounts/TRACKING_STALLED").asLong())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("A-06 계약 큐(55 계획서 A1 · A2) — 해지 요청 · 검토 대기는 대기 행(경과 = 큐 기준 열) · 계약 배지 = 계약 요약 actionRequiredCount · 할 일 합계에 든다")
    void contractQueues() throws Exception {
        long totalBefore = json(adminGet(HOME).andExpect(status().isOk())).at("/todo/total").asLong();
        jdbc.update("INSERT INTO contract_termination_request (contract_id, requester_type, requester_id, reason_code, "
                        + "status, requested_at) VALUES (?, 'SELLER', ?, 'QUALITY_ISSUE', 'PENDING', ?)",
                groupBuy.getContract().getId(), brand.seller().getId(), LocalDateTime.now().minusDays(2));
        GroupBuy reviewing = seedIn(GroupBuyStatus.PREPARING);
        jdbc.update("UPDATE contract SET status = 'REVIEW_PENDING', review_requested_at = ? WHERE contract_id = ?",
                LocalDateTime.now().minusDays(3), reviewing.getContract().getId());

        JsonNode todo = json(adminGet(HOME).andExpect(status().isOk())).get("todo");
        JsonNode termination = rowOf(todo, "CONTRACT_TERMINATION");
        assertThat(termination.get("urgency").asText()).isEqualTo("WAITING");
        assertThat(termination.get("menu").asText()).isEqualTo("CONTRACTS");
        assertThat(termination.get("title").asText()).isEqualTo("계약 해지 요청");
        assertThat(termination.get("count").asLong()).isEqualTo(1);
        assertThat(termination.get("ageText").asText()).isEqualTo("2일 경과");
        assertThat(termination.at("/target/type").asText()).isEqualTo("QUEUE");
        assertThat(termination.at("/target/filter").asText()).isEqualTo("/contracts?queue=TERMINATION");
        JsonNode review = rowOf(todo, "CONTRACT_REVIEW");
        assertThat(review.get("ageText").asText()).isEqualTo("3일 경과");
        assertThat(review.at("/target/filter").asText()).isEqualTo("/contracts?queue=REVIEW");
        assertThat(todo.get("total").asLong()).isEqualTo(totalBefore + 2);

        JsonNode summary = json(adminGet(CONTRACT_SUMMARY).andExpect(status().isOk()));
        assertThat(summary.at("/queues/TERMINATION").asLong()).isEqualTo(1);
        assertThat(summary.at("/queues/REVIEW").asLong()).isEqualTo(1);
        assertThat(json(adminGet(BADGES).andExpect(status().isOk())).get("contracts").asLong())
                .isEqualTo(summary.get("actionRequiredCount").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("A-07 소통 스레드 배지(55 계획서 A12 · D24) — 운영팀 채널에 회원 답글이 오면 threads +1(메시지 수) · posts.total · 할 일 합계는 그대로 · 읽으면 빠진다")
    void operatorThreadBadge() throws Exception {
        MessageThread channel = inTransaction(() -> operatorChannels.requireMarketChannel(brand.market()));
        JsonNode before = json(adminGet(BADGES).andExpect(status().isOk()));
        long totalBefore = json(adminGet(HOME).andExpect(status().isOk())).at("/todo/total").asLong();

        for (String content : List.of("정산 일정 문의드립니다.", "확인 부탁드립니다.")) {
            sellerPost("/v1/seller/threads/" + channel.getId() + "/messages",
                    Map.of("clientMessageId", UUID.randomUUID().toString(), "content", content))
                    .andExpect(status().isCreated());
        }

        JsonNode after = json(adminGet(BADGES).andExpect(status().isOk()));
        assertThat(after.at("/posts/threads").asLong()).isEqualTo(before.at("/posts/threads").asLong() + 2);
        assertThat(after.at("/posts/total").asLong()).isEqualTo(before.at("/posts/total").asLong());
        assertThat(json(adminGet(HOME)).at("/todo/total").asLong()).isEqualTo(totalBefore);
        JsonNode tabs = json(adminGet("/v1/admin/connections/summary").andExpect(status().isOk()));
        assertThat(after.at("/posts/threads").asLong())
                .isEqualTo(tabs.at("/brand/unreadCount").asLong() + tabs.at("/influencer/unreadCount").asLong());

        mockMvc.perform(post("/v1/admin/threads/" + channel.getId() + "/read").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        assertThat(json(adminGet(BADGES)).at("/posts/threads").asLong()).isEqualTo(before.at("/posts/threads").asLong());
    }

    // ------------------------------------------------------------------ 픽스처

    private static JsonNode rowOf(JsonNode todo, String queue) {
        for (JsonNode row : todo.get("rows")) {
            if (row.get("queue").asText().equals(queue)) {
                return row;
            }
        }
        throw new AssertionError("no row for " + queue);
    }

    private void seedInquiry(LocalDateTime createdAt, String content) {
        jdbc.update("INSERT INTO one_to_one_inquiry (user_id, type, content, status, created_at, modified_at) "
                + "VALUES (?, 'DELIVERY', ?, 'WAITING', ?, ?)", consumer.getId(), content, createdAt, createdAt);
    }

    /** 운영자 사유 환불 편입만(집행 전) — 집행 대기 큐. */
    private Long requestOperatorRefund(OrderDeliveryGroup group) throws Exception {
        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks",
                Map.of("reason", "POST_CONFIRM_DEFECT", "amount", 1_000, "detail", "구매확정 후 하자 확인"))
                .andExpect(status().isOk());
        return jdbc.queryForObject("SELECT MAX(refund_task_id) FROM order_refund_task "
                + "WHERE delivery_group_id = ? AND source = 'OPERATOR_REASON'", Long.class, group.getId());
    }
}
