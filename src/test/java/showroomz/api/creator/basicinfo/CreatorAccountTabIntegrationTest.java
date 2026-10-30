package showroomz.api.creator.basicinfo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.repository.CreatorAdminHistoryRepository;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;
import showroomz.domain.member.creator.service.CreatorSanctionGuard;
import showroomz.domain.member.creator.service.CreatorWithdrawalChecklist;
import showroomz.domain.member.creator.service.CreatorWithdrawalEligibility;
import showroomz.domain.member.creator.service.CreatorWithdrawalService;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorWithdrawalCondition;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스튜디오 기본정보 관리 — 계정 탭(ui-studio-06 S1 ~ S8 · 50 설계서 14절 AC). 정산 시드({@link SettlementTestSupport})가 진행 중 공구 1건 ·
 * 체결완료 계약 1건 · 인플루언서 1명 · 운영자 「김운영」을 깐다 — 그래서 시작 상태는 탈퇴 조건 미충족(S4)이다.
 */
@DisplayName("스튜디오 기본정보 — 계정 탭 · 업무 이메일 · 탈퇴 요청 · 취소 · 반려 확인")
class CreatorAccountTabIntegrationTest extends SettlementTestSupport {

    private static final String BASE = "/v1/creator/basic-info";

    @Autowired private CreatorWithdrawalEligibility eligibility;
    @Autowired private CreatorWithdrawalChecklist checklist;
    @Autowired private CreatorWithdrawalRequestRepository withdrawalRequests;
    @Autowired private CreatorWithdrawalService withdrawalService;
    @Autowired private CreatorSanctionGuard sanctionGuard;
    @Autowired private CreatorAdminHistoryRepository adminHistories;

    @Test
    @DisplayName("[AC-01] 계정 탭 — 이름 · 휴대폰은 서버가 마스킹 · 원값은 응답에 없다 · 로그인 수단 · 인플루언서 외 403")
    void identityMasked() throws Exception {
        jdbc.update("UPDATE creator SET real_name = ?, phone_number = ?, birthday = ? WHERE creator_id = ?",
                "소지연", "010-1234-6416", "2003-07-19", creator.getId());

        JsonNode body = json(creatorGet(BASE + "/account").andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.realName").value("소*연"))
                .andExpect(jsonPath("$.identity.phoneNumber").value("010-****-6416"))
                .andExpect(jsonPath("$.identity.birthday").value("2003-07-19"))
                .andExpect(jsonPath("$.businessEmail").value(creator.getBusinessEmail()))
                .andExpect(jsonPath("$.login.provider").value("LOCAL"))
                .andExpect(jsonPath("$.login.providerLabel").value("이메일")));
        assertThat(body.toString()).doesNotContain("소지연").doesNotContain("010-1234-6416");

        mockMvc.perform(get(BASE + "/account").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[AC-02] 조건 미충족(S4) — 진행중 공구 1 · 체결완료 계약 1 · 조정 협의 1 · 0 / 3 · 0 건 키 없음")
    void blockedBreakdown() throws Exception {
        seedSettlement("가을 공구", 1_000_000, SettlementStatus.ADJUSTING);

        creatorGet(BASE + "/account").andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawal.state").value("BLOCKED"))
                .andExpect(jsonPath("$.withdrawal.satisfiedCount").value(0))
                .andExpect(jsonPath("$.withdrawal.totalCount").value(3))
                .andExpect(jsonPath("$.withdrawal.approvalDueBusinessDays").value(5))
                .andExpect(jsonPath("$.withdrawal.conditions[0].code").value("UNFINISHED_GROUP_BUYS"))
                .andExpect(jsonPath("$.withdrawal.conditions[0].count").value(1))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown.length()").value(1))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown[0].key").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown[0].label").value("진행중"))
                .andExpect(jsonPath("$.withdrawal.conditions[1].code").value("UNFINISHED_CONTRACTS"))
                .andExpect(jsonPath("$.withdrawal.conditions[1].breakdown[0].key").value("CONCLUDED"))
                .andExpect(jsonPath("$.withdrawal.conditions[1].breakdown[0].label").value("계약 성립"))
                .andExpect(jsonPath("$.withdrawal.conditions[1].count").value(1))
                .andExpect(jsonPath("$.withdrawal.conditions[2].code").value("UNSETTLED"))
                .andExpect(jsonPath("$.withdrawal.conditions[2].breakdown[0].key").value("ADJUSTING"))
                .andExpect(jsonPath("$.withdrawal.conditions[2].count").value(1))
                .andExpect(jsonPath("$.withdrawal.pendingRequest").doesNotExist());
    }

    @Test
    @DisplayName("[AC-03] 조건 모두 충족(S5) — AVAILABLE · 3 / 3 · 조건 행은 사라지지 않고 충족으로 남는다")
    void available() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);

