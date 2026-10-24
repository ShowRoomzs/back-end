package showroomz.api.common.sanction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.SanctionHoldParty;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.product.entity.Product;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.PayoutStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1017 설계서 P1 — 계정 정지 차단 범위 확정(ui-partner-15 rev.2 · ui-studio-13 rev.1).
 *
 * <ul>
 *   <li>SC-01 브랜드 가드 — 초안 수정 · 복제 · 재발송 요청 · 연장 요청 · 진열 복귀 409 · 조기 마감 · 미진열 전환은 허용</li>
 *   <li>SC-02 인플루언서 가드 — 연결 수락 · 연장 수락 · 탈퇴 요청 · 쇼룸명 · 재발송 409 · 소개 수정 · 연장 거절은 허용</li>
 *   <li>SC-03 운영자 보류 — 검토 통과 · 체결 · 오픈 승인 409 · 오픈 스케줄러가 인플루언서 정지도 건너뛴다</li>
 *   <li>SC-04 서명 기한 — 정지 동안 멈추고 해제되면 멈춘 만큼 밀린다 · 양측 정지는 둘 다 풀려야 재개</li>
 *   <li>SC-05 보류 배지 — 내 정지 경고 · 상대 사정 중립(정지 사실 비노출) · 어드민은 당사자를 밝힌다</li>
 *   <li>SC-06 연결 — 검색 connectable=false · 요청 409 · 요청함 acceptable=false · 수락 409</li>
 *   <li>SC-07 정산 — 지급 정지 사유가 목록 · 상세 · 분배 행에 보인다 · 카드 payload 에 상세 사유 · 지급 정지</li>
 * </ul>
 */
@DisplayName("1017 P1 — 계정 정지 차단 범위 · 보류 · 서명 기한 · 상대 정지 대칭")
class SanctionScopeIntegrationTest extends SettlementTestSupport {

    @Autowired private showroomz.domain.member.creator.repository.CreatorRepository creators;

    // ------------------------------------------------------------------ SC-01 브랜드 가드

