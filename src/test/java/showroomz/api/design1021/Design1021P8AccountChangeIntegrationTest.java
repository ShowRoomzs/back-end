package showroomz.api.design1021;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 P8 — 인플루언서 정산 계좌 변경 → 변경 요청(기-22 · 8절).
 */
@DisplayName("1021 P8 — 인플루언서 계좌 변경 요청 · 확인 중 지급 보류 · 반려 · 취소 · 어드민 목록 필터 · 대조표")
class Design1021P8AccountChangeIntegrationTest extends SettlementTestSupport {

    private static final String BASE = "/v1/creator/basic-info";
    private static final String NEW_ACCOUNT = "1101234567";

    @org.springframework.beans.factory.annotation.Autowired
    private SettlementTaxDocumentRepository documentRepository;

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
    @DisplayName("요청 — 기존 계좌 유지 · 확인 중 블록 · 지급 요청 전 예정은 보류 · 이미 나간 지급은 그대로 · 중복 409 · 반려하면 보류 해제 · 계좌 그대로")
    void requestHoldsThenRejectReleases() throws Exception {
        Settlement scheduled = seedSettlement("예정 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), scheduled.getId());
        Settlement inFlight = seedSettlement("지급 요청 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'REQUESTED' WHERE settlement_id = ? AND payee = 'CREATOR'",
                inFlight.getId());

        requestChange().andExpect(status().isOk())
                .andExpect(jsonPath("$.account.bankName").value("국민은행"))
                .andExpect(jsonPath("$.pendingAccountChange.bankName").value("신한은행"))
                .andExpect(jsonPath("$.pendingAccountChange.maskedAccountNumber").isNotEmpty())
                .andExpect(jsonPath("$.pendingAccountChange.holdingPayoutCount").value(1));
        requestChange().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ALREADY_PENDING"));
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getFailCode()).isEqualTo("CREATOR_ACCOUNT_UNVERIFIED");
        assertThat(payout(inFlight.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);

        long requestId = latestRequestId();
        JsonNode list = json(adminGet("/v1/admin/change-requests?status=PENDING&requesterType=CREATOR&type=CREATOR_ACCOUNT")
                .andExpect(status().isOk()));
        assertThat(list.at("/content/0/requestId").asLong()).isEqualTo(requestId);
        assertThat(list.at("/content/0/typeLabel").asText()).isEqualTo("계좌 변경 (인플루언서)");
        assertThat(json(adminGet("/v1/admin/change-requests?status=PENDING&requesterType=BRAND"))
                .at("/content").size()).isZero();

        adminGet("/v1/admin/change-requests/" + requestId).andExpect(status().isOk())
                .andExpect(jsonPath("$.diff[1].fieldKey").value("ACCOUNT_NUMBER"))
                .andExpect(jsonPath("$.diff[1].requestedValue").value(NEW_ACCOUNT))
                .andExpect(jsonPath("$.evidence.documentLabel").value("통장 사본"))
                .andExpect(jsonPath("$.holderCheck.rule").value("REAL_NAME"));
        JsonNode reasons = json(adminGet("/v1/admin/change-requests/reject-reasons?type=CREATOR_ACCOUNT"));
        assertThat(reasons.findValuesAsText("label")).contains("예금주가 본인 실명과 일치하지 않음", "통장 사본 미첨부");

        adminPost("/v1/admin/change-requests/" + requestId + "/reject",
                Map.of("reasonType", "HOLDER_NAME_MISMATCH")).andExpect(status().isOk());
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.account.bankName").value("국민은행"))
                .andExpect(jsonPath("$.pendingAccountChange").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT account_number_enc FROM brand_change_request WHERE request_id = ?",
                String.class, requestId)).isNull();
    }

