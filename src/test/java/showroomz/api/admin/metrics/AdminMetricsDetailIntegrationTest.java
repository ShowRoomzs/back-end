package showroomz.api.admin.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.metrics.service.AdminMetricsQueryService;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.seller.entity.SellerApplication;
import showroomz.domain.member.seller.repository.SellerApplicationRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.PostPolicyClause;
import showroomz.domain.post.type.PostReportReason;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영 지표 세부(1017 설계서 7-2 · 9절) — 팔로워 → 구매(결제 전 팔로우만) · 재구매 · 운영 SLA · 첫 답변 · 큐별 분해 · 공구 물량 · ROI ·
 * 판매 중 공구 차트 · 신규 소비자 경로 · 탈퇴 방향 · 분모 0 · 신규 / 재공구 판정이 기간 시작에 따라 바뀐다.
 */
@DisplayName("1017 운영 지표 세부")
class AdminMetricsDetailIntegrationTest extends SettlementTestSupport {

    private static final String METRICS = "/v1/admin/metrics";

    @Autowired private AdminMetricsQueryService metricsQueryService;
    @Autowired private CreatorFollowRepository creatorFollowRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostReportRepository postReportRepository;
    @Autowired private SellerApplicationRepository sellerApplicationRepository;

    /** 지표는 같은 기간을 10분 캐시한다 — 테스트마다 데이터가 다르므로 비운다. */
    @BeforeEach
    void clearMetricsCache() {
        ((Map<?, ?>) ReflectionTestUtils.getField(metricsQueryService, "cache")).clear();
    }

    @Test
    @DisplayName("MD-01 팔로워 → 구매 — 결제 전에 팔로우한 구매자 ÷ 진행 쇼룸 팔로워(「1/3」)")
    void followerConversionCountsFollowBeforePayment() throws Exception {
        purchase(creamVariant, 1);
        followEvent(consumer, LocalDateTime.now().minusDays(1));
        follow(consumer);
        follow(createConsumer("fan-1", "팬하나"));
        follow(createConsumer("fan-2", "팬둘"));

        JsonNode body = json(adminGet(METRICS + "?preset=TODAY&tab=DEMAND").andExpect(status().isOk()));
        Map<String, JsonNode> rows = rows(body);
        assertThat(rows.get("FOLLOWER_CONVERSION").get("current").asDouble()).isEqualTo(33.3);
        assertThat(rows.get("FOLLOWER_CONVERSION").get("currentCount").asText()).isEqualTo("1/3");
        // 정의 문구 · 분해 표에 「구매자」가 없다(55 계획서 A14)
        assertThat(rows.get("FOLLOWER_CONVERSION").get("note").asText()).isEqualTo("공구 구매 소비자 ÷ 진행 쇼룸 팔로워");
        assertThat(body.at("/breakdown/title").asText()).isEqualTo("쇼룸별 전환 · 구매 소비자 순 · 상위 5곳");
        assertThat(body.at("/breakdown/columns").toString())
                .isEqualTo("[\"인플루언서(쇼룸)\",\"팔로워\",\"구매 소비자\",\"전환율\"]");
        JsonNode first = body.at("/breakdown/rows/0");
        assertThat(first.get(1).asText()).isEqualTo("3");
        assertThat(first.get(2).asText()).isEqualTo("1");
        assertThat(first.get(3).asText()).isEqualTo("33.3%");
        assertThat(body.toString()).doesNotContain("구매자");
    }

    @Test
    @DisplayName("MD-02 팔로워 → 구매 — 결제 뒤에 팔로우했으면 전환으로 세지 않는다")
    void followAfterPaymentDoesNotConvert() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        LocalDateTime paidAt = jdbc.queryForObject("SELECT paid_at FROM orders WHERE order_id = ?", LocalDateTime.class,
                purchase.orderId());
        followEvent(consumer, paidAt.plusMinutes(1));
        follow(consumer);

