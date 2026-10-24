package showroomz.api.admin.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.metrics.service.AdminMetricsQueryService;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.PostPolicyClause;
import showroomz.domain.post.type.PostReportReason;

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

        Map<String, JsonNode> rows = rows("TODAY", "DEMAND");
        assertThat(rows.get("FOLLOWER_CONVERSION").get("current").asDouble()).isEqualTo(33.3);
        assertThat(rows.get("FOLLOWER_CONVERSION").get("currentCount").asText()).isEqualTo("1/3");
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

    // ------------------------------------------------------------------ 픽스처

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
