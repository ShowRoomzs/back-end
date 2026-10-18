package showroomz.api.admin.creator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyHistory;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorApplicationRepository;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostNotificationLog;
import showroomz.domain.post.repository.PostNotificationLogRepository;
import showroomz.domain.post.type.PostNotificationEvent;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 53 테스트 시나리오 5 · 6절 — 운영 현황 · 쇼룸 활동 · 최근 정산 · 계약 · 연결 브랜드 · 처리 이력. 산식 · 대상 집합 · 정렬 · 페이지 경계를 본다.
 * 기본 흐름은 {@link AdminCreatorIntegrationTest}(CR-07)가 본다.
 */
@DisplayName("어드민 인플루언서 상세 — 하단 카드 산식 · 정렬 · 처리 이력")
class AdminCreatorLowerCardsIntegrationTest extends AdminCreatorTestSupport {

    @Autowired private CreatorFollowRepository follows;
    @Autowired private PostNotificationLogRepository postNotificationLogs;
    @Autowired private CreatorApplicationRepository applications;
    @Autowired private OperatorChannelService operatorChannels;

    // ------------------------------------------------------------------ 5-1. 운영 현황 · 쇼룸 활동

    @Test
    @DisplayName("[OV-01] 운영 현황 — 연결 = PAIR 연결중만 · 판매 중 = 진행중 + 중단 예정 · 조정 협의 = 열린 협의")
    void operations() throws Exception {
        connect(fixture.createBrand("b2@showroomz.test", "무드랩"), creator);
        connect(fixture.createBrand("b3@showroomz.test", "데일리랩"), creator);
        Connection ended = connect(fixture.createBrand("b4@showroomz.test", "오브제랩"), creator);
        jdbc.update("UPDATE connection SET status = 'DISCONNECTED', disconnected_at = ? WHERE connection_id = ?",
                LocalDateTime.now(), ended.getId());
        connectionRepository.save(Connection.requestPair(fixture.createBrand("b5@showroomz.test", "요청랩").market(), creator));
        seedIn(GroupBuyStatus.SUSPENSION_SCHEDULED);
        seedIn(GroupBuyStatus.READY);
        seedIn(GroupBuyStatus.ENDED);
        openAdjustment(seedSettlement("가을 립밤 공구", 1_000_000, SettlementStatus.REVIEWING));

        adminGet(base + "/overview").andExpect(status().isOk())
                .andExpect(jsonPath("$.operations.connectedBrandCount").value(3))
                .andExpect(jsonPath("$.operations.sellingGroupBuyCount").value(2))
                .andExpect(jsonPath("$.operations.openAdjustmentCount").value(1));
    }

