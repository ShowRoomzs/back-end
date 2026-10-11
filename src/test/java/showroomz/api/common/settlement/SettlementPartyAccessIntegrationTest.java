package showroomz.api.common.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터(13) · 쇼룸 스튜디오(12) 정산 보완 — 남의 정산 404 규칙(1-1 「존재를 알리지 않는다」)이 상세뿐 아니라 하위 경로 ·
 * 파일 · 조정 협의까지 걸리는지, 역할이 다른 토큰이 막히는지를 본다. 서피스별 정상 응답은 각 서피스 테스트가 본다.
 *
 * <ul>
 *   <li>PA-01 다른 브랜드 — 명세 · 명세 다운로드 · 세금계산서 다운로드 404</li>
 *   <li>PA-02 다른 인플루언서 — 명세 · 명세 다운로드 · 원천징수영수증 · 세금계산서 첨부 404(상태 판정보다 먼저)</li>
 *   <li>PA-03 조정 협의 제3자 — 미리보기 · 요청 404 · 동의 · 반대 · 다른 금액 제안 403 · 고정 카드 403 · 협의 불변</li>
 *   <li>PA-04 역할 경계 — 브랜드 토큰 → 스튜디오 403 · 인플루언서 토큰 → 파트너센터 판매자 없음 404 · 토큰 없음 401</li>
 *   <li>PA-05 세금계산서 첨부(4-4) — 첨부 없이 제출하면 404 · 반려 뒤 첨부해 재제출하면 PDF 스트림</li>
 * </ul>
 */
@DisplayName("파트너센터 · 쇼룸 스튜디오 정산 보완 — 남의 정산 · 조정 협의 제3자 · 역할 경계 · 세금계산서 첨부")
class SettlementPartyAccessIntegrationTest extends SettlementTestSupport {

