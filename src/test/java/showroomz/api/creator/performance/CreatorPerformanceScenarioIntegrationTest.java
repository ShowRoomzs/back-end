package showroomz.api.creator.performance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollowEvent;
import showroomz.domain.member.creator.repository.CreatorFollowEventRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImpression;
import showroomz.domain.post.entity.PostLike;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.product.entity.ProductDetailView;
import showroomz.domain.product.repository.ProductDetailViewRepository;
import showroomz.domain.product.type.ProductDetailViewSource;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.showroom.entity.ShowroomVisit;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쇼룸 스튜디오 성과 관리(10) — 테스트 시나리오(dev/성과관리 공통/쇼룸 성과관리/49_쇼룸스튜디오_성과관리_테스트_시나리오.md)의
 * 보완 케이스. 기본 흐름(P-01 · P-05 · P-08 · P-09 ① · P-10 일부)은 {@link CreatorPerformanceIntegrationTest}가 본다.
 *
 * <p>시나리오 9절(판정 보류) 항목 — 분배 실패의 스튜디오 상태 · {@code size ≤ 0} · KPI 성비 가림 · 판매 0건만 있을 때의 L2 문구 ·
 * 구매전환율 정렬의 조정 협의 행 · 팔로워 경계 시각 — 은 결정 전이라 여기서 단언하지 않는다.
 *
 * <p>시드 공구: 크림 27,200(리워드 12% · 단위 3,264) · 세럼 24,000(10% · 단위 2,400) · 고정 지급비 300,000.
 */
@DisplayName("쇼룸 스튜디오 성과 관리(10) — 시나리오 보완")
class CreatorPerformanceScenarioIntegrationTest extends SettlementTestSupport {

    private static final String PERFORMANCE = "/v1/creator/performance";
    private static final long FIXED_FEE = 300_000;
    private static final long CREAM_REWARD = 3_264;
    private static final long SERUM_REWARD = 2_400;

    @Autowired private ShowroomVisitRepository showroomVisitRepository;
    @Autowired private CreatorFollowEventRepository followEventRepository;
    @Autowired private PostImpressionRepository postImpressionRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private ProductDetailViewRepository productDetailViewRepository;

    // ------------------------------------------------------------------ 시드

    private String detailUrl() {
        return PERFORMANCE + "/" + groupBuy.getId();
    }

    private JsonNode detail() throws Exception {
        return json(creatorGet(detailUrl()).andExpect(status().isOk()));
    }

    private JsonNode detailOf(Long groupBuyId) throws Exception {
        return json(creatorGet(PERFORMANCE + "/" + groupBuyId).andExpect(status().isOk()));
    }