    @Test
    @DisplayName("[OV-02] 정산 진행 중 리워드 — 인플루언서 지급 행이 지급 완료 · 해당 없음이 아닌 정산의 리워드(세전) 합 · 인플루언서 몫만 나간 분배 실패는 뺀다")
    void rewardInProgress() throws Exception {
        Settlement waiting = seedSettlement("A 공구", 1_000_000, SettlementStatus.REVIEWING);
        Settlement blocked = seedSettlement("B 공구", 2_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        Settlement paid = seedSettlement("C 공구", 3_000_000, SettlementStatus.PAID);
        Settlement failed = seedSettlement("D 공구", 4_000_000, SettlementStatus.PAYOUT_FAILED);
        Settlement notApplicable = seedSettlement("E 공구", 500_000, SettlementStatus.PAYOUT_SCHEDULED);
        creatorPayout(blocked, PayoutStatus.BLOCKED);
        creatorPayout(paid, PayoutStatus.PAID);
        creatorPayout(failed, PayoutStatus.PAID);
        jdbc.update("UPDATE settlement_payout SET status = 'FAILED' WHERE settlement_id = ? AND payee = 'BRAND'",
                failed.getId());
        creatorPayout(notApplicable, PayoutStatus.NOT_APPLICABLE);

        long expected = settlement(waiting.getId()).getRewardAmount() + settlement(blocked.getId()).getRewardAmount();
        adminGet(base + "/overview").andExpect(jsonPath("$.operations.rewardInProgressAmount").value(expected));
    }

    @Test
    @DisplayName("[OV-03 · OV-04] 쇼룸 활동 — 공구 게시물(임시저장 제외) · 검토 반려 · 숨김(공구 숨김 + 일반 노출 중지 순수) · 일반 게시물 4상태 · 앱 내 팔로우 · 1:1 스레드 · 남의 기록 제외")
    void activity() throws Exception {
        GroupBuy ready = seedIn(GroupBuyStatus.READY);
        GroupBuy endedBuy = seedIn(GroupBuyStatus.ENDED);
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        seedPost(ready.getId(), GroupBuyPostReviewStatus.DRAFT, false);
        GroupBuyPost rejected = seedPost(endedBuy.getId(), GroupBuyPostReviewStatus.REJECTED, true);
        groupBuyEvent(endedBuy.getId(), GroupBuyEventType.OPEN_REJECTED);
        groupBuyEvent(endedBuy.getId(), GroupBuyEventType.OPEN_REJECTED);
        groupBuyEvent(endedBuy.getId(), GroupBuyEventType.POST_HIDDEN);

        List<Long> general = new ArrayList<>();
        for (PostStatus status : List.of(PostStatus.PUBLISHED, PostStatus.PUBLISHED, PostStatus.SUSPENDED,
                PostStatus.UNDER_REVIEW, PostStatus.DRAFT, PostStatus.DELETED)) {
            general.add(generalPost(creator, status));
        }
        LocalDateTime now = LocalDateTime.now();
        postNotificationLogs.save(new PostNotificationLog(general.get(2), creator.getId(), PostNotificationEvent.SUSPENDED, null, now));
        postNotificationLogs.save(new PostNotificationLog(general.get(0), creator.getId(), PostNotificationEvent.SUSPENDED, null, now));
        postNotificationLogs.save(new PostNotificationLog(general.get(0), creator.getId(),
                PostNotificationEvent.SUSPENSION_CANCELED, null, now));

        for (int i = 1; i <= 3; i++) {
            Users fan = createConsumer("fan" + i, "팬" + i);
            follows.save(new CreatorFollow(fan, creator));
        }
        Connection second = connect(fixture.createBrand("b2@showroomz.test", "무드랩"), creator);
        MessageThread dormant = messageThreadRepository.save(MessageThread.openFor(second));
        jdbc.update("UPDATE message_thread SET status = 'DORMANT' WHERE thread_id = ?", dormant.getId());

        // 남의 기록 — 다른 인플루언서의 게시물 · 팔로우 · 노출 중지는 세지 않는다.
        Creator other = createCreator("다른_쇼룸", "other");
        Long otherPost = generalPost(other, PostStatus.PUBLISHED);
        postNotificationLogs.save(new PostNotificationLog(otherPost, other.getId(), PostNotificationEvent.SUSPENDED, null, now));
        follows.save(new CreatorFollow(createConsumer("fan9", "팬9"), other));

        assertThat(rejected.getHiddenAt()).isNotNull();
        JsonNode activity = json(adminGet(base + "/overview").andExpect(status().isOk())).get("activity");
        assertThat(activity.get("groupBuyPostCount").asLong()).isEqualTo(2);
        assertThat(activity.get("reviewRejectCount").asLong()).isEqualTo(2);
        assertThat(activity.get("hiddenCount").asLong()).isEqualTo(2);
        assertThat(activity.get("generalPostCount").asLong()).isEqualTo(4);
        assertThat(activity.get("followerCount").asLong()).isEqualTo(3);
        assertThat(activity.get("threadCount").asLong()).isEqualTo(2);
        assertThat(activity.get("openThreadCount").asLong()).isEqualTo(1);
        assertThat(activity.has("complianceRate")).isFalse();
    }

    @Test
    @DisplayName("[OV-05] 탈퇴 후 — 팔로우 · 게시물 집계는 그대로 · 연결 브랜드는 일괄 종료로 0")
    void overviewAfterWithdrawal() throws Exception {
        follows.save(new CreatorFollow(createConsumer("fan1", "팬1"), creator));
        generalPost(creator, PostStatus.PUBLISHED);
        adminGet(base + "/overview").andExpect(jsonPath("$.operations.connectedBrandCount").value(1));

        approveWithdrawal();

        adminGet(base + "/overview").andExpect(status().isOk())
                .andExpect(jsonPath("$.operations.connectedBrandCount").value(0))
                .andExpect(jsonPath("$.operations.sellingGroupBuyCount").value(0))
                .andExpect(jsonPath("$.activity.followerCount").value(1))
                .andExpect(jsonPath("$.activity.generalPostCount").value(1));
    }

    // ------------------------------------------------------------------ 5-2. 최근 정산

    @Test
    @DisplayName("[SL-01] 최근 정산 — 기본 5건 · 생성 최신순 · 전체 건수 · 행 값(정산 관리와 같은 배지 · 인플루언서 지급 상태)")
    void recentSettlements() throws Exception {
        List<Settlement> seeded = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Settlement s = seedSettlement("정산 " + i + " 공구", 1_000_000 + i * 10_000L, SettlementStatus.REVIEWING);
            jdbc.update("UPDATE settlement SET created_at = ? WHERE settlement_id = ?",
                    LocalDateTime.now().minusDays(10 - i), s.getId());
            seeded.add(s);
        }
        Settlement newest = settlement(seeded.get(6).getId());

        JsonNode list = json(adminGet(base + "/settlements").andExpect(status().isOk()));
        assertThat(list.get("total").asLong()).isEqualTo(7);
        assertThat(list.get("content")).hasSize(5);
        List<Long> ids = new ArrayList<>();
        list.get("content").forEach(row -> ids.add(row.get("settlementId").asLong()));
        assertThat(ids).containsExactly(seeded.get(6).getId(), seeded.get(5).getId(), seeded.get(4).getId(),
                seeded.get(3).getId(), seeded.get(2).getId());

        JsonNode first = list.get("content").get(0);
        assertThat(first.get("settlementNumber").asText()).isEqualTo(newest.getSettlementNumber());
        assertThat(first.get("groupBuyTitle").asText()).isEqualTo("정산 6 공구");
        assertThat(first.get("groupBuyId").asLong()).isEqualTo(groupBuyIdOf(newest));
        assertThat(first.get("marketName").asText()).isEqualTo("글로우랩");
        assertThat(first.get("status").asText()).isEqualTo("REVIEWING");
        assertThat(first.get("statusLabel").asText()).isEqualTo(SettlementStatus.REVIEWING.getLabel());
        assertThat(first.get("tone").asText()).isEqualTo(SettlementStatus.REVIEWING.getTone().name());
        assertThat(first.get("rewardAmount").asLong()).isEqualTo(newest.getRewardAmount());
        assertThat(first.get("creatorPayoutStatus").asText()).isEqualTo("WAITING");
        assertThat(first.get("creatorPayoutStatusLabel").asText()).isEqualTo(PayoutStatus.WAITING.getLabel());
        assertThat(first.get("adjustment").isNull()).isTrue();
    }