    @Test
    @DisplayName("요청 중 — 지급 배치가 보류를 다시 판정해도 풀리지 않는다(확인 전 기존 계좌 지급 금지 · 8-0 ②) · 취소하면 풀린다")
    void batchKeepsHoldWhilePending() throws Exception {
        Settlement scheduled = seedSettlement("예정 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), scheduled.getId());
        requestChange().andExpect(status().isOk());

        payoutService.releaseBlocked(LocalDate.now());

        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getFailCode()).isEqualTo("CREATOR_ACCOUNT_UNVERIFIED");

        cancel().andExpect(status().isOk());
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("[55 S1-05] 사업자 — 요청 중에 운영자가 세금계산서를 대조(M4)해도 보류는 풀리지 않는다 · 요청을 거두면 그때 풀린다")
    void taxInvoiceVerificationKeepsHoldWhilePending() throws Exception {
        makeCreatorBusiness();
        Settlement s = confirm(generatedSettlement());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        requestChange().andExpect(status().isOk());
        Long invoiceId = documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(s.getId(),
                TaxDocumentType.CREATOR_TAX_INVOICE).orElseThrow().getId();

        submitTaxInvoice(s).andExpect(status().isOk());
        adminPost("/v1/admin/settlements/" + s.getId() + "/tax-documents/" + invoiceId + "/verify",
                Map.of("result", "MATCH")).andExpect(status().isOk());

        // 세금계산서 사유는 풀렸지만 계좌 확인 중 사유가 남는다 — 확인 전 기존 계좌로 지급하지 않는다(1021 8-0 ②).
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        payoutService.releaseBlocked(LocalDate.now());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        cancel().andExpect(status().isOk());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("[55 S1-06] 요청 중에 새로 확정되는 정산 — 인플루언서 행이 처음부터 보류 · 어드민 상세 보류 사유는 「정산 계좌 확인 중」 · 브랜드 행은 예정")
    void settlementConfirmedWhilePendingIsHeld() throws Exception {
        requestChange().andExpect(status().isOk());

        Settlement s = confirm(seedSettlement("요청 중 확정", 1_000_000, SettlementStatus.REVIEWING));

        // 확정 때의 정책 보류는 사유 코드 없이 저장된다(includeUncoded) — 화면의 사유는 판정식이 계산해 내린다.
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(json(adminGet("/v1/admin/settlements/" + s.getId())).at("/rail/blockReasons/0/code").asText())
                .isEqualTo("CREATOR_ACCOUNT_UNVERIFIED");
        payoutService.releaseBlocked(LocalDate.now());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        cancel().andExpect(status().isOk());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("[55 S1-07] 사업자 구분 변경 요청만 열려 있으면 계좌 확인 중이 아니다 — 지급 배치가 남은 계좌 보류를 푼다")
    void businessTypeChangeDoesNotHoldPayout() throws Exception {
        Settlement s = seedSettlement("구분 변경 중", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'BLOCKED', fail_code = 'CREATOR_ACCOUNT_UNVERIFIED', due_date = ? "
                + "WHERE settlement_id = ? AND payee = 'CREATOR'", LocalDate.now().plusDays(2), s.getId());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(BASE + "/business-type-change-requests").header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("targetType", "BUSINESS",
                                "businessRegistrationNumber", "123-45-67890",
                                "evidenceFileUrl", "https://cdn.showroomz.test/creator-document/license.pdf",
                                "evidenceFileName", "사업자등록증.pdf", "evidenceFileSize", 1_048_576))))
                .andExpect(status().isCreated());

        payoutService.releaseBlocked(LocalDate.now());

        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("취소 — 확인 중 요청을 거두면 보류가 풀리고 계좌 그대로 · 열린 요청이 없으면 400(CHANGE_REQUEST_NOT_PENDING)")
    void cancelRequest() throws Exception {
        Settlement scheduled = seedSettlement("예정 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), scheduled.getId());
        requestChange().andExpect(status().isOk());

        cancel().andExpect(status().isOk()).andExpect(jsonPath("$.pendingAccountChange").doesNotExist());
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        cancel().andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));
    }

    @Test
    @DisplayName("승인 — 새 계좌 반영 · PG 실존 확인 통과로 확인 완료 · 보류 해제 · 사업자 구분 변경 배너에는 섞이지 않는다")
    void approveApplies() throws Exception {
        requestChange().andExpect(status().isOk());
        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.businessTypeChange").doesNotExist());

        adminPost("/v1/admin/change-requests/" + latestRequestId() + "/approve", Map.of()).andExpect(status().isOk());

        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.account.bankName").value("신한은행"))
                .andExpect(jsonPath("$.account.verification.status").value("VERIFIED"))
                .andExpect(jsonPath("$.pendingAccountChange").doesNotExist())
                .andExpect(jsonPath("$.businessTypeChange").doesNotExist());
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions requestChange() throws Exception {
        return mockMvc.perform(put(BASE + "/settlement-account").header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("bankCode", "088",
                        "accountNumber", NEW_ACCOUNT,
                        "bankbookImageUrl", "https://cdn.showroomz.test/creator-document/bankbook.jpg",
                        "bankbookFileName", "통장사본_신한은행.jpg"))));
    }

    private ResultActions cancel() throws Exception {
        return mockMvc.perform(delete(BASE + "/settlement-account/change-request")
                .header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private long latestRequestId() {
        return jdbc.queryForObject("SELECT MAX(request_id) FROM brand_change_request WHERE creator_id = ? "
                + "AND type = 'CREATOR_ACCOUNT'", Long.class, creator.getId());
    }

    /** 실제 주문으로 만든 정산(생성까지) — 확정은 테스트가 한다. */
    private Settlement generatedSettlement() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(java.time.LocalDateTime.of(2026, 9, 30, 10, 0));
    }

    /** 스튜디오 세금계산서 승인번호 제출(첨부 포함). */
    private ResultActions submitTaxInvoice(Settlement s) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/v1/creator/settlements/" + s.getId() + "/tax-invoice")
                .file(new org.springframework.mock.web.MockMultipartFile("attachment", "invoice.pdf", "application/pdf",
                        "%PDF-1.4 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .param("approvalNumber", "20261007-41000012-38475920")
                .header(HttpHeaders.AUTHORIZATION, creatorToken));
    }
}
