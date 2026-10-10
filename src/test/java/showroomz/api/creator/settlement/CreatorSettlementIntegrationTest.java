package showroomz.api.creator.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쇼룸 스튜디오 정산 관리(12) 조회 — 44 스튜디오 설계서 7-2 C-01 · C-02(세금계산서 항목 제외) · C-03 · C-09 · C-08(지급 부분).
 * 산식 · 상태 전이는 정산 도메인 테스트가 소유하고 여기서는 <b>스튜디오 응답</b>만 본다.
 */
@DisplayName("쇼룸 스튜디오 정산 관리(12) — 목록 · 요약 · 상세 · 명세")
class CreatorSettlementIntegrationTest extends SettlementTestSupport {

    private static final String SETTLEMENTS = "/v1/creator/settlements";

    private Settlement reviewing() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(LocalDateTime.now().withNano(0));
    }

    private String otherCreatorToken() {
        Creator other = createCreator("다른_쇼룸", "other-creator");
        return bearerToken(other.getUser().getUsername(), RoleType.CREATOR, other.getUser().getId());
    }

    @Test
    @DisplayName("C-01 목록 — 남의 정산 404 · PAYOUT_FAILED → PAID · 「상품별」 리워드율 · 사업자 내 행 보류면 payoutDate = null")
    void list() throws Exception {
        makeCreatorBusiness();
        registerSellerAccount();
        Settlement mine = confirm(reviewing());
        pay(mine, mine.getPayoutDueDate());   // 브랜드 먼저 지급 · 내 행은 보류
        Settlement failed = seedSettlement("두 번째 앵콜 공구", 10_000, SettlementStatus.PAYOUT_FAILED);

        JsonNode page = json(creatorGet(SETTLEMENTS).andExpect(status().isOk()));
        assertThat(page.at("/content")).hasSize(2);
        JsonNode seeded = page.at("/content/1");
        assertThat(seeded.at("/settlementId").asLong()).isEqualTo(failed.getId());
        assertThat(seeded.at("/status").asText()).isEqualTo("PAID");
        JsonNode real = page.at("/content/0");
        assertThat(real.at("/rewardRateLabel").asText()).isEqualTo("상품별");
        assertThat(real.at("/brandName").asText()).isEqualTo("글로우랩");
        assertThat(real.at("/payoutDate").isNull()).isTrue();
        assertThat(real.at("/creatorVatAmount").asLong()).isEqualTo(mine.getCreatorVatAmount());

        creatorGet(SETTLEMENTS + "?keyword=글로우랩").andExpect(jsonPath("$.content.length()").value(2));
        creatorGet(SETTLEMENTS + "?status=PAYOUT_FAILED").andExpect(status().isBadRequest());
        String other = otherCreatorToken();
        mockMvc.perform(get(SETTLEMENTS + "/" + mine.getId()).header("Authorization", other))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("C-02 요약 — 빈 상태 amount = null · attentionCount = 요청 가능 창이 열린 정산 확인 중 · 조정 협의는 세지 않음")
    void summaryKpi() throws Exception {
        Settlement s = reviewing();

        creatorGet(SETTLEMENTS + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaid.amount").doesNotExist())
                .andExpect(jsonPath("$.totalPaid.count").value(0))
                .andExpect(jsonPath("$.payoutScheduled.amount").doesNotExist())
                .andExpect(jsonPath("$.reviewing.count").value(1))
                .andExpect(jsonPath("$.reviewing.nearestDueAt").exists())
                .andExpect(jsonPath("$.attentionCount").value(1));

        jdbc.update("UPDATE settlement SET status = 'ADJUSTING' WHERE settlement_id = ?", s.getId());
        creatorGet(SETTLEMENTS + "/summary")
                .andExpect(jsonPath("$.attentionCount").value(0))
                .andExpect(jsonPath("$.adjusting.count").value(1))
                .andExpect(jsonPath("$.statusCounts.ADJUSTING").value(1));
    }

    @Test
    @DisplayName("C-03 상세 REVIEWING — 요청 가능 · 상한 · 분배 행 「확인 기간 후 지급」 · 세금계산서 null · 지급 예정일 null · 마감 뒤 요청 불가")
    void reviewingDetail() throws Exception {
        Settlement s = reviewing();
        long upper = settlementCalculator.maxRewardAmount(s);

        JsonNode detail = json(creatorGet(SETTLEMENTS + "/" + s.getId()).andExpect(status().isOk()));
        assertThat(detail.at("/settlement/status").asText()).isEqualTo("REVIEWING");
        assertThat(detail.at("/review/canRequestAdjustment").asBoolean()).isTrue();
        assertThat(detail.at("/review/maxRewardAmount").asLong()).isEqualTo(upper);
        assertThat(upper % 10).isZero();
        assertThat(detail.at("/timeline/payoutDueDate").isNull()).isTrue();
        assertThat(detail.at("/payouts/shownAfterConfirm").asBoolean()).isTrue();
        assertThat(detail.at("/payouts/rows/0/payee").asText()).isEqualTo("CREATOR");
        assertThat(detail.at("/payouts/rows/0/statusLabel").asText()).isEqualTo("확인 기간 후 지급");
        assertThat(detail.at("/taxInvoice").isNull()).isTrue();
        assertThat(detail.at("/withholding/receiptLabel").asText()).isEqualTo("징수 예정액");
        assertThat(detail.at("/breakdown/rewardRateLabel").asText()).isEqualTo("상품별");
        assertThat(detail.at("/breakdown/rewardRates")).hasSize(2);
        assertThat(detail.at("/breakdown/rewardAfterClawback").asLong()).isEqualTo(s.getRewardAmount());
        assertThat(detail.at("/breakdown/pgFee").isMissingNode()).isTrue();
        assertThat(detail.at("/items/downloadAvailable").asBoolean()).isFalse();
        assertThat(detail.at("/items/preview/0/consumerNameMasked").isMissingNode()).isTrue();

        jdbc.update("UPDATE settlement SET review_due_at = ? WHERE settlement_id = ?",
                LocalDateTime.now().minusMinutes(1), s.getId());
        creatorGet(SETTLEMENTS + "/" + s.getId()).andExpect(jsonPath("$.review.canRequestAdjustment").value(false));
    }

    @Test
    @DisplayName("C-08(지급) 인플루언서 몫만 FAILED — 정산 라벨 PAID · 내 행 「지급 확인 중」 · payoutDate = null · 브랜드 행 지급 완료")
    void creatorPayoutFailed() throws Exception {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement s = confirm(reviewing());
        payoutGateway.failFor(SettlementPayee.CREATOR, "BANK_REJECTED", "예금주 불일치");
        pay(s, s.getPayoutDueDate());

        creatorGet(SETTLEMENTS).andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[0].payoutDate").doesNotExist());
        JsonNode detail = json(creatorGet(SETTLEMENTS + "/" + s.getId()));
        assertThat(detail.at("/settlement/status").asText()).isEqualTo("PAID");
        assertThat(detail.at("/payouts/rows/0/status").asText()).isEqualTo("FAILED");
        assertThat(detail.at("/payouts/rows/0/statusLabel").asText()).isEqualTo("지급 확인 중");
        assertThat(detail.at("/payouts/rows/1/status").asText()).isEqualTo("PAID");
        assertThat(detail.at("/payouts/rows/1/statusLabel").asText()).endsWith("지급 완료");
        assertThat(detail.at("/payment/accountNumberMasked").asText()).isEqualTo("******789012");
        assertThat(detail.at("/payment/accountHolder").asText()).isEqualTo("김지민");
        assertThat(detail.at("/timeline/payoutDueNote").asText()).startsWith("자동 확정 ");
    }

    @Test
    @DisplayName("C-09 명세 — 페이지 · 확정 전 다운로드 409 · xlsx 에 소비자 열 없음")
    void items() throws Exception {
        Settlement s = reviewing();

        creatorGet(SETTLEMENTS + "/" + s.getId() + "/items").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].consumerNameMasked").doesNotExist())
                .andExpect(jsonPath("$.content[0].subOrderNumber").doesNotExist());
        creatorGet(SETTLEMENTS + "/" + s.getId() + "/items/download").andExpect(status().isConflict());

        confirm(s);
        byte[] xlsx = creatorGet(SETTLEMENTS + "/" + s.getId() + "/items/download").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        List<List<String>> rows = readSheet(xlsx);
        assertThat(rows.get(0)).contains("주문번호", "상품", "리워드").doesNotContain("주문자", "하위주문번호", "정산번호");
        assertThat(rows.size()).isGreaterThanOrEqualTo(2 + 1 + 3);
    }

    @Test
    @DisplayName("C-04 상세 ADJUSTING — adjustment.turn · 내 요청 금액 · review = null · 수취자 행 「보류 중」")
    void adjustingDetail() throws Exception {
        Settlement s = reviewing();
        mockMvc.perform(post(SETTLEMENTS + "/" + s.getId() + "/adjustment")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("rewardAmount", s.getRewardAmount() + 5_000, "reason", "추가 콘텐츠"))))
                .andExpect(status().isCreated());

        JsonNode detail = json(creatorGet(SETTLEMENTS + "/" + s.getId()).andExpect(status().isOk()));
        assertThat(detail.get("settlement").get("status").asText()).isEqualTo("ADJUSTING");
        assertThat(detail.get("review").isNull()).isTrue();
        JsonNode adjustment = detail.get("adjustment");
        assertThat(adjustment.get("turn").asText()).isEqualTo("THEIR_TURN");
        assertThat(adjustment.get("requesterType").asText()).isEqualTo("CREATOR");
        assertThat(adjustment.get("myLatestAmount").asLong()).isEqualTo(s.getRewardAmount() + 5_000);
        assertThat(adjustment.get("proposals").get(0).get("mine").asBoolean()).isTrue();
        assertThat(adjustment.get("remainingBusinessDays").asInt()).isPositive();
        assertThat(detail.get("payouts").get("shownAfterConfirm").asBoolean()).isTrue();
        detail.get("payouts").get("rows").forEach(row ->
                assertThat(row.get("statusLabel").asText()).isEqualTo("보류 중"));
    }

    // ------------------------------------------------------------------ 증빙(단계 7)

    private static final byte[] PDF = "%PDF-1.4 invoice".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private org.springframework.test.web.servlet.ResultActions submitInvoice(Settlement s, String number,
                                                                            org.springframework.mock.web.MockMultipartFile file)
            throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(SETTLEMENTS + "/" + s.getId() + "/tax-invoice");
        request.param("approvalNumber", number);
        if (file != null) {
            request.file(file);
        }
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    @Test
    @DisplayName("C-06 사업자 확정 직후 — 세금계산서 카드 PENDING_INPUT · 공급받는자 = 설정 · 내 행 BLOCKED 「발행 필요」 · 문장 「발행 확인 후 + 3영업일」 · 요약 attentionCount 가산")
    void businessCardAfterConfirm() throws Exception {
        makeCreatorBusiness();
        Settlement s = confirm(reviewing());

        JsonNode detail = json(creatorGet(SETTLEMENTS + "/" + s.getId()).andExpect(status().isOk()));
        assertThat(detail.at("/taxInvoice/cardStatus").asText()).isEqualTo("PENDING_INPUT");
        assertThat(detail.at("/taxInvoice/supplier/name").asText()).isEqualTo("SHOWROOMZ TEST");
        assertThat(detail.at("/taxInvoice/supplyAmount").asLong()).isEqualTo(s.getRewardAfterClawback());
        assertThat(detail.at("/taxInvoice/vatAmount").asLong()).isEqualTo(s.getCreatorVatAmount());
        assertThat(detail.at("/timeline/payoutDueDate").isNull()).isTrue();
        assertThat(detail.at("/timeline/payoutDueNote").asText()).isEqualTo("발행 확인 후 + 3영업일");
        assertThat(detail.at("/payouts/rows/0/statusLabel").asText()).isEqualTo("발행 필요");
        assertThat(detail.at("/withholding").isNull()).isTrue();
        creatorGet(SETTLEMENTS + "/summary").andExpect(jsonPath("$.attentionCount").value(1));
    }

    @Test
    @DisplayName("C-07 제출 검증 — 비사업자 409 · 확정 전 409 · 형식 400 · 다른 정산에서 확인된 번호 400 · PDF 아님 400 · 정상 → 「확인 중」")
    void submitValidation() throws Exception {
        Settlement individual = confirm(seedSettlement("가을 립밤 공구", 1_000_000, SettlementStatus.REVIEWING));
        submitInvoice(individual, "20261007-41000012-38475920", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_INVOICE_NOT_REQUIRED"));

        makeCreatorBusiness();
        Settlement s = reviewing();
        submitInvoice(s, "20261007-41000012-38475920", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_INVOICE_NOT_OPEN"));
        confirm(s);
        submitInvoice(s, "2026-1007-41000012", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_INVOICE_NUMBER_INVALID"));
        submitInvoice(s, "", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_INVOICE_NUMBER_INVALID"));
        jdbc.update("INSERT INTO settlement_tax_document (settlement_id, type, status, supply_amount, vat_amount, "
                        + "total_amount, approval_number, created_at) VALUES (?, 'CREATOR_TAX_INVOICE', 'VERIFIED', 1, 0, 1, ?, ?)",
                individual.getId(), "20260901-41000012-00000001", LocalDateTime.now());
        submitInvoice(s, "20260901-41000012-00000001", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_INVOICE_NUMBER_INVALID"));
        submitInvoice(s, "20261007-41000012-38475920",
                new org.springframework.mock.web.MockMultipartFile("attachment", "invoice.png", "image/png",
                        new byte[]{1, 2, 3})).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        submitInvoice(otherSettlementOf(s), "20261007-41000012-38475920", null).andExpect(status().isNotFound());

        submitInvoice(s, " 20261007-41000012-38475920 ",
                new org.springframework.mock.web.MockMultipartFile("attachment", "invoice.pdf", "application/pdf", PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value(s.getId()))
                .andExpect(jsonPath("$.taxInvoice.cardStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.taxInvoice.approvalNumber").value("20261007-41000012-38475920"));
        creatorGet(SETTLEMENTS + "/" + s.getId()).andExpect(jsonPath("$.payouts.rows[0].statusLabel").value("확인 중"))
                .andExpect(jsonPath("$.timeline.payoutDueNote").value("확인 후 + 3영업일"));
        byte[] attachment = creatorGet(SETTLEMENTS + "/" + s.getId() + "/tax-invoice/attachment")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(attachment).isEqualTo(PDF);
        submitInvoice(s, "20261007-41000012-38475920", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_TAX_INVOICE_NOT_OPEN"));
    }

    /** 다른 크리에이터의 정산 — 내 토큰으로 제출하면 404. */
    private Settlement otherSettlementOf(Settlement mine) {
        Creator other = createCreator("다른_쇼룸2", "other-creator2");
        jdbc.update("UPDATE settlement SET creator_id = ? WHERE settlement_id = ?", other.getId(),
                seedSettlement("다른 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED).getId());
        return settlementRepository.findAll().stream()
                .filter(x -> other.getId().equals(x.getCreatorId())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("C-10 연간 지급 내역 — 비사업자 원천징수 열 · 데이터 없는 해 404")
    void annualStatement() throws Exception {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement s = pay(confirm(reviewing()), java.time.LocalDate.now().plusDays(30));
        assertThat(s.getStatus()).isEqualTo(SettlementStatus.PAID);
        int year = payout(s.getId(), SettlementPayee.CREATOR).getPaidAt().getYear();

        byte[] xlsx = creatorGet(SETTLEMENTS + "/annual-statement?year=" + year).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        List<List<String>> rows = readSheet(xlsx);
        assertThat(rows.get(0)).contains("소득세", "지방소득세").doesNotContain("부가세");
        assertThat(rows.get(1)).contains(s.getSettlementNumber());
        assertThat(rows.get(rows.size() - 1).get(0)).isEqualTo("합계");
        creatorGet(SETTLEMENTS + "/annual-statement?year=" + (year - 1)).andExpect(status().isNotFound());
    }
}
