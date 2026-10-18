package showroomz.api.admin.creator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.DTO.WithdrawalInfoResponse;
import showroomz.api.app.user.DTO.WithdrawalRequest;
import showroomz.api.app.user.service.UserService;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.repository.CreatorAdminHistoryRepository;
import showroomz.domain.member.creator.repository.CreatorPrivacyAccessLogRepository;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;
import showroomz.domain.member.creator.service.CreatorSanctionGuard;
import showroomz.domain.member.creator.service.CreatorWithdrawalService;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.ThreadStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 회원 관리 — 인플루언서 상세(ui-admin-18 rev.1 · 53 설계서).
 *
 * <ul>
 *   <li>CR-01 상세 — 기본 마스킹 · 계좌 마스킹 · 운영자 외 403</li>
 *   <li>CR-02 마스킹 해제 — 실값 · 세션은 운영자 1명 · 1건 · 두 번 눌러도 로그 1건 · 지금 잠금 · 이력</li>
 *   <li>CR-03 메모 — 누적 · 300자 · 작성자 · 24시간</li>
 *   <li>CR-04 정지 · 해제 — 사유 · 카드 · 이력 · 중복 409 · 서버 가드</li>
 *   <li>CR-05 지급 정지 — 정산 정보 불일치만 · 예정 행 보류 · 확정 · 지시 앞단 · 해제 다음 영업일</li>
 *   <li>CR-06 탈퇴 — 조건 재검증 409 · 승인 파기 · 연결 종료 · 소비자 계정 유지 · 반려</li>
 *   <li>CR-07 하단 카드 — 운영 현황 · 최근 정산 · 계약 · 연결 브랜드</li>
 * </ul>
 */
@DisplayName("어드민 인플루언서 상세 — 마스킹 · 메모 · 정지 · 지급 정지 · 탈퇴 심사")
class AdminCreatorIntegrationTest extends SettlementTestSupport {

    private static final String CREATORS = "/v1/admin/creators/";

    @Autowired private CreatorAdminHistoryRepository adminHistories;
    @Autowired private CreatorPrivacyAccessLogRepository privacyLogs;
    @Autowired private CreatorWithdrawalRequestRepository withdrawalRequests;
    @Autowired private CreatorWithdrawalService withdrawalService;
    @Autowired private CreatorSanctionGuard sanctionGuard;
    @Autowired private UserService userService;

    private Seller otherOperator;
    private String otherAdminToken;
    private String base;

    @BeforeEach
    void setUpCreatorMember() {
        otherOperator = fixture.createAdmin("ops2@showroomz.test", "박운영");
        otherAdminToken = adminToken(otherOperator);
        base = CREATORS + creator.getId();
        jdbc.update("UPDATE creator SET real_name = ?, birthday = ?, phone_number = ?, bank_name = ?, account_number = ? "
                        + "WHERE creator_id = ?", "김지민", "1994-06-02", "01033918842", "카카오뱅크", "333301128842",
                creator.getId());
    }

    // ------------------------------------------------------------------ CR-01 상세

