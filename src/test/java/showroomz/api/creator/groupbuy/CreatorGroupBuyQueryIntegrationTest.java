package showroomz.api.creator.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("[통합] 쇼룸 스튜디오 공구 조회 — 가시성 · 목록 · 요약 · 상세에서 덜어내는 것")
class CreatorGroupBuyQueryIntegrationTest extends CreatorGroupBuyTestSupport {

    // ── 0-4 가시성 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("남의 공구는 403이 아니라 404 — 없는 공구와 문구가 같고 코드만 갈린다")
    void othersGroupBuyIsNotFound() throws Exception {
        Creator other = createCreator("다른_쇼룸", "other");
        connect(brand, other);
        LocalDateTime startAt = LocalDateTime.now().plusDays(10).withNano(0);
        GroupBuy others = seed(brand, other, "남의 공구", startAt, startAt.plusDays(7));

        String notOwned = studioDetail(others.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_OWNED_BY_CREATOR"))
                .andReturn().getResponse().getContentAsString();
        String missing = studioDetail(999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(notOwned).get("message"))
                .isEqualTo(objectMapper.readTree(missing).get("message"));
        // 실행 API도 같은 판정을 통과한다.
        saveDraft(others.getId(), Map.of("title", "가로채기")).andExpect(status().isNotFound());
        studioList(null).andExpect(jsonPath("$.content").isEmpty());
    }

    // ── 1-1 · 1-2 · 1-3 목록 · 정렬 · 요약 ─────────────────────────────────────

    @Test
    @DisplayName("시작일 빠른순 — 비종결은 시작일 오름차순, 종결은 그 뒤에 시작일 내림차순")
    void startAtAscPutsTerminalLast() throws Exception {
        Fixture f = seedMixed();

        studioList(null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].groupBuyId").value(contains(
                        f.soon.getId().intValue(), f.later.getId().intValue(),
                        f.suspended.getId().intValue(), f.ended.getId().intValue())))
                .andExpect(jsonPath("$.content[0].brandName").value("글로우랩"))
                .andExpect(jsonPath("$.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.content[0].remark").doesNotExist());
    }

    @Test
    @DisplayName("내 조치 필요 — 요약 카운트 · 행 플래그 · 「먼저」 정렬이 같은 판정을 쓴다")
    void actionRequiredIsOneDefinition() throws Exception {
        Fixture f = seedMixed();

        // soon: 승인대기(운영자 차례) · later: 미작성(내 차례) · ended: 이행 확인 전(내 차례) · suspended: 없음
        studioSummary().andExpect(status().isOk())
                .andExpect(jsonPath("$.actionRequiredCount").value(2))
                .andExpect(jsonPath("$.tabCounts.ALL").value(4))
                .andExpect(jsonPath("$.tabCounts.PREPARING").value(2))
                .andExpect(jsonPath("$.tabCounts.ENDED").value(1))
                .andExpect(jsonPath("$.tabCounts.SUSPENDED").value(1));

        studioList("sort=ACTION_REQUIRED_FIRST").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].groupBuyId").value(contains(
                        f.later.getId().intValue(), f.ended.getId().intValue(),
                        f.soon.getId().intValue(), f.suspended.getId().intValue())))
                .andExpect(jsonPath("$.content[*].actionRequired").value(contains(true, true, false, false)));
    }

    @Test
    @DisplayName("연장 대기는 종료 전까지만 조치 대상 · 숨김 게시물은 조치 대상")
    void extensionAndHiddenPostAreActions() throws Exception {
        GroupBuy extension = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(extension.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        seedExtension(extension.getId(), 7);
        GroupBuy hidden = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(hidden.getId(), GroupBuyPostReviewStatus.APPROVED, true);

        studioSummary().andExpect(jsonPath("$.actionRequiredCount").value(2));

        // 종료 시각이 지난 연장은 응답할 수 없다 — 배지에서도 빠진다.
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), extension.getId());
        studioSummary().andExpect(jsonPath("$.actionRequiredCount").value(1));
    }

    @Test
    @DisplayName("탭 · 검색 — 중단은 별도 탭 · 검색은 공구명·브랜드명·공구번호")
    void tabAndKeyword() throws Exception {
        Fixture f = seedMixed();

        studioList("tab=SUSPENDED").andExpect(jsonPath("$.content[*].groupBuyId")
                .value(contains(f.suspended.getId().intValue())));
        studioList("tab=ENDED").andExpect(jsonPath("$.content[*].groupBuyId")
                .value(contains(f.ended.getId().intValue())));
        studioList("keyword=나중").andExpect(jsonPath("$.content[*].groupBuyId")
                .value(contains(f.later.getId().intValue())));
        studioList("keyword=" + f.soon.getGroupBuyNumber()).andExpect(jsonPath("$.content[*].groupBuyId")
                .value(contains(f.soon.getId().intValue())));
        studioList("keyword=글로우랩").andExpect(jsonPath("$.pageInfo.totalResults").value(4));
    }

    // ── 4 상세 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("준비중 상세 — 내 차례만 경고 · 최소 물량·비고 없음 · 내가 받는 금액 · 대가관계 표시")
    void preparingDetail() throws Exception {
        GroupBuy groupBuy = seedPreparing();

        studioDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("PREPARING"))
                .andExpect(jsonPath("$.brand.name").value("글로우랩"))
                .andExpect(jsonPath("$.brand.pairThreadId").value(pairThreadId))
                .andExpect(jsonPath("$.items[0].unitReward").value(3264))
                .andExpect(jsonPath("$.items[0].myRewardRate").value(12.0))
                .andExpect(jsonPath("$.items[0].minQuantity").doesNotExist())
                .andExpect(jsonPath("$.items[0].regularPrice").doesNotExist())
                .andExpect(jsonPath("$.payout.fixedFeeAmount").value(300_000))
                .andExpect(jsonPath("$.payout.platformGuaranteed").value(false))
                .andExpect(jsonPath("$.payout.salesReward").doesNotExist())
                .andExpect(jsonPath("$.payout.disputeChannel.threadId").value(pairThreadId))
                .andExpect(jsonPath("$.readiness.gates[*].state").value(contains("WAITING", "MY_TURN", "WAITING")))
                .andExpect(jsonPath("$.readiness.gates[*].tone").value(contains("NEUTRAL", "WARNING", "NEUTRAL")))
                .andExpect(jsonPath("$.readiness.registrationDeadline").exists())
                .andExpect(jsonPath("$.readiness.reviewSlaBusinessDays").value(3))
                .andExpect(jsonPath("$.post.status").value("NOT_WRITTEN"))
                .andExpect(jsonPath("$.post.disclosureText")
                        .value("유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다"))
                .andExpect(jsonPath("$.post.sellerInfoAutoAttached").value(true))
                .andExpect(jsonPath("$.remark").doesNotExist())
                .andExpect(jsonPath("$.sales").doesNotExist())
                .andExpect(jsonPath("$.permissions.canWritePost").value(true))
                .andExpect(jsonPath("$.permissions.canEditPost").value(false))
                .andExpect(jsonPath("$.permissions.canRequestSuspension").value(false))
                .andExpect(jsonPath("$.permissions.canOpenPairThread").value(true))
                .andExpect(jsonPath("$.navigation.prevGroupBuyId").doesNotExist());
    }

    @Test
    @DisplayName("브랜드 요청은 사유 라벨만 — 운영자에게 쓴 메모는 상세에도 이력에도 나가지 않는다")
    void brandRequestMemoIsNotExposed() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        action(groupBuy.getId(), "suspension-request",
                Map.of("reasonCode", "QUALITY_ISSUE", "memo", "인플루언서에게는 말하지 않은 내부 사정"))
                .andExpect(status().isOk());

        studioDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRequest.type").value("SUSPEND"))
                .andExpect(jsonPath("$.activeRequest.requesterType").value("SELLER"))
                .andExpect(jsonPath("$.activeRequest.mine").value(false))
                .andExpect(jsonPath("$.activeRequest.reasonLabel").value("상품 품질 이슈"))
                .andExpect(jsonPath("$.activeRequest.memo").doesNotExist())
                .andExpect(jsonPath("$.history[-1:].eventType").value(contains("SUSPENSION_REQUESTED")))
                .andExpect(jsonPath("$.history[-1:].detail").value(contains("상품 품질 이슈")))
                .andExpect(jsonPath("$.permissions.canRequestSuspension").value(false));

        // 브랜드 자신의 메모는 파트너 화면에 그대로 보인다.
        detail(groupBuy.getId()).andExpect(jsonPath("$.activeRequest.memo").value("인플루언서에게는 말하지 않은 내부 사정"));
    }

    @Test
    @DisplayName("크리에이터 요청으로 중단된 공구 — 파트너 종결 정보에 크리에이터 메모가 없다")
    void creatorMemoHiddenInPartnerClosure() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        studioAction(groupBuy.getId(), "suspension-request",
                Map.of("reasonCode", "DELIVERY_FAILURE", "memo", "운영자에게만 전하는 내용")).andExpect(status().isOk());
        Long requestId = changeRequestRepository.findByGroupBuyIdOrderByRequestedAtDescIdDesc(groupBuy.getId()).get(0).getId();
        // 운영자 승인은 어드민 설계 소관이라 결과 상태를 SQL로 옮긴다.
        jdbc.update("UPDATE group_buy_change_request SET status = 'APPROVED', decided_at = ?, decision_reason = ? "
                + "WHERE change_request_id = ?", Timestamp.valueOf(LocalDateTime.now()), "배송 지연 확인", requestId);
        moveTo(groupBuy.getId(), GroupBuyStatus.SUSPENDED);
        jdbc.update("UPDATE group_buy SET closing_change_request_id = ? WHERE group_buy_id = ?", requestId, groupBuy.getId());

        detail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.closure.requester.type").value("CREATOR"))
                .andExpect(jsonPath("$.closure.requester.reasonLabel").value("배송 지연 · 미발송이 계속됨"))
                .andExpect(jsonPath("$.closure.requester.memo").doesNotExist())
                .andExpect(jsonPath("$.closure.decisionReason").value("배송 지연 확인"));
    }

    @Test
    @DisplayName("이력 화이트리스트 — 물량 스냅샷은 버리고 브랜드의 소명은 내리지 않는다")
    void historyWhitelist() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        action(groupBuy.getId(), "stock-confirmation", null).andExpect(status().isOk());
        jdbc.update("INSERT INTO group_buy_history (group_buy_id, event_type, actor_type, detail, occurred_at) "
                        + "VALUES (?, 'APPEAL_SUBMITTED', 'SELLER', '소명 본문', ?)",
                groupBuy.getId(), Timestamp.valueOf(LocalDateTime.now()));

        studioDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.history[*].eventType").value(contains("CREATED", "STOCK_CONFIRMED")))
                .andExpect(jsonPath("$.history[1].detail").doesNotExist())
                .andExpect(jsonPath("$.history[*].eventType").value(not(hasItem("APPEAL_SUBMITTED"))))
                .andExpect(jsonPath("$.readiness.gates[0].state").value("DONE"));
    }

    @Test
    @DisplayName("직권 중단 예고 — 통지 본문·집행 예정만 · 소명 기한은 없다 · 게시물 수정·연장 응답 잠금")
    void suspensionScheduledDetail() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);
        seedNotice(groupBuy.getId(), LocalDateTime.now().plusDays(3).withNano(0));

        studioDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.status").value("SUSPENSION_SCHEDULED"))
                .andExpect(jsonPath("$.adminSuspension.reasonClause").value("ART17_1_LAW"))
                .andExpect(jsonPath("$.adminSuspension.noticeBody").exists())
                .andExpect(jsonPath("$.adminSuspension.businessDaysUntilExecution").isNumber())
                .andExpect(jsonPath("$.adminSuspension.appealDeadlineAt").doesNotExist())
                .andExpect(jsonPath("$.post.status").value("EXPOSED"))
                .andExpect(jsonPath("$.permissions.canEditPost").value(false))
                .andExpect(jsonPath("$.permissions.canRequestSuspension").value(false));
    }

    @Test
    @DisplayName("종료 상세 — 내가 받는 금액 없음 · 확인 방향이 파트너와 반대 · 자동 이행 스위치를 내린다")
    void endedDetail() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);

        studioDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.closure.closeType").value("COMPLETED"))
                .andExpect(jsonPath("$.afterEnd.fulfillment.mine").doesNotExist())
                .andExpect(jsonPath("$.afterEnd.fulfillment.myTarget.party").value("BRAND"))
                .andExpect(jsonPath("$.afterEnd.fulfillment.myTarget.duties")
                        .value(contains("ORDER_DELIVERY", "FIXED_FEE_PAYMENT")))
                .andExpect(jsonPath("$.afterEnd.fulfillment.theirTarget.duties")
                        .value(contains("SHOWROOM_POST", "FEED", "REELS", "STORY")))
                .andExpect(jsonPath("$.afterEnd.fulfillment.theirTarget.counts.story").value(3))
                .andExpect(jsonPath("$.afterEnd.fulfillment.autoConfirmOnTimeout").value(false))
                .andExpect(jsonPath("$.afterEnd.fulfillment.dueAt").exists())
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(true));
    }

    @Test
    @DisplayName("이웃 — 목록 조건을 넘기면 같은 정렬 기준으로 앞뒤 1건")
    void navigationFollowsListOrder() throws Exception {
        Fixture f = seedMixed();

        studioDetail(f.later.getId(), "tab=ALL").andExpect(status().isOk())
                .andExpect(jsonPath("$.navigation.prevGroupBuyId").value(f.soon.getId()))
                .andExpect(jsonPath("$.navigation.nextGroupBuyId").value(f.suspended.getId()));
        studioDetail(f.soon.getId(), "tab=ALL")
                .andExpect(jsonPath("$.navigation.prevGroupBuyId").doesNotExist());
    }

    // ── 픽스처 ───────────────────────────────────────────────────────────

    /** 준비중 2(승인대기 · 미작성) · 종료 1 · 중단 1 — 시작일을 서로 다르게 둔다. */
    private Fixture seedMixed() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy soon = seed(brand, creator, "곧 시작 공구", now.plusDays(5), now.plusDays(12));
        seedPost(soon.getId(), GroupBuyPostReviewStatus.PENDING, false);
        GroupBuy later = seed(brand, creator, "나중 시작 공구", now.plusDays(10), now.plusDays(17));
        GroupBuy ended = seed(brand, creator, "끝난 공구", now.minusDays(20), now.minusDays(15));
        moveTo(ended.getId(), GroupBuyStatus.ENDED);
        GroupBuy suspended = seed(brand, creator, "중단된 공구", now.minusDays(10), now.minusDays(5));
        moveTo(suspended.getId(), GroupBuyStatus.SUSPENDED);
        return new Fixture(soon, later, ended, suspended);
    }

    private record Fixture(GroupBuy soon, GroupBuy later, GroupBuy ended, GroupBuy suspended) {
    }
}
