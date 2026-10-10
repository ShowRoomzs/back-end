package showroomz.api.admin.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 정산 관리 보완(07a · 07b · M3 · M4 · M5) — 정상 흐름은 {@link AdminSettlementIntegrationTest} ·
 * {@link AdminSettlementEvidenceIntegrationTest}가 본다. 여기서는 <b>거절되는 요청이 아무것도 바꾸지 않는지</b>를 본다.
 *
 * <ul>
 *   <li>AG-01 역할 경계 — 운영자 외 403 · 토큰 없음 401 · 주민등록번호가 담긴 원천세 자료는 반출 기록도 남지 않는다</li>
 *   <li>AG-02 M3 재분배 — 남의 행 · 없는 정산 404 · 분배 실패가 아닌 정산 409 · 본문 400 · 회차 · 이력 · 지시 불변</li>
 *   <li>AG-03 M4 대조 — 제출 전 · 브랜드 문서 409 · 다른 정산 경로 404 · 본문 400 · 보류 그대로</li>
 *   <li>AG-04 M5 발행본 — 인플루언서 문서 409 · 승인번호 · 파일 · 발행일 · PDF 아님 400 · 다른 정산 경로 404 · 저장 0건</li>
 * </ul>
 */
@DisplayName("어드민 정산 관리 보완 — 역할 경계 · M3 재분배 · M4 대조 · M5 발행본 등록의 거절 분기")
class AdminSettlementGuardIntegrationTest extends SettlementTestSupport {

