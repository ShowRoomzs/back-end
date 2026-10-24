package showroomz.api.design1021;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.creator.type.CreatorAccountVerificationStatus;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 8절 — 인플루언서 계좌 변경 요청의 P8 기본 흐름 밖: 사업자 구분 변경과의 상호 배타 · 승인 뒤 PG 실존 확인 실패 · 반려 알림 문장 ·
 * 처리 뒤 계좌번호 마스킹 · 변경 요청 대기 수 · 계좌 이력 주체.
 */
@DisplayName("1021 8절 계좌 변경 상세 — 사업자 구분 변경과 배타 · 승인 뒤 확인 실패 · 알림 · 처리 뒤 마스킹 · 대기 수 · 이력")
class Design1021AccountChangeDetailIntegrationTest extends SettlementTestSupport {

    private static final String BASE = "/v1/creator/basic-info";
    private static final String CHANGE_REQUESTS = "/v1/admin/change-requests";
    private static final String NEW_ACCOUNT = "1101234567";

    @org.springframework.beans.factory.annotation.Autowired private NotificationRepository notificationRepository;

    @BeforeEach
    void setUpBanks() {
        registerCreatorAccount();
        registerCreatorResidentNumber();
        registerSellerAccount();
        if (!bankRepository.existsById("088")) {
            fixture.createBank("088", "신한은행");
        }
    }