    private static final String SELLER = "/v1/seller";
    private static final String CREATOR = "/v1/creator";
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 9, 30, 10, 0);
    private static final String APPROVAL = "20261007-41000012-38475920";
    private static final byte[] PDF = "%PDF-1.4 invoice".getBytes(StandardCharsets.US_ASCII);

    // ------------------------------------------------------------------ PA-01 · PA-02 남의 정산

    @Test
    @DisplayName("PA-01 다른 브랜드 — 상세 · 명세 · 명세 다운로드 · 브랜드 세금계산서 다운로드 모두 404 SETTLEMENT_NOT_FOUND · 본인은 명세 다운로드 200")
    void otherBrandCannotReachAnySubPath() throws Exception {
        Settlement s = confirm(generated());
        Long brandInvoiceId = documentId(s, "BRAND_TAX_INVOICE");
        String base = SELLER + "/settlements/" + s.getId();
        sellerGet(base + "/items/download").andExpect(status().isOk());

        String other = otherBrandToken();
        for (String path : List.of("", "/items", "/items/download",
                "/tax-documents/" + brandInvoiceId + "/download")) {
            sellerGet(base + path, other).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("PA-02 다른 인플루언서 — 상세 · 명세 · 명세 다운로드 · 원천징수영수증 · 세금계산서 첨부 404 · 본인의 영수증은 409(준비 전)라 소유 판정이 먼저다")
    void otherCreatorCannotReachAnySubPath() throws Exception {
        Settlement s = confirm(generated());
        String base = CREATOR + "/settlements/" + s.getId();
        creatorGet(base + "/items/download").andExpect(status().isOk());
        creatorGet(base + "/withholding-receipt").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_RECEIPT_NOT_READY"));

        String other = otherCreatorToken();
        for (String path : List.of("", "/items", "/items/download", "/withholding-receipt",
                "/tax-invoice/attachment")) {
            mockMvc.perform(get(base + path).header(HttpHeaders.AUTHORIZATION, other))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        }
    }

    // ------------------------------------------------------------------ PA-03 조정 협의 제3자

    @Test
    @DisplayName("PA-03 조정 협의 제3자 — 남의 정산 미리보기 · 요청 404(행 없음 · 보류 없음) · 열린 협의의 동의 · 반대 · 다른 금액 제안 403 · 고정 카드 403 · 당사자 동의는 그대로 통한다")
    void thirdPartiesCannotTouchAdjustment() throws Exception {
        Settlement s = seedSettlement("여름 수분 세럼 공구", 1_000_000, SettlementStatus.REVIEWING);
        String otherCreator = otherCreatorToken();
        String otherBrand = otherBrandToken();
        Map<String, Object> body = Map.of("rewardAmount", 130_000, "reason", "추가 콘텐츠 2건 제작");

        for (String[] surface : List.of(new String[]{CREATOR, otherCreator}, new String[]{SELLER, otherBrand})) {
            String settlementPath = surface[0] + "/settlements/" + s.getId();
            mockMvc.perform(get(settlementPath + "/adjustment/preview").param("rewardAmount", "130000")
                            .header(HttpHeaders.AUTHORIZATION, surface[1]))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
            postJson(settlementPath + "/adjustment", body, surface[1])
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        }
        assertThat(count("settlement_adjustment")).isZero();
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.REVIEWING);
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getStatus).containsOnly(PayoutStatus.WAITING);

        JsonNode requested = json(postJson(CREATOR + "/settlements/" + s.getId() + "/adjustment", body, creatorToken)
                .andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        long proposalId = requested.get("proposalId").asLong();
        long threadId = requested.get("threadId").asLong();

        for (String[] surface : List.of(new String[]{CREATOR, otherCreator}, new String[]{SELLER, otherBrand})) {
            String adjustmentPath = surface[0] + "/settlement-adjustments/" + adjustmentId;
            postJson(adjustmentPath + "/proposals/" + proposalId + "/accept", Map.of(), surface[1])
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_ACCESS_DENIED"));
            postJson(adjustmentPath + "/proposals/" + proposalId + "/reject", Map.of(), surface[1])
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_ACCESS_DENIED"));
            postJson(adjustmentPath + "/proposals", Map.of("rewardAmount", 120_000, "reason", "중간 금액 제안"),
                    surface[1]).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_ACCESS_DENIED"));
            mockMvc.perform(get(surface[0] + "/threads/" + threadId + "/adjustment")
                            .header(HttpHeaders.AUTHORIZATION, surface[1]))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
        }
        assertThat(jdbc.queryForObject("SELECT status FROM settlement_adjustment WHERE adjustment_id = ?",
                String.class, adjustmentId)).isEqualTo("OPEN");
        assertThat(jdbc.queryForList("SELECT status FROM settlement_adjustment_proposal WHERE adjustment_id = ?",
                String.class, adjustmentId)).containsExactly("PENDING");

        sellerPost(SELLER + "/settlement-adjustments/" + adjustmentId + "/proposals/" + proposalId + "/accept",
                Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AGREED"));
        assertThat(settlement(s.getId()).getRewardAmount()).isEqualTo(130_000);
    }

    // ------------------------------------------------------------------ PA-04 역할 경계

    @Test
    @DisplayName("PA-04 역할 경계 — 브랜드 토큰은 스튜디오 정산 403 · 인플루언서 토큰은 파트너센터 정산에서 판매자를 찾지 못해 404 · 토큰 없으면 401 · 조정 행 없음")
    void roleBoundaries() throws Exception {
        Settlement s = seedSettlement("가을 립밤 공구", 1_000_000, SettlementStatus.REVIEWING);
        Map<String, Object> body = Map.of("rewardAmount", 130_000, "reason", "추가 콘텐츠 2건 제작");

        mockMvc.perform(get(CREATOR + "/settlements").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CREATOR + "/settlements/" + s.getId()).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
        postJson(CREATOR + "/settlements/" + s.getId() + "/adjustment", body, brandToken)
                .andExpect(status().isForbidden());

        // /v1/seller/** 는 인플루언서 역할도 통과시킨다 — 정산은 판매자 계정으로 마켓을 찾으므로 남의 정산과 같은 404.
        mockMvc.perform(get(SELLER + "/settlements").header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"));
        mockMvc.perform(get(SELLER + "/settlements/" + s.getId()).header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isNotFound());
        postJson(SELLER + "/settlements/" + s.getId() + "/adjustment", body, creatorToken)
                .andExpect(status().isNotFound());

        mockMvc.perform(get(SELLER + "/settlements")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(CREATOR + "/settlements")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(CREATOR + "/settlements/" + s.getId() + "/adjustment/preview"))
                .andExpect(status().isUnauthorized());

        assertThat(count("settlement_adjustment")).isZero();
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.REVIEWING);
    }

    // ------------------------------------------------------------------ PA-05 세금계산서 첨부

    @Test
    @DisplayName("PA-05 세금계산서 첨부(4-4) — 첨부 없이 제출하면 404 · M4 반려 뒤 PDF 를 붙여 재제출하면 같은 행에 붙어 PDF 스트림 · 다른 인플루언서 404")
    void taxInvoiceAttachment() throws Exception {
        makeCreatorBusiness();
        Settlement s = confirm(generated());
        String attachment = CREATOR + "/settlements/" + s.getId() + "/tax-invoice/attachment";
        Long invoiceId = documentId(s, "CREATOR_TAX_INVOICE");

        submitInvoice(s, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.taxInvoice.cardStatus").value("SUBMITTED"));
        creatorGet(attachment).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));

        adminPost("/v1/admin/settlements/" + s.getId() + "/tax-documents/" + invoiceId + "/verify",
                Map.of("result", "RECIPIENT_MISMATCH")).andExpect(status().isOk());
        submitInvoice(s, new MockMultipartFile("attachment", "invoice.pdf", "application/pdf", PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxInvoice.attachmentName").value("invoice.pdf"));

        byte[] streamed = creatorGet(attachment).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(streamed).isEqualTo(PDF);
        assertThat(documentId(s, "CREATOR_TAX_INVOICE")).isEqualTo(invoiceId);
        mockMvc.perform(get(attachment).header(HttpHeaders.AUTHORIZATION, otherCreatorToken()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 보조

    private Settlement generated() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(GENERATED_AT);
    }

    private String otherBrandToken() {
        BrandFixture.Brand other = fixture.createBrand("other-brand@showroomz.test", "다른브랜드");
        return sellerToken(other.seller());
    }

    private String otherCreatorToken() {
        Creator other = createCreator("다른_쇼룸", "other-creator");
        return bearerToken(other.getUser().getUsername(), RoleType.CREATOR, other.getUser().getId());
    }

    private Long documentId(Settlement s, String type) {
        return jdbc.queryForObject("SELECT document_id FROM settlement_tax_document WHERE settlement_id = ? "
                + "AND type = ? AND clawback_id IS NULL", Long.class, s.getId(), type);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private ResultActions postJson(String url, Object body, String token) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions submitInvoice(Settlement s, MockMultipartFile file) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(CREATOR + "/settlements/" + s.getId() + "/tax-invoice");
        request.param("approvalNumber", APPROVAL);
        if (file != null) {
            request.file(file);
        }
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, creatorToken));
    }
}