    private static final String SETTLEMENTS = "/v1/admin/settlements";
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 9, 30, 10, 0);
    private static final String CREATOR_APPROVAL = "20261007-41000012-38475920";
    private static final String BRAND_APPROVAL = "20261010-41000099-00001234";
    private static final byte[] PDF = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);

    @Autowired private SettlementTaxDocumentRepository documentRepository;

    // ------------------------------------------------------------------ AG-01 역할 경계

    @Test
    @DisplayName("AG-01 운영자만 — 브랜드 · 인플루언서 토큰은 목록 · 요약 · 상세 · 명세 · 원천세 자료 · 재분배 · 대조 403 · 토큰 없으면 401 · 반출 기록 없음")
    void operatorsOnly() throws Exception {
        Settlement s = seedSettlement("가을 립밤 공구", 1_000_000, SettlementStatus.REVIEWING);
        Long payoutId = payout(s.getId(), SettlementPayee.CREATOR).getId();
        String base = SETTLEMENTS + "/" + s.getId();

        for (String token : List.of(brandToken, creatorToken)) {
            mockMvc.perform(get(SETTLEMENTS).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(SETTLEMENTS + "/summary").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(base).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(base + "/items").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(SETTLEMENTS + "/withholding-report").param("month", "2026-10")
                    .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/payouts/" + payoutId + "/redistribute")
                    .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("accountSource", "PREVIOUS")))).andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/tax-documents/1/verify")
                    .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("result", "MATCH")))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(SETTLEMENTS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(SETTLEMENTS + "/withholding-report").param("month", "2026-10"))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_withholding_report_log", Long.class)).isZero();
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_RETRIED);
    }

    // ------------------------------------------------------------------ AG-02 M3 재분배

    @Test
    @DisplayName("AG-02 M3 거절 — 다른 정산의 행 · 없는 정산 404 · 확인 중 정산 409 STATE_CHANGED · accountSource 없음 · 모르는 값 400 · 회차 · 이력 · PG 지시 불변")
    void redistributeRejections() throws Exception {
        Settlement failed = failedOnCreator();
        Long creatorPayoutId = payout(failed.getId(), SettlementPayee.CREATOR).getId();
        Settlement reviewing = seedSettlement("다른 공구", 1_000_000, SettlementStatus.REVIEWING);
        Long otherPayoutId = payout(reviewing.getId(), SettlementPayee.CREATOR).getId();
        int gatewayCalls = payoutGateway.calls().size();

        // 경로의 정산과 행이 맞지 않으면 존재를 알리지 않는다.
        redistribute(failed.getId(), otherPayoutId, "PREVIOUS").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        redistribute(reviewing.getId(), creatorPayoutId, "PREVIOUS").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        redistribute(999_999L, creatorPayoutId, "PREVIOUS").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        // 분배 실패가 아닌 정산(확인 중)의 행.
        redistribute(reviewing.getId(), otherPayoutId, "CURRENT_PROFILE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_STATE_CHANGED"));
        // 운영자는 계좌를 입력하지 않는다 — 출처만 고른다.
        String url = SETTLEMENTS + "/" + failed.getId() + "/payouts/" + creatorPayoutId + "/redistribute";
        adminPost(url, Map.of()).andExpect(status().isBadRequest());
        adminPost(url, Map.of("accountSource", "TYPED_BY_OPERATOR")).andExpect(status().isBadRequest());

        assertThat(payout(failed.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payout(failed.getId(), SettlementPayee.CREATOR).getAttempt()).isZero();
        assertThat(settlement(failed.getId()).getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);
        assertThat(settlement(reviewing.getId()).getStatus()).isEqualTo(SettlementStatus.REVIEWING);
        assertThat(payout(reviewing.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.WAITING);
        assertThat(settlementEvents(failed.getId())).doesNotContain(SettlementEventType.PAYOUT_RETRIED);
        assertThat(payoutGateway.calls()).hasSize(gatewayCalls);
        adminGet(SETTLEMENTS + "/" + failed.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.canRedistribute").value(true))
                .andExpect(jsonPath("$.rail.failedPayout.attempt").value(0));
    }

    // ------------------------------------------------------------------ AG-03 M4 대조

    @Test
    @DisplayName("AG-03 M4 거절 — 제출 전 · 브랜드 세금계산서 409 · 다른 정산 경로 404 · result 없음 · 모르는 값 400 · 인플루언서 행 BLOCKED 그대로")
    void verifyRejections() throws Exception {
        makeCreatorBusiness();
        registerSellerAccount();
        Settlement s = confirm(generatedSettlement());
        Settlement other = seedSettlement("다른 공구", 1_000_000, SettlementStatus.REVIEWING);
        Long creatorInvoiceId = document(s, TaxDocumentType.CREATOR_TAX_INVOICE).getId();
        Long brandInvoiceId = document(s, TaxDocumentType.BRAND_TAX_INVOICE).getId();

        // 제출 전(입력 대기) — 대조할 승인번호가 없다.
        verify(s.getId(), creatorInvoiceId, "MATCH").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED"));
        // 브랜드 세금계산서는 SHOWROOMZ 가 발행한다 — 대조 대상이 아니다.
        verify(s.getId(), brandInvoiceId, "MATCH").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED"));

        submitCreatorInvoice(s).andExpect(status().isOk());
        verify(other.getId(), creatorInvoiceId, "MATCH").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        verify(999_999L, creatorInvoiceId, "MATCH").andExpect(status().isNotFound());
        String url = SETTLEMENTS + "/" + s.getId() + "/tax-documents/" + creatorInvoiceId + "/verify";
        adminPost(url, Map.of()).andExpect(status().isBadRequest());
        adminPost(url, Map.of("result", "LOOKS_FINE")).andExpect(status().isBadRequest());

        assertThat(document(s, TaxDocumentType.CREATOR_TAX_INVOICE).getStatus()).isEqualTo(TaxDocumentStatus.SUBMITTED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getDueDate()).isNull();
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.TAX_INVOICE_VERIFIED,
                SettlementEventType.TAX_INVOICE_REJECTED);
        adminGet(SETTLEMENTS + "/" + s.getId()).andExpect(jsonPath("$.actions.canVerifyInvoice").value(true));

        // 거절이 상태를 건드리지 않았으므로 바른 경로의 대조는 그대로 통한다.
        verify(s.getId(), creatorInvoiceId, "MATCH").andExpect(status().isOk());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    // ------------------------------------------------------------------ AG-04 M5 발행본

    @Test
    @DisplayName("AG-04 M5 거절 — 인플루언서 세금계산서 409 · 승인번호 형식 · 파일 없음 · 발행일 없음 · PDF 아님 400 · 다른 정산 경로 404 · 파일 저장 0건 · 발행 대기 그대로")
    void issueRejections() throws Exception {
        makeCreatorBusiness();
        Settlement s = confirm(generatedSettlement());
        Settlement other = seedSettlement("다른 공구", 1_000_000, SettlementStatus.REVIEWING);
        Long brandInvoiceId = document(s, TaxDocumentType.BRAND_TAX_INVOICE).getId();
        Long creatorInvoiceId = document(s, TaxDocumentType.CREATOR_TAX_INVOICE).getId();
        int storedBefore = taxDocumentStorage.size();
        MockMultipartFile pdf = new MockMultipartFile("file", "brand-invoice.pdf", "application/pdf", PDF);

        issue(s.getId(), creatorInvoiceId, pdf, BRAND_APPROVAL, "2026-10-10").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED"));
        issue(s.getId(), brandInvoiceId, pdf, "2026-1010-41000099", "2026-10-10").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_INVOICE_NUMBER_INVALID"));
        issue(s.getId(), brandInvoiceId, pdf, null, "2026-10-10").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_INVOICE_NUMBER_INVALID"));
        issue(s.getId(), brandInvoiceId, null, BRAND_APPROVAL, "2026-10-10").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        issue(s.getId(), brandInvoiceId, pdf, BRAND_APPROVAL, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        issue(s.getId(), brandInvoiceId, new MockMultipartFile("file", "brand-invoice.pdf", "application/pdf",
                "<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8)), BRAND_APPROVAL, "2026-10-10")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        issue(other.getId(), brandInvoiceId, pdf, BRAND_APPROVAL, "2026-10-10").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));

        SettlementTaxDocument brand = document(s, TaxDocumentType.BRAND_TAX_INVOICE);
        assertThat(brand.getStatus()).isEqualTo(TaxDocumentStatus.PENDING_ISSUE);
        assertThat(brand.getApprovalNumber()).isNull();
        assertThat(brand.hasFile()).isFalse();
        assertThat(taxDocumentStorage.size()).isEqualTo(storedBefore);
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.BRAND_INVOICE_ISSUED);
        adminGet(SETTLEMENTS + "/" + s.getId()).andExpect(jsonPath("$.actions.canRegisterBrandInvoice").value(true));
        sellerGet("/v1/seller/settlements/" + s.getId() + "/tax-documents/" + brandInvoiceId + "/download")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_INVOICE_NOT_ISSUED"));

        issue(s.getId(), brandInvoiceId, pdf, BRAND_APPROVAL, "2026-10-10").andExpect(status().isOk());
        assertThat(taxDocumentStorage.size()).isEqualTo(storedBefore + 1);
    }

    // ------------------------------------------------------------------ 보조

    private Settlement generatedSettlement() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(GENERATED_AT);
    }

    /** 3자 분배에서 인플루언서 행만 PG 가 거절 — 정산 PAYOUT_FAILED · 브랜드 PAID. */
    private Settlement failedOnCreator() {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement s = confirm(generatedSettlement());
        payoutGateway.failFor(SettlementPayee.CREATOR, "BANK_REJECTED", "예금주 불일치");
        Settlement failed = pay(s, payout(s.getId(), SettlementPayee.BRAND).getDueDate());
        assertThat(failed.getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);
        return failed;
    }

    private SettlementTaxDocument document(Settlement s, TaxDocumentType type) {
        return documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(s.getId(), type).orElseThrow();
    }

    private ResultActions redistribute(Long settlementId, Long payoutId, String source) throws Exception {
        return adminPost(SETTLEMENTS + "/" + settlementId + "/payouts/" + payoutId + "/redistribute",
                Map.of("accountSource", source));
    }

    private ResultActions verify(Long settlementId, Long documentId, String result) throws Exception {
        return adminPost(SETTLEMENTS + "/" + settlementId + "/tax-documents/" + documentId + "/verify",
                Map.of("result", result));
    }

    private ResultActions submitCreatorInvoice(Settlement s) throws Exception {
        return mockMvc.perform(multipart("/v1/creator/settlements/" + s.getId() + "/tax-invoice")
                .param("approvalNumber", CREATOR_APPROVAL)
                .header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private ResultActions issue(Long settlementId, Long documentId, MockMultipartFile file, String approvalNumber,
                                String issuedDate) throws Exception {
        MockMultipartHttpServletRequestBuilder request =
                multipart(SETTLEMENTS + "/" + settlementId + "/tax-documents/" + documentId + "/issue");
        if (file != null) {
            request.file(file);
        }
        if (approvalNumber != null) {
            request.param("approvalNumber", approvalNumber);
        }
        if (issuedDate != null) {
            request.param("issuedDate", issuedDate);
        }
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, adminToken));
    }
}