    @Test
    @DisplayName("[SL-02 · SL-04] 조정 협의 행에만 협의 블록 · 인플루언서 지급 행 상태 그대로 · 지급 행이 없으면 null(500 아님)")
    void settlementAdjustmentAndMissingPayout() throws Exception {
        Settlement adjusting = seedSettlement("조정 공구", 1_000_000, SettlementStatus.REVIEWING);
        Settlement plain = seedSettlement("일반 공구", 1_000_000, SettlementStatus.REVIEWING);
        jdbc.update("UPDATE settlement SET created_at = ? WHERE settlement_id = ?", LocalDateTime.now().minusDays(3),
                plain.getId());
        long adjustmentId = openAdjustment(adjusting);
        jdbc.update("DELETE FROM settlement_payout WHERE settlement_id = ?", plain.getId());

        JsonNode content = json(adminGet(base + "/settlements").andExpect(status().isOk())).get("content");
        JsonNode adjustingRow = content.get(0);
        assertThat(adjustingRow.get("settlementId").asLong()).isEqualTo(adjusting.getId());
        assertThat(adjustingRow.get("status").asText()).isEqualTo(settlement(adjusting.getId()).getStatus().name());
        assertThat(adjustingRow.get("creatorPayoutStatus").asText())
                .isEqualTo(payout(adjusting.getId(), SettlementPayee.CREATOR).getStatus().name());
        assertThat(adjustingRow.get("adjustment").get("adjustmentId").asLong()).isEqualTo(adjustmentId);
        assertThat(adjustingRow.get("adjustment").get("deadlineAt").asText()).isNotEmpty();
        assertThat(adjustingRow.get("adjustment").get("threadId").isNumber()).isTrue();

        JsonNode plainRow = content.get(1);
        assertThat(plainRow.get("adjustment").isNull()).isTrue();
        assertThat(plainRow.get("creatorPayoutStatus").isNull()).isTrue();
        assertThat(plainRow.get("creatorPayoutStatusLabel").isNull()).isTrue();
    }

