package showroomz.api.creator.performance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollowEvent;
import showroomz.domain.member.creator.repository.CreatorFollowEventRepository;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.showroom.entity.ShowroomVisit;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쇼룸 스튜디오 성과 관리(10) — 49 스튜디오 성과 설계서 6-2 의 P-01 · P-04 · P-05 · P-09 · P-10 중 응답 레이어.
 * 지표 산식 · 하위주문 분류는 공통 리더(48)가 소유하고, 여기서는 <b>스튜디오의 창</b>(3종 상태 · 리워드 단계 · 예정 표시 · 팔로워 비교 ·
 * KPI 모집단)을 본다.
 */
@DisplayName("쇼룸 스튜디오 성과 관리(10) — 목록 · 요약 · 상세")
class CreatorPerformanceIntegrationTest extends SettlementTestSupport {

    private static final String PERFORMANCE = "/v1/creator/performance";

    @Autowired private ShowroomVisitRepository showroomVisitRepository;
    @Autowired private CreatorFollowEventRepository followEventRepository;

    private Settlement settledAsPaid() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        jdbc.update("UPDATE settlement SET status = 'PAID', paid_at = ? WHERE settlement_id = ?",
                LocalDateTime.now().withNano(0), s.getId());
        return settlement(s.getId());
    }

    /** 조정 협의 1건 시드(이슈 스레드 설계서 1-1 테이블) — 협의 흐름은 그쪽 테스트가 소유하고 여기서는 성과 응답만 본다. */
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

    private void seedProposal(Long adjustmentId, int seq, String proposer, long proposerId, long amount,
                              String status, LocalDateTime at) {
        jdbc.update("INSERT INTO settlement_adjustment_proposal (adjustment_id, seq, proposer_type, proposer_id, "
                        + "reward_amount, reason, status, proposed_at) VALUES (?, ?, ?, ?, ?, '사유', ?, ?)",
                adjustmentId, seq, proposer, proposerId, amount, status, at);
    }

    @Test
    @DisplayName("P-08 조정 협의 — 판매 지표는 확정값 · 리워드만 「합의 후 확정」 · 경고 안내 + 이슈 스레드 · KPI 모집단 제외 · 응답 버튼 없음")
    void adjusting() throws Exception {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        long original = s.getOriginalRewardAmount();
        Long adjustmentId = seedAdjustment(s, "OPEN", null, null);
        LocalDateTime at = LocalDateTime.now().minusDays(1).withNano(0);
        seedProposal(adjustmentId, 1, "CREATOR", creator.getId(), original + 1_000, "COUNTERED", at.minusDays(1));
        seedProposal(adjustmentId, 2, "SELLER", brand.marketId(), original + 500, "PENDING", at);
        jdbc.update("UPDATE settlement SET status = 'ADJUSTING' WHERE settlement_id = ?", s.getId());

        JsonNode d = json(creatorGet(PERFORMANCE + "/" + groupBuy.getId()).andExpect(status().isOk()));
        assertThat(d.at("/groupBuy/status").asText()).isEqualTo("ADJUSTING");
        assertThat(d.at("/groupBuy/statusTone").asText()).isEqualTo("WARNING");
        assertThat(d.at("/notice/tone").asText()).isEqualTo("WARNING");
        assertThat(d.at("/notice/link").asText()).isEqualTo("ISSUE_THREAD");
        assertThat(d.at("/notice/threadId").asLong()).isEqualTo(pairThreadId);
        assertThat(d.at("/sales/estimated").asBoolean()).isFalse();
        assertThat(d.at("/reward/stage").asText()).isEqualTo("AFTER_AGREEMENT");
        assertThat(d.at("/reward/originalRewardAmount").asLong()).isEqualTo(original);
        assertThat(d.at("/reward/myRequest/amount").asLong()).isEqualTo(original + 1_000);
        assertThat(d.at("/reward/counterProposal/amount").asLong()).isEqualTo(original + 500);
        assertThat(d.at("/reward/finalRewardAmount").isNull()).isTrue();
        assertThat(d.at("/reward/adjustmentDeadlineAt").isNull()).isFalse();
        assertThat(d.at("/reward").has("permissions")).isFalse();

        JsonNode row = json(creatorGet(PERFORMANCE)).at("/content/0");
        assertThat(row.at("/confirmedSalesAmount").asLong()).isEqualTo(s.getConfirmedSalesAmount());
        assertThat(row.at("/rewardStage").asText()).isEqualTo("AFTER_AGREEMENT");
        assertThat(row.at("/myRewardAmount").isNull()).isTrue();
        creatorGet(PERFORMANCE + "/summary")
                .andExpect(jsonPath("$.kpi.settledCount").value(0))
                .andExpect(jsonPath("$.statusCounts.ADJUSTING").value(1));
    }

    @Test
    @DisplayName("P-09 합의 반영(D4) — 배지는 정산 완료 · 중립 안내 + 기록 보기 · 상품별은 계약 기준 그대로 + 차액 한 줄")
    void agreed() throws Exception {
        Settlement s = settledAsPaid();
        long original = s.getOriginalRewardAmount();
        LocalDateTime agreedAt = LocalDateTime.now().minusDays(3).withNano(0);
        Long adjustmentId = seedAdjustment(s, "AGREED", original + 500, agreedAt);
        seedProposal(adjustmentId, 1, "SELLER", brand.marketId(), original + 500, "ACCEPTED", agreedAt.minusDays(1));
        jdbc.update("UPDATE settlement SET reward_amount = ? WHERE settlement_id = ?", original + 500, s.getId());

        JsonNode d = json(creatorGet(PERFORMANCE + "/" + groupBuy.getId()).andExpect(status().isOk()));
        assertThat(d.at("/groupBuy/status").asText()).isEqualTo("SETTLED");
        assertThat(d.at("/notice/tone").asText()).isEqualTo("NEUTRAL");
        assertThat(d.at("/notice/link").asText()).isEqualTo("ISSUE_THREAD_HISTORY");
        assertThat(d.at("/notice/text").asText()).contains("브랜드 제안").contains("+500원");
        assertThat(d.at("/reward/adjustment/delta").asLong()).isEqualTo(500);
        assertThat(d.at("/reward/adjustment/agreedProposalLabel").asText()).isEqualTo("브랜드 제안");
        assertThat(d.at("/reward/finalRewardAmount").asLong()).isEqualTo(original + 500);
        assertThat(d.at("/sales/contractRewardAmount").asLong()).isEqualTo(original);
        assertThat(d.at("/sales/adjustmentDelta").asLong()).isEqualTo(500);
        assertThat(d.at("/rewardBar/salesReward/note").asText()).contains("조정");
    }

    @Test
    @DisplayName("P-01 가시성 — 진행 중 409 · 남의 공구 404 · 종료 공구 없으면 빈 상태(KPI 는 「—」 · 완료 0)")
    void visibility() throws Exception {
        creatorGet(PERFORMANCE + "/" + groupBuy.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_READY"));
        creatorGet(PERFORMANCE + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAnyEnded").value(false))
                .andExpect(jsonPath("$.kpi.settledCount").value(0))
                .andExpect(jsonPath("$.kpi.totalReward").doesNotExist())
                .andExpect(jsonPath("$.periodLabel").value("최근 6개월"));
        creatorGet(PERFORMANCE).andExpect(jsonPath("$.content.length()").value(0));

        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Creator other = createCreator("다른_쇼룸", "other-creator");
        String otherToken = bearerToken(other.getUser().getUsername(), RoleType.CREATOR, other.getUser().getId());
        mockMvc.perform(get(PERFORMANCE + "/" + groupBuy.getId()).header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        creatorGet(PERFORMANCE + "?months=5").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("P-05 집계 중(정산 전) — 목록은 「—」 · 상세는 판매 블록 예정 · 리워드 「정산 후 확정」 · 미종결 안내는 중립")
    void collecting() throws Exception {
        confirmedGroup(creamVariant, 1);
        paidGroup();                              // 미종결 1건
        endGroupBuy();

        JsonNode page = json(creatorGet(PERFORMANCE).andExpect(status().isOk()));
        JsonNode row = page.at("/content/0");
        assertThat(row.at("/status").asText()).isEqualTo("COLLECTING");
        assertThat(row.at("/statusTone").asText()).isEqualTo("NEUTRAL");
        assertThat(row.at("/confirmedSalesAmount").isNull()).isTrue();
        assertThat(row.at("/rewardStage").asText()).isEqualTo("AFTER_SETTLEMENT");
        assertThat(row.at("/myRewardAmount").isNull()).isTrue();

        JsonNode d = json(creatorGet(PERFORMANCE + "/" + groupBuy.getId()).andExpect(status().isOk()));
        assertThat(d.at("/groupBuy/status").asText()).isEqualTo("COLLECTING");
        assertThat(d.at("/notice/tone").asText()).isEqualTo("NEUTRAL");
        assertThat(d.at("/notice/text").asText()).contains("1건");
        assertThat(d.at("/sales/estimated").asBoolean()).isTrue();
        assertThat(d.at("/consumers/estimated").asBoolean()).isTrue();
        assertThat(d.at("/funnel/orderCount").asInt()).isEqualTo(2);
        assertThat(d.at("/funnel/estimated").asBoolean()).isTrue();
        assertThat(d.at("/reward/stage").asText()).isEqualTo("AFTER_SETTLEMENT");
        assertThat(d.at("/reward/finalRewardAmount").isNull()).isTrue();
        assertThat(d.at("/reward/originalRewardAmount").asLong()).isPositive();   // 예정액
        assertThat(d.at("/rewardBar/fixedFee/amount").asLong()).isEqualTo(300_000);
        assertThat(d.at("/reward/payout/amount").isNull()).isTrue();
        assertThat(d.has("delivery")).isFalse();                                // 파트너 전용 블록 없음
    }

    @Test
    @DisplayName("P-09 · P-10 정산 완료 — 리워드 확정 · 실지급은 정산 행 그대로 · KPI 모집단 = 정산 완료 · 상품별 합 = 계약 기준 리워드")
    void settled() throws Exception {
        Settlement s = settledAsPaid();
        long myReward = s.getRewardAmount() + 300_000;

        JsonNode d = json(creatorGet(PERFORMANCE + "/" + groupBuy.getId()).andExpect(status().isOk()));
        assertThat(d.at("/groupBuy/status").asText()).isEqualTo("SETTLED");
        assertThat(d.at("/groupBuy/statusTone").asText()).isEqualTo("SUCCESS");
        assertThat(d.at("/notice").isNull()).isTrue();
        assertThat(d.at("/sales/estimated").asBoolean()).isFalse();
        assertThat(d.at("/reward/stage").asText()).isEqualTo("CONFIRMED");
        assertThat(d.at("/reward/finalRewardAmount").asLong()).isEqualTo(s.getRewardAmount());
        assertThat(d.at("/reward/totalRewardAmount").asLong()).isEqualTo(myReward);
        assertThat(d.at("/reward/payout/amount").asLong()).isEqualTo(s.getCreatorPayoutAmount());
        assertThat(d.at("/reward/payout/note").asText()).contains("원천징수");
        assertThat(d.at("/kpi/myReward/amount").asLong()).isEqualTo(myReward);
        assertThat(d.at("/kpi/confirmedSales/value").asLong()).isEqualTo(s.getConfirmedSalesAmount());
        assertThat(d.at("/funnel/confirmedOrderCount").asInt()).isEqualTo(2);
        long productRewardSum = 0;
        for (JsonNode p : d.at("/sales/products")) {
            productRewardSum += p.at("/rewardAmount").asLong();
        }
        assertThat(productRewardSum).isEqualTo(d.at("/sales/contractRewardAmount").asLong())
                .isEqualTo(s.getOriginalRewardAmount());
        assertThat(d.at("/recollaboration/threadId").asLong()).isEqualTo(pairThreadId);

        JsonNode summary = json(creatorGet(PERFORMANCE + "/summary").andExpect(status().isOk()));
        assertThat(summary.at("/hasAnyEnded").asBoolean()).isTrue();
        assertThat(summary.at("/kpi/settledCount").asInt()).isEqualTo(1);
        assertThat(summary.at("/kpi/totalReward").asLong()).isEqualTo(myReward);
        assertThat(summary.at("/kpi/averageReward").asLong()).isEqualTo(myReward);
        assertThat(summary.at("/statusCounts/SETTLED").asInt()).isEqualTo(1);
        assertThat(summary.at("/statusCounts/COLLECTING").asInt()).isEqualTo(0);

        JsonNode row = json(creatorGet(PERFORMANCE + "?status=SETTLED&keyword=글로우랩")).at("/content/0");
        assertThat(row.at("/myRewardAmount").asLong()).isEqualTo(myReward);
        assertThat(row.at("/confirmedQuantity").asLong()).isEqualTo(2);
        creatorGet(PERFORMANCE + "?status=ADJUSTING").andExpect(jsonPath("$.content.length()").value(0));
        creatorGet(PERFORMANCE + "?keyword=없는브랜드").andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("P-02 · P-04 유입 · 팔로워 — 사람당 첫 방문 경로 4종(합 = 방문) · 방문 · 주문 당시 팔로워로 비교표를 가른다")
    void trafficAndFollowers() throws Exception {
        LocalDateTime at = LocalDateTime.now().minusMinutes(30).withNano(0);
        followEventRepository.save(CreatorFollowEvent.follow(creator.getId(), consumer.getId(),
                groupBuy.getStartAt().minusDays(1), null));
        showroomVisitRepository.save(new ShowroomVisit(creator, consumer, "u:" + consumer.getId(),
                ShowroomVisitSource.INSTAGRAM_LINK, at));
        showroomVisitRepository.save(new ShowroomVisit(creator, null, "d:anon-1", ShowroomVisitSource.GROUP_BUY_POST, at));
        showroomVisitRepository.save(new ShowroomVisit(creator, null, "d:anon-2",
                ShowroomVisitSource.PRODUCT_DIRECT_LINK, at));
        showroomVisitRepository.save(new ShowroomVisit(creator, null, "d:anon-2", ShowroomVisitSource.DIRECT,
                at.plusMinutes(5)));                    // 같은 사람의 두 번째 경로는 세지 않는다
        confirmedGroup(creamVariant, 1);               // 팔로워(consumer)의 주문
        endGroupBuy();

        JsonNode d = json(creatorGet(PERFORMANCE + "/" + groupBuy.getId()).andExpect(status().isOk()));
        assertThat(d.at("/traffic/visitorCount").asLong()).isEqualTo(3);
        long sum = 0;
        for (JsonNode source : d.at("/traffic/sources")) {
            sum += source.at("/visitors").asLong();
            switch (source.at("/source").asText()) {
                case "PROFILE_LINK", "POST_LINK", "PRODUCT_LINK" -> assertThat(source.at("/visitors").asLong()).isEqualTo(1);
                case "INTERNAL" -> assertThat(source.at("/visitors").asLong()).isZero();
                default -> throw new AssertionError(source);
            }
        }
        assertThat(sum).isEqualTo(3);
        assertThat(d.at("/followers/follower/visitors").asLong()).isEqualTo(1);
        assertThat(d.at("/followers/nonFollower/visitors").asLong()).isEqualTo(2);
        assertThat(d.at("/followers/follower/orders").asLong()).isEqualTo(1);
        assertThat(d.at("/followers/nonFollower/orders").asLong()).isZero();
        assertThat(d.at("/followers/followerVisitorRate").asDouble()).isEqualTo(33.3);
        assertThat(d.at("/consumers/followerBuyerCount").asLong()).isEqualTo(1);
        assertThat(d.at("/consumers/distribution/suppressed").asBoolean()).isTrue();   // 표본 1명 < 30
    }
}