    @Test
    @DisplayName("CR-01 상세 — 본인확인 · 계좌는 서버가 마스킹해 내린다 · 회원번호 INF- · 활성 · 운영자 외 403")
    void detailMaskedByDefault() throws Exception {
        adminGet(base).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberNo").value("INF-" + creator.getId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.statusTone").value("SUCCESS"))
                .andExpect(jsonPath("$.identity.unmasked").value(false))
                .andExpect(jsonPath("$.identity.name").value("김*민"))
                .andExpect(jsonPath("$.identity.birthday").value("1994.**.**"))
                .andExpect(jsonPath("$.identity.phone").value("010-****-8842"))
                .andExpect(jsonPath("$.identity.carrier").value(nullValue()))
                .andExpect(jsonPath("$.settlementAccount.accountNumber").value("333*****8842"))
                .andExpect(jsonPath("$.settlementAccount.accountHolder").value("김*민"))
                .andExpect(jsonPath("$.business.businessType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.business.withholdingType").value("WITHHOLDING_3_3"))
                .andExpect(jsonPath("$.unmask.allowed").value(true))
                .andExpect(jsonPath("$.rail.currentStatus.suspension").value(nullValue()))
                .andExpect(jsonPath("$.rail.withdrawalReview").value(nullValue()));

        mockMvc.perform(get(base).header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().isForbidden());
        mockMvc.perform(get(base).header(HttpHeaders.AUTHORIZATION, brandToken)).andExpect(status().isForbidden());
        adminGet(CREATORS + 999_999).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ CR-02 마스킹 해제

    @Test
    @DisplayName("CR-02 해제 — 실값 · 만 나이 · 처리 이력 즉시 기록 · 두 번 눌러도 로그 1건 · 다른 운영자는 마스킹 · 지금 잠금")
    void unmaskSession() throws Exception {
        adminPost(base + "/privacy-unmask", Map.of("reason", "SETTLEMENT_WITHHOLDING_CHECK")).andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.unmasked").value(true))
                .andExpect(jsonPath("$.identity.name").value("김지민"))
                .andExpect(jsonPath("$.identity.birthday").value("1994.06.02"))
                .andExpect(jsonPath("$.identity.phone").value("010-3391-8842"))
                .andExpect(jsonPath("$.unmask.operatorName").value("김운영"))
                .andExpect(jsonPath("$.settlementAccount.accountNumber").value("333*****8842"));
        adminPost(base + "/privacy-unmask", Map.of("reason", "CS_RESPONSE")).andExpect(status().isOk());
        assertThat(privacyLogs.findByCreatorId(creator.getId())).hasSize(1);
        assertThat(events()).containsExactly(CreatorAdminEventType.PRIVACY_UNMASKED);

        adminGet(base).andExpect(jsonPath("$.identity.unmasked").value(true));
        mockMvc.perform(get(base).header(HttpHeaders.AUTHORIZATION, otherAdminToken))
                .andExpect(jsonPath("$.identity.unmasked").value(false))
                .andExpect(jsonPath("$.identity.name").value("김*민"));

        mockMvc.perform(post(base + "/privacy-lock").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        adminGet(base).andExpect(jsonPath("$.identity.unmasked").value(false))
                .andExpect(jsonPath("$.unmask.lastReason").value("SETTLEMENT_WITHHOLDING_CHECK"));

        // 10분 고정 만료 — 시각을 되돌리면 다시 마스킹이다.
        adminPost(base + "/privacy-unmask", Map.of("reason", "CS_RESPONSE")).andExpect(status().isOk());
        jdbc.update("UPDATE creator_privacy_access_log SET expires_at = ? WHERE locked_at IS NULL",
                LocalDateTime.now().minusSeconds(1));
        adminGet(base).andExpect(jsonPath("$.identity.unmasked").value(false));

        adminPost(base + "/privacy-unmask", Map.of("reason", "OTHER")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ CR-03 메모

    @Test
    @DisplayName("CR-03 메모 — 누적 · 300자 초과 400 · 다른 운영자 403 · 24시간 경과 403 · 삭제는 목록에서 빠진다")
    void memos() throws Exception {
        long memoId = json(adminPost(base + "/memos", Map.of("content", "벨라코스 고정 지급비 미지급 문의 — 계약서 근거 안내"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.authorName").value("김운영"))).get("memoId").asLong();
        adminPost(base + "/memos", Map.of("content", "두 번째 메모")).andExpect(status().isOk());
        adminPost(base + "/memos", Map.of("content", "가".repeat(301))).andExpect(status().isBadRequest());

        mockMvc.perform(get(base + "/memos").header(HttpHeaders.AUTHORIZATION, otherAdminToken))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[0].content").value("두 번째 메모"))
                .andExpect(jsonPath("$.content[0].editable").value(false));
        mockMvc.perform(patch(base + "/memos/" + memoId).header(HttpHeaders.AUTHORIZATION, otherAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("content", "남의 메모"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CREATOR_MEMO_NOT_AUTHOR"));

        mockMvc.perform(patch(base + "/memos/" + memoId).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("content", "고친 메모"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("고친 메모"));

        jdbc.update("UPDATE creator_admin_memo SET created_at = ? WHERE memo_id = ?",
                LocalDateTime.now().minusHours(25), memoId);
        mockMvc.perform(delete(base + "/memos/" + memoId).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CREATOR_MEMO_EDIT_WINDOW_CLOSED"));
        jdbc.update("UPDATE creator_admin_memo SET created_at = ? WHERE memo_id = ?", LocalDateTime.now(), memoId);
        mockMvc.perform(delete(base + "/memos/" + memoId).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        adminGet(base + "/memos").andExpect(jsonPath("$.total").value(1));
        assertThat(events()).containsExactly(CreatorAdminEventType.MEMO_DELETED, CreatorAdminEventType.MEMO_UPDATED,
                CreatorAdminEventType.MEMO_CREATED, CreatorAdminEventType.MEMO_CREATED);
    }

    // ------------------------------------------------------------------ CR-04 정지 · 해제

    @Test
    @DisplayName("CR-04 정지 — 기타 상세 없음 400 · 정지 · 사유 · 처리자 · 지급 유지 · 「계정 정지」 카드 · 중복 409 · 가드 · 해제 · 「정지 해제」 카드")
    void suspendAndRelease() throws Exception {
        adminPost(base + "/suspend", Map.of("reason", "OTHER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREATOR_SUSPEND_DETAIL_REQUIRED"));

        adminPost(base + "/suspend", Map.of("reason", "AD_DISCLOSURE_OMITTED_REPEATED",
                        "reasonDetail", "공구 게시물 3건에서 유료 광고 표기 누락"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.rail.currentStatus.suspension.reasonLabel").value("협찬 표기 누락 반복"))
                .andExpect(jsonPath("$.rail.currentStatus.suspension.operatorName").value("김운영"))
                .andExpect(jsonPath("$.rail.currentStatus.suspension.payoutHeld").value(false))
                .andExpect(jsonPath("$.rail.currentStatus.suspension.payoutNote").value(containsString("유지")))
                .andExpect(jsonPath("$.rail.operatorChannelThreadId").isNumber());
        adminPost(base + "/suspend", Map.of("reason", "GROUP_BUY_NONPERFORMANCE")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_ALREADY_SUSPENDED"));
        assertThat(cardTypes()).containsExactly(MessageCardType.ACCOUNT_SUSPENDED);
        // 소비자 계정 축은 건드리지 않는다.
        assertThat(userStatus()).isEqualTo(UserStatus.NORMAL);

        Creator suspended = creatorRepository.findById(creator.getId()).orElseThrow();
        assertThatThrownBy(() -> sanctionGuard.requireCanPublishPost(suspended))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.CREATOR_SUSPENDED);
        assertThatThrownBy(() -> sanctionGuard.requireCanReceiveContract(suspended))
                .extracting("errorCode").isEqualTo(ErrorCode.CREATOR_COUNTERPARTY_SUSPENDED);
        sanctionGuard.requireCanSubmitGroupBuyPost(suspended);   // 플래그 기본 꺼짐(§55 B-11)
        sanctionGuard.requireCanAcceptConnection(suspended);     // 정지는 연결 수락을 막지 않는다

        adminPost(base + "/release", Map.of("note", "시정 확인")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rail.currentStatus.suspension").value(nullValue()));
        adminPost(base + "/release", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_SUSPENDED"));
        assertThat(cardTypes()).containsExactly(MessageCardType.ACCOUNT_SUSPENDED, MessageCardType.ACCOUNT_SUSPENSION_RELEASED);
        assertThat(events()).containsExactly(CreatorAdminEventType.SUSPENSION_RELEASED, CreatorAdminEventType.SUSPENDED);
        adminGet(base + "/histories").andExpect(jsonPath("$.content[0].label").value("정지 해제"))
                .andExpect(jsonPath("$.content[1].detail").value("협찬 표기 누락 반복 — 공구 게시물 3건에서 유료 광고 표기 누락"))
                .andExpect(jsonPath("$.content[1].actorName").value("김운영"));
    }

    // ------------------------------------------------------------------ CR-05 지급 정지

    @Test
    @DisplayName("CR-05 정산 정보 불일치 — 인플루언서 예정 행만 보류 · 브랜드 행 그대로 · 해제하면 다음 영업일 지급 예정")
    void payoutHoldOnSettlementMismatch() throws Exception {
        registerCreatorResidentNumber();
        Settlement s = seedSettlement("가을 립밤 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        LocalDate due = LocalDate.now().plusDays(2);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?", due, s.getId());

        adminPost(base + "/suspend", Map.of("reason", "SETTLEMENT_INFO_MISMATCH")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rail.currentStatus.suspension.payoutHeld").value(true));
        SettlementPayout held = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(held.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(held.getFailCode()).isEqualTo("CREATOR_SUSPENDED");
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        adminGet("/v1/admin/settlements/" + s.getId()).andExpect(status().isOk());

        adminPost(base + "/release", Map.of()).andExpect(status().isOk());
        SettlementPayout released = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(released.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(released.getFailCode()).isNull();
        assertThat(released.getDueDate()).isEqualTo(businessCalendar.addBusinessDays(LocalDate.now(), 1));
        assertThat(settlementEvents(s.getId()))
                .contains(SettlementEventType.PAYOUT_BLOCKED, SettlementEventType.PAYOUT_HOLD_RELEASED);
    }

    @Test
    @DisplayName("CR-05b 정지 중 — 확정은 인플루언서 몫을 보류로 · 지급 지시 앞단은 예정에 올라온 행도 막는다 · 다른 보류 사유가 남으면 해제해도 보류")
    void payoutHoldAtConfirmAndClaim() throws Exception {
        registerSellerAccount();
        registerCreatorAccount();
        Settlement reviewing = seedSettlement("겨울 크림 공구", 1_000_000, SettlementStatus.REVIEWING);
        adminPost(base + "/suspend", Map.of("reason", "SETTLEMENT_INFO_MISMATCH")).andExpect(status().isOk());

        // 확정 — 주민등록번호도 없는 비사업자라 정지 · 주민번호 두 사유로 보류된다.
        Settlement confirmed = confirm(reviewing);
        assertThat(payout(confirmed.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(confirmed.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);

        // 지시 앞단 — 다른 경로로 예정에 올라온 인플루언서 행을 다시 막는다. 브랜드 몫은 나간다.
        LocalDate today = LocalDate.now();
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ? "
                + "AND payee IN ('CREATOR', 'BRAND')", today, confirmed.getId());
        pay(confirmed, today);
        assertThat(payout(confirmed.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(confirmed.getId(), SettlementPayee.CREATOR).getFailCode()).isEqualTo("CREATOR_SUSPENDED");
        assertThat(payout(confirmed.getId(), SettlementPayee.BRAND).getStatus())
                .isIn(PayoutStatus.REQUESTED, PayoutStatus.PAID);

        // 해제 — 주민등록번호 미등록이 남아 보류 유지 · 정지 사유만 지운다.
        adminPost(base + "/release", Map.of()).andExpect(status().isOk());
        SettlementPayout stillBlocked = payout(confirmed.getId(), SettlementPayee.CREATOR);
        assertThat(stillBlocked.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(stillBlocked.getFailCode()).isNull();
    }

    // ------------------------------------------------------------------ CR-06 탈퇴

    @Test
    @DisplayName("CR-06 탈퇴 — 진행중 공구가 있으면 신청 · 승인 409(롤백) · 종료 후 승인 · 파기 · 연결 종료 · 소비자 계정 유지 · 조치 없음")
    void withdrawalApproval() throws Exception {
        assertThatThrownBy(() -> withdrawalService.request(creator.getId(), "본업 집중", LocalDateTime.now()))
                .extracting("errorCode").isEqualTo(ErrorCode.CREATOR_WITHDRAWAL_CONDITION_UNMET);
        CreatorWithdrawalRequest request = inTransaction(() -> withdrawalRequests.save(
                CreatorWithdrawalRequest.request(creator.getId(), "콘텐츠 활동을 중단하고 본업에 집중하려 합니다.",
                        LocalDateTime.now().minusDays(4), LocalDate.now().plusDays(1))));
        String approve = base + "/withdrawal-requests/" + request.getId() + "/approve";

        adminGet(base).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rail.withdrawalReview.elapsedDays").value(4))
                .andExpect(jsonPath("$.rail.withdrawalReview.conditions.unfinishedGroupBuys").value(1));
        adminPost(approve, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_CONDITION_UNMET"))
                .andExpect(jsonPath("$.message").value(containsString("끝나지 않은 공구 1건")))
                // 브랜드와 같은 응답 모양 — 미충족 조건만 unmet 에(정합 계획서 R0-4).
                .andExpect(jsonPath("$.unmet.UNFINISHED_GROUP_BUYS").value(1))
                .andExpect(jsonPath("$.unmet.UNSETTLED").doesNotExist());
        assertThat(withdrawalRequests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberWithdrawalStatus.PENDING);

        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        adminPost(approve, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.identity.destroyed").value(true))
                .andExpect(jsonPath("$.identity.name").value(nullValue()))
                .andExpect(jsonPath("$.settlementAccount.destroyed").value(true))
                .andExpect(jsonPath("$.basic.showroomUrl").value(nullValue()))
                .andExpect(jsonPath("$.basic.showroomPublic").value(false))
                .andExpect(jsonPath("$.unmask.allowed").value(false))
                .andExpect(jsonPath("$.rail.currentStatus.withdrawal.operatorName").value("김운영"))
                .andExpect(jsonPath("$.rail.withdrawalReview").value(nullValue()));

        Creator withdrawn = creatorRepository.findById(creator.getId()).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(CreatorStatus.WITHDRAWN);
        assertThat(withdrawn.getRealName()).isNull();
        assertThat(withdrawn.getAccountNumber()).isNull();
        assertThat(withdrawn.getBusinessEmail()).isEqualTo("jimin-biz@showroomz.test");
        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getStatus())
                .isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(messageThreadRepository.findById(pairThreadId).orElseThrow().getStatus()).isEqualTo(ThreadStatus.DORMANT);
        assertThat(jdbc.queryForObject("SELECT role_type FROM users WHERE user_id = ?", String.class,
                creator.getUser().getId())).isEqualTo(RoleType.USER.name());
        assertThat(userStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(events()).containsExactly(CreatorAdminEventType.WITHDRAWAL_APPROVED, CreatorAdminEventType.CONNECTIONS_ENDED);

        adminPost(approve, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_PENDING"));
        adminPost(base + "/suspend", Map.of("reason", "GROUP_BUY_NONPERFORMANCE")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWN"));
        adminPost(base + "/memos", Map.of("content", "탈퇴 후 메모")).andExpect(status().isConflict());
        adminPost(base + "/privacy-unmask", Map.of("reason", "CS_RESPONSE")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_PRIVACY_UNMASK_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("CR-06b 반려 — 기타 안내 없음 400 · 반려해도 회원 상태는 그대로 · 대기 중에는 새 연결 수락 · 서명 요청 차단 · 반려 후 풀린다")
    void withdrawalRejection() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        CreatorWithdrawalRequest request = withdrawalService.request(creator.getId(), "본업 집중", LocalDateTime.now());
        Creator pending = creatorRepository.findById(creator.getId()).orElseThrow();
        assertThatThrownBy(() -> sanctionGuard.requireCanAcceptConnection(pending))
                .extracting("errorCode").isEqualTo(ErrorCode.CREATOR_WITHDRAWAL_PENDING);
        assertThatThrownBy(() -> sanctionGuard.requireCanReceiveContract(pending))
                .extracting("errorCode").isEqualTo(ErrorCode.CREATOR_COUNTERPARTY_WITHDRAWAL_PENDING);
        assertThatThrownBy(() -> withdrawalService.request(creator.getId(), "또 신청", LocalDateTime.now()))
                .extracting("errorCode").isEqualTo(ErrorCode.CREATOR_WITHDRAWAL_ALREADY_PENDING);

        String reject = base + "/withdrawal-requests/" + request.getId() + "/reject";
        adminPost(reject, Map.of("reason", "OTHER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_REJECT_DETAIL_REQUIRED"));
        adminPost(reject, Map.of("reason", "DISPUTE_IN_PROGRESS", "note", "분쟁 종결 후 다시 신청해 주세요"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rail.withdrawalReview").value(nullValue()));
        assertThat(withdrawalRequests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberWithdrawalStatus.REJECTED);
        sanctionGuard.requireCanAcceptConnection(creatorRepository.findById(creator.getId()).orElseThrow());
        assertThat(events()).containsExactly(CreatorAdminEventType.WITHDRAWAL_REJECTED,
                CreatorAdminEventType.WITHDRAWAL_REQUESTED);
    }

    // ------------------------------------------------------------------ CR-07 하단 카드

    @Test
    @DisplayName("CR-07 하단 — 운영 현황(연결 · 판매 중 공구 · 진행 중 리워드 · 조정 협의) · 최근 정산 · 계약(고정 지급비) · 연결 브랜드")
    void lowerCards() throws Exception {
        Settlement s = seedSettlement("가을 립밤 공구", 1_000_000, SettlementStatus.REVIEWING);
        long reward = settlement(s.getId()).getRewardAmount();

        adminGet(base + "/overview").andExpect(status().isOk())
                .andExpect(jsonPath("$.operations.connectedBrandCount").value(1))
                .andExpect(jsonPath("$.operations.sellingGroupBuyCount").value(1))
                .andExpect(jsonPath("$.operations.rewardInProgressAmount").value(reward))
                .andExpect(jsonPath("$.operations.openAdjustmentCount").value(0))
                .andExpect(jsonPath("$.activity.followerCount").value(0));
        adminGet(base + "/settlements").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].settlementNumber").value(s.getSettlementNumber()))
                .andExpect(jsonPath("$.content[0].status").value("REVIEWING"))
                .andExpect(jsonPath("$.content[0].marketName").value("글로우랩"))
                .andExpect(jsonPath("$.content[0].creatorPayoutStatus").value("WAITING"));
        adminGet(base + "/contracts").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].status").value("CONCLUDED"))
                .andExpect(jsonPath("$.content[0].fixedFeeAmount").value(300_000))
                .andExpect(jsonPath("$.content[0].fixedFeeTrigger").value("POST_REGISTERED"))
                .andExpect(jsonPath("$.content[0].fixedFeePaidAt").value(nullValue()));
        adminGet(base + "/connections").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].marketName").value("글로우랩"))
                .andExpect(jsonPath("$.content[0].sellingGroupBuyCount").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CONNECTED"));
        adminGet(CREATORS + "reason-options").andExpect(status().isOk())
                .andExpect(jsonPath("$.suspensionReasons.length()").value(6))
                .andExpect(jsonPath("$.releaseReasons.length()").value(2))
                .andExpect(jsonPath("$.privacyUnmaskReasons.length()").value(4));
    }

    // ------------------------------------------------------------------ CR-08 소비자 앱 탈퇴 우회 차단(정합 계획서 R0-2)

    @Test
    @DisplayName("CR-08 소비자 앱 탈퇴 — 활성 · 정지 인플루언서는 409 · 탈퇴 정보 creatorReviewRequired · 소비자 계정 그대로")
    void consumerWithdrawalBlockedForCreator() throws Exception {
        String username = creator.getUser().getUsername();
        WithdrawalRequest request = new ObjectMapper().readValue("{\"agreeConsent\":true}", WithdrawalRequest.class);

        WithdrawalInfoResponse info = userService.getWithdrawalInfo(username);
        assertThat(info.isCreatorReviewRequired()).isTrue();
        assertThat(info.isWithdrawable()).isFalse();
        assertThatThrownBy(() -> userService.withdrawUser(username, request))
                .extracting("errorCode").isEqualTo(ErrorCode.USER_WITHDRAWAL_REQUIRES_CREATOR_REVIEW);

        // 정지 중에도 막는다 — 정지를 피하려는 탈퇴 길을 두지 않는다(§51-3).
        adminPost(base + "/suspend", Map.of("reason", "CONSUMER_RESPONSE_NEGLECT")).andExpect(status().isOk());
        assertThatThrownBy(() -> userService.withdrawUser(username, request))
                .extracting("errorCode").isEqualTo(ErrorCode.USER_WITHDRAWAL_REQUIRES_CREATOR_REVIEW);
        assertThat(userStatus()).isEqualTo(UserStatus.NORMAL);
    }

    // ------------------------------------------------------------------ 보조

    private List<CreatorAdminEventType> events() {
        return adminHistories.findByCreatorIdOrderByCreatedAtDescIdDesc(creator.getId()).stream()
                .map(h -> h.getEventType()).toList();
    }

    private List<MessageCardType> cardTypes() {
        return jdbc.queryForList("SELECT m.card_type FROM message m JOIN message_thread t ON t.thread_id = m.thread_id "
                        + "JOIN connection c ON c.connection_id = t.connection_id WHERE c.creator_id = ? "
                        + "AND c.type = 'OPERATOR_CREATOR' AND m.card_type IS NOT NULL ORDER BY m.message_id",
                String.class, creator.getId()).stream().map(MessageCardType::valueOf).toList();
    }

    private UserStatus userStatus() {
        return UserStatus.valueOf(jdbc.queryForObject("SELECT status FROM users WHERE user_id = ?", String.class,
                creator.getUser().getId()));
    }
}
