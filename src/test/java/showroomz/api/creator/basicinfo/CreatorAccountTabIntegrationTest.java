package showroomz.api.creator.basicinfo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;
import showroomz.domain.member.creator.service.CreatorWithdrawalChecklist;
import showroomz.domain.member.creator.service.CreatorWithdrawalEligibility;
import showroomz.domain.member.creator.type.CreatorWithdrawalCondition;
import showroomz.domain.settlement.type.SettlementStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
                .andExpect(jsonPath("$.withdrawal.conditions[1].breakdown[0].label").value("체결완료"))
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

    // ------------------------------------------------------------------ helpers

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