    @Test
    @DisplayName("열린 요청은 인플루언서당 1건 — 사업자 구분 변경이 대기면 계좌 변경 409 · 취소하면 접수 · 그 뒤 사업자 구분 변경 409 · "
            + "버튼은 숨기지 않고 비활성 사유(55 S3)")
    void exclusiveWithBusinessTypeChange() throws Exception {
        // 요청 전 — 두 버튼 모두 활성 · 사유 없음.
        creatorGet(BASE + "/settlement")
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_BUSINESS"))
                .andExpect(jsonPath("$.actions.businessTypeChangeBlockedReason").doesNotExist())
                .andExpect(jsonPath("$.actions.accountChange").value(true))
                .andExpect(jsonPath("$.actions.accountChangeBlockedReason").doesNotExist());

        long businessRequestId = json(businessTypeChange().andExpect(status().isCreated())).get("requestId").asLong();
        // 사업자 구분 변경 대기 — 같은 자리 [취소] · [계좌 변경] 비활성 + 사유.
        creatorGet(BASE + "/settlement")
                .andExpect(jsonPath("$.actions.businessTypeChange").value("CANCEL"))
                .andExpect(jsonPath("$.actions.businessTypeChangeBlockedReason").doesNotExist())
                .andExpect(jsonPath("$.actions.accountChange").value(false))
                .andExpect(jsonPath("$.actions.accountChangeBlockedReason").value("사업자 구분 확인이 끝난 뒤 요청할 수 있어요"));

        requestChange(NEW_ACCOUNT).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ALREADY_PENDING"));

        mockMvc.perform(post(BASE + "/business-type-change-requests/" + businessRequestId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().isNoContent());
        // 계좌 변경 대기 — 요청 응답부터 [사업자 여부 변경 요청]은 값 그대로 + 비활성 사유 · [계좌 변경]은 활성.
        requestChange(NEW_ACCOUNT).andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_BUSINESS"))
                .andExpect(jsonPath("$.actions.businessTypeChangeBlockedReason").value("정산 계좌 확인이 끝난 뒤 요청할 수 있어요"))
                .andExpect(jsonPath("$.actions.accountChange").value(true))
                .andExpect(jsonPath("$.actions.accountChangeBlockedReason").doesNotExist());
        businessTypeChange().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ALREADY_PENDING"));
        // 취소된 사업자 구분 변경은 배너가 없다 · 계좌 변경 요청 블록만 보인다.
        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.businessTypeChange").doesNotExist())
                .andExpect(jsonPath("$.pendingAccountChange.bankName").value("신한은행"))
                .andExpect(jsonPath("$.actions.businessTypeChangeBlockedReason").value("정산 계좌 확인이 끝난 뒤 요청할 수 있어요"));

        // 계좌 변경 취소 — 사유가 사라진다.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(BASE + "/settlement-account/change-request").header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAccountChange").doesNotExist())
                .andExpect(jsonPath("$.actions.businessTypeChangeBlockedReason").doesNotExist())
                .andExpect(jsonPath("$.actions.accountChange").value(true));
    }

    @Test
    @DisplayName("계좌 변경 요청 id 를 사업자 구분 변경 취소 · 확인 API 로 보내면 404 — 요청은 확인 중 · 지급 보류 그대로")
    void businessTypeCancelRejectsAccountRequest() throws Exception {
        Settlement s = seedSettlement("예정 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), s.getId());
        requestChange(NEW_ACCOUNT).andExpect(status().isOk());
        long accountRequestId = latestRequestId();

        mockMvc.perform(post(BASE + "/business-type-change-requests/" + accountRequestId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_FOUND"));
        mockMvc.perform(post(BASE + "/business-type-change-requests/" + accountRequestId + "/acknowledge")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_FOUND"));

        assertThat(jdbc.queryForObject("SELECT status FROM brand_change_request WHERE request_id = ?", String.class,
                accountRequestId)).isEqualTo("PENDING");
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.pendingAccountChange.bankName").value("신한은행"));
    }

    @Test
    @DisplayName("반대 방향 — 사업자 구분 변경만 열려 있으면 계좌 변경 취소는 400 · 사업자 구분 변경 요청은 그대로")
    void accountCancelLeavesBusinessTypeRequest() throws Exception {
        long businessRequestId = json(businessTypeChange().andExpect(status().isCreated())).get("requestId").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(BASE + "/settlement-account/change-request").header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));

        assertThat(jdbc.queryForObject("SELECT status FROM brand_change_request WHERE request_id = ?", String.class,
                businessRequestId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("승인 뒤 PG 실존 확인이 실패하면 「확인 실패」 · 보류된 지급은 계속 막힘 · 승인 알림은 간다 · 계좌 이력 주체는 인플루언서")
    void approvedButVerificationFails() throws Exception {
        Settlement s = seedSettlement("예정 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), s.getId());
        requestChange(NEW_ACCOUNT).andExpect(status().isOk());
        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name(), "예금주 불일치");

        adminPost(CHANGE_REQUESTS + "/" + latestRequestId() + "/approve", Map.of()).andExpect(status().isOk());

        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.account.bankName").value("신한은행"))
                .andExpect(jsonPath("$.account.verification.status").value("FAILED"))
                .andExpect(jsonPath("$.account.verification.label").value("확인 실패"));
        assertThat(creatorRepository.findById(creator.getId()).orElseThrow().getAccountVerificationStatus())
                .isEqualTo(CreatorAccountVerificationStatus.FAILED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(notices(NotificationType.ACCOUNT_CHANGE_APPROVED)).singleElement()
                .satisfies(n -> assertThat(n.getRecipientType()).isEqualTo(NotificationRecipientType.CREATOR));
        assertThat(jdbc.queryForObject("SELECT actor_type FROM creator_admin_history WHERE creator_id = ? "
                + "AND event_type = 'SETTLEMENT_ACCOUNT_CHANGED' ORDER BY history_id DESC LIMIT 1", String.class,
                creator.getId())).isEqualTo("CREATOR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_settlement_account_history WHERE creator_id = ?",
                Long.class, creator.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("반려 — 사유 문장이 알림에 그대로 · 처리 뒤 상세는 계좌번호를 가린다(암호문은 비워짐) · 대기 수에서 빠진다")
    void rejectNotifiesAndMasks() throws Exception {
        requestChange(NEW_ACCOUNT).andExpect(status().isOk());
        long requestId = latestRequestId();
        adminGet(CHANGE_REQUESTS + "/summary").andExpect(jsonPath("$.pendingCount").value(1));

        Map<String, Object> reject = new HashMap<>();
        reject.put("reasonType", "BANKBOOK_UNREADABLE");
        adminPost(CHANGE_REQUESTS + "/" + requestId + "/reject", reject).andExpect(status().isOk());

        assertThat(notices(NotificationType.ACCOUNT_CHANGE_REJECTED)).singleElement().satisfies(n -> {
            assertThat(n.getRecipientType()).isEqualTo(NotificationRecipientType.CREATOR);
            assertThat(n.getTitle()).contains("통장 사본 판독 불가");
        });
        JsonNode detail = json(adminGet(CHANGE_REQUESTS + "/" + requestId + "?status=ALL").andExpect(status().isOk()));
        String requested = detail.at("/diff/1/requestedValue").asText();
        assertThat(requested).isNotEqualTo(NEW_ACCOUNT).contains("*");
        assertThat(jdbc.queryForObject("SELECT account_number_enc FROM brand_change_request WHERE request_id = ?",
                String.class, requestId)).isNull();
        adminGet(CHANGE_REQUESTS + "/summary").andExpect(jsonPath("$.pendingCount").value(0));
        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.account.bankName").value("국민은행"));
    }

    @Test
    @DisplayName("사업자 구분 변경 사유는 계좌 변경을 반려할 수 없다(400) · 계좌용 사유 목록에는 사업자등록증 사유가 없다")
    void rejectReasonMustFitType() throws Exception {
        requestChange(NEW_ACCOUNT).andExpect(status().isOk());

        adminPost(CHANGE_REQUESTS + "/" + latestRequestId() + "/reject", Map.of("reasonType", "LICENSE_UNREADABLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH"));
        JsonNode reasons = json(adminGet(CHANGE_REQUESTS + "/reject-reasons?type=CREATOR_ACCOUNT"));
        List<String> codes = reasons.findValuesAsText("code");
        assertThat(codes).contains("BANKBOOK_MISSING", "HOLDER_NAME_MISMATCH", "ACCOUNT_NUMBER_INVALID",
                "BANKBOOK_UNREADABLE", "OTHER").doesNotContain("LICENSE_UNREADABLE", "EVIDENCE_MISSING");
    }

    @Test
    @DisplayName("통장 파일명 없이 요청해도 접수 · 승인 뒤 표시 파일명은 비어 있다")
    void bankbookFileNameOptional() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("bankCode", "088");
        body.put("accountNumber", NEW_ACCOUNT);
        body.put("bankbookImageUrl", "https://cdn.showroomz.test/creator-document/bankbook.jpg");
        mockMvc.perform(put(BASE + "/settlement-account").header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk());

        adminPost(CHANGE_REQUESTS + "/" + latestRequestId() + "/approve", Map.of()).andExpect(status().isOk());
        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.account.bankName").value("신한은행"))
                .andExpect(jsonPath("$.account.bankbook.fileName").doesNotExist());
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions requestChange(String accountNumber) throws Exception {
        return mockMvc.perform(put(BASE + "/settlement-account").header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("bankCode", "088",
                        "accountNumber", accountNumber,
                        "bankbookImageUrl", "https://cdn.showroomz.test/creator-document/bankbook.jpg",
                        "bankbookFileName", "통장사본_신한은행.jpg"))));
    }

    private ResultActions businessTypeChange() throws Exception {
        return mockMvc.perform(post(BASE + "/business-type-change-requests").header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("targetType", "BUSINESS",
                        "businessRegistrationNumber", "123-45-67890",
                        "evidenceFileUrl", "https://cdn.showroomz.test/creator-document/license.pdf",
                        "evidenceFileName", "사업자등록증.pdf", "evidenceFileSize", 1_048_576))));
    }

    private long latestRequestId() {
        return jdbc.queryForObject("SELECT MAX(request_id) FROM brand_change_request WHERE creator_id = ? "
                + "AND type = 'CREATOR_ACCOUNT'", Long.class, creator.getId());
    }

    private List<Notification> notices(NotificationType type) {
        return notificationRepository.findAll().stream().filter(n -> n.getType() == type).toList();
    }
}