    @Test
    @DisplayName("[SL-03 · SL-05] size 는 1 ~ 50 으로 조인다(0 · 음수 → 1) · 정산 0건이면 빈 목록")
    void settlementSizeBounds() throws Exception {
        adminGet(base + "/settlements").andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
        for (int i = 0; i < 3; i++) {
            seedSettlement("정산 " + i + " 공구", 1_000_000, SettlementStatus.REVIEWING);
        }
        adminGet(base + "/settlements?size=0").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.total").value(3));
        adminGet(base + "/settlements?size=-1").andExpect(jsonPath("$.content.length()").value(1));
        adminGet(base + "/settlements?size=2").andExpect(jsonPath("$.content.length()").value(2));
        adminGet(base + "/settlements?size=100").andExpect(jsonPath("$.content.length()").value(3));
    }

    // ------------------------------------------------------------------ 5-3. 계약

    @Test
    @DisplayName("[CT-01 · CT-05 · CT-06] 계약 — 인플루언서에게 도착한 6상태만(브랜드 전용 · 삭제 제외) · 고정 지급비 없음은 전부 null · 페이지 경계")
    void receivedContracts() throws Exception {
        for (ContractStatus s : List.of(ContractStatus.DRAFT, ContractStatus.REVIEW_PENDING, ContractStatus.REVIEW_REJECTED,
                ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING, ContractStatus.DECLINED, ContractStatus.EXPIRED,
                ContractStatus.CANCELED)) {
            contractIn(s);
        }
        Long deleted = contractIn(ContractStatus.CONCLUDED);
        jdbc.update("UPDATE contract SET deleted_at = ? WHERE contract_id = ?", LocalDateTime.now(), deleted);

        JsonNode page = json(adminGet(base + "/contracts").andExpect(status().isOk()));
        assertThat(page.get("pageInfo").get("totalResults").asLong()).isEqualTo(6);
        Map<String, JsonNode> byStatus = new HashMap<>();
        page.get("content").forEach(row -> byStatus.put(row.get("status").asText(), row));
        assertThat(byStatus.keySet()).containsExactlyInAnyOrder("SIGNING", "CONCLUSION_PENDING", "CONCLUDED", "DECLINED",
                "EXPIRED", "CANCELED");
        assertThat(page.get("content").get(0).get("status").asText()).isEqualTo("CANCELED");   // 생성 최신순

        JsonNode signing = byStatus.get("SIGNING");
        assertThat(signing.get("statusLabel").asText()).isEqualTo(ContractStatus.SIGNING.getLabel());
        assertThat(signing.get("marketName").asText()).isEqualTo("글로우랩");
        for (String field : List.of("fixedFeeAmount", "fixedFeeTrigger", "fixedFeeTriggerLabel", "fixedFeeTriggerReachedAt",
                "fixedFeePaidAt")) {
            assertThat(signing.get(field).isNull()).as(field).isTrue();
        }

        adminGet(base + "/contracts?page=1&size=4").andExpect(jsonPath("$.content.length()").value(4));
        adminGet(base + "/contracts?page=2&size=4").andExpect(jsonPath("$.content.length()").value(2));
        adminGet(base + "/contracts?page=0&size=4").andExpect(jsonPath("$.content.length()").value(4));
        adminGet(base + "/contracts?size=0").andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("[CT-02 · CT-03 · CT-04] 지급 시점 도래 — 게시물 승인 · 공구 종료 · 정산 지급 완료 시각(사실만) · 미도래 null · 지급 기록은 브랜드 자기 신고 그대로")
    void fixedFeeTrigger() throws Exception {
        Long postContract = contractIdOf(groupBuy.getId());
        assertThat(row(postContract).get("fixedFeeTrigger").asText()).isEqualTo("POST_REGISTERED");
        assertThat(row(postContract).get("fixedFeeTriggerReachedAt").isNull()).isTrue();
        assertThat(row(postContract).get("fixedFeePaidAt").isNull()).isTrue();

        GroupBuyPost approved = seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);

        GroupBuy endedBuy = seedIn(GroupBuyStatus.ENDED);
        Long endedContract = contractIdOf(endedBuy.getId());
        jdbc.update("UPDATE contract SET fixed_fee_trigger = 'GROUP_BUY_ENDED' WHERE contract_id = ?", endedContract);

        Settlement paidSettlement = seedSettlement("정산 완료 공구", 1_000_000, SettlementStatus.PAID);
        LocalDateTime paidAt = LocalDateTime.now().minusDays(1).withNano(0);
        jdbc.update("UPDATE settlement SET paid_at = ? WHERE settlement_id = ?", paidAt, paidSettlement.getId());
        Long settledContract = contractIdOf(groupBuyIdOf(paidSettlement));
        jdbc.update("UPDATE contract SET fixed_fee_trigger = 'SETTLEMENT_COMPLETED' WHERE contract_id = ?", settledContract);

        LocalDateTime brandPaid = LocalDateTime.now().minusHours(3).withNano(0);
        jdbc.update("UPDATE contract SET fixed_fee_paid_at = ? WHERE contract_id = ?", brandPaid, postContract);

        JsonNode post = row(postContract);
        assertThat(post.get("fixedFeeAmount").asInt()).isEqualTo(300_000);
        assertThat(at(post.get("fixedFeeTriggerReachedAt"))).isEqualTo(approved.getReviewedAt());
        assertThat(at(post.get("fixedFeePaidAt"))).isEqualTo(brandPaid);

        JsonNode ended = row(endedContract);
        assertThat(ended.get("fixedFeeTrigger").asText()).isEqualTo("GROUP_BUY_ENDED");
        assertThat(at(ended.get("fixedFeeTriggerReachedAt"))).isEqualTo(reload(endedBuy.getId()).getEndedAt());
        assertThat(ended.get("fixedFeePaidAt").isNull()).isTrue();
        assertThat(ended.has("unpaid")).isFalse();

        JsonNode settled = row(settledContract);
        assertThat(at(settled.get("fixedFeeTriggerReachedAt"))).isEqualTo(paidAt);
    }