        Map<String, JsonNode> rows = rows("TODAY", "DEMAND");
        assertThat(rows.get("FOLLOWER_CONVERSION").get("currentCount").asText()).isEqualTo("0/1");
    }

    @Test
    @DisplayName("MD-03 재구매율 — 기간 시작 전 결제 이력이 있는 구매자 · 구매 소비자 · 공구당 구매자 중앙값")
    void repurchase() throws Exception {
        purchase(creamVariant, 1);
        Purchase earlier = purchase(serumVariant, 1);
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = ?", LocalDateTime.now().minusDays(3), earlier.orderId());

        Map<String, JsonNode> rows = rows("TODAY", "DEMAND");
        assertThat(rows.get("BUYERS").get("current").asLong()).isEqualTo(1);
        assertThat(rows.get("BUYERS_MEDIAN").get("current").asDouble()).isEqualTo(1.0);
        assertThat(rows.get("REPURCHASE_RATE").get("current").asDouble()).isEqualTo(100.0);
        assertThat(rows.get("REPURCHASE_RATE").get("currentCount").asText()).isEqualTo("1/1");
        assertThat(rows.get("REPURCHASE_RATE").get("note").asText()).isEqualTo("이전 구매 이력이 있는 구매 소비자");
    }

    @Test
    @DisplayName("MD-04 운영 — SLA 준수율(24시간) · 1:1 첫 답변 평균 · 초과 건수 · 큐별 분해 표")
    void operationSla() throws Exception {
        LocalDateTime answeredAt = LocalDateTime.now().minusMinutes(30);
        answeredInquiry(answeredAt.minusHours(1), answeredAt);
        answeredInquiry(answeredAt.minusHours(30), answeredAt);

        JsonNode body = json(adminGet(METRICS + "?preset=TODAY&tab=OPERATION").andExpect(status().isOk()));
        Map<String, JsonNode> rows = rows(body);
        assertThat(rows.get("SLA_RATE").get("current").asDouble()).isEqualTo(50.0);
        assertThat(rows.get("SLA_RATE").get("currentCount").asText()).isEqualTo("1/2");
        assertThat(rows.get("INQUIRY_FIRST_ANSWER_HOURS").get("current").asDouble()).isEqualTo(15.5);
        assertThat(rows.get("INQUIRY_FIRST_ANSWER_HOURS").get("note").asText()).isEqualTo("초과 1건");
        assertThat(rows.get("PROCESSED").get("current").asLong()).isEqualTo(2);

        JsonNode inquiryRow = body.at("/breakdown/rows/0");
        assertThat(inquiryRow.get(0).asText()).isEqualTo("1:1 문의");
        assertThat(inquiryRow.get(1).asText()).isEqualTo("2");
        assertThat(inquiryRow.get(2).asText()).isEqualTo("15.5시간");
        assertThat(inquiryRow.get(3).asText()).isEqualTo("1");
        assertThat(body.at("/breakdown/rows").get(body.at("/breakdown/rows").size() - 1).get(0).asText()).isEqualTo("배송 예외");
    }

    @Test
    @DisplayName("MD-05 공구 — 기간 중 종료된 공구만 물량 · ROI 판정 · 진행 중 · 종료 분리 · 판매 중 공구 일별 차트")
    void groupBuyTab() throws Exception {
        seedIn(GroupBuyStatus.ENDED); // 판매 0 · 최소 물량 500 · 고정 지급비 30만 원

        JsonNode body = json(adminGet(METRICS + "?preset=CUSTOM&from=" + LocalDate.now().minusDays(6) + "&to="
                + LocalDate.now() + "&tab=GROUP_BUY").andExpect(status().isOk()));
        Map<String, JsonNode> rows = rows(body);
        assertThat(rows.get("GROUP_BUYS_RUN").get("current").asLong()).isEqualTo(2);
        assertThat(rows.get("GROUP_BUYS_RUN").get("note").asText()).isEqualTo("진행 중 1 · 종료 1");
        assertThat(rows.get("MIN_QUANTITY_RATE").get("currentCount").asText()).isEqualTo("0/1");
        assertThat(rows.get("BRAND_ROI").get("current").asDouble()).isEqualTo(0.0);

        JsonNode points = body.at("/chart/points");
        assertThat(body.at("/chart/metricKey").asText()).isEqualTo("SELLING_GROUP_BUYS");
        assertThat(points).hasSize(7);
        assertThat(points.get(6).get("value").asDouble()).isEqualTo(1.0); // 오늘은 진행 중 공구 하나
    }

    @Test
    @DisplayName("MD-06 수요 — 신규 소비자 경로 분해 · 소비자 탈퇴는 늘면 BAD(방향 DOWN)")
    void newConsumersAndWithdrawals() throws Exception {
        Users kakao = createConsumer("kakao-user", "카카오유저");
        jdbc.update("UPDATE USERS SET PROVIDER_TYPE = 'KAKAO' WHERE USER_ID = ?", kakao.getId());
        jdbc.update("INSERT INTO user_status_history (user_id, previous_status, new_status, reason, created_at, modified_at) "
                + "VALUES (?, 'NORMAL', 'WITHDRAWN', '탈퇴', ?, ?)", kakao.getId(), LocalDateTime.now(), LocalDateTime.now());

        Map<String, JsonNode> rows = rows("TODAY", "DEMAND");
        assertThat(rows.get("NEW_CONSUMERS").get("note").asText()).contains("카카오 1");
        assertThat(rows.get("CONSUMER_WITHDRAWALS").get("current").asLong()).isEqualTo(1);
        assertThat(rows.get("CONSUMER_WITHDRAWALS").get("deltaKind").asText()).isEqualTo("BAD");
    }

    @Test
    @DisplayName("MD-07 분모 0 — 값 null · 증감 NEUTRAL · 나빠진 지표가 없으면 탭 배지 0")
    void emptyDenominators() throws Exception {
        Map<String, JsonNode> core = rows("TODAY", "CORE");
        assertThat(core.get("RETURN_EXCHANGE_RATE").get("current").isNull()).isTrue();
        assertThat(core.get("RETURN_EXCHANGE_RATE").get("deltaKind").asText()).isEqualTo("NEUTRAL");
        assertThat(core.get("GMV_MEDIAN").get("current").isNull()).isTrue();

        JsonNode tabs = json(adminGet(METRICS + "/tabs?preset=TODAY").andExpect(status().isOk()));
        tabs.forEach(t -> assertThat(t.get("worsenedCount").asLong()).as(t.get("tab").asText()).isZero());
    }

    @Test
    @DisplayName("MD-08 공급 — 기간 시작 전에 오픈한 적 있으면 재공구 · 기간 안에서 처음 열었으면 신규")
    void newVersusReturningCreators() throws Exception {
        purchase(creamVariant, 1); // 진행 중 공구는 3일 전에 열렸다

        assertThat(rows("TODAY", "SUPPLY").get("ACTIVE_CREATORS").get("note").asText()).isEqualTo("신규 0 · 재공구 1");
        clearMetricsCache();
        Map<String, JsonNode> week = rows(json(adminGet(METRICS + "?preset=CUSTOM&from=" + LocalDate.now().minusDays(6)
                + "&to=" + LocalDate.now() + "&tab=SUPPLY").andExpect(status().isOk())));
        assertThat(week.get("ACTIVE_CREATORS").get("note").asText()).isEqualTo("신규 1 · 재공구 0");
        assertThat(week.get("ACTIVE_BRANDS").get("note").asText()).isEqualTo("신규 1 · 재공구 0");
    }

    @Test
    @DisplayName("MD-09 운영 — 게시물 신고율 설명은 「신고 N ÷ 게시물 M · 근거 서비스 운영정책 — 커뮤니티 장」이다(55 계획서 A15)")
    void postReportRateNote() throws Exception {
        Post post = postRepository.save(Post.published(creatorRepository.findById(creator.getId()).orElseThrow(),
                "신고될 게시물", new BigDecimal("0.8000"), LocalDateTime.now().minusDays(1)));
        postReportRepository.save(new PostReport(post, userRepository.findById(consumer.getId()).orElseThrow(),
                PostReportReason.MISLEADING_INFO, null, LocalDateTime.now().minusMinutes(10)));

        JsonNode reportRate = rows("TODAY", "OPERATION").get("POST_REPORT_RATE");
        String[] count = reportRate.get("currentCount").asText().split("/");
        assertThat(count[0]).isEqualTo("1");
        assertThat(Long.parseLong(count[1])).isGreaterThanOrEqualTo(1);
        assertThat(reportRate.get("note").asText())
                .isEqualTo("신고 1 ÷ 게시물 " + count[1] + " · 근거 서비스 운영정책 — 커뮤니티 장")
                .endsWith(PostPolicyClause.POLICY_CHAPTER);
    }

    @Test
    @DisplayName("[56 D2] 공구 탭 분해 표 — 공구별 성과에 「브랜드 × 인플루언서」 열(핵심 탭 공구별 거래액과 같은 자리 · 같은 값)")
    void groupBuyPerformanceHasPair() throws Exception {
        purchase(creamVariant, 1);

        JsonNode groupBuy = json(adminGet(METRICS + week() + "&tab=GROUP_BUY").andExpect(status().isOk()));
        assertThat(groupBuy.at("/breakdown/title").asText()).isEqualTo("공구별 성과 상위 5");
        assertThat(groupBuy.at("/breakdown/columns").toString())
                .isEqualTo("[\"공구\",\"브랜드 × 인플루언서\",\"거래액\",\"물량 달성\",\"ROI\"]");
        JsonNode first = groupBuy.at("/breakdown/rows/0");
        assertThat(first.toString()).isEqualTo("[\"글로우 크림 앵콜 공구\",\"글로우랩 × 글로우_지민\",\"27,200원\",\"—\",\"—\"]");

        JsonNode core = json(adminGet(METRICS + week() + "&tab=CORE").andExpect(status().isOk()));
        assertThat(core.at("/breakdown/columns/1").asText()).isEqualTo("브랜드 × 인플루언서");
        assertThat(core.at("/breakdown/rows/0/1").asText()).isEqualTo(first.get(1).asText());
    }

    @Test
    @DisplayName("[56 D3] 신규 입점 — 현재 · 비교 기간 모두 「N곳 · M명」으로 나눈다 · 설명은 정의 문구 · 증감은 회색")
    void newOnboardingBreakdownBothPeriods() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime current = yesterday.atTime(10, 0);
        LocalDateTime previous = yesterday.minusDays(1).atTime(10, 0);
        approvedSellerApplication("new-brand", "새브랜드", current.minusDays(3), current);
        approvedCreatorApplication(createConsumer("new-creator-1", "새인플1"), current.minusDays(2), current);
        approvedCreatorApplication(createConsumer("new-creator-2", "새인플2"), current.minusDays(2), current);
        approvedSellerApplication("old-brand-1", "지난브랜드1", previous.minusDays(3), previous);
        approvedSellerApplication("old-brand-2", "지난브랜드2", previous.minusDays(3), previous);

        Map<String, JsonNode> rows = rows(json(adminGet(METRICS + "?preset=CUSTOM&from=" + yesterday + "&to=" + yesterday
                + "&tab=SUPPLY").andExpect(status().isOk())));
        JsonNode onboarding = rows.get("NEW_ONBOARDING");
        assertThat(onboarding.get("current").asLong()).isEqualTo(3);
        assertThat(onboarding.get("previous").asLong()).isEqualTo(2);
        assertThat(onboarding.get("currentBreakdown").asText()).isEqualTo("1곳 · 2명");
        assertThat(onboarding.get("previousBreakdown").asText()).isEqualTo("2곳 · 0명");
        assertThat(onboarding.get("note").asText()).isEqualTo("브랜드 · 인플루언서 입점 승인");
        assertThat(onboarding.get("deltaKind").asText()).isEqualTo("NEUTRAL");
        assertThat(rows.get("ACTIVE_CREATORS").get("currentBreakdown").isNull()).isTrue(); // 나누지 않는 지표는 null
    }

    @Test
    @DisplayName("[56 D4] 보조 설명 값 — 공구 중단 「신청 1 · 직권 1」 · 공구당 거래액 「중앙값 · N건」 · 수수료 「베타 0% · 정상 요율 2% 적용 시」")
    void suspensionsMedianAndFeeNotes() throws Exception {
        purchase(creamVariant, 1);
        LocalDateTime now = LocalDateTime.now();
        Long requestId = seedPendingRequest(groupBuy.getId(), ChangeRequestType.SUSPEND, GroupBuyActorType.CREATOR,
                "CREATOR_REASON").getId();
        jdbc.update("UPDATE group_buy_change_request SET status = 'APPROVED', decided_at = ? WHERE change_request_id = ?",
                now.minusMinutes(10), requestId);
        Long suspensionId = seedNotice(groupBuy.getId(), now.plusDays(2)).getId();
        jdbc.update("UPDATE group_buy_admin_suspension SET status = 'EXECUTED', executed_at = ? WHERE admin_suspension_id = ?",
                now.minusMinutes(5), suspensionId);

        Map<String, JsonNode> groupBuyTab = rows(json(adminGet(METRICS + week() + "&tab=GROUP_BUY").andExpect(status().isOk())));
        assertThat(groupBuyTab.get("SUSPENSIONS").get("current").asLong()).isEqualTo(2);
        assertThat(groupBuyTab.get("SUSPENSIONS").get("note").asText()).isEqualTo("신청 1 · 직권 1");
        assertThat(groupBuyTab.get("GMV_MEDIAN").get("note").asText()).isEqualTo("중앙값 · 1건");

        Map<String, JsonNode> core = rows(json(adminGet(METRICS + week() + "&tab=CORE").andExpect(status().isOk())));
        assertThat(core.get("GMV_MEDIAN").get("note").asText()).isEqualTo("중앙값 · 1건");
        assertThat(core.get("FEE_REVENUE").get("note").asText()).isEqualTo("베타 0% · 정상 요율 2% 적용 시");
    }

    @Test
    @DisplayName("[56 D4] 환불 실패 설명 — 기간 중 실패 건의 저장된 사유 「모두 …」 · 재시도 성공으로 사유가 지워진 건은 「외 N건」")
    void refundFailureReasons() throws Exception {
        Long cream = operatorRefund(confirmedGroup(creamVariant, 1), "POST_CONFIRM_DEFECT", 1_000);
        Long serum = operatorRefund(confirmedGroup(serumVariant, 1), "POST_CONFIRM_DEFECT", 1_000);
        for (Long taskId : new Long[]{cream, serum}) {
            jdbc.update("UPDATE order_refund_task SET status = 'FAILED', failed_at = ?, last_error = '카드 유효기간 만료' "
                    + "WHERE refund_task_id = ?", LocalDateTime.now().minusMinutes(5), taskId);
        }

        JsonNode failures = rows(json(adminGet(METRICS + week() + "&tab=OPERATION").andExpect(status().isOk())))
                .get("REFUND_FAILURES");
        assertThat(failures.get("current").asLong()).isEqualTo(2);
        assertThat(failures.get("note").asText()).isEqualTo("모두 카드 유효기간 만료");

        clearMetricsCache();
        jdbc.update("UPDATE order_refund_task SET status = 'DONE', last_error = NULL WHERE refund_task_id = ?", serum);
        failures = rows(json(adminGet(METRICS + week() + "&tab=OPERATION").andExpect(status().isOk()))).get("REFUND_FAILURES");
        assertThat(failures.get("current").asLong()).isEqualTo(2); // 실패 시각은 남는다
        assertThat(failures.get("note").asText()).isEqualTo("카드 유효기간 만료 1건 외 1건");
    }

    @Test
    @DisplayName("[56 D4] 정산 조정 설명 — 「정산 N건 중 M건 · 합의 대기 K건」(협의 중인 것만 합의 대기)")
    void adjustmentAwaitingAgreement() throws Exception {
        Settlement open = seedSettlement("조정 협의 중 공구", 1_000_000, SettlementStatus.REVIEWING);
        Settlement agreed = seedSettlement("조정 합의 공구", 1_000_000, SettlementStatus.REVIEWING);
        adjustment(open, "OPEN", pairThreadId);
        adjustment(agreed, "AGREED", pairThreadId + 10_000);

        JsonNode rate = rows(json(adminGet(METRICS + week() + "&tab=OPERATION").andExpect(status().isOk())))
                .get("ADJUSTMENT_REQUEST_RATE");
        assertThat(rate.get("currentCount").asText()).isEqualTo("2/2");
        assertThat(rate.get("note").asText()).isEqualTo("2건 중 2건 · 합의 대기 1건");
    }

    // ------------------------------------------------------------------ 픽스처

    private void adjustment(Settlement settlement, String status, Long threadId) {
        LocalDateTime openedAt = LocalDateTime.now().minusHours(3);
        jdbc.update("INSERT INTO settlement_adjustment (settlement_id, settlement_number, group_buy_id, market_id, creator_id, "
                        + "thread_id, requester_type, status, original_reward_amount, max_reward_amount, opened_at, deadline_at, "
                        + "notice_due_at, created_at, modified_at) VALUES (?, ?, ?, ?, ?, ?, 'CREATOR', ?, 1000, 2000, ?, ?, ?, ?, ?)",
                settlement.getId(), settlement.getSettlementNumber(), settlement.getGroupBuyId(), brand.marketId(),
                creator.getId(), threadId, status, openedAt, openedAt.plusDays(3), openedAt.plusDays(2), openedAt, openedAt);
    }

    /** 입점 승인된 브랜드 신청 — 신청 화면이 만드는 행에 상태 · 신청 · 처리 시각만 옮긴다. */
    private void approvedSellerApplication(String account, String marketName, LocalDateTime createdAt,
                                           LocalDateTime processedAt) {
        BrandFixture.Brand applicant = fixture.createBrand(account + "@showroomz.test", marketName);
        SellerApplication application = sellerApplicationRepository.save(
                SellerApplication.createFrom(applicant.seller(), marketName, "02-1234-5678"));
        jdbc.update("UPDATE seller_application SET status = 'APPROVED', created_at = ?, processed_at = ? "
                + "WHERE seller_application_id = ?", createdAt, processedAt, application.getId());
    }

    private void approvedCreatorApplication(Users applicant, LocalDateTime createdAt, LocalDateTime processedAt) {
        String accountId = applicant.getUsername();
        jdbc.update("INSERT INTO CREATOR_APPLICATION (USER_ID, SNS_TYPE, CHANNEL_URL, ACCOUNT_ID, STATUS, PROCESSED_AT, "
                        + "created_at, modified_at) VALUES (?, 'INSTAGRAM', ?, ?, 'APPROVED', ?, ?, ?)", applicant.getId(),
                "https://instagram.com/" + accountId, accountId, processedAt, createdAt, processedAt);
    }

    /** 지난 7일 + 오늘 — 자정 경계에 걸리지 않는 기간. */
    private static String week() {
        return "?preset=CUSTOM&from=" + LocalDate.now().minusDays(6) + "&to=" + LocalDate.now();
    }

    private Map<String, JsonNode> rows(String preset, String tab) throws Exception {
        return rows(json(adminGet(METRICS + "?preset=" + preset + "&tab=" + tab).andExpect(status().isOk())));
    }

    private static Map<String, JsonNode> rows(JsonNode body) {
        Map<String, JsonNode> rows = new HashMap<>();
        body.get("rows").forEach(r -> rows.put(r.get("key").asText(), r));
        return rows;
    }

    private void followEvent(Users user, LocalDateTime at) {
        jdbc.update("INSERT INTO creator_follow_event (creator_id, user_id, event_type, occurred_at) VALUES (?, ?, 'FOLLOW', ?)",
                creator.getId(), user.getId(), at);
    }

    private void follow(Users user) {
        transactionTemplate.executeWithoutResult(tx -> creatorFollowRepository.save(new CreatorFollow(
                userRepository.findById(user.getId()).orElseThrow(), creatorRepository.findById(creator.getId()).orElseThrow())));
    }

    private void answeredInquiry(LocalDateTime createdAt, LocalDateTime answeredAt) {
        jdbc.update("INSERT INTO one_to_one_inquiry (user_id, type, content, status, answer_content, answered_at, created_at, "
                        + "modified_at) VALUES (?, 'DELIVERY', '배송 문의', 'ANSWERED', '안내드렸습니다', ?, ?, ?)",
                consumer.getId(), answeredAt, createdAt, answeredAt);
    }
}
