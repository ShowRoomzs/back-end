package showroomz.api.admin.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 증빙(44 어드민 설계서 5절 · 7-2 EVIDENCE · 7-6 M4 · 7-7 M5 · 5-5) — ST-05 증빙 행 · ST-08 영수증 · ST-09 · 증빙 탭 · 원천세 자료.
 * 스튜디오 제출 · 파트너 다운로드까지 한 흐름으로 돈다(서피스별 응답은 각 서피스 테스트가 본다).
 */
@DisplayName("정산 증빙 — 세금계산서 행 · 승인번호 제출 · M4 대조 · M5 발행본 · 원천징수영수증 · 증빙 탭 · 원천세 자료")
class AdminSettlementEvidenceIntegrationTest extends SettlementTestSupport {

    private static final String SETTLEMENTS = "/v1/admin/settlements";
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 9, 30, 10, 0);
    private static final String APPROVAL = "20261007-41000012-38475920";
    private static final byte[] PDF = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);

    @org.springframework.beans.factory.annotation.Autowired
    private SettlementTaxDocumentRepository documentRepository;

    @Test
    @DisplayName("ST-05 확정 — 브랜드 세금계산서 발행 대기(기한 다음 달 10일) · 사업자면 인플루언서 세금계산서 입력 대기 + 인플루언서 행 BLOCKED")
    void confirmCreatesTaxDocuments() {
        makeCreatorBusiness();
        Settlement s = confirm(generatedSettlement());

        SettlementTaxDocument brand = document(s, TaxDocumentType.BRAND_TAX_INVOICE);
        assertThat(brand.getStatus()).isEqualTo(TaxDocumentStatus.PENDING_ISSUE);
        assertThat(brand.getSupplyAmount()).isEqualTo(s.getRewardAmount());
        assertThat(brand.getVatAmount()).isEqualTo(s.getRewardVatAmount());
        assertThat(brand.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        SettlementTaxDocument creatorInvoice = document(s, TaxDocumentType.CREATOR_TAX_INVOICE);
        assertThat(creatorInvoice.getStatus()).isEqualTo(TaxDocumentStatus.PENDING_INPUT);
        assertThat(creatorInvoice.getSupplyAmount()).isEqualTo(s.getRewardAfterClawback());
        assertThat(creatorInvoice.getVatAmount()).isEqualTo(s.getCreatorVatAmount());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.TAX_INVOICE_REQUESTED);
    }

    @Test
    @DisplayName("ST-09 승인번호 제출 → M4 반려 → 재제출(같은 행) → 확인 → 인플루언서 행 SCHEDULED(+3영업일) · M5 등록 뒤 버튼 사라짐 · 파트너 다운로드")
    void creatorInvoiceVerificationFlow() throws Exception {
        makeCreatorBusiness();
        registerSellerAccount();
        Settlement s = confirm(generatedSettlement());
        Long invoiceId = document(s, TaxDocumentType.CREATOR_TAX_INVOICE).getId();
        Long brandInvoiceId = document(s, TaxDocumentType.BRAND_TAX_INVOICE).getId();

        submit(s, APPROVAL, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.taxInvoice.cardStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.taxInvoice.attachmentName").value("invoice.pdf"));
        JsonNode submitted = json(adminGet(SETTLEMENTS + "/" + s.getId()));
        assertThat(submitted.at("/actions/canVerifyInvoice").asBoolean()).isTrue();
        assertThat(submitted.at("/actions/canRegisterBrandInvoice").asBoolean()).isTrue();
        assertThat(submitted.at("/rail/blockReasons/0/code").asText()).isEqualTo("TAX_INVOICE_UNVERIFIED");
        JsonNode invoiceRow = taxDocumentRow(submitted, "CREATOR_TAX_INVOICE");
        assertThat(invoiceRow.get("approvalNumber").asText()).isEqualTo("20261007-********-****5920");
        assertThat(invoiceRow.at("/actions/canVerify").asBoolean()).isTrue();
        adminGet(SETTLEMENTS + "/summary").andExpect(jsonPath("$.gnbBadge").value(2))
                .andExpect(jsonPath("$.tabCounts.EVIDENCE").value(2));

        adminPost(SETTLEMENTS + "/" + s.getId() + "/tax-documents/" + invoiceId + "/verify",
                Map.of("result", "AMOUNT_MISMATCH")).andExpect(status().isOk());
        creatorGet("/v1/creator/settlements/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.taxInvoice.cardStatus").value("REJECTED"))
                .andExpect(jsonPath("$.taxInvoice.rejectReasonLabel").value("금액 불일치"))
                .andExpect(jsonPath("$.timeline.payoutDueNote").value("재제출 확인 후 + 3영업일"));
        adminPost(SETTLEMENTS + "/" + s.getId() + "/tax-documents/" + invoiceId + "/verify",
                Map.of("result", "MATCH")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED"));

        submit(s, APPROVAL, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.taxInvoice.cardStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.taxInvoice.attachmentName").value("invoice.pdf"));
        assertThat(documentRepository.findBySettlementIdOrderByIdAsc(s.getId()))
                .filteredOn(d -> d.getType() == TaxDocumentType.CREATOR_TAX_INVOICE).singleElement()
                .extracting(SettlementTaxDocument::getId).isEqualTo(invoiceId);

        JsonNode verified = json(adminPost(SETTLEMENTS + "/" + s.getId() + "/tax-documents/" + invoiceId + "/verify",
                Map.of("result", "MATCH")).andExpect(status().isOk()));
        assertThat(verified.at("/actions/canVerifyInvoice").asBoolean()).isFalse();
        assertThat(verified.at("/rail/blockReasons")).isEmpty();
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getDueDate())
                .isEqualTo(businessCalendar.addBusinessDays(LocalDate.now(), 3));
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.TAX_INVOICE_SUBMITTED,
                SettlementEventType.TAX_INVOICE_REJECTED, SettlementEventType.TAX_INVOICE_VERIFIED);

        // M5 — 발행본 등록 전 파트너 다운로드 409 · 등록 뒤 스트림 · 버튼 사라짐 · 다시 등록 409.
        sellerGet("/v1/seller/settlements/" + s.getId() + "/tax-documents/" + brandInvoiceId + "/download")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_INVOICE_NOT_ISSUED"));
        JsonNode issued = json(issue(s, brandInvoiceId, "20261010-41000099-00001234").andExpect(status().isOk()));
        assertThat(issued.at("/actions/canRegisterBrandInvoice").asBoolean()).isFalse();
        assertThat(taxDocumentRow(issued, "BRAND_TAX_INVOICE").get("status").asText()).isEqualTo("ISSUED");
        byte[] downloaded = sellerGet("/v1/seller/settlements/" + s.getId() + "/tax-documents/" + brandInvoiceId
                + "/download").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);
        issue(s, brandInvoiceId, "20261010-41000099-00001234").andExpect(status().isConflict());
        adminGet(SETTLEMENTS + "/summary").andExpect(jsonPath("$.gnbBadge").value(0));
    }

    @Test
    @DisplayName("ST-08 영수증 — 비사업자 인플루언서 몫 지급 완료 → 원천징수영수증 GENERATED · 스튜디오 스트림 · 생성 전 409")
    void withholdingReceiptGenerated() throws Exception {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement s = confirm(generatedSettlement());
        creatorGet("/v1/creator/settlements/" + s.getId() + "/withholding-receipt").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_RECEIPT_NOT_READY"));
        doReturn(PDF).when(renderer).render(anyString(), anyString());

        Settlement paid = pay(s, payout(s.getId(), SettlementPayee.BRAND).getDueDate());

        SettlementTaxDocument receipt = document(paid, TaxDocumentType.WITHHOLDING_RECEIPT);
        assertThat(receipt.getStatus()).isEqualTo(TaxDocumentStatus.GENERATED);
        assertThat(receipt.getFileName()).isEqualTo("원천징수영수증_" + s.getSettlementNumber() + ".pdf");
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.WITHHOLDING_RECEIPT_GENERATED);
        creatorGet("/v1/creator/settlements/" + s.getId()).andExpect(jsonPath("$.withholding.receiptAvailable").value(true));
        byte[] pdf = creatorGet("/v1/creator/settlements/" + s.getId() + "/withholding-receipt")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(pdf).isEqualTo(PDF);
    }

    @Test
    @DisplayName("영수증 생성 실패는 지급을 막지 않는다 — PENDING_ISSUE 로 남고 재생성 배치가 만든다")
    void receiptFailureIsRetried() {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement s = pay(confirm(generatedSettlement()), LocalDate.of(2026, 10, 12));
        SettlementTaxDocument pending = document(s, TaxDocumentType.WITHHOLDING_RECEIPT);
        assertThat(pending.getStatus()).isEqualTo(TaxDocumentStatus.PENDING_ISSUE);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.PAID);

        doReturn(PDF).when(renderer).render(anyString(), anyString());
        new showroomz.global.scheduler.SettlementWithholdingReceiptRetryScheduler(documentRepository,
                withholdingReceiptGenerator).tick();

        assertThat(document(s, TaxDocumentType.WITHHOLDING_RECEIPT).getStatus()).isEqualTo(TaxDocumentStatus.GENERATED);
    }

    @Test
    @DisplayName("07a 증빙 탭 — 문서 행 · 주민번호 미등록 가상 행 · 툴바 · 처리 끝은 빠진다")
    void evidenceTab() throws Exception {
        Settlement s = confirm(generatedSettlement());   // 비사업자 · 주민번호 미등록

        JsonNode tab = json(adminGet(SETTLEMENTS + "?tab=EVIDENCE").andExpect(status().isOk()));
        assertThat(tab.at("/pageInfo/totalResults").asLong()).isEqualTo(2);
        List<String> types = tab.findValuesAsText("type");
        assertThat(types).containsExactlyInAnyOrder("BRAND_TAX_INVOICE", "RESIDENT_NUMBER_MISSING");
        assertThat(tab.at("/toolbar/count").asLong()).isEqualTo(2);
        assertThat(tab.at("/toolbar/operatorActionCount").asLong()).isEqualTo(1);
        assertThat(tab.at("/toolbar/payoutBlockedCount").asLong()).isEqualTo(1);
        JsonNode virtual = null;
        for (JsonNode row : tab.get("content")) {
            if ("RESIDENT_NUMBER_MISSING".equals(row.get("type").asText())) {
                virtual = row;
            }
        }
        assertThat(virtual).isNotNull();
        assertThat(virtual.get("documentId").isNull()).isTrue();
        assertThat(virtual.get("payoutImpact").asText()).isEqualTo("BLOCKING");
        assertThat(virtual.get("settlementId").asLong()).isEqualTo(s.getId());

        registerCreatorResidentNumber();
        issue(s, document(s, TaxDocumentType.BRAND_TAX_INVOICE).getId(), "20261010-41000099-00001234")
                .andExpect(status().isOk());
        assertThat(json(adminGet(SETTLEMENTS + "?tab=EVIDENCE")).at("/pageInfo/totalResults").asLong()).isZero();
    }

    @Test
    @DisplayName("원천세 신고 자료 — 그 달 지급된 비사업자 · 주민등록번호 복호화 · 반출 기록 1행")
    void withholdingReport() throws Exception {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement s = pay(confirm(generatedSettlement()), LocalDate.of(2026, 10, 12));

        byte[] xlsx = adminGet(SETTLEMENTS + "/withholding-report?month=2026-10").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        List<List<String>> rows = readSheet(xlsx);
        assertThat(rows.get(0)).contains("주민등록번호", "소득세", "지방소득세");
        assertThat(rows.get(1)).contains(s.getSettlementNumber(), "김지민", RESIDENT_NUMBER);
        assertThat(jdbc.queryForObject("SELECT row_count FROM settlement_withholding_report_log "
                + "WHERE report_month = '2026-10'", Integer.class)).isEqualTo(1);

        // 지급이 없는 달 — 헤더 + 합계(0) 행만.
        List<List<String>> empty = readSheet(adminGet(SETTLEMENTS + "/withholding-report?month=2026-09")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(empty).hasSize(2);
        assertThat(empty.get(1).get(0)).isEqualTo("합계");
        adminGet(SETTLEMENTS + "/withholding-report").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("EV-02 합의로 리워드가 바뀐 정산 — 확인 기간 · 협의 중에는 증빙 행 없음 · 합의 확정 때 생긴 브랜드 세금계산서 공급가 = 합의 후 리워드(수정세금계산서 없음)")
    void brandInvoiceUsesAgreedReward() throws Exception {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        long agreed = s.getRewardAmount() + 1_000;
        assertThat(documentRepository.findBySettlementIdOrderByIdAsc(s.getId())).isEmpty();

        JsonNode requested = json(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/creator/settlements/" + s.getId() + "/adjustment")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("rewardAmount", agreed, "reason", "추가 콘텐츠 2건 제작"))))
                .andExpect(status().isCreated()));
        assertThat(documentRepository.findBySettlementIdOrderByIdAsc(s.getId())).isEmpty();
        sellerPost("/v1/seller/settlement-adjustments/" + requested.get("adjustmentId").asLong() + "/proposals/"
                + requested.get("proposalId").asLong() + "/accept", Map.of()).andExpect(status().isOk());

        Settlement confirmed = settlement(s.getId());
        assertThat(confirmed.getRewardAmount()).isEqualTo(agreed);
        assertThat(documentRepository.findBySettlementIdOrderByIdAsc(s.getId())).singleElement().satisfies(d -> {
            assertThat(d.getType()).isEqualTo(TaxDocumentType.BRAND_TAX_INVOICE);
            assertThat(d.getStatus()).isEqualTo(TaxDocumentStatus.PENDING_ISSUE);
            assertThat(d.getSupplyAmount()).isEqualTo(agreed).isNotEqualTo(confirmed.getOriginalRewardAmount());
            assertThat(d.getVatAmount()).isEqualTo(confirmed.getRewardVatAmount()).isEqualTo(agreed / 10);
            assertThat(d.getDueDate()).isEqualTo(taxDocumentService.brandInvoiceDueDate(
                    confirmed.getConfirmedAt().toLocalDate()));
        });
    }

    @Test
    @DisplayName("EV-03 브랜드 세금계산서 기한 — 확정일이 속한 달의 다음 달 N일 · 01.31 → 02.10 · 12월 → 다음 해 1월 · N 이 달 길이보다 크면 말일")
    void brandInvoiceDueDateRollsMonth() {
        assertThat(taxDocumentService.brandInvoiceDueDate(LocalDate.of(2027, 1, 31))).isEqualTo(LocalDate.of(2027, 2, 10));
        assertThat(taxDocumentService.brandInvoiceDueDate(LocalDate.of(2026, 10, 6))).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(taxDocumentService.brandInvoiceDueDate(LocalDate.of(2026, 12, 1))).isEqualTo(LocalDate.of(2027, 1, 10));

        int original = settlementProperties.getBrandInvoiceDueDay();
        try {
            settlementProperties.setBrandInvoiceDueDay(31);
            assertThat(taxDocumentService.brandInvoiceDueDate(LocalDate.of(2027, 1, 31)))
                    .isEqualTo(LocalDate.of(2027, 2, 28));
            assertThat(taxDocumentService.brandInvoiceDueDate(LocalDate.of(2028, 1, 15)))
                    .isEqualTo(LocalDate.of(2028, 2, 29));
            assertThat(taxDocumentService.brandInvoiceDueDate(LocalDate.of(2026, 9, 30)))
                    .isEqualTo(LocalDate.of(2026, 10, 31));
        } finally {
            settlementProperties.setBrandInvoiceDueDay(original);
        }
    }

    // ------------------------------------------------------------------ 보조

    @org.springframework.beans.factory.annotation.Autowired
    private showroomz.domain.settlement.service.WithholdingReceiptGenerator withholdingReceiptGenerator;
    @org.springframework.beans.factory.annotation.Autowired
    private showroomz.domain.settlement.service.SettlementTaxDocumentService taxDocumentService;
    @org.springframework.beans.factory.annotation.Autowired
    private showroomz.global.config.properties.SettlementProperties settlementProperties;

    private Settlement generatedSettlement() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(GENERATED_AT);
    }

    private SettlementTaxDocument document(Settlement s, TaxDocumentType type) {
        return documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(s.getId(), type).orElseThrow();
    }

    private ResultActions submit(Settlement s, String approvalNumber, boolean withAttachment) throws Exception {
        var request = multipart("/v1/creator/settlements/" + s.getId() + "/tax-invoice");
        request.param("approvalNumber", approvalNumber);
        if (withAttachment) {
            request.file(new MockMultipartFile("attachment", "invoice.pdf", "application/pdf", PDF));
        }
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private ResultActions issue(Settlement s, Long documentId, String approvalNumber) throws Exception {
        return mockMvc.perform(multipart(SETTLEMENTS + "/" + s.getId() + "/tax-documents/" + documentId + "/issue")
                .file(new MockMultipartFile("file", "brand-invoice.pdf", "application/pdf", PDF))
                .param("approvalNumber", approvalNumber)
                .param("issuedDate", "2026-10-10")
                .header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    private static JsonNode taxDocumentRow(JsonNode detail, String type) {
        for (JsonNode row : detail.get("taxDocuments")) {
            if (type.equals(row.get("type").asText())) {
                return row;
            }
        }
        throw new AssertionError("증빙 행 없음: " + type);
    }
}