    // ------------------------------------------------------------------ 5-4. 연결 브랜드

    @Test
    @DisplayName("[CN-01 · CN-02 · CN-03] 연결 브랜드 — 연결중 먼저 → 응답 최신순 · 요청중 · 거절 · 운영팀 연결 제외 · 오래된 종료 행도 남는다 · 브랜드별 판매 중 공구")
    void connections() throws Exception {
        seedIn(GroupBuyStatus.SUSPENSION_SCHEDULED);
        Connection b2 = connect(fixture.createBrand("b2@showroomz.test", "무드랩"), creator);
        Connection b3 = connect(fixture.createBrand("b3@showroomz.test", "데일리랩"), creator);
        Connection b4 = connect(fixture.createBrand("b4@showroomz.test", "오브제랩"), creator);
        connectionRepository.save(Connection.requestPair(fixture.createBrand("b5@showroomz.test", "요청랩").market(), creator));
        Connection rejectedPair = Connection.requestPair(fixture.createBrand("b6@showroomz.test", "거절랩").market(), creator);
        rejectedPair.markRejected();
        connectionRepository.save(rejectedPair);
        operatorChannels.ensureCreatorChannel(creator);

        respondedAt(b2, LocalDateTime.of(2026, 8, 5, 10, 11, 12));
        respondedAt(b3, LocalDateTime.of(2025, 7, 1, 9, 0, 1));
        respondedAt(b4, LocalDateTime.of(2026, 8, 20, 15, 30, 1));
        jdbc.update("UPDATE connection SET status = 'DISCONNECTED', disconnected_at = ? WHERE connection_id IN (?, ?)",
                LocalDateTime.now().minusDays(30), b3.getId(), b4.getId());

        JsonNode list = json(adminGet(base + "/connections").andExpect(status().isOk()));
        assertThat(list.get("total").asLong()).isEqualTo(4);
        List<Long> order = new ArrayList<>();
        list.get("content").forEach(row -> order.add(row.get("connectionId").asLong()));
        assertThat(order).containsExactly(connection.getId(), b2.getId(), b4.getId(), b3.getId());

        JsonNode mine = list.get("content").get(0);
        assertThat(mine.get("marketId").asLong()).isEqualTo(brand.marketId());
        assertThat(mine.get("marketName").asText()).isEqualTo("글로우랩");
        assertThat(mine.get("sellingGroupBuyCount").asLong()).isEqualTo(2);
        assertThat(mine.get("status").asText()).isEqualTo("CONNECTED");
        assertThat(mine.get("disconnectedAt").isNull()).isTrue();
        JsonNode moodlab = list.get("content").get(1);
        assertThat(at(moodlab.get("connectedAt"))).isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 11, 12));
        assertThat(moodlab.get("sellingGroupBuyCount").asLong()).isZero();
        JsonNode oldest = list.get("content").get(3);
        assertThat(oldest.get("status").asText()).isEqualTo("DISCONNECTED");
        assertThat(oldest.get("disconnectedAt").isNull()).isFalse();
    }

    // ------------------------------------------------------------------ 6. 처리 이력

    @Test
    @DisplayName("[HS-01 · HS-02 · HS-03] 파생 이력 — 신청마다 가입 신청 · 승인 건에만 입점 승인(처리자 이름 · 못 찾으면 「운영자」) · 저장 행 0(백필 없음)")
    void derivedHistories() throws Exception {
        application(false, operator.getEmail(), LocalDateTime.of(2026, 3, 1, 10, 0, 1));
        application(true, "unknown-ops@showroomz.test", LocalDateTime.of(2026, 3, 15, 11, 2, 7));
        application(true, operator.getEmail(), LocalDateTime.of(2026, 4, 2, 9, 30, 5));

        JsonNode page = json(adminGet(base + "/histories").andExpect(status().isOk()));
        assertThat(page.get("total").asLong()).isEqualTo(5);
        List<String> types = new ArrayList<>();
        page.get("content").forEach(row -> types.add(row.get("eventType").asText()));
        assertThat(types).containsExactly("ONBOARDING_APPROVED", "SIGNED_UP", "ONBOARDING_APPROVED", "SIGNED_UP", "SIGNED_UP");

        JsonNode latestApproval = page.get("content").get(0);
        assertThat(latestApproval.get("historyId").isNull()).isTrue();
        assertThat(latestApproval.get("label").asText()).isEqualTo("입점 승인");
        assertThat(latestApproval.get("tone").asText()).isEqualTo("SUCCESS");
        assertThat(latestApproval.get("actorType").asText()).isEqualTo("ADMIN");
        assertThat(latestApproval.get("actorName").asText()).isEqualTo("김운영");
        assertThat(page.get("content").get(2).get("actorName").asText()).isEqualTo("운영자");
        JsonNode signedUp = page.get("content").get(1);
        assertThat(signedUp.get("label").asText()).isEqualTo("가입 신청");
        assertThat(signedUp.get("tone").asText()).isEqualTo("INFO");
        assertThat(signedUp.get("actorName").asText()).isEqualTo("인플루언서 본인");
        assertThat(events()).isEmpty();
    }

    @Test
    @DisplayName("[HS-04 · HS-05] 저장 · 파생 이력이 시각 최신순으로 섞인다 · 페이지(기본 1 · 20 · 범위 밖은 빈 목록 · size 1 ~ 100)")
    void historyOrderAndPaging() throws Exception {
        application(true, operator.getEmail(), LocalDateTime.of(2026, 3, 15, 11, 2, 7));
        for (int i = 0; i < 23; i++) {
            adminPost(base + "/memos", Map.of("content", "메모 " + i)).andExpect(status().isOk());
        }

        JsonNode first = json(adminGet(base + "/histories").andExpect(status().isOk()));
        assertThat(first.get("total").asLong()).isEqualTo(25);
        assertThat(first.get("page").asInt()).isEqualTo(1);
        assertThat(first.get("size").asInt()).isEqualTo(20);
        assertThat(first.get("content")).hasSize(20);
        assertThat(first.get("content").get(0).get("eventType").asText()).isEqualTo("MEMO_CREATED");
        assertThat(first.get("content").get(0).get("historyId").isNumber()).isTrue();

        JsonNode second = json(adminGet(base + "/histories?page=2&size=20"));
        assertThat(second.get("content")).hasSize(5);
        assertThat(second.get("content").get(3).get("eventType").asText()).isEqualTo("ONBOARDING_APPROVED");
        assertThat(second.get("content").get(4).get("eventType").asText()).isEqualTo("SIGNED_UP");

        adminGet(base + "/histories?page=9").andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.total").value(25));
        adminGet(base + "/histories?size=0").andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
        adminGet(base + "/histories?size=101").andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(25));
    }

    @Test
    @DisplayName("[HS-06] 레일 — 최신 3건 · 탈퇴 심사 카드가 붙으면 2건(B1-b) · historyTotal 은 전체")
    void railHistorySize() throws Exception {
        for (int i = 0; i < 5; i++) {
            adminPost(base + "/memos", Map.of("content", "메모 " + i)).andExpect(status().isOk());
        }
        adminGet(base).andExpect(jsonPath("$.rail.recentHistories.length()").value(3))
                .andExpect(jsonPath("$.rail.historyTotal").value(5));

        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        withdrawalService.request(creator.getId(), "본업 집중", LocalDateTime.now());
        adminGet(base).andExpect(jsonPath("$.rail.withdrawalReview").exists())
                .andExpect(jsonPath("$.rail.recentHistories.length()").value(2))
                .andExpect(jsonPath("$.rail.recentHistories[0].eventType").value("WITHDRAWAL_REQUESTED"))
                .andExpect(jsonPath("$.rail.recentHistories[0].tone").value("WARNING"))
                .andExpect(jsonPath("$.rail.recentHistories[0].actorName").value("인플루언서 본인"))
                .andExpect(jsonPath("$.rail.recentHistories[0].detail").value("본업 집중"))
                .andExpect(jsonPath("$.rail.historyTotal").value(6));
    }

    @Test
    @DisplayName("[HS-07 · HS-08 · HS-10] 주체 3분기(운영자 스냅샷 · 인플루언서 본인 · 시스템) · 라벨 · 톤 · 탈퇴 뒤에도 이력은 전부 남는다")
    void actorsLabelsAndRetention() throws Exception {
        adminPost(base + "/suspend", Map.of("reason", "GROUP_BUY_NONPERFORMANCE")).andExpect(status().isOk());
        jdbc.update("UPDATE seller SET name = ? WHERE seller_id = ?", "김운영(개명)", operator.getId());
        adminPost(base + "/release", Map.of()).andExpect(status().isOk());
        approveWithdrawal();

        JsonNode content = json(adminGet(base + "/histories").andExpect(status().isOk())).get("content");
        Map<String, JsonNode> byType = new HashMap<>();
        content.forEach(row -> byType.putIfAbsent(row.get("eventType").asText(), row));
        assertThat(byType.keySet()).containsExactlyInAnyOrder("SUSPENDED", "SUSPENSION_RELEASED", "WITHDRAWAL_REQUESTED",
                "CONNECTIONS_ENDED", "WITHDRAWAL_APPROVED");

        assertHistory(byType.get("SUSPENDED"), "정지", "DANGER", "ADMIN", "김운영");
        assertHistory(byType.get("SUSPENSION_RELEASED"), "정지 해제", "SUCCESS", "ADMIN", "김운영(개명)");
        assertHistory(byType.get("WITHDRAWAL_REQUESTED"), "탈퇴 신청 접수", "WARNING", "CREATOR", "인플루언서 본인");
        assertHistory(byType.get("CONNECTIONS_ENDED"), "연결 일괄 종료", "NEUTRAL", "SYSTEM", "시스템");
        assertHistory(byType.get("WITHDRAWAL_APPROVED"), "탈퇴 승인 · 스튜디오 자격 해지", "NEUTRAL", "ADMIN", "김운영(개명)");
        assertThat(byType.get("SUSPENDED").get("detail").asText()).isEqualTo("공구 진행 불이행");
        assertThat(byType.get("CONNECTIONS_ENDED").get("detail").asText()).isEqualTo("탈퇴 승인 · 연결 1건 일괄 종료");
        assertThat(events()).containsExactly(CreatorAdminEventType.WITHDRAWAL_APPROVED, CreatorAdminEventType.CONNECTIONS_ENDED,
                CreatorAdminEventType.WITHDRAWAL_REQUESTED, CreatorAdminEventType.SUSPENSION_RELEASED,
                CreatorAdminEventType.SUSPENDED);
    }

    // ------------------------------------------------------------------ 보조

    private void assertHistory(JsonNode row, String label, String tone, String actorType, String actorName) {
        assertThat(row.get("label").asText()).isEqualTo(label);
        assertThat(row.get("tone").asText()).isEqualTo(tone);
        assertThat(row.get("actorType").asText()).isEqualTo(actorType);
        assertThat(row.get("actorName").asText()).isEqualTo(actorName);
    }

    private long openAdjustment(Settlement settlement) throws Exception {
        return json(creatorPost("/v1/creator/settlements/" + settlement.getId() + "/adjustment",
                Map.of("rewardAmount", 130_000, "reason", "추가 콘텐츠 2건 제작")).andExpect(status().isCreated()))
                .get("adjustmentId").asLong();
    }

    private void creatorPayout(Settlement settlement, PayoutStatus status) {
        jdbc.update("UPDATE settlement_payout SET status = ? WHERE settlement_id = ? AND payee = 'CREATOR'", status.name(),
                settlement.getId());
    }

    private void groupBuyEvent(Long groupBuyId, GroupBuyEventType type) {
        inTransaction(() -> groupBuyHistoryRepository.save(GroupBuyHistory.builder()
                .groupBuy(groupBuyRepository.getReferenceById(groupBuyId))
                .eventType(type)
                .actorType(GroupBuyActorType.ADMIN)
                .occurredAt(LocalDateTime.now())
                .build()));
    }

    private Long generalPost(Creator owner, PostStatus status) {
        Long postId = inTransaction(() -> postRepository.save(Post.draft(owner, "아침 루틴 정리", null)).getId());
        jdbc.update("UPDATE post SET status = ? WHERE post_id = ?", status.name(), postId);
        return postId;
    }

    private Long contractIdOf(Long groupBuyId) {
        return jdbc.queryForObject("SELECT contract_id FROM group_buy WHERE group_buy_id = ?", Long.class, groupBuyId);
    }

    private Long groupBuyIdOf(Settlement settlement) {
        return jdbc.queryForObject("SELECT group_buy_id FROM settlement WHERE settlement_id = ?", Long.class,
                settlement.getId());
    }

    private JsonNode row(Long contractId) throws Exception {
        JsonNode content = json(adminGet(base + "/contracts?size=50").andExpect(status().isOk())).get("content");
        for (JsonNode row : content) {
            if (row.get("contractId").asLong() == contractId) {
                return row;
            }
        }
        throw new AssertionError("계약 행 없음: " + contractId);
    }

    private void respondedAt(Connection connection, LocalDateTime at) {
        jdbc.update("UPDATE connection SET responded_at = ? WHERE connection_id = ?", at, connection.getId());
    }

    /** 입점 신청 1건 — 처리 시각 · 신청 시각은 SQL 로 고정한다. */
    private void application(boolean approve, String processorEmail, LocalDateTime processedAt) {
        CreatorApplication saved = inTransaction(() -> {
            CreatorApplication app = CreatorApplication.createApplication(creator.getUser(), SnsType.INSTAGRAM,
                    "https://instagram.com/jimin", "jimin", 12_000, "jimin-biz@showroomz.test", REAL_NAME, "1994-06-02",
                    PHONE);
            if (approve) {
                app.approve(processorEmail);
            } else {
                app.reject("ETC", "자료 보완", "자료 보완", processorEmail);
            }
            return applications.save(app);
        });
        jdbc.update("UPDATE creator_application SET processed_at = ?, created_at = ? WHERE creator_application_id = ?",
                processedAt, processedAt.minusDays(4), saved.getId());
    }

    /** 응답 시각 — 전역 직렬화 형식 {@code yyyy-MM-dd'T'HH:mm:ss'Z'}(JacksonConfig · 초 단위)를 되돌린다. */
    private static LocalDateTime at(JsonNode value) {
        return LocalDateTime.parse(value.asText().replace("Z", ""));
    }
}