    private Settlement settledAsPaid() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return markPaid(generate(LocalDateTime.now().withNano(0)));
    }

    private Settlement markPaid(Settlement s) {
        jdbc.update("UPDATE settlement SET status = 'PAID', paid_at = ? WHERE settlement_id = ?",
                LocalDateTime.now().withNano(0), s.getId());
        return settlement(s.getId());
    }

    private void settlementStatus(Settlement s, String status) {
        jdbc.update("UPDATE settlement SET status = ? WHERE settlement_id = ?", status, s.getId());
    }

    /** 공구 기간을 옮긴다 — 집계 구간은 [start_at, ended_at). */
    private void window(Long groupBuyId, LocalDateTime start, LocalDateTime end) {
        jdbc.update("UPDATE group_buy SET start_at = ?, end_at = ?, ended_at = ? WHERE group_buy_id = ?",
                start, end, end, groupBuyId);
    }

    private void paidAt(OrderDeliveryGroup group, LocalDateTime at) {
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = "
                + "(SELECT order_id FROM order_delivery_group WHERE delivery_group_id = ?)", at, group.getId());
    }

    private void visit(Users user, String visitorKey, ShowroomVisitSource source, LocalDateTime at) {
        inTransaction(() -> showroomVisitRepository.save(new ShowroomVisit(
                creatorRepository.findById(creator.getId()).orElseThrow(), user, visitorKey, source, at)));
    }

    private void impression(Long postId, Users user, String viewerKey, LocalDateTime at) {
        inTransaction(() -> postImpressionRepository.save(new PostImpression(
                postRepository.findById(postId).orElseThrow(), creator.getId(), user, viewerKey, at)));
    }

    private void follow(Users user, LocalDateTime at) {
        followEventRepository.save(CreatorFollowEvent.follow(creator.getId(), user.getId(), at, null));
    }

    private void unfollow(Users user, LocalDateTime at) {
        followEventRepository.save(CreatorFollowEvent.unfollow(creator.getId(), user.getId(), at));
    }

    private Long approvedPost() {
        return seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false).getPostId();
    }

    /** 공구에 묶이지 않은 같은 인플루언서의 다른 게시물 — 귀속 오답용. */
    private Long otherPost() {
        return inTransaction(() -> postRepository.save(Post.draft(
                creatorRepository.findById(creator.getId()).orElseThrow(), "일상 게시물", null)).getId());
    }

    private void identity(Users user, String gender) {
        inTransaction(() -> {
            Users u = userRepository.findById(user.getId()).orElseThrow();
            u.updateIdentity(u.getNickname(), "1998-03-02", gender, "010-1234-5678", LocalDateTime.now());
            return null;
        });
    }

    /** 이후 주문을 다른 소비자가 하게 바꾼다 — 기본 배송지까지 깐다. */
    private Users switchConsumer(String username) {
        Users user = createConsumer(username, username);
        consumer = user;
        consumerToken = bearerToken(user.getUsername(), RoleType.USER, user.getId());
        address = deliveryAddressRepository.save(DeliveryAddress.builder()
                .user(user).recipientName(username).zipCode("06234").address("서울 강남구 테헤란로 000")
                .detailAddress("쇼룸타워 12층").phoneNumber("010-1234-5678").isDefault(true).build());
        return user;
    }

    private Long seedAdjustment(Settlement s, String status, Long agreedAmount, LocalDateTime closedAt) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("INSERT INTO settlement_adjustment (settlement_id, settlement_number, group_buy_id, market_id, "
                        + "creator_id, thread_id, requester_type, status, original_reward_amount, max_reward_amount, "
                        + "agreed_reward_amount, final_reward_amount, opened_at, deadline_at, notice_due_at, closed_at, "
                        + "created_at, modified_at) VALUES (?, ?, ?, ?, ?, ?, 'CREATOR', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.getId(), s.getSettlementNumber(), s.getGroupBuyId(), s.getMarketId(), s.getCreatorId(), pairThreadId,
                status, s.getOriginalRewardAmount(), s.getOriginalRewardAmount() * 2, agreedAmount, agreedAmount,
                now.minusDays(2), now.plusDays(10), now.plusDays(9), closedAt, now, now);
        return jdbc.queryForObject("SELECT adjustment_id FROM settlement_adjustment WHERE settlement_id = ?",
                Long.class, s.getId());
    }

    private void seedProposal(Long adjustmentId, int seq, String proposer, long amount, String status,
                              LocalDateTime at) {
        long proposerId = "CREATOR".equals(proposer) ? creator.getId() : brand.marketId();
        jdbc.update("INSERT INTO settlement_adjustment_proposal (adjustment_id, seq, proposer_type, proposer_id, "
                        + "reward_amount, reason, status, proposed_at) VALUES (?, ?, ?, ?, ?, '사유', ?, ?)",
                adjustmentId, seq, proposer, proposerId, amount, status, at);
    }

    /** 1-4 #3 — 블록 「예정」 플래그 여덟 개를 한 번에. */
    private static List<Boolean> estimatedFlags(JsonNode d) {
        return List.of(d.at("/kpi/confirmedSales/estimated").asBoolean(),
                d.at("/kpi/conversionRate/estimated").asBoolean(),
                d.at("/kpi/repeatRate/estimated").asBoolean(),
                d.at("/kpi/genderEstimated").asBoolean(),
                d.at("/funnel/estimated").asBoolean(),
                d.at("/sales/estimated").asBoolean(),
                d.at("/consumers/estimated").asBoolean(),
                d.at("/followers/conversionEstimated").asBoolean());
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.at("/content").forEach(row -> ids.add(row.at("/groupBuyId").asLong()));
        return ids;
    }

    private static JsonNode product(JsonNode d, Long productId) {
        for (JsonNode row : d.at("/sales/products")) {
            if (row.at("/productId").asLong() == productId) {
                return row;
            }
        }
        throw new AssertionError("상품 행 없음: " + productId);
    }

    // ------------------------------------------------------------------ 2절 가시성 · 권한

    @Test
    @DisplayName("VS-01 ~ VS-04 · VS-07 · VS-09 — 종료 전 상태는 전부 409 · 남의 공구는 진행 중이어도 404 · 없는 id 404 · SETTLED 공구 열림 · 비인증 401 · 다른 역할 403")
    void visibilityByStatus() throws Exception {
        confirmedGroup(creamVariant, 1);
        for (GroupBuyStatus st : List.of(GroupBuyStatus.PREPARING, GroupBuyStatus.READY,
                GroupBuyStatus.SUSPENSION_SCHEDULED, GroupBuyStatus.SUSPENDED)) {
            moveTo(groupBuy.getId(), st);
            creatorGet(detailUrl()).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_READY"));
            creatorGet(PERFORMANCE).andExpect(jsonPath("$.content.length()").value(0));
        }
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);                 // VS-09 — 매번 판정한다
        creatorGet(detailUrl()).andExpect(status().isOk());
        moveTo(groupBuy.getId(), GroupBuyStatus.SETTLED);               // VS-04
        creatorGet(detailUrl()).andExpect(status().isOk());
        creatorGet(PERFORMANCE).andExpect(jsonPath("$.content.length()").value(1));

        Creator other = createCreator("다른_쇼룸", "other-creator");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy others = seed(brand, other, "남의 진행 중 공구", now.minusDays(1), now.plusDays(5));
        moveTo(others.getId(), GroupBuyStatus.IN_PROGRESS);
        JsonNode notMine = json(creatorGet(PERFORMANCE + "/" + others.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_FOUND")));       // VS-02 — 409 가 아니다
        JsonNode missing = json(creatorGet(PERFORMANCE + "/9999999").andExpect(status().isNotFound()));
        assertThat(missing).isEqualTo(notMine);                                     // VS-03 — 코드 · 문구 모두 같다

        mockMvc.perform(get(PERFORMANCE)).andExpect(status().isUnauthorized());     // VS-07
        String userToken = bearerToken(consumer.getUsername(), RoleType.USER, consumer.getId());
        for (String url : List.of(PERFORMANCE, PERFORMANCE + "/summary", detailUrl())) {
            mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, userToken)).andExpect(status().isForbidden());
            mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, brandToken)).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("VS-05 · P-11 판매 0건 공구 — 상세 404 · 목록 · 칩에 없음 / VS-06 전부 취소된 공구 — 모집단에 들고 정산 완료면 내 리워드 = 고정 지급비")
    void zeroSalesAndAllCancelled() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy zeroSales = seed(brand, creator, "판매 0건 공구", now.minusDays(9), now.minusDays(2));
        moveTo(zeroSales.getId(), GroupBuyStatus.ENDED);
        creatorGet(PERFORMANCE + "/" + zeroSales.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_FOUND"));
        creatorGet(PERFORMANCE).andExpect(jsonPath("$.content.length()").value(0));
        creatorGet(PERFORMANCE + "/summary").andExpect(jsonPath("$.statusCounts.COLLECTING").value(0));

        cancelledBySeller(preparing(paidGroup()));
        endGroupBuy();
        Settlement s = markPaid(generate(now));

        JsonNode row = json(creatorGet(PERFORMANCE).andExpect(status().isOk())).at("/content/0");
        assertThat(row.at("/groupBuyId").asLong()).isEqualTo(groupBuy.getId());
        assertThat(row.at("/status").asText()).isEqualTo("SETTLED");
        assertThat(row.at("/myRewardAmount").asLong()).isEqualTo(s.getRewardAmount() + FIXED_FEE)
                .isEqualTo(FIXED_FEE);
        assertThat(row.at("/conversionRate").isNull()).isTrue();          // 방문 0 — 잰 적 없음

        JsonNode d = detail();
        assertThat(d.at("/funnel/orderCount").asInt()).isEqualTo(1);
        assertThat(d.at("/funnel/cancelCount").asInt()).isEqualTo(1);
        assertThat(d.at("/funnel/confirmedOrderCount").asInt()).isZero();
        assertThat(d.at("/consumers/buyerCount").asInt()).isZero();
        assertThat(d.at("/reward/finalRewardAmount").asLong()).isZero();
        assertThat(d.at("/reward/totalRewardAmount").asLong()).isEqualTo(FIXED_FEE);

        JsonNode kpi = json(creatorGet(PERFORMANCE + "/summary")).at("/kpi");
        assertThat(kpi.at("/settledCount").asInt()).isEqualTo(1);
        assertThat(kpi.at("/totalReward").asLong()).isEqualTo(FIXED_FEE);
        assertThat(kpi.at("/averageRepeatRate").isNull()).isTrue();      // 구매 소비자 0 → 평균에서 빠진다(SM-14)
        assertThat(kpi.at("/averageConversionRate").isNull()).isTrue();
    }

    // ------------------------------------------------------------------ 5절 상태별 조립

    @Test
    @DisplayName("P-07 정산 확인 중 · 지급 예정(D2') — 집계 중 배지 · 예정 플래그 전부 false · 안내 없음 · 리워드는 원래 금액만 · 목록은 「—」")
    void reviewingAndPayoutScheduled() throws Exception {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));

        for (String[] step : List.of(new String[]{"REVIEWING", "정산 확인 중"},
                new String[]{"PAYOUT_SCHEDULED", "지급 예정"})) {
            settlementStatus(s, step[0]);
            JsonNode d = detail();
            assertThat(d.at("/groupBuy/status").asText()).isEqualTo("COLLECTING");
            assertThat(d.at("/groupBuy/statusTone").asText()).isEqualTo("NEUTRAL");
            assertThat(d.at("/notice").isNull()).isTrue();
            assertThat(estimatedFlags(d)).containsOnly(false);
            assertThat(d.at("/reward/stage").asText()).isEqualTo("AFTER_SETTLEMENT");
            assertThat(d.at("/reward/originalRewardAmount").asLong()).isEqualTo(s.getOriginalRewardAmount())
                    .isEqualTo(CREAM_REWARD + SERUM_REWARD);
            assertThat(d.at("/reward/finalRewardAmount").isNull()).isTrue();
            assertThat(d.at("/reward/totalRewardAmount").isNull()).isTrue();
            assertThat(d.at("/reward/payout/amount").isNull()).isTrue();
            assertThat(d.at("/rewardBar/salesReward/amount").isNull()).isTrue();
            assertThat(d.at("/rewardBar/salesReward/note").asText()).isEqualTo("확정 거래액 × 리워드율(상품별)");   // 예정 아님(55 S7)
            assertThat(d.at("/kpi/myReward/amount").isNull()).isTrue();
            assertThat(d.at("/reward/settlementId").asLong()).isEqualTo(s.getId());
            assertThat(d.at("/reward/settlementStatusLabel").asText()).isEqualTo(step[1]);

            JsonNode row = json(creatorGet(PERFORMANCE)).at("/content/0");
            assertThat(row.at("/status").asText()).isEqualTo("COLLECTING");
            assertThat(row.at("/confirmedSalesAmount").isNull()).isTrue();       // LS-02
            assertThat(row.at("/confirmedQuantity").isNull()).isTrue();
            assertThat(row.at("/conversionRate").isNull()).isTrue();
            assertThat(row.at("/myRewardAmount").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("DT-01 종결됐지만 정산 전 — 예정 플래그 true · 안내 없음 / P-05 ① 미종결은 예정에서 구매확정으로 센다")
    void collectingWithoutOpenOrders() throws Exception {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        JsonNode closed = detail();
        assertThat(closed.at("/groupBuy/status").asText()).isEqualTo("COLLECTING");
        assertThat(estimatedFlags(closed)).containsOnly(true);
        assertThat(closed.at("/notice").isNull()).isTrue();

        moveTo(groupBuy.getId(), GroupBuyStatus.IN_PROGRESS);
        paidGroup();                                                          // 미종결 크림 1
        endGroupBuy();
        JsonNode open = detail();
        assertThat(open.at("/notice/text").asText()).startsWith("처리 중인 주문 1건이 남아 있습니다.");
        assertThat(open.at("/funnel/orderCount").asInt()).isEqualTo(2);
        assertThat(open.at("/funnel/confirmedOrderCount").asInt()).isEqualTo(2);
        assertThat(open.at("/kpi/confirmedSales/basis").asText()).isEqualTo("구매확정 2건 기준");
        assertThat(open.at("/reward/originalRewardAmount").asLong()).isEqualTo(CREAM_REWARD * 2);
        assertThat(open.at("/rewardBar/salesReward/originalAmount").asLong()).isEqualTo(CREAM_REWARD * 2);
        assertThat(open.at("/rewardBar/salesReward/note").asText()).isEqualTo("확정 거래액 × 리워드율(상품별) · 예정");
        assertThat(open.at("/rewardBar/fixedFee/stage").asText()).isEqualTo("CONFIRMED");
        assertThat(open.at("/reward/settlementId").isNull()).isTrue();
    }

    @Test
    @DisplayName("P-05 ② ④ 미처리 반품 안내가 우선 · 반품 처리 결과만큼만 예정액이 준다(계산식 차이가 아니다)")
    void projectedRewardShrinksByReturn() throws Exception {
        OrderDeliveryGroup group = deliveredFrom(purchase(creamVariant, 2).group(), LocalDateTime.now().minusHours(2));
        endGroupBuy();
        JsonNode before = detail();
        assertThat(before.at("/reward/originalRewardAmount").asLong()).isEqualTo(CREAM_REWARD * 2);
        assertThat(product(before, cream.getProductId()).at("/confirmedQuantity").asLong()).isEqualTo(2);

        Long claimId = returnClaim(group, itemsOf(group).get(0), 1, ClaimReason.CHANGE_OF_MIND);
        JsonNode requested = detail();
        assertThat(requested.at("/notice/tone").asText()).isEqualTo("NEUTRAL");
        assertThat(requested.at("/notice/text").asText())
                .isEqualTo("미처리 반품 1건이 남아 있습니다. 아래 «예정» 표시 값은 처리 결과에 따라 줄어들 수 있습니다.");

        passClaim(claimId);
        confirmLater(group);
        JsonNode after = detail();
        assertThat(after.at("/reward/originalRewardAmount").asLong())
                .isEqualTo(before.at("/reward/originalRewardAmount").asLong() - CREAM_REWARD);
        assertThat(product(after, cream.getProductId()).at("/confirmedQuantity").asLong()).isEqualTo(1);
        assertThat(after.at("/funnel/confirmedOrderCount").asInt()).isEqualTo(1);   // 부분 반품 — 하위주문은 구매확정
        assertThat(after.at("/notice").isNull()).isTrue();
    }

    @Test
    @DisplayName("DT-02 ~ DT-04 조정 협의 — 협의 행이 없어도 200 · 내 제안만이면 상대 제안 null · 여러 번 오가면 각자 최신")
    void adjustingOffers() throws Exception {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        long original = s.getOriginalRewardAmount();
        settlementStatus(s, "ADJUSTING");

        JsonNode orphan = detail();                                            // DT-04
        assertThat(orphan.at("/groupBuy/status").asText()).isEqualTo("ADJUSTING");
        assertThat(orphan.at("/notice").isNull()).isTrue();
        assertThat(orphan.at("/reward/myRequest").isNull()).isTrue();
        assertThat(orphan.at("/reward/adjustmentDeadlineAt").isNull()).isTrue();

        Long adjustmentId = seedAdjustment(s, "OPEN", null, null);
        LocalDateTime t = LocalDateTime.now().minusDays(2).withNano(0);
        seedProposal(adjustmentId, 1, "CREATOR", original + 1_000, "PENDING", t);
        JsonNode mineOnly = detail();                                          // DT-02
        assertThat(mineOnly.at("/reward/myRequest/amount").asLong()).isEqualTo(original + 1_000);
        assertThat(mineOnly.at("/reward/counterProposal").isNull()).isTrue();

        jdbc.update("UPDATE settlement_adjustment_proposal SET status = 'COUNTERED' WHERE adjustment_id = ?",
                adjustmentId);
        seedProposal(adjustmentId, 2, "SELLER", original + 200, "COUNTERED", t.plusHours(1));
        seedProposal(adjustmentId, 3, "CREATOR", original + 800, "COUNTERED", t.plusHours(2));
        seedProposal(adjustmentId, 4, "SELLER", original + 500, "PENDING", t.plusHours(3));
        JsonNode latest = detail();                                            // DT-03
        assertThat(latest.at("/reward/myRequest/amount").asLong()).isEqualTo(original + 800);
        assertThat(latest.at("/reward/counterProposal/amount").asLong()).isEqualTo(original + 500);
        assertThat(latest.at("/reward/adjustmentThreadId").asLong()).isEqualTo(pairThreadId);
    }

    @Test
    @DisplayName("P-09 ② 내 요청 합의 — 「내 요청」 · 문구도 같은 주체")
    void agreedOnMyRequest() throws Exception {
        Settlement s = settledAsPaid();
        long original = s.getOriginalRewardAmount();
        LocalDateTime agreedAt = LocalDateTime.now().minusDays(1).withNano(0);
        Long adjustmentId = seedAdjustment(s, "AGREED", original + 700, agreedAt);
        seedProposal(adjustmentId, 1, "CREATOR", original + 700, "ACCEPTED", agreedAt.minusHours(5));
        jdbc.update("UPDATE settlement SET reward_amount = ? WHERE settlement_id = ?", original + 700, s.getId());

        JsonNode d = detail();
        assertThat(d.at("/reward/adjustment/agreedProposalLabel").asText()).isEqualTo("내 요청");
        assertThat(d.at("/notice/text").asText()).contains("내 요청 금액에").contains("(+700원)");
        assertThat(d.at("/reward/totalRewardAmount").asLong()).isEqualTo(original + 700 + FIXED_FEE);
        assertThat(d.at("/rewardBar/totalReward/note").asText()).contains("조정 합의 반영");
    }

    @Test
    @DisplayName("P-09 ③ 감액 합의 — 차액 음수 · 부호는 「−」 · 상품별 표는 계약 기준 그대로")
    void agreedDecrease() throws Exception {
        Settlement s = settledAsPaid();
        long original = s.getOriginalRewardAmount();
        LocalDateTime agreedAt = LocalDateTime.now().minusDays(1).withNano(0);
        Long adjustmentId = seedAdjustment(s, "AGREED", original - 1_000, agreedAt);
        seedProposal(adjustmentId, 1, "SELLER", original - 1_000, "ACCEPTED", agreedAt.minusHours(5));
        jdbc.update("UPDATE settlement SET reward_amount = ? WHERE settlement_id = ?", original - 1_000, s.getId());

        JsonNode d = detail();
        assertThat(d.at("/reward/adjustment/delta").asLong()).isEqualTo(-1_000);
        assertThat(d.at("/sales/adjustmentDelta").asLong()).isEqualTo(-1_000);
        assertThat(d.at("/notice/text").asText()).contains("(−1,000원)");
        assertThat(d.at("/rewardBar/salesReward/note").asText()).contains("조정 −1,000원");
        assertThat(d.at("/reward/finalRewardAmount").asLong()).isEqualTo(original - 1_000);
        assertThat(d.at("/sales/contractRewardAmount").asLong()).isEqualTo(original);
    }

    @Test
    @DisplayName("P-09 ④ 기한 만료 — D1 과 같은 화면(안내 · 조정 줄 없음) · 스레드 id 는 남는다")
    void expiredAdjustmentLooksLikeD1() throws Exception {
        Settlement s = settledAsPaid();
        seedAdjustment(s, "EXPIRED", null, LocalDateTime.now().minusDays(1).withNano(0));

        JsonNode d = detail();
        assertThat(d.at("/groupBuy/status").asText()).isEqualTo("SETTLED");
        assertThat(d.at("/notice").isNull()).isTrue();
        assertThat(d.at("/reward/adjustment").isNull()).isTrue();
        assertThat(d.at("/sales/adjustmentDelta").isNull()).isTrue();
        assertThat(d.at("/reward/finalRewardAmount").asLong()).isEqualTo(s.getOriginalRewardAmount());
        assertThat(d.at("/reward/adjustmentThreadId").asLong()).isEqualTo(pairThreadId);
    }

    @Test
    @DisplayName("DT-05 합의됐지만 지급 전 — 집계 중 · 「정산 후 확정」 · 합의 안내 · 조정 줄 없음")
    void agreedButNotPaid() throws Exception {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        long original = s.getOriginalRewardAmount();
        LocalDateTime agreedAt = LocalDateTime.now().minusHours(3).withNano(0);
        Long adjustmentId = seedAdjustment(s, "AGREED", original + 500, agreedAt);
        seedProposal(adjustmentId, 1, "SELLER", original + 500, "ACCEPTED", agreedAt.minusHours(1));
        jdbc.update("UPDATE settlement SET status = 'PAYOUT_SCHEDULED', reward_amount = ? WHERE settlement_id = ?",
                original + 500, s.getId());

        JsonNode d = detail();
        assertThat(d.at("/groupBuy/status").asText()).isEqualTo("COLLECTING");
        assertThat(d.at("/reward/stage").asText()).isEqualTo("AFTER_SETTLEMENT");
        assertThat(d.at("/notice").isNull()).isTrue();
        assertThat(d.at("/reward/adjustment").isNull()).isTrue();
        assertThat(d.at("/sales/adjustmentDelta").isNull()).isTrue();
        assertThat(d.at("/reward/originalRewardAmount").asLong()).isEqualTo(original);
        assertThat(d.at("/reward/finalRewardAmount").isNull()).isTrue();
    }

    @Test
    @DisplayName("DT-07 · DT-08 · DT-09 · TR-05 · FW-08 · SL-10 응답 모양 — 블록은 항상 객체 · 파트너 전용 · 권한 키 없음 · 「인스타그램」 없음 · 조회는 멱등")
    void responseShape() throws Exception {
        Settlement s = settledAsPaid();
        String first = creatorGet(detailUrl()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        JsonNode d = objectMapper.readTree(first);

        for (String block : List.of("traffic", "funnel", "sales", "consumers", "followers", "reward",
                "recollaboration", "rewardBar", "kpi")) {
            assertThat(d.get(block).isObject()).as(block).isTrue();
        }
        for (String key : List.of("delivery", "breakdown", "cost", "roas", "permissions")) {
            assertThat(d.has(key)).as(key).isFalse();
        }
        assertThat(d.at("/reward").has("permissions")).isFalse();
        assertThat(d.at("/reward").has("brandPayoutAmount")).isFalse();
        assertThat(d.at("/followers").has("total")).isFalse();
        assertThat(d.at("/kpi").has("confirmedQuantity")).isFalse();
        assertThat(first).doesNotContain("인스타그램").doesNotContain("unfollow").doesNotContain("Unfollow");

        List<String> sources = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        d.at("/traffic/sources").forEach(src -> {
            sources.add(src.at("/source").asText());
            labels.add(src.at("/label").asText());
        });
        assertThat(sources).containsExactly("INTERNAL", "PROFILE_LINK", "POST_LINK", "PRODUCT_LINK");
        assertThat(labels).containsExactly("쇼룸 내부 탐색", "프로필 쇼룸 링크", "공구 게시물 링크", "상품 상세 직접 링크");
        assertThat(d.at("/traffic/sources/0/ratio").isNull()).isTrue();      // 방문 0 — 비율 없음(TR-04)
        assertThat(d.at("/groupBuy/contractId").asLong()).isEqualTo(groupBuy.getContract().getId());   // RW-10

        String second = creatorGet(detailUrl()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(second)).isEqualTo(d);
        Settlement after = settlement(s.getId());
        assertThat(after.getStatus()).isEqualTo(s.getStatus());
        assertThat(after.getRewardAmount()).isEqualTo(s.getRewardAmount());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement", Long.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 3절 목록 · 4절 요약

    /** A 정산 완료(종료 −10일) · B 정산 완료(−20일 · 리워드 작음) · C 집계 중(−1일) · D 집계 중(−100일). */
    private record Listing(Long a, Long b, Long c, Long d) {
    }

    private Listing seedListing() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        markPaid(generate(now));
        Long a = groupBuy.getId();
        window(a, now.minusDays(17), now.minusDays(10));
        visit(null, "d:a-1", ShowroomVisitSource.DIRECT, now.minusDays(12));
        visit(null, "d:a-2", ShowroomVisitSource.DIRECT, now.minusDays(12));

        openNextGroupBuy(creator);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        markPaid(generate(now));
        Long b = groupBuy.getId();
        window(b, now.minusDays(27), now.minusDays(20));
        visit(null, "d:b-1", ShowroomVisitSource.DIRECT, now.minusDays(22));
        jdbc.update("UPDATE contract SET title = ? WHERE contract_id = ?", "Glow Serum Set",
                groupBuy.getContract().getId());

        openNextGroupBuy(creator);
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Long c = groupBuy.getId();
        window(c, now.minusDays(8), now.minusDays(1));

        openNextGroupBuy(creator);
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Long d = groupBuy.getId();
        window(d, now.minusDays(107), now.minusDays(100));
        return new Listing(a, b, c, d);
    }

    @Test
    @DisplayName("LS-04 · LS-06 ~ LS-12 · LS-15 ~ LS-19 · LS-21 · SM-02 · SM-04 목록 — 행 값 · 필터 · 검색 · 리워드/전환율 정렬 · 기간 · 페이징 · 잘못된 파라미터 400")
    void listing() throws Exception {
        Listing l = seedListing();

        JsonNode all = json(creatorGet(PERFORMANCE).andExpect(status().isOk()));
        assertThat(ids(all)).containsExactly(l.c(), l.a(), l.b(), l.d());                 // LS-06 종료일 최신순
        JsonNode rowA = all.at("/content/1");
        assertThat(rowA.at("/status").asText()).isEqualTo("SETTLED");                     // LS-04
        assertThat(rowA.at("/statusLabel").asText()).isEqualTo("정산 완료");
        assertThat(rowA.at("/statusTone").asText()).isEqualTo("SUCCESS");
        assertThat(rowA.at("/myRewardAmount").asLong()).isEqualTo(CREAM_REWARD + FIXED_FEE);
        assertThat(rowA.at("/conversionRate").asDouble()).isEqualTo(50.0);
        assertThat(rowA.at("/rewardStage").asText()).isEqualTo("CONFIRMED");
        assertThat(rowA.at("/brandName").asText()).isEqualTo("글로우랩");
        assertThat(all.at("/content/0/statusLabel").asText()).isEqualTo("집계 중");

        assertThat(ids(json(creatorGet(PERFORMANCE + "?status=SETTLED")))).containsExactly(l.a(), l.b());
        assertThat(ids(json(creatorGet(PERFORMANCE + "?status=COLLECTING&status=SETTLED")))).hasSize(4);
        assertThat(ids(json(creatorGet(PERFORMANCE + "?status=ADJUSTING")))).isEmpty();
        assertThat(ids(json(creatorGet(PERFORMANCE + "?keyword=glow")))).containsExactly(l.b());   // LS-08 대소문자
        assertThat(ids(json(mockMvc.perform(get(PERFORMANCE).param("keyword", "  글로우랩  ")
                .header(HttpHeaders.AUTHORIZATION, creatorToken))))).hasSize(4);               // 공백 무시
        assertThat(ids(json(creatorGet(PERFORMANCE + "?keyword=")))).hasSize(4);                  // LS-09
        assertThat(ids(json(creatorGet(PERFORMANCE + "?status=SETTLED&keyword=glow")))).containsExactly(l.b());
        JsonNode none = json(creatorGet(PERFORMANCE + "?keyword=없는브랜드").andExpect(status().isOk()));
        assertThat(ids(none)).isEmpty();
        assertThat(none.at("/pageInfo/totalResults").asLong()).isZero();                    // LS-21

        // LS-12 — 리워드 큰 순 · 값 없는 행은 종료일 최신순으로 맨 뒤
        assertThat(ids(json(creatorGet(PERFORMANCE + "?sort=REWARD_DESC"))))
                .containsExactly(l.a(), l.b(), l.c(), l.d());
        // LS-13(정산 완료 · 집계 중만) — B 100.0 > A 50.0 · 집계 중은 맨 뒤
        assertThat(ids(json(creatorGet(PERFORMANCE + "?sort=CONVERSION_DESC"))))
                .containsExactly(l.b(), l.a(), l.c(), l.d());

        assertThat(ids(json(creatorGet(PERFORMANCE + "?months=3")))).containsExactly(l.c(), l.a(), l.b());   // LS-15
        assertThat(ids(json(creatorGet(PERFORMANCE + "?months=12")))).hasSize(4);
        for (String bad : List.of("?months=5", "?months=0", "?months=abc", "?status=PAID", "?sort=LATEST")) {
            creatorGet(PERFORMANCE + bad).andExpect(status().isBadRequest())                 // LS-16 · LS-17
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        JsonNode p1 = json(creatorGet(PERFORMANCE + "?size=2&page=1"));                    // LS-18
        JsonNode p2 = json(creatorGet(PERFORMANCE + "?size=2&page=2"));
        JsonNode p3 = json(creatorGet(PERFORMANCE + "?size=2&page=3"));
        assertThat(ids(p1)).containsExactly(l.c(), l.a());
        assertThat(ids(p2)).containsExactly(l.b(), l.d());
        assertThat(ids(p3)).isEmpty();
        assertThat(p1.at("/pageInfo/totalResults").asLong()).isEqualTo(4);
        assertThat(ids(json(creatorGet(PERFORMANCE + "?size=2&page=0")))).isEqualTo(ids(p1));   // LS-19
        assertThat(json(creatorGet(PERFORMANCE + "?size=150").andExpect(status().isOk()))
                .at("/pageInfo/limit").asInt()).isEqualTo(100);

        JsonNode summary = json(creatorGet(PERFORMANCE + "/summary"));                      // SM-02 · SM-04
        assertThat(summary.at("/statusCounts/COLLECTING").asInt()).isEqualTo(2);
        assertThat(summary.at("/statusCounts/ADJUSTING").asInt()).isZero();
        assertThat(summary.at("/statusCounts/SETTLED").asInt()).isEqualTo(2);
        JsonNode recent = json(creatorGet(PERFORMANCE + "/summary?months=3"));
        assertThat(recent.at("/statusCounts/COLLECTING").asInt()).isEqualTo(1);
        assertThat(recent.at("/periodLabel").asText()).isEqualTo("최근 3개월");
        assertThat(recent.at("/periodFrom").asText()).isEqualTo(java.time.LocalDate.now().minusMonths(3).toString());
        creatorGet(PERFORMANCE + "/summary?months=5").andExpect(status().isBadRequest());     // SM-05
    }

    @Test
    @DisplayName("SM-01 종료 공구가 전부 집계 중 — L1(hasAnyEnded) · KPI 는 「—」 · 완료 0")
    void summaryAllCollecting() throws Exception {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        JsonNode summary = json(creatorGet(PERFORMANCE + "/summary").andExpect(status().isOk()));
        assertThat(summary.at("/hasAnyEnded").asBoolean()).isTrue();
        assertThat(summary.at("/kpi/settledCount").asInt()).isZero();
        for (String key : List.of("totalReward", "averageReward", "averageConversionRate", "averageRepeatRate",
                "averageFollowerBuyerRate", "averagePostImpressions", "genderRatio")) {
            assertThat(summary.at("/kpi/" + key).isNull()).as(key).isTrue();
        }
        assertThat(summary.at("/statusCounts/COLLECTING").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("SM-06 ~ SM-13 · CS-01 · CS-02 KPI — 모집단은 정산 완료만 · 비율은 공구별 산술평균 · 재구매는 이전 내 공구 · 성비 큰 쪽 앞")
    void summaryKpi() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Users a = consumer;
        identity(a, "FEMALE");
        follow(a, now.minusDays(30));

        // GB1 — A 크림 1 · 방문 2 · 게시물 조회 3 → 전환 50.0 · 재구매 0.0 · 팔로워 100.0
        Long post1 = approvedPost();
        OrderDeliveryGroup g1 = confirmedGroup(creamVariant, 1);
        endGroupBuy();
        markPaid(generate(now));
        Long gb1 = groupBuy.getId();
        window(gb1, now.minusDays(20), now.minusDays(13));
        paidAt(g1, now.minusDays(15));
        visit(a, "u:" + a.getId(), ShowroomVisitSource.DIRECT, now.minusDays(15));
        visit(null, "d:g1", ShowroomVisitSource.DIRECT, now.minusDays(14));
        for (int i = 0; i < 3; i++) {
            impression(post1, null, "d:g1", now.minusDays(15).plusHours(i));
        }

        // GB2 — A 세럼 1(재구매 · 팔로워) · B 크림 1 · 방문 5 · 조회 2 → 전환 40.0 · 재구매 50.0 · 팔로워 50.0
        openNextGroupBuy(creator);
        Long post2 = approvedPost();
        confirmedGroup(serumVariant, 1);
        Users b = switchConsumer("minho");
        identity(b, "MALE");
        confirmedGroup(creamVariant, 1);
        confirmedGroup(creamVariant, 1);                       // CS-01 — 같은 소비자의 두 번째 구매확정은 한 명
        endGroupBuy();
        markPaid(generate(now));
        Long gb2 = groupBuy.getId();
        visit(a, "u:" + a.getId(), ShowroomVisitSource.DIRECT, now.minusHours(1));
        visit(b, "u:" + b.getId(), ShowroomVisitSource.DIRECT, now.minusHours(1));
        for (int i = 1; i <= 3; i++) {
            visit(null, "d:g2-" + i, ShowroomVisitSource.DIRECT, now.minusHours(1));
        }
        impression(post2, null, "d:g2-1", now.minusHours(1));
        impression(post2, null, "d:g2-2", now.minusHours(1));

        // GB3 — 집계 중(정산 없음) · 방문 1 · 구매확정 1 → 들어가면 평균이 바뀐다
        openNextGroupBuy(creator);
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        window(groupBuy.getId(), now.minusDays(40), now.minusDays(33));
        visit(null, "d:g3", ShowroomVisitSource.DIRECT, now.minusDays(35));

        JsonNode g2 = detailOf(gb2);
        assertThat(g2.at("/consumers/buyerCount").asInt()).isEqualTo(2);
        assertThat(g2.at("/consumers/repeatBuyerCount").asInt()).isEqualTo(1);
        assertThat(g2.at("/consumers/newBuyerCount").asInt()).isEqualTo(1);
        assertThat(g2.at("/consumers/followerBuyerCount").asInt()).isEqualTo(1);
        assertThat(g2.at("/kpi/repeatRate/value").asDouble()).isEqualTo(50.0);
        assertThat(g2.at("/kpi/repeatRate/basis").asText()).isEqualTo("구매 소비자 2명 중 1명");
        assertThat(g2.at("/funnel/confirmedOrderCount").asInt()).isEqualTo(3);
        assertThat(detailOf(gb1).at("/consumers/repeatBuyerCount").asInt()).isZero();

        JsonNode summary = json(creatorGet(PERFORMANCE + "/summary").andExpect(status().isOk()));
        JsonNode kpi = summary.at("/kpi");
        long gb2Reward = SERUM_REWARD + CREAM_REWARD * 2;
        long total = (CREAM_REWARD + FIXED_FEE) + (gb2Reward + FIXED_FEE);
        assertThat(kpi.at("/settledCount").asInt()).isEqualTo(2);
        assertThat(kpi.at("/totalReward").asLong()).isEqualTo(total);
        assertThat(kpi.at("/averageReward").asLong()).isEqualTo(total / 2);
        assertThat(kpi.at("/averageConversionRate").asDouble()).isEqualTo(55.0);   // (50.0 + 60.0) ÷ 2 · 합산비 아님
        assertThat(kpi.at("/averageRepeatRate").asDouble()).isEqualTo(25.0);
        assertThat(kpi.at("/averageFollowerBuyerRate").asDouble()).isEqualTo(75.0);
        assertThat(kpi.at("/averagePostImpressions").asLong()).isEqualTo(2);       // 5 ÷ 2 절사
        assertThat(kpi.at("/genderRatio/majorFirst").asText()).isEqualTo("FEMALE");
        assertThat(kpi.at("/genderRatio/female").asInt()).isEqualTo(67);
        assertThat(kpi.at("/genderRatio/male").asInt()).isEqualTo(33);
        assertThat(summary.at("/statusCounts/COLLECTING").asInt()).isEqualTo(1);
        assertThat(summary.at("/statusCounts/SETTLED").asInt()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ 6-1 유입 · 6-3 판매

    @Test
    @DisplayName("P-02 · TR-01 ~ TR-04 · TR-06 · TR-07 · SL-04 · SL-05 · SL-07 유입 · 게시물 · 상품 상세 조회 — 반열림 구간 · 구간 안 첫 경로 · 귀속은 공구 게시물만")
    void trafficPostAndProducts() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long postId = approvedPost();
        Long otherPostId = otherPost();
        OrderDeliveryGroup creamOrder = confirmedGroup(creamVariant, 1);
        OrderDeliveryGroup serumOrder = confirmedGroup(serumVariant, 1);
        jdbc.update("UPDATE order_delivery_group SET attributed_post_id = ? WHERE delivery_group_id = ?", postId,
                creamOrder.getId());
        jdbc.update("UPDATE order_delivery_group SET attributed_post_id = ? WHERE delivery_group_id = ?", otherPostId,
                serumOrder.getId());
        endGroupBuy();
        LocalDateTime s = groupBuy.getStartAt();
        LocalDateTime e = now.minusHours(1);
        window(groupBuy.getId(), s, e);

        visit(null, "d:before", ShowroomVisitSource.DIRECT, s.minusSeconds(1));
        visit(null, "d:start", ShowroomVisitSource.APP_SEARCH, s);                  // 시작 포함 · INTERNAL
        visit(null, "d:direct", ShowroomVisitSource.DIRECT, s.plusHours(1));         // INTERNAL
        visit(null, "d:last", ShowroomVisitSource.GROUP_BUY_POST, e.minusSeconds(1));
        visit(null, "d:end", ShowroomVisitSource.GROUP_BUY_POST, e);                // 끝 제외
        visit(null, "d:after", ShowroomVisitSource.GROUP_BUY_POST, e.plusMinutes(30));
        visit(null, "d:multi", ShowroomVisitSource.INSTAGRAM_LINK, s.minusHours(1)); // 구간 밖은 판정 재료가 아니다
        visit(null, "d:multi", ShowroomVisitSource.PRODUCT_DIRECT_LINK, s.plusHours(2));

        impression(postId, null, "d:v1", s.plusHours(1));
        impression(postId, null, "d:v1", s.plusHours(2));
        impression(postId, null, "d:v2", s.plusHours(1));
        impression(postId, consumer, "u:" + consumer.getId(), s.plusHours(1));
        impression(postId, null, "d:v9", e);                                         // 구간 밖
        inTransaction(() -> postLikeRepository.save(new PostLike(
                userRepository.findById(consumer.getId()).orElseThrow(),
                postRepository.findById(postId).orElseThrow())));
        jdbc.update("UPDATE post_like SET created_at = ? WHERE post_id = ?", s.plusHours(3), postId);

        Long gb = groupBuy.getId();
        Long cid = creator.getId();
        Long creamId = cream.getProductId();
        Long serumId = serum.getProductId();
        inTransaction(() -> productDetailViewRepository.saveAll(List.of(
                new ProductDetailView(creamId, gb, cid, null, "d:v1", ProductDetailViewSource.POST, postId, s.plusHours(1)),
                new ProductDetailView(creamId, gb, cid, null, "d:v1", ProductDetailViewSource.POST, postId, s.plusHours(3)),
                new ProductDetailView(creamId, gb, cid, null, "d:v2", ProductDetailViewSource.POST, otherPostId,
                        s.plusHours(1)),
                new ProductDetailView(serumId, gb, cid, null, "d:v3", ProductDetailViewSource.SHOWROOM, null,
                        s.plusHours(2)),
                new ProductDetailView(creamId, null, null, null, "d:v4", ProductDetailViewSource.OTHER, null,
                        s.plusHours(2)),                                                          // 공구 밖
                new ProductDetailView(creamId, gb, cid, null, "d:v5", ProductDetailViewSource.POST, postId, e))));

        JsonNode d = detail();
        JsonNode t = d.at("/traffic");
        assertThat(t.at("/visitorCount").asLong()).isEqualTo(4);
        List<Long> visitors = new ArrayList<>();
        List<Double> ratios = new ArrayList<>();
        t.at("/sources").forEach(src -> {
            visitors.add(src.at("/visitors").asLong());
            ratios.add(src.at("/ratio").asDouble());
        });
        assertThat(visitors).containsExactly(2L, 0L, 1L, 1L);           // INTERNAL · PROFILE · POST · PRODUCT
        assertThat(ratios).containsExactly(50.0, 0.0, 25.0, 25.0);

        JsonNode p = t.at("/post");
        assertThat(p.at("/postId").asLong()).isEqualTo(postId);
        assertThat(p.at("/impressions").asLong()).isEqualTo(4);
        assertThat(p.at("/uniqueViewers").asLong()).isEqualTo(3);
        assertThat(p.at("/productViews").asLong()).isEqualTo(2);        // 다른 게시물 귀속 · 구간 밖 제외(TR-07)
        assertThat(p.at("/productViewRate").asDouble()).isEqualTo(50.0);
        assertThat(p.at("/orders").asLong()).isEqualTo(1);
        assertThat(p.at("/orderRate").asDouble()).isEqualTo(50.0);
        assertThat(p.at("/likes").asLong()).isEqualTo(1);
        assertThat(p.at("/shares").isNull()).isTrue();

        assertThat(product(d, creamId).at("/detailViews").asLong()).isEqualTo(3);   // SL-04
        assertThat(product(d, serumId).at("/detailViews").asLong()).isEqualTo(1);
        assertThat(product(d, creamId).at("/orders").asLong()).isEqualTo(1);
        assertThat(d.at("/sales/highlights/mostViewed/productId").asLong()).isEqualTo(creamId);   // SL-05
        assertThat(d.at("/sales/highlights/mostViewed/basis").asText()).isEqualTo("상세 조회 3회 · 전체의 75%");
        assertThat(d.at("/sales/highlights/bestConversion/productId").asLong()).isEqualTo(serumId);
        assertThat(d.at("/sales/highlights/bestConversion/basis").asText()).isEqualTo("조회 1회 중 1건 · 100.0%");
        assertThat(d.at("/sales/highlights/topReward/basis").asText()).contains("판매 리워드 예정액");

        JsonNode f = d.at("/funnel");                                   // SL-07
        assertThat(f.at("/visitorCount").asLong()).isEqualTo(4);
        assertThat(f.at("/orderCount").asLong()).isEqualTo(2);
        assertThat(f.at("/nonOrderVisitors").asLong()).isEqualTo(2);
        assertThat(f.at("/confirmedRate").asDouble()).isEqualTo(100.0);
        assertThat(f.at("/finalConversionRate").asDouble()).isEqualTo(50.0);
        assertThat(d.at("/kpi/conversionRate/basis").asText()).isEqualTo("쇼룸 방문 4명 대비 구매확정");
    }

    @Test
    @DisplayName("P-06 ① · SL-01 상품 행 — 계약 상품 전부(안 팔린 상품도) · 율은 행마다 · 한 하위주문의 두 상품은 각 1건 · 정산 뒤 Σ 리워드 = 계약 기준")
    void productRows() throws Exception {
        confirmedGroupWithTwoItems();
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        JsonNode live = detail();
        assertThat(live.at("/sales/products").size()).isEqualTo(2);
        assertThat(product(live, cream.getProductId()).at("/orders").asLong()).isEqualTo(2);
        assertThat(product(live, serum.getProductId()).at("/orders").asLong()).isEqualTo(1);
        assertThat(product(live, cream.getProductId()).at("/rewardRate").decimalValue()).isEqualByComparingTo("12.0");
        assertThat(product(live, serum.getProductId()).at("/rewardRate").decimalValue()).isEqualByComparingTo("10.0");

        Settlement s = generate(LocalDateTime.now().withNano(0));
        JsonNode d = detail();
        long sum = 0;
        for (JsonNode row : d.at("/sales/products")) {
            sum += row.at("/rewardAmount").asLong();
        }
        assertThat(sum).isEqualTo(d.at("/sales/contractRewardAmount").asLong())
                .isEqualTo(s.getOriginalRewardAmount()).isEqualTo(CREAM_REWARD * 2 + SERUM_REWARD);
        assertThat(d.at("/kpi/confirmedSales/value").asLong()).isEqualTo(s.getConfirmedSalesAmount());
    }

    @Test
    @DisplayName("P-06 ② · SL-02 정산 뒤에는 정산 명세 행이 정본 — 원천 주문을 바꿔도 상품 행 · 확정 거래액 그대로")
    void settlementRowsWin() throws Exception {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        JsonNode before = detail();

        jdbc.update("UPDATE order_product SET quantity = 5 WHERE delivery_group_id IN "
                + "(SELECT delivery_group_id FROM order_delivery_group WHERE group_buy_id = ?)", groupBuy.getId());
        JsonNode after = detail();
        assertThat(after.at("/sales/products")).isEqualTo(before.at("/sales/products"));
        assertThat(after.at("/kpi/confirmedSales/value").asLong()).isEqualTo(s.getConfirmedSalesAmount());
        assertThat(product(after, cream.getProductId()).at("/confirmedQuantity").asLong()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 6-2 팔로워

    @Test
    @DisplayName("P-04 · FW-01 · FW-04 ~ FW-06 팔로워 — 사건 시각 판정 · 언팔로우 뒤 주문은 비팔로워 · 재팔로우 · 증감과 종료 시점 총량")
    void followersAtEventTime() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime s = groupBuy.getStartAt();
        LocalDateTime e = now.minusHours(1);
        Users a = consumer;
        Users b = createConsumer("follower-b", "비");
        follow(a, s.minusDays(1));
        follow(b, s.plusDays(1));

        Long postId = approvedPost();
        OrderDeliveryGroup aOrder = confirmedGroup(creamVariant, 1);
        Users c = switchConsumer("follower-c");
        follow(c, s.minusDays(1));
        unfollow(c, s.plusDays(1).plusHours(12));
        OrderDeliveryGroup cOrder = confirmedGroup(creamVariant, 1);
        follow(c, s.plusDays(2).plusHours(6));                              // FW-01 재팔로우
        endGroupBuy();
        window(groupBuy.getId(), s, e);
        paidAt(aOrder, s.plusDays(1));
        paidAt(cOrder, s.plusDays(2));                                      // 언팔로우 뒤 · 재팔로우 전

        visit(a, "u:" + a.getId(), ShowroomVisitSource.DIRECT, s.plusHours(1));
        visit(b, "u:" + b.getId(), ShowroomVisitSource.DIRECT, s.plusHours(12));   // 팔로우 전 방문
        visit(c, "u:" + c.getId(), ShowroomVisitSource.DIRECT, s.plusHours(1));
        visit(null, "d:anon", ShowroomVisitSource.DIRECT, s.plusHours(1));
        impression(postId, a, "u:" + a.getId(), s.plusHours(1));
        impression(postId, b, "u:" + b.getId(), s.plusHours(2));
        impression(postId, null, "d:anon", s.plusHours(2));

        JsonNode d = detail();
        JsonNode follower = d.at("/followers/follower");
        JsonNode other = d.at("/followers/nonFollower");
        assertThat(follower.at("/visitors").asLong()).isEqualTo(2);             // A · C
        assertThat(other.at("/visitors").asLong()).isEqualTo(2);                // B(팔로우 전) · 비로그인
        assertThat(follower.at("/orders").asLong()).isEqualTo(1);               // A
        assertThat(other.at("/orders").asLong()).isEqualTo(1);                  // C(언팔로우 뒤 결제)
        assertThat(follower.at("/postViewers").asLong()).isEqualTo(1);          // FW-04
        assertThat(other.at("/postViewers").asLong()).isEqualTo(2);
        assertThat(follower.at("/conversionRate").asDouble()).isEqualTo(50.0);  // FW-05
        assertThat(other.at("/conversionRate").asDouble()).isEqualTo(50.0);
        assertThat(follower.at("/confirmedOrders").asLong()).isEqualTo(1);      // 전환율 분자(55 S8) — A
        assertThat(other.at("/confirmedOrders").asLong()).isEqualTo(1);         // C
        assertThat(follower.at("/confirmedOrders").asLong() + other.at("/confirmedOrders").asLong())
                .isEqualTo(d.at("/funnel/confirmedOrderCount").asLong());       // 두 열 합 = 퍼널 구매확정
        assertThat(d.at("/followers/followerVisitorRate").asDouble()).isEqualTo(50.0);   // FW-06
        assertThat(d.at("/followers/delta").asInt()).isEqualTo(1);              // B +1 · C −1 · C +1
        assertThat(d.at("/followers/countAtEnd").asInt()).isEqualTo(3);         // A · B · C
        assertThat(d.at("/kpi/followerDelta/delta").asInt()).isEqualTo(1);
        assertThat(d.at("/kpi/followerDelta/countAtEnd").asInt()).isEqualTo(3);
        assertThat(d.at("/consumers/followerBuyerCount").asInt()).isEqualTo(1);
        assertThat(d.at("/consumers/followerBuyerRate").asDouble()).isEqualTo(50.0);
    }

    // ------------------------------------------------------------------ 6-5 리워드

    @Test
    @DisplayName("RW-01 ~ RW-04 실지급 — 정산 전은 현재 사업자 구분 · 정산 뒤는 정산 스냅샷이 이긴다 · 금액은 정산 행 그대로(재계산 없음)")
    void payoutLine() throws Exception {
        makeCreatorBusiness();
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        JsonNode before = detail();                                             // RW-04
        assertThat(before.at("/reward/payout/businessType").asText()).isEqualTo("BUSINESS");
        assertThat(before.at("/reward/payout/note").asText()).isEqualTo("사업자 · 부가세 포함 금액");
        assertThat(before.at("/reward/payout/amount").isNull()).isTrue();

        Settlement s = markPaid(generate(LocalDateTime.now().withNano(0)));
        jdbc.update("UPDATE creator SET business_type = 'INDIVIDUAL' WHERE creator_id = ?", creator.getId());
        JsonNode paid = detail();                                               // RW-03
        assertThat(paid.at("/reward/payout/businessType").asText()).isEqualTo("BUSINESS");
        assertThat(paid.at("/reward/payout/amount").asLong()).isEqualTo(s.getCreatorPayoutAmount());   // RW-02

        jdbc.update("UPDATE settlement SET creator_payout_amount = 123456 WHERE settlement_id = ?", s.getId());
        assertThat(detail().at("/reward/payout/amount").asLong()).isEqualTo(123_456);                  // RW-01
    }

    @Test
    @DisplayName("RW-05 · RW-06 고정 지급비 — 계약 금액 그대로 · 지급 여부 필드 없음 · 비어 있으면 0 이고 내 리워드는 판매 리워드만")
    void fixedFee() throws Exception {
        Settlement s = settledAsPaid();
        JsonNode d = detail();
        // 확정(D1) — 판매 리워드 보조줄은 산식 그대로(1021 기-11 · 55 S7) · 「예정」이 붙지 않는다.
        assertThat(d.at("/rewardBar/salesReward/stage").asText()).isEqualTo("CONFIRMED");
        assertThat(d.at("/rewardBar/salesReward/note").asText()).isEqualTo("확정 거래액 × 리워드율(상품별)");
        assertThat(d.at("/reward/fixedFeeAmount").asLong()).isEqualTo(FIXED_FEE);
        assertThat(d.at("/rewardBar/fixedFee/note").asText()).isEqualTo("브랜드 직접 지급");
        assertThat(d.at("/rewardBar/fixedFee").has("paid")).isFalse();
        assertThat(d.at("/reward").has("fixedFeePaid")).isFalse();

        jdbc.update("UPDATE contract SET fixed_fee_amount = NULL WHERE contract_id = ?", groupBuy.getContract().getId());
        JsonNode none = detail();
        assertThat(none.at("/reward/fixedFeeAmount").asLong()).isZero();
        assertThat(none.at("/reward/totalRewardAmount").asLong()).isEqualTo(s.getRewardAmount());
        assertThat(json(creatorGet(PERFORMANCE)).at("/content/0/myRewardAmount").asLong())
                .isEqualTo(s.getRewardAmount());
    }

    // ------------------------------------------------------------------ 7절 원천 로그

    @Test
    @DisplayName("PV-01 ~ PV-04 · PV-06 상품 상세 조회 적재 — 비로그인 204(visitorId 키) · visitorId 없으면 400 · 로그인은 사용자 키 + 게시물 귀속 · 30분 세션")
    void productDetailViewEndpoint() throws Exception {
        Long postId = approvedPost();
        impression(postId, consumer, "u:" + consumer.getId(), LocalDateTime.now().minusHours(1));
        String url = "/v1/user/products/" + cream.getProductId() + "/views";

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("source", "post", "visitorId", "abc"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("source", "SHOWROOM", "visitorId", "abc"))))
                .andExpect(status().isNoContent());                         // 같은 세션 — 적재 안 함
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("source", "POST"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT viewer_key, user_id, group_buy_id, creator_id, "
                + "source, attributed_post_id FROM product_detail_view ORDER BY view_id");
        assertThat(rows).hasSize(2);
        Map<String, Object> anon = rows.get(0);
        assertThat(anon.get("VIEWER_KEY")).isEqualTo("d:abc");
        assertThat(anon.get("USER_ID")).isNull();
        assertThat(anon.get("SOURCE")).isEqualTo("POST");
        assertThat(((Number) anon.get("GROUP_BUY_ID")).longValue()).isEqualTo(groupBuy.getId());
        assertThat(((Number) anon.get("CREATOR_ID")).longValue()).isEqualTo(creator.getId());
        assertThat(anon.get("ATTRIBUTED_POST_ID")).isNull();                // 비로그인 키는 게시물을 본 적 없다
        Map<String, Object> mine = rows.get(1);
        assertThat(mine.get("VIEWER_KEY")).isEqualTo("u:" + consumer.getId());
        assertThat(mine.get("SOURCE")).isEqualTo("OTHER");
        assertThat(((Number) mine.get("ATTRIBUTED_POST_ID")).longValue()).isEqualTo(postId);
    }

    @Test
    @DisplayName("AT-01 · AT-04 · AT-07 결제 귀속 — 결제 직전 본 공구 게시물이 하위주문에 붙고 한 번만 쓰인다 · 성과 「게시물 → 주문」")
    void paymentAttribution() throws Exception {
        Long postId = approvedPost();
        Long otherPostId = otherPost();
        impression(postId, consumer, "u:" + consumer.getId(), LocalDateTime.now().minusHours(1));

        OrderDeliveryGroup group = purchase(creamVariant, 1).group();
        assertThat(attributed(group)).isEqualTo(postId);                     // AT-01

        Integer updated = inTransaction(() -> deliveryGroupRepository.attributePost(group.getId(), otherPostId));
        assertThat(updated).isZero();                                        // AT-04 — 이미 있으면 0행
        assertThat(attributed(group)).isEqualTo(postId);

        endGroupBuy();
        assertThat(detail().at("/traffic/post/orders").asLong()).isEqualTo(1);   // AT-07
    }

    private Long attributed(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT attributed_post_id FROM order_delivery_group WHERE delivery_group_id = ?",
                Long.class, group.getId());
    }

    @Test
    @DisplayName("SV-01 · SV-02 쇼룸 방문 소스 — from=product → PRODUCT_DIRECT_LINK · 모르는 값 · 빈 값 → DIRECT")
    void showroomVisitSource() {
        assertThat(ShowroomVisitSource.fromLinkValue("product")).isEqualTo(ShowroomVisitSource.PRODUCT_DIRECT_LINK);
        assertThat(ShowroomVisitSource.fromLinkValue("PRODUCT_DIRECT_LINK"))
                .isEqualTo(ShowroomVisitSource.PRODUCT_DIRECT_LINK);
        assertThat(ShowroomVisitSource.fromLinkValue(null)).isEqualTo(ShowroomVisitSource.DIRECT);
        assertThat(ShowroomVisitSource.fromLinkValue("nope")).isEqualTo(ShowroomVisitSource.DIRECT);
    }
}