        creatorGet(BASE + "/account").andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawal.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.withdrawal.satisfiedCount").value(3))
                .andExpect(jsonPath("$.withdrawal.conditions.length()").value(3))
                .andExpect(jsonPath("$.withdrawal.conditions[0].satisfied").value(true))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown.length()").value(0));
    }

    @Test
    @DisplayName("[AC-04] 불변식 — 판정식 통과 ⇔ 조건 행 전부 충족 · ① · ③ 건수는 판정식과 같다")
    void checklistMatchesEligibility() {
        assertInvariant();
        seedSettlement("가을 공구", 1_000_000, SettlementStatus.ADJUSTING);
        assertInvariant();
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        assertInvariant();
        jdbc.update("UPDATE settlement SET status = 'PAID'");
        assertInvariant();
        assertThat(eligibility.evaluate(creator.getId()).eligible()).isTrue();
    }

    @Test
    @DisplayName("[AC-05] 업무 이메일 — 점 없는 도메인 400 · 공백 400 · 앞뒤 공백은 지우고 저장")
    void businessEmail() throws Exception {
        patchEmail("soyeon@example").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 형식이 올바르지 않습니다."));
        patchEmail("   ").andExpect(status().isBadRequest());

        patchEmail("  new@brand.com ").andExpect(status().isOk())
                .andExpect(jsonPath("$.businessEmail").value("new@brand.com"));
        assertThat(creatorRepository.findById(creator.getId()).orElseThrow().getBusinessEmail()).isEqualTo("new@brand.com");
    }

    @Test
    @DisplayName("[AC-06 · AC-07] 탈퇴 요청 → 대기(S7) · 중복 409 · 취소 → 다시 가능 · 조건 미충족 409 · 사유 공백 400")
    void requestAndCancel() throws Exception {
        requestWithdrawal("활동 중단").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_CONDITION_UNMET"))
                .andExpect(jsonPath("$.unmet.UNFINISHED_GROUP_BUYS").value(1));
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        requestWithdrawal("  ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_REASON_REQUIRED"));

        long requestId = json(requestWithdrawal("활동 중단").andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").isNotEmpty())).path("requestId").asLong();
        creatorGet(BASE + "/account")
                .andExpect(jsonPath("$.withdrawal.state").value("PENDING"))
                .andExpect(jsonPath("$.withdrawal.pendingRequest.requestId").value(requestId))
                .andExpect(jsonPath("$.withdrawal.pendingRequest.reasonText").value("활동 중단"))
                .andExpect(jsonPath("$.withdrawal.conditions.length()").value(3));
        requestWithdrawal("또 신청").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_ALREADY_PENDING"));

        creatorPost(BASE + "/withdrawal-requests/" + requestId + "/cancel").andExpect(status().isNoContent());
        creatorPost(BASE + "/withdrawal-requests/" + requestId + "/cancel").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_PENDING"));
        creatorGet(BASE + "/account").andExpect(jsonPath("$.withdrawal.state").value("AVAILABLE"));
    }

    @Test
    @DisplayName("[AC-08] 운영자 반려 → 반려 안내(S8) 사유 · 안내 → [확인] 두 번 204 → 체크리스트로 돌아온다")
    void rejectionAcknowledged() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        long requestId = json(requestWithdrawal("활동 중단").andExpect(status().isCreated())).path("requestId").asLong();
        reject(requestId);

        creatorGet(BASE + "/account")
                .andExpect(jsonPath("$.withdrawal.state").value("REJECTED"))
                .andExpect(jsonPath("$.withdrawal.rejection.requestId").value(requestId))
                .andExpect(jsonPath("$.withdrawal.rejection.reasonCode").value("DISPUTE_IN_PROGRESS"))
                .andExpect(jsonPath("$.withdrawal.rejection.reasonLabel").value("분쟁·신고 처리 중"))
                .andExpect(jsonPath("$.withdrawal.rejection.note").value("분쟁 종결 후 다시 신청해 주세요"));

        creatorPost(BASE + "/withdrawal-requests/" + requestId + "/acknowledge").andExpect(status().isNoContent());
        creatorPost(BASE + "/withdrawal-requests/" + requestId + "/acknowledge").andExpect(status().isNoContent());
        creatorGet(BASE + "/account")
                .andExpect(jsonPath("$.withdrawal.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.withdrawal.rejection").doesNotExist());
    }

    @Test
    @DisplayName("[AC-09] 반려 안내를 닫지 않고 재요청 — 201 · 옛 반려는 확인한 것으로 남는다")
    void rerequestAcknowledgesOldRejection() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        long rejectedId = json(requestWithdrawal("활동 중단").andExpect(status().isCreated())).path("requestId").asLong();
        reject(rejectedId);

        requestWithdrawal("다시 신청").andExpect(status().isCreated());

        assertThat(withdrawalRequests.findById(rejectedId).orElseThrow().getResultAcknowledgedAt()).isNotNull();
        creatorGet(BASE + "/account").andExpect(jsonPath("$.withdrawal.state").value("PENDING"));
    }

    @Test
    @DisplayName("[AC-10] 대기 중 신청에 [확인] 409 · 없는 신청 404")
    void acknowledgeRequiresRejection() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        long requestId = json(requestWithdrawal("활동 중단").andExpect(status().isCreated())).path("requestId").asLong();

        creatorPost(BASE + "/withdrawal-requests/" + requestId + "/acknowledge").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_REJECTED"));
        creatorPost(BASE + "/withdrawal-requests/999999/acknowledge").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ 시나리오 보완(50 테스트 시나리오 14-2)

    @Test
    @DisplayName("[AC-02 전체] 시안 S1 조합 — 진행중 1 · 종료 후 주문 종결 대기 1 · 체결완료(미종결 공구 2건의 계약) · 정산 확인 중 1 · 조정 협의 2 · 지급 예정 1")
    void blockedBreakdownFullComposition() throws Exception {
        paidGroup();
        endGroupBuy();
        seedIn(GroupBuyStatus.IN_PROGRESS);
        seedSettlement("정산 확인 중 공구", 1_000_000, SettlementStatus.REVIEWING);
        seedSettlement("조정 공구 1", 1_000_000, SettlementStatus.ADJUSTING);
        seedSettlement("조정 공구 2", 1_000_000, SettlementStatus.ADJUSTING);
        seedSettlement("지급 예정 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);

        creatorGet(BASE + "/account").andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawal.state").value("BLOCKED"))
                .andExpect(jsonPath("$.withdrawal.satisfiedCount").value(0))
                .andExpect(jsonPath("$.withdrawal.conditions[0].count").value(2))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown.length()").value(2))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown[0].key").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown[1].key").value("AWAITING_ORDER_CLOSURE"))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown[1].label").value("종료 후 주문 종결 대기"))
                .andExpect(jsonPath("$.withdrawal.conditions[0].resolution").isNotEmpty())
                .andExpect(jsonPath("$.withdrawal.conditions[1].count").value(2))
                .andExpect(jsonPath("$.withdrawal.conditions[1].breakdown.length()").value(1))
                .andExpect(jsonPath("$.withdrawal.conditions[1].breakdown[0].key").value("CONCLUDED"))
                .andExpect(jsonPath("$.withdrawal.conditions[2].count").value(4))
                .andExpect(jsonPath("$.withdrawal.conditions[2].breakdown[0].key").value("REVIEWING"))
                .andExpect(jsonPath("$.withdrawal.conditions[2].breakdown[0].count").value(1))
                .andExpect(jsonPath("$.withdrawal.conditions[2].breakdown[1].key").value("ADJUSTING"))
                .andExpect(jsonPath("$.withdrawal.conditions[2].breakdown[1].count").value(2))
                .andExpect(jsonPath("$.withdrawal.conditions[2].breakdown[2].key").value("PAYOUT_SCHEDULED"))
                .andExpect(jsonPath("$.withdrawal.conditions[2].breakdown[2].count").value(1));
        assertInvariant();
    }

    @Test
    @DisplayName("[WD-01 · WD-04] 내역 키 전수 · 코드 순서 — 공구 4상태 · 계약 서명 진행중 · 체결 처리 대기 · 분배 실패 · 남의 데이터는 세지 않는다")
    void breakdownKeysInOrder() throws Exception {
        seedIn(GroupBuyStatus.PREPARING);
        seedIn(GroupBuyStatus.READY);
        seedIn(GroupBuyStatus.SUSPENSION_SCHEDULED);
        draftContractIn("SIGNING");
        draftContractIn("CONCLUSION_PENDING");
        seedSettlement("분배 실패 공구", 1_000_000, SettlementStatus.PAYOUT_FAILED);
        seed(brand, createCreator("다른_쇼룸", "other"), "남의 공구",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5));

        creatorGet(BASE + "/account").andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawal.conditions[0].count").value(4))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown[0].key").value("PREPARING"))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown[1].key").value("READY"))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown[2].key").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.withdrawal.conditions[0].breakdown[3].key").value("SUSPENSION_SCHEDULED"))
                .andExpect(jsonPath("$.withdrawal.conditions[1].breakdown[0].key").value("SIGNING"))
                .andExpect(jsonPath("$.withdrawal.conditions[1].breakdown[1].key").value("CONCLUSION_PENDING"))
                .andExpect(jsonPath("$.withdrawal.conditions[1].breakdown[2].key").value("CONCLUDED"))
                .andExpect(jsonPath("$.withdrawal.conditions[1].breakdown[2].count").value(4))
                .andExpect(jsonPath("$.withdrawal.conditions[1].count").value(6))
                .andExpect(jsonPath("$.withdrawal.conditions[2].breakdown[0].key").value("PAYOUT_FAILED"))
                .andExpect(jsonPath("$.withdrawal.conditions[2].count").value(1));
        assertInvariant();
    }

    @Test
    @DisplayName("[AC-06 · AC-07 제약] 대기 중 — 새 연결 수락 409 · 서명 요청 대상 409 → 취소하면 같은 순간 둘 다 풀린다 · 이력 요청 → 취소")
    void pendingRestrictionsLiftOnCancel() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        BrandFixture.Brand other = fixture.createBrand("other-brand@showroomz.test", "다른랩");
        Connection requested = connectionRepository.save(Connection.requestPair(other.market(), creator));
        long requestId = json(requestWithdrawal("활동 중단").andExpect(status().isCreated())).path("requestId").asLong();

        creatorPost("/v1/creator/connections/" + requested.getId() + "/accept").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_PENDING"));
        assertThatThrownBy(() -> sanctionGuard.requireCanReceiveContract(reloadCreator()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREATOR_COUNTERPARTY_WITHDRAWAL_PENDING);

        creatorPost(BASE + "/withdrawal-requests/" + requestId + "/cancel").andExpect(status().isNoContent());

        sanctionGuard.requireCanReceiveContract(reloadCreator());
        creatorPost("/v1/creator/connections/" + requested.getId() + "/accept").andExpect(status().isNoContent());
        assertThat(adminEvents()).containsSubsequence(CreatorAdminEventType.WITHDRAWAL_REQUESTED,
                CreatorAdminEventType.WITHDRAWAL_CANCELED);
    }

    @Test
    @DisplayName("[WD-05] 탈퇴 사유 — null 400 · 500자 201 · 501자 400(INVALID_INPUT) · 앞뒤 공백은 지우고 저장")
    void withdrawalReasonBoundaries() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        Map<String, Object> nullReason = new HashMap<>();
        nullReason.put("reason", null);
        mockMvc.perform(post(BASE + "/withdrawal-requests").header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(nullReason)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_REASON_REQUIRED"));
        requestWithdrawal("가".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("탈퇴 사유는 500자 이내로 입력해 주세요."));
        assertThat(withdrawalRequests.findAll()).isEmpty();

        long longId = json(requestWithdrawal("가".repeat(500)).andExpect(status().isCreated())).path("requestId").asLong();
        creatorPost(BASE + "/withdrawal-requests/" + longId + "/cancel").andExpect(status().isNoContent());

        long trimmedId = json(requestWithdrawal("  활동 중단  ").andExpect(status().isCreated())).path("requestId").asLong();
        assertThat(withdrawalRequests.findById(trimmedId).orElseThrow().getReasonText()).isEqualTo("활동 중단");
    }

    @Test
    @DisplayName("[WD-08] 처리 기한 — 2026-10-02(금) 신청 · 10.05 · 10.09 공휴일 · 주말을 건너 +5영업일 = 10.13(화)")
    void dueDateSkipsHolidays() {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        fixHolidays();

        CreatorWithdrawalRequest saved = withdrawalService.request(creator.getId(), "활동 중단",
                LocalDateTime.of(2026, 10, 2, 14, 0));

        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 13));
    }

    @Test
    @DisplayName("[WD-09] 정지 중 신청 — 허용(§51-5 · 조건은 그대로)")
    void suspendedCreatorCanRequest() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        jdbc.update("UPDATE creator SET status = 'SUSPENDED' WHERE creator_id = ?", creator.getId());

        requestWithdrawal("활동 중단").andExpect(status().isCreated());
        creatorGet(BASE + "/account").andExpect(jsonPath("$.withdrawal.state").value("PENDING"));
    }

    @Test
    @DisplayName("[WD-10 · WD-11 · WD-12] 남의 신청 취소 · 확인 404 · 반려된 신청 취소 409 · 취소된 신청 확인 409 · 취소 후 재요청은 새 행")
    void cancelAndAcknowledgeGuards() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        Creator other = createCreator("다른_쇼룸", "other");
        CreatorWithdrawalRequest othersRequest = withdrawalService.request(other.getId(), "활동 중단", LocalDateTime.now());
        creatorPost(BASE + "/withdrawal-requests/" + othersRequest.getId() + "/cancel").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_FOUND"));
        adminPost("/v1/admin/creators/" + other.getId() + "/withdrawal-requests/" + othersRequest.getId() + "/reject",
                Map.of("reason", "DISPUTE_IN_PROGRESS", "note", "분쟁 종결 후 다시 신청해 주세요")).andExpect(status().isOk());
        creatorPost(BASE + "/withdrawal-requests/" + othersRequest.getId() + "/acknowledge").andExpect(status().isNotFound());
        assertThat(withdrawalRequests.findById(othersRequest.getId()).orElseThrow().getResultAcknowledgedAt()).isNull();

        long canceledId = json(requestWithdrawal("활동 중단").andExpect(status().isCreated())).path("requestId").asLong();
        creatorPost(BASE + "/withdrawal-requests/" + canceledId + "/cancel").andExpect(status().isNoContent());
        creatorPost(BASE + "/withdrawal-requests/" + canceledId + "/acknowledge").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_REJECTED"));

        long rejectedId = json(requestWithdrawal("다시 신청").andExpect(status().isCreated())).path("requestId").asLong();
        assertThat(rejectedId).isNotEqualTo(canceledId);
        assertThat(withdrawalRequests.findById(canceledId).orElseThrow().getStatus()).isEqualTo(MemberWithdrawalStatus.CANCELED);
        reject(rejectedId);
        creatorPost(BASE + "/withdrawal-requests/" + rejectedId + "/cancel").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_PENDING"));
    }

    @Test
    @DisplayName("[WD-13 · AT-03] 반려 안내 중에도 조건 행은 내려온다 · 배너 판정은 가장 최근 신청만 — 재요청 뒤 취소하면 반려로 돌아가지 않는다")
    void rejectionBannerFollowsLatestRequest() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        long rejectedId = json(requestWithdrawal("활동 중단").andExpect(status().isCreated())).path("requestId").asLong();
        reject(rejectedId);
        creatorGet(BASE + "/account")
                .andExpect(jsonPath("$.withdrawal.state").value("REJECTED"))
                .andExpect(jsonPath("$.withdrawal.conditions.length()").value(3));

        long nextId = json(requestWithdrawal("다시 신청").andExpect(status().isCreated())).path("requestId").asLong();
        creatorPost(BASE + "/withdrawal-requests/" + nextId + "/cancel").andExpect(status().isNoContent());

        creatorGet(BASE + "/account")
                .andExpect(jsonPath("$.withdrawal.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.withdrawal.rejection").doesNotExist())
                .andExpect(jsonPath("$.withdrawal.pendingRequest").doesNotExist());
    }

    @Test
    @DisplayName("[EM-01 · EM-02] 이메일 형식 경계 — a@b.c 통과 · 골뱅이 둘 · 가운데 공백 · 로컬 없음 · 도메인 공백 400 · 같은 값 200")
    void businessEmailFormats() throws Exception {
        patchEmail("a@b.c").andExpect(status().isOk()).andExpect(jsonPath("$.businessEmail").value("a@b.c"));
        for (String invalid : List.of("a@@b.com", "a b@c.com", "@b.com", "a@b .com")) {
            patchEmail(invalid).andExpect(status().isBadRequest());
        }
        patchEmail("a@b.c").andExpect(status().isOk()).andExpect(jsonPath("$.businessEmail").value("a@b.c"));
        assertThat(reloadCreator().getBusinessEmail()).isEqualTo("a@b.c");
    }

    @Test
    @DisplayName("[EM-03 · EM-04] 길이 초과 400 · 다른 인플루언서와 같은 주소도 저장(중복 검사 없음) · 처리 이력은 남기지 않는다")
    void businessEmailLengthAndSideEffects() throws Exception {
        String before = reloadCreator().getBusinessEmail();
        patchEmail("a".repeat(65) + "@brand.com").andExpect(status().isBadRequest());
        patchEmail("a".repeat(500) + "@brand.com").andExpect(status().isBadRequest());
        assertThat(reloadCreator().getBusinessEmail()).isEqualTo(before);

        Creator other = createCreator("다른_쇼룸", "other");
        int histories = adminEvents().size();
        patchEmail(other.getBusinessEmail()).andExpect(status().isOk());
        assertThat(reloadCreator().getBusinessEmail()).isEqualTo(other.getBusinessEmail());
        assertThat(adminEvents()).hasSize(histories);
    }

    @Test
    @DisplayName("[AT-02] 로그인 수단 라벨 — 카카오 · 네이버 · 구글 · 애플 · 페이스북")
    void loginProviderLabels() throws Exception {
        Map<String, String> labels = Map.of("KAKAO", "카카오", "NAVER", "네이버", "GOOGLE", "구글", "APPLE", "애플",
                "FACEBOOK", "페이스북");
        for (Map.Entry<String, String> label : labels.entrySet()) {
            jdbc.update("UPDATE USERS SET PROVIDER_TYPE = ? WHERE USER_ID = ?", label.getKey(), creator.getUser().getId());
            creatorGet(BASE + "/account")
                    .andExpect(jsonPath("$.login.provider").value(label.getKey()))
                    .andExpect(jsonPath("$.login.providerLabel").value(label.getValue()));
        }
    }

    // ------------------------------------------------------------------ helpers

    private void draftContractIn(String status) {
        Long contractId = inTransaction(() -> contractRepository.saveAndFlush(
                Contract.createDraft(brand.market(), creator, null)).getId());
        jdbc.update("UPDATE contract SET status = ? WHERE contract_id = ?", status, contractId);
    }

    private Creator reloadCreator() {
        return creatorRepository.findById(creator.getId()).orElseThrow();
    }

    private List<CreatorAdminEventType> adminEvents() {
        return adminHistories.findByCreatorIdOrderByCreatedAtDescIdDesc(creator.getId()).stream()
                .map(h -> h.getEventType()).toList().reversed();
    }

    private void assertInvariant() {
        CreatorWithdrawalEligibility.Result result = eligibility.evaluate(creator.getId());
        List<CreatorWithdrawalChecklist.Condition> conditions = checklist.read(creator.getId());
        assertThat(conditions.stream().allMatch(CreatorWithdrawalChecklist.Condition::satisfied))
                .isEqualTo(result.eligible());
        for (CreatorWithdrawalChecklist.Condition c : conditions) {
            if (c.condition() == CreatorWithdrawalCondition.UNFINISHED_CONTRACTS) {
                assertThat(c.count()).isGreaterThanOrEqualTo(result.count(c.condition()));
            } else {
                assertThat(c.count()).isEqualTo(result.count(c.condition()));
            }
        }
    }

    private void reject(long requestId) throws Exception {
        adminPost("/v1/admin/creators/" + creator.getId() + "/withdrawal-requests/" + requestId + "/reject",
                Map.of("reason", "DISPUTE_IN_PROGRESS", "note", "분쟁 종결 후 다시 신청해 주세요"))
                .andExpect(status().isOk());
    }

    private ResultActions requestWithdrawal(String reason) throws Exception {
        return mockMvc.perform(post(BASE + "/withdrawal-requests").header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("reason", reason))));
    }

    private ResultActions patchEmail(String email) throws Exception {
        return mockMvc.perform(patch(BASE + "/account/business-email").header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("businessEmail", email))));
    }

    private ResultActions creatorPost(String url) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, creatorToken));
    }
}
