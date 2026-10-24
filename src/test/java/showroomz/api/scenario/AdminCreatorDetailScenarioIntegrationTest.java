package showroomz.api.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.creator.AdminCreatorTestSupport;
import showroomz.api.app.user.DTO.WithdrawalRequest;
import showroomz.api.app.user.service.UserService;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 53 테스트 시나리오 16절 — 시안 ui-admin-18 의 상태(A1 · A1-u · B1 · C1 · D1 · M1 ~ M4)를 한 흐름으로 순회한다. 단계마다 어드민 응답 ·
 * 스튜디오 응답 · DB 를 함께 본다. 스튜디오 탈퇴 신청은 실제 API(50 설계서 4절)를 탄다.
 */
@DisplayName("[시나리오] 어드민 인플루언서 상세 — 시안 상태 순회")
class AdminCreatorDetailScenarioIntegrationTest extends AdminCreatorTestSupport {

    private static final String STUDIO = "/v1/creator/basic-info";

    @Autowired private UserService userService;

    @Test
    @DisplayName("[E2E-1] A1 → A1-u → 잠금 → C1(정산 정보 불일치) → A1 — 마스킹 · 열람 기록 · 지급 보류 · 카드 · 다음 영업일 지급")
    void unmaskThenSuspendThenRelease() throws Exception {
        registerCreatorResidentNumber();
        Settlement s = seedSettlement("가을 립밤 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), s.getId());

        adminGet(base).andExpect(jsonPath("$.identity.name").value("김*민"))
                .andExpect(jsonPath("$.unmask.allowed").value(true));
        adminPost(base + "/privacy-unmask", Map.of("reason", "SETTLEMENT_WITHHOLDING_CHECK")).andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.name").value(REAL_NAME));
        mockMvc.perform(post(base + "/privacy-lock").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        adminGet(base).andExpect(jsonPath("$.identity.unmasked").value(false));

        adminPost(base + "/suspend", Map.of("reason", "SETTLEMENT_INFO_MISMATCH")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.rail.currentStatus.suspension.payoutHeld").value(true));
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        creatorGet("/v1/creator/profile").andExpect(jsonPath("$.sanction.payoutHeld").value(true));

        adminPost(base + "/release", Map.of("note", "계좌 실명 재확인")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rail.recentHistories[0].label").value("정지 해제"))
                .andExpect(jsonPath("$.rail.recentHistories[1].label").value("정지"))
                .andExpect(jsonPath("$.rail.recentHistories[2].label").value("본인확인 정보 마스킹 해제"));
        SettlementPayout released = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(released.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(released.getDueDate()).isEqualTo(businessCalendar.addBusinessDays(LocalDate.now(), 1));
        assertThat(cardTypes()).containsExactly(MessageCardType.ACCOUNT_SUSPENDED, MessageCardType.ACCOUNT_SUSPENSION_RELEASED);
        assertThat(privacyLogs.findByCreatorId(creator.getId())).hasSize(1);
        assertThat(userStatus()).isEqualTo(UserStatus.NORMAL);
    }

    @Test
    @DisplayName("[E2E-2] A1 → B1 → M3 반려 → B1 재신청 → M2 승인 409(롤백) → 공구 종료 → M2 승인 → D1")
    void withdrawalReviewCycle() throws Exception {
        jdbc.update("UPDATE creator SET showroom_address = ? WHERE creator_id = ?", "jimin_room", creator.getId());
        adminPost(base + "/memos", Map.of("content", "탈퇴 문의 접수")).andExpect(status().isOk());
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);

        long first = json(studioRequest("콘텐츠 활동을 중단합니다").andExpect(status().isCreated())).get("requestId").asLong();
        adminGet(base).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rail.withdrawalReview.requestId").value(first))
                .andExpect(jsonPath("$.rail.withdrawalReview.conditions.unfinishedGroupBuys").value(0))
                .andExpect(jsonPath("$.rail.recentHistories.length()").value(2))
                .andExpect(jsonPath("$.rail.recentHistories[0].eventType").value("WITHDRAWAL_REQUESTED"));

        adminPost(base + "/withdrawal-requests/" + first + "/reject",
                Map.of("reason", "DISPUTE_IN_PROGRESS", "note", "분쟁 종결 후 다시 신청해 주세요")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rail.withdrawalReview").doesNotExist());
        creatorGet(STUDIO + "/account").andExpect(jsonPath("$.withdrawal.state").value("REJECTED"));

        long second = json(studioRequest("다시 신청합니다").andExpect(status().isCreated())).get("requestId").asLong();
        assertThat(second).isNotEqualTo(first);
        GroupBuy late = seedIn(GroupBuyStatus.READY);
        adminPost(base + "/withdrawal-requests/" + second + "/approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.unmet.UNFINISHED_GROUP_BUYS").value(1));
        assertThat(withdrawalRequests.findById(second).orElseThrow().getStatus()).isEqualTo(MemberWithdrawalStatus.PENDING);
        assertThat(reloadCreator().getStatus()).isEqualTo(CreatorStatus.ACTIVE);

        moveTo(late.getId(), GroupBuyStatus.ENDED);
        adminPost(base + "/withdrawal-requests/" + second + "/approve", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.identity.destroyed").value(true))
                .andExpect(jsonPath("$.basic.showroomPublic").value(false));
        assertThat(withdrawalRequests.findById(first).orElseThrow().getStatus()).isEqualTo(MemberWithdrawalStatus.REJECTED);
        assertThat(jdbc.queryForObject("SELECT role_type FROM users WHERE user_id = ?", String.class,
                creator.getUser().getId())).isEqualTo("USER");
        assertThat(json(getAs(consumerToken, "/v1/user/showrooms?size=50")).get("content").findValues("showroomId")
                .stream().map(v -> v.asLong()).toList()).doesNotContain(creator.getId());
        adminGet(base + "/memos").andExpect(jsonPath("$.total").value(1));
        assertThat(events()).startsWith(CreatorAdminEventType.WITHDRAWAL_APPROVED, CreatorAdminEventType.CONNECTIONS_ENDED,
                CreatorAdminEventType.WITHDRAWAL_REQUESTED, CreatorAdminEventType.WITHDRAWAL_REJECTED,
                CreatorAdminEventType.WITHDRAWAL_REQUESTED, CreatorAdminEventType.MEMO_CREATED);
        assertThat(userStatus()).isEqualTo(UserStatus.NORMAL);
    }

    @Test
    @DisplayName("[E2E-3] C1 ∧ B1 → D1 — 지급 무관 사유 정지 중 신청 · 승인이 정지를 시스템 종결 · 이력 순서")
    void suspendedCreatorWithdraws() throws Exception {
        adminPost(base + "/suspend", Map.of("reason", "CONSUMER_RESPONSE_NEGLECT")).andExpect(status().isOk());
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        long requestId = json(studioRequest("본업 집중").andExpect(status().isCreated())).get("requestId").asLong();
        adminGet(base).andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.rail.withdrawalReview.requestId").value(requestId));

        adminPost(base + "/withdrawal-requests/" + requestId + "/approve", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.rail.currentStatus.suspension").doesNotExist());
        assertThat(events()).containsExactly(CreatorAdminEventType.WITHDRAWAL_APPROVED,
                CreatorAdminEventType.CONNECTIONS_ENDED, CreatorAdminEventType.SUSPENSION_RELEASED,
                CreatorAdminEventType.WITHDRAWAL_REQUESTED, CreatorAdminEventType.SUSPENDED);
        assertThat(cardTypes()).containsExactly(MessageCardType.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("[E2E-4] 정지로 탈퇴를 피하는 길이 없다 — 지급 정지 · 미정산이 남으면 스튜디오 신청 409 · 승인 409 · 소비자 앱 탈퇴 409")
    void suspensionCannotBeEscapedByWithdrawal() throws Exception {
        adminPost(base + "/suspend", Map.of("reason", "SETTLEMENT_INFO_MISMATCH")).andExpect(status().isOk());
        seedSettlement("보류 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);

        studioRequest("정지 중 탈퇴").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_CONDITION_UNMET"));
        CreatorWithdrawalRequest saved = savePendingRequest("정지 중 탈퇴", LocalDateTime.now().minusDays(1));
        adminPost(base + "/withdrawal-requests/" + saved.getId() + "/approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.unmet.UNSETTLED").value(1));

        WithdrawalRequest consumerWithdrawal = new ObjectMapper().readValue("{\"agreeConsent\":true}", WithdrawalRequest.class);
        assertThatThrownBy(() -> userService.withdrawUser(creator.getUser().getUsername(), consumerWithdrawal))
                .extracting("errorCode").isEqualTo(ErrorCode.USER_WITHDRAWAL_REQUIRES_CREATOR_REVIEW);
        assertThat(reloadCreator().getStatus()).isEqualTo(CreatorStatus.SUSPENDED);
        assertThat(userStatus()).isEqualTo(UserStatus.NORMAL);
    }

    private ResultActions studioRequest(String reason) throws Exception {
        return mockMvc.perform(post(STUDIO + "/withdrawal-requests").header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("reason", reason))));
    }
}