    @Test
    @DisplayName("SC-01 브랜드 정지 — 초안 수정 · 복제 · 재발송 요청 · 연장 요청 · 진열 복귀는 409, 조기 마감 · 미진열 전환은 허용")
    void brandGuards() throws Exception {
        Contract draft = seedContract(ContractStatus.DRAFT, null);
        Contract expired = seedContract(ContractStatus.EXPIRED, null);
        Contract signing = seedContract(ContractStatus.SIGNING, LocalDateTime.now().plusDays(5));
        suspendBrand("PRODUCT_INFO_FALSIFIED", null);

        sellerPut("/v1/seller/contracts/" + draft.getId(), Map.of("version", draft.getVersion()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_SUSPENDED"));
        sellerPost("/v1/seller/contracts/" + expired.getId() + "/duplicate", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_SUSPENDED"));
        sellerPost("/v1/seller/contracts/" + signing.getId() + "/resend-request", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_SUSPENDED"));
        sellerPost("/v1/seller/group-buys/" + groupBuy.getId() + "/extension-request", Map.of("extensionDays", 3))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_SUSPENDED"));

        // 진열 → 미진열은 허용, 미진열 → 진열 복귀는 막는다(S2c)
        Product product = createProduct(brand, "시즌 핸드크림", 18_000);
        sellerPost("/v1/seller/products/batch/display-status",
                Map.of("productIds", java.util.List.of(product.getProductId()), "displayStatus", "HIDDEN"))
                .andExpect(status().isOk());
        sellerPost("/v1/seller/products/batch/display-status",
                Map.of("productIds", java.util.List.of(product.getProductId()), "displayStatus", "DISPLAY"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_SUSPENDED"));

        // 조기 마감 요청은 판매를 줄이는 쪽이라 허용(정지 가드에 걸리지 않는다)
        sellerPost("/v1/seller/group-buys/" + groupBuy.getId() + "/early-close-request",
                Map.of("reasonCode", "STOCK_SOLD_OUT"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("BRAND_SUSPENDED"));

        // 삭제는 여전히 된다(초안 열람 · 삭제만)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/v1/seller/contracts/" + draft.getId()).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().is2xxSuccessful());
    }

    // ------------------------------------------------------------------ SC-02 인플루언서 가드

    @Test
    @DisplayName("SC-02 인플루언서 정지 — 연결 수락 · 연장 수락 · 쇼룸명 · 재발송 409, 소개 수정 · 연장 거절은 허용 · 탈퇴 요청은 정지가 아니라 진행 중 계약으로 막힌다(1021 7-3)")
    void creatorGuards() throws Exception {
        BrandRequest other = otherBrandRequest();
        Contract signing = seedContract(ContractStatus.SIGNING, LocalDateTime.now().plusDays(5));
        seedExtension();
        suspendCreator("AD_LAW_VIOLATION_REPEATED", "효능 표현");

        creatorPost("/v1/creator/connections/" + other.connectionId() + "/accept", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREATOR_SUSPENDED"));
        creatorPost("/v1/creator/group-buys/" + groupBuy.getId() + "/extension/acceptance", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREATOR_SUSPENDED"));
        creatorPost("/v1/creator/basic-info/withdrawal-requests", Map.of("reason", "활동 종료"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_CONDITION_UNMET"));
        creatorPost("/v1/creator/contracts/" + signing.getId() + "/resend-request", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREATOR_SUSPENDED"));

        // 시드 쇼룸명은 형식 규칙(한글 · 영문 · 숫자 · 공백)을 벗어난 값일 수 있다 — 유효한 이름으로 맞춰 둔다.
        String currentName = "하린 뷰티랩";
        jdbc.update("UPDATE creator SET showroom_name = ? WHERE creator_id = ?", currentName, creator.getId());
        creatorPut("/v1/creator/showroom/profile", Map.of("showroomName", "하린 새쇼룸", "introduction", "소개"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREATOR_SUSPENDED"));
        creatorPut("/v1/creator/showroom/profile", Map.of("showroomName", currentName, "introduction", "고친 소개글"))
                .andExpect(status().isOk());

        creatorPost("/v1/creator/group-buys/" + groupBuy.getId() + "/extension/rejection",
                Map.of("reasonCode", "SCHEDULE_CONFLICT"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("CREATOR_SUSPENDED"));
    }

    // ------------------------------------------------------------------ SC-03 운영자 보류

    @Test
    @DisplayName("SC-03 운영자 — 정지 당사자가 있으면 검토 통과 · 체결 · 오픈 승인 409 · 오픈 스케줄러는 인플루언서 정지도 건너뛴다")
    void operatorHolds() throws Exception {
        Contract review = seedContract(ContractStatus.REVIEW_PENDING, null);
        suspendBrand("REPEATED_SHIPPING_DELAY", null);
        adminPost("/v1/admin/contracts/" + review.getId() + "/review/approve", approveBody())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_HELD_BY_SANCTION"));
        releaseBrand();

        Contract pending = seedContract(ContractStatus.CONCLUSION_PENDING, LocalDateTime.now().plusDays(5));
        suspendCreator("GROUP_BUY_NONPERFORMANCE", null);
        adminPost("/v1/admin/contracts/" + pending.getId() + "/conclude", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_HELD_BY_SANCTION"));

        // 오픈 스케줄러 — READY · 시작 시각 경과 · 인플루언서 정지 → 열지 않는다
        GroupBuy ready = seed(brand, creator, "준비완료 공구", LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusDays(5));
        moveTo(ready.getId(), GroupBuyStatus.READY);
        assertThat(lifecycleService.open(ready.getId(), LocalDateTime.now())).isFalse();
        assertThat(reload(ready.getId()).getStatus()).isEqualTo(GroupBuyStatus.READY);
        releaseCreator();
        assertThat(lifecycleService.open(ready.getId(), LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("SC-03b 오픈 승인 — 당사자 정지 중이면 409 GROUP_BUY_HELD_BY_SANCTION")
    void openApprovalHeld() throws Exception {
        GroupBuy preparing = seed(brand, creator, "준비중 공구", LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(17));
        seedPost(preparing.getId(), showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus.PENDING, false);
        suspendCreator("GROUP_BUY_NONPERFORMANCE", null);
        adminPost("/v1/admin/group-buys/" + preparing.getId() + "/open-review/approve", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_BUY_HELD_BY_SANCTION"));
    }

    // ------------------------------------------------------------------ SC-04 서명 기한

    @Test
    @DisplayName("SC-04 서명 기한 — 정지 동안 멈추고 해제 시 멈춘 만큼 밀린다 · 보류 중 만료 불가")
    void signatureDeadlinePausesAndResumes() throws Exception {
        LocalDateTime deadline = LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.SECONDS);
        Contract signing = seedContract(ContractStatus.SIGNING, deadline);

        suspendCreator("GROUP_BUY_NONPERFORMANCE", null);
        Contract held = contractRepository.findById(signing.getId()).orElseThrow();
        assertThat(held.getSanctionHoldParty()).isEqualTo(SanctionHoldParty.CREATOR);
        // 정지 시각을 10일 전으로 돌린다 — 그 사이 원래 기한이 지났다
        jdbc.update("UPDATE contract SET sanction_hold_since = ?, signature_deadline_at = ? WHERE contract_id = ?",
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(7), signing.getId());

        adminPost("/v1/admin/contracts/" + signing.getId() + "/expire", Map.of("dashboardRechecked", true))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_EXPIRE_NOT_DUE"));
        creatorGet("/v1/creator/contracts/" + signing.getId())
                .andExpect(jsonPath("$.signatureDeadlinePaused").value(true));

        releaseCreator();
        Contract resumed = contractRepository.findById(signing.getId()).orElseThrow();
        assertThat(resumed.getSanctionHoldSince()).isNull();
        assertThat(resumed.getSanctionHoldParty()).isNull();
        // 원래 기한(7일 전) + 멈춘 기간(10일) ≈ 3일 뒤
        assertThat(resumed.getSignatureDeadlineAt()).isBetween(LocalDateTime.now().plusDays(3).minusMinutes(1),
                LocalDateTime.now().plusDays(3).plusMinutes(1));
    }

    @Test
    @DisplayName("SC-04b 양측 정지 — BOTH 로 넓어지고 한쪽 해제로는 재개되지 않는다")
    void bothPartiesHold() throws Exception {
        Contract signing = seedContract(ContractStatus.SIGNING, LocalDateTime.now().plusDays(3));
        suspendCreator("GROUP_BUY_NONPERFORMANCE", null);
        suspendBrand("REPEATED_SHIPPING_DELAY", null);
        assertThat(contractRepository.findById(signing.getId()).orElseThrow().getSanctionHoldParty())
                .isEqualTo(SanctionHoldParty.BOTH);

        releaseCreator();
        Contract stillHeld = contractRepository.findById(signing.getId()).orElseThrow();
        assertThat(stillHeld.getSanctionHoldParty()).isEqualTo(SanctionHoldParty.BRAND);
        assertThat(stillHeld.getSanctionHoldSince()).isNotNull();

        releaseBrand();
        assertThat(contractRepository.findById(signing.getId()).orElseThrow().getSanctionHoldSince()).isNull();
    }

    // ------------------------------------------------------------------ SC-05 보류 배지

    @Test
    @DisplayName("SC-05 보류 배지 — 브랜드 정지: 파트너 경고 · 스튜디오 「상대 브랜드 사정」 중립 · 어드민 BRAND · 진행 중 공구는 변화 없음")
    void holdBadges() throws Exception {
        Contract signing = seedContract(ContractStatus.SIGNING, LocalDateTime.now().plusDays(3));
        GroupBuy preparing = seed(brand, creator, "오픈 전 공구", LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(17));
        suspendBrand("REPEATED_SHIPPING_DELAY", null);

        sellerGet("/v1/seller/contracts/" + signing.getId())
                .andExpect(jsonPath("$.sanctionHold.kind").value("MINE"))
                .andExpect(jsonPath("$.sanctionHold.label").value("체결 보류 · 계정 이용 정지"))
                .andExpect(jsonPath("$.sanctionHold.tone").value("WARNING"))
                .andExpect(jsonPath("$.signatureDeadlinePaused").value(true));
        creatorGet("/v1/creator/contracts/" + signing.getId())
                .andExpect(jsonPath("$.sanctionHold.kind").value("COUNTERPARTY"))
                .andExpect(jsonPath("$.sanctionHold.label").value("체결 보류 · 상대 브랜드 사정"))
                .andExpect(jsonPath("$.sanctionHold.tone").value("NEUTRAL"));
        adminGet("/v1/admin/contracts/" + signing.getId())
                .andExpect(jsonPath("$.contract.sanctionHold.party").value("BRAND"));

        sellerGet("/v1/seller/group-buys/" + preparing.getId())
                .andExpect(jsonPath("$.sanctionHold.label").value("승인 보류 · 계정 이용 정지"));
        creatorGet("/v1/creator/group-buys/" + preparing.getId())
                .andExpect(jsonPath("$.sanctionHold.label").value("오픈 보류 · 상대 브랜드 사정"));
        adminGet("/v1/admin/group-buys/" + preparing.getId())
                .andExpect(jsonPath("$.sanctionHold.party").value("BRAND"));
        // 진행 중 공구는 변화 없음
        sellerGet("/v1/seller/group-buys/" + groupBuy.getId())
                .andExpect(jsonPath("$.sanctionHold").value(nullValue()));

        // 응답 어디에도 상대(브랜드)의 정지 사유가 새지 않는다
        creatorGet("/v1/creator/contracts/" + signing.getId())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("배송 지연 반복"));
    }

    // ------------------------------------------------------------------ SC-06 연결

    @Test
    @DisplayName("SC-06 연결 — 정지 쇼룸 검색 connectable=false · 요청 409 / 정지 브랜드 요청은 acceptable=false · 수락 409 · 거절은 된다")
    void connections() throws Exception {
        Creator target = createCreator("정지예정쇼룸", "suspend_target");
        suspendCreatorById(target.getId(), "GROUP_BUY_NONPERFORMANCE");

        sellerGet("/v1/seller/connections/creators?keyword=정지예정")
                .andExpect(jsonPath("$.content[0].connectable").value(false))
                .andExpect(jsonPath("$.content[0].unavailableReason").value("UNAVAILABLE"));
        sellerPost("/v1/seller/connections", Map.of("creatorId", target.getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREATOR_COUNTERPARTY_SUSPENDED"));

        BrandRequest other = otherBrandRequest();
        suspendBrandById(other.marketId(), "REPEATED_SHIPPING_DELAY");
        creatorGet("/v1/creator/connections/requests?status=REQUESTED")
                .andExpect(jsonPath("$.content[0].acceptable").value(false));
        creatorPost("/v1/creator/connections/" + other.connectionId() + "/accept", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_COUNTERPARTY_SUSPENDED"));
        creatorPost("/v1/creator/connections/" + other.connectionId() + "/reject", Map.of())
                .andExpect(status().is2xxSuccessful());
    }

    // ------------------------------------------------------------------ SC-07 정산 · 카드

    @Test
    @DisplayName("SC-07 정산 지급 정지 — 목록 · 상세 · 내 분배 행에 사유 · 「정지 해제 다음 영업일 지급」 · 원래 예정일 / 카드에 상세 사유 · 지급 정지")
    void payoutHoldAndCard() throws Exception {
        Settlement s = confirm(seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING));
        suspendBrand("SETTLEMENT_INFO_MISMATCH", "예금주와 상호가 다릅니다.");
        assertThat(payoutRepository.findBySettlementId(s.getId()).stream()
                .filter(p -> p.getPayee() == SettlementPayee.BRAND).findFirst().orElseThrow().getStatus())
                .isEqualTo(PayoutStatus.BLOCKED);

        sellerGet("/v1/seller/settlements/" + s.getId())
                .andExpect(jsonPath("$.payoutHold.reason").value("BRAND_SUSPENDED"))
                .andExpect(jsonPath("$.payoutHold.sanction").value(true))
                .andExpect(jsonPath("$.payoutHold.scheduleLabel").value("정지 해제 다음 영업일 지급"))
                .andExpect(jsonPath("$.payouts[0].hold.reason").value("BRAND_SUSPENDED"))
                .andExpect(jsonPath("$.payouts[1].hold").value(nullValue()));
        sellerGet("/v1/seller/settlements")
                .andExpect(jsonPath("$.content[?(@.settlementId == " + s.getId() + ")].payoutHold.sanction").value(true));

        Long channelThreadId = Long.valueOf(sellerGet("/v1/seller/account-status").andReturn().getResponse()
                .getContentAsString().replaceAll(".*\"operatorChannelThreadId\":(\\d+).*", "$1"));
        sellerGet("/v1/seller/threads/" + channelThreadId + "/messages")
                .andExpect(jsonPath("$.content[?(@.card.cardType == 'ACCOUNT_SUSPENDED')].card.detail.sanction.reasonDetail")
                        .value("예금주와 상호가 다릅니다."))
                .andExpect(jsonPath("$.content[?(@.card.cardType == 'ACCOUNT_SUSPENDED')].card.detail.sanction.payoutHeld")
                        .value(true))
                .andExpect(jsonPath("$.content[?(@.card.cardType == 'ACCOUNT_SUSPENDED')].card.detail.sanction.memberType")
                        .value("BRAND"));
    }

    @Test
    @DisplayName("[56 A1] 정지 중 확정 — 인플루언서 몫이 사유 코드 없이 보류돼도 스튜디오 목록 · 상세 · 내 분배 행에 「계정 이용 정지」 · 「정지 해제 다음 영업일 지급」 · 파트너 화면엔 안 보인다")
    void creatorHoldAtConfirmShowsSanction() throws Exception {
        Settlement reviewing = seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING);
        suspendCreator("SETTLEMENT_INFO_MISMATCH", null);
        Settlement s = confirm(reviewing);
        SettlementPayout mine = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(mine.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        // 확정의 정책 보류는 사유 코드를 남기지 않는다 — 해제 경로가 이 모양을 전제로 해 저장 방식은 그대로 둔다.
        assertThat(mine.getFailCode()).isNull();

        creatorGet("/v1/creator/settlements/" + s.getId())
                .andExpect(jsonPath("$.settlement.payoutHold.reason").value("CREATOR_SUSPENDED"))
                .andExpect(jsonPath("$.settlement.payoutHold.label").value(PayoutBlockReason.CREATOR_SUSPENDED.getLabel()))
                .andExpect(jsonPath("$.settlement.payoutHold.scheduleLabel").value("정지 해제 다음 영업일 지급"))
                .andExpect(jsonPath("$.settlement.payoutHold.sanction").value(true))
                .andExpect(jsonPath("$.payouts.rows[0].hold.reason").value("CREATOR_SUSPENDED"))
                .andExpect(jsonPath("$.payouts.rows[1].hold").value(nullValue()));
        creatorGet("/v1/creator/settlements")
                .andExpect(jsonPath("$.content[?(@.settlementId == " + s.getId() + ")].payoutHold.reason")
                        .value("CREATOR_SUSPENDED"))
                .andExpect(jsonPath("$.content[?(@.settlementId == " + s.getId() + ")].payoutHold.sanction").value(true));
        // 파트너는 자기 행(브랜드 몫 · 예정대로)만 보류 이유를 본다 — 인플루언서 정지는 드러나지 않는다.
        sellerGet("/v1/seller/settlements/" + s.getId())
                .andExpect(jsonPath("$.payoutHold").value(nullValue()))
                .andExpect(jsonPath("$.payouts[1].hold").value(nullValue()));
    }

    @Test
    @DisplayName("[56 A3] 보류 행의 원래 예정일 — 보류가 행 예정일을 지워도 정산의 지급 예정일로 「원래 09.09」 · 스튜디오 S6 · 파트너 S5 같은 값")
    void originalDueDateSurvivesHold() throws Exception {
        // 스튜디오 S6 — 정지 중 확정: 인플루언서 행은 처음부터 예정일 없이 보류된다.
        Settlement reviewing = seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING);
        suspendCreator("SETTLEMENT_INFO_MISMATCH", null);
        Settlement s = confirm(reviewing);
        assertThat(s.getPayoutDueDate()).isNotNull();
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getDueDate()).isNull();
        String due = s.getPayoutDueDate().toString();

        creatorGet("/v1/creator/settlements/" + s.getId())
                .andExpect(jsonPath("$.settlement.payoutHold.originalDueDate").value(due))
                .andExpect(jsonPath("$.payouts.rows[0].hold.originalDueDate").value(due));
        creatorGet("/v1/creator/settlements")
                .andExpect(jsonPath("$.content[?(@.settlementId == " + s.getId() + ")].payoutHold.originalDueDate")
                        .value(due));

        // 파트너 S5 — 예정이던 브랜드 몫이 정지로 보류되면 행 예정일이 지워진다.
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getDueDate()).isEqualTo(s.getPayoutDueDate());
        suspendBrand("SETTLEMENT_INFO_MISMATCH", null);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getDueDate()).isNull();

        sellerGet("/v1/seller/settlements/" + s.getId())
                .andExpect(jsonPath("$.payoutHold.originalDueDate").value(due))
                .andExpect(jsonPath("$.payouts[0].hold.originalDueDate").value(due));
        sellerGet("/v1/seller/settlements")
                .andExpect(jsonPath("$.content[?(@.settlementId == " + s.getId() + ")].payoutHold.originalDueDate")
                        .value(due));
    }

    /** 알려진 한계(56 수정 기록 5-2) — 「원래」는 확정 기준 날짜다. 해제 때 늦춰진 행 예정일은 남지 않는다. */
    @Test
    @DisplayName("[56 A3-04] 알려진 한계 — 원래 예정일이 지난 뒤 해제되면 행 예정일이 늦춰지고, 다시 보류되면 「원래」는 늦춰진 날이 아니라 확정 기준 날짜다")
    void originalDueDateAfterReleaseAndRehold() throws Exception {
        Settlement s = confirm(seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING));
        java.time.LocalDate original = java.time.LocalDate.now().minusDays(10);
        jdbc.update("UPDATE settlement SET payout_due_date = ? WHERE settlement_id = ?", original, s.getId());
        jdbc.update("UPDATE settlement_payout SET due_date = ? WHERE settlement_id = ? AND payee = 'BRAND'", original, s.getId());

        suspendBrand("SETTLEMENT_INFO_MISMATCH", null);
        releaseBrand();
        SettlementPayout released = payout(s.getId(), SettlementPayee.BRAND);
        assertThat(released.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(released.getDueDate()).isAfter(original);   // 「원래 예정일과 오늘 중 늦은 날」 — 지난 날로 되돌리지 않는다

        suspendBrand("SETTLEMENT_INFO_MISMATCH", null);
        sellerGet("/v1/seller/settlements/" + s.getId())
                .andExpect(jsonPath("$.payoutHold.reason").value("BRAND_SUSPENDED"))
                .andExpect(jsonPath("$.payoutHold.originalDueDate").value(original.toString()));
    }

    // ------------------------------------------------------------------ helpers

    private record BrandRequest(Long marketId, Long connectionId) {
    }

    /** 다른 브랜드가 이 인플루언서에게 보낸 연결 요청(REQUESTED). */
    private BrandRequest otherBrandRequest() {
        var other = fixture.createBrand("other-brand@showroomz.test", "다른브랜드");
        Connection request = transactionTemplate.execute(tx -> connectionRepository.save(
                Connection.requestPair(other.market(), creators.findById(creator.getId()).orElseThrow())));
        assertThat(request.getStatus()).isEqualTo(ConnectionStatus.REQUESTED);
        return new BrandRequest(other.marketId(), request.getId());
    }

    private Contract seedContract(ContractStatus status, LocalDateTime deadline) {
        Contract saved = transactionTemplate.execute(tx -> contractRepository.saveAndFlush(
                Contract.createDraft(marketRepository.findById(brand.marketId()).orElseThrow(),
                        creators.findById(creator.getId()).orElseThrow(), connection)));
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE contract SET status = ?, title = ?, review_requested_at = ?, signature_requested_at = ?, "
                        + "signature_deadline_at = ?, brand_signed_at = ?, creator_signed_at = ? WHERE contract_id = ?",
                status.name(), "보류 계약", now.minusDays(2),
                status == ContractStatus.SIGNING || status == ContractStatus.CONCLUSION_PENDING ? now.minusDays(1) : null,
                deadline,
                status == ContractStatus.CONCLUSION_PENDING ? now.minusHours(3) : null,
                status == ContractStatus.CONCLUSION_PENDING ? now.minusHours(2) : null,
                saved.getId());
        return contractRepository.findById(saved.getId()).orElseThrow();
    }

    private void seedExtension() {
        transactionTemplate.executeWithoutResult(tx -> extensionRequestRepository.save(GroupBuyExtensionRequest.request(
                groupBuyRepository.findById(groupBuy.getId()).orElseThrow(), brand.seller().getId(), 3, "수요 증가",
                LocalDateTime.now().minusHours(1))));
    }

    private Map<String, Object> approveBody() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return Map.of("recipientsRegistered", true, "documentUploaded", true, "requestSent", true,
                "signatureRequestedAt", now.minusMinutes(1).toString(), "signatureDeadlineAt", now.plusDays(7).toString());
    }

    private void suspendBrand(String reason, String detail) throws Exception {
        suspendBrandById(brand.marketId(), reason, detail);
    }

    private void suspendBrandById(Long marketId, String reason) throws Exception {
        suspendBrandById(marketId, reason, null);
    }

    private void suspendBrandById(Long marketId, String reason, String detail) throws Exception {
        Map<String, Object> body = detail == null ? Map.of("reason", reason) : Map.of("reason", reason, "reasonDetail", detail);
        adminPost("/v1/admin/brands/" + marketId + "/suspend", body).andExpect(status().isOk());
    }

    private void releaseBrand() throws Exception {
        adminPost("/v1/admin/brands/" + brand.marketId() + "/release", Map.of("reason", "RESOLVED"))
                .andExpect(status().isOk());
    }

    private void suspendCreator(String reason, String detail) throws Exception {
        Map<String, Object> body = detail == null ? Map.of("reason", reason) : Map.of("reason", reason, "reasonDetail", detail);
        adminPost("/v1/admin/creators/" + creator.getId() + "/suspend", body).andExpect(status().isOk());
    }

    private void suspendCreatorById(Long creatorId, String reason) throws Exception {
        adminPost("/v1/admin/creators/" + creatorId + "/suspend", Map.of("reason", reason)).andExpect(status().isOk());
    }

    private void releaseCreator() throws Exception {
        adminPost("/v1/admin/creators/" + creator.getId() + "/release", Map.of("reason", "RESOLVED"))
                .andExpect(status().isOk());
    }

    private ResultActions creatorPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions creatorPut(String url, Object body) throws Exception {
        return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions sellerPut(String url, Object body) throws Exception {
        return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    @SuppressWarnings("unused")
    private ResultActions anyGet(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token));
    }
}
