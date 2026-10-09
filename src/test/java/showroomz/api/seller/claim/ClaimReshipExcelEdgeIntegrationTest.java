package showroomz.api.seller.claim;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.ClaimReshipColumn;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.support.IntegrationTest;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 재발송 엑셀의 경계(보강 시나리오 7-1절 EX-01 ~ EX-09). EX-04(접수번호를 숫자만 적은 행)는 규칙이 정해지지 않아
 * 테스트를 두지 않는다. 판정 대기(EX-02 · N6)는 권장안을 기대값으로 비활성 보관한다.
 */
@IntegrationTest
class ClaimReshipExcelEdgeIntegrationTest extends ClaimTestSupport {

    private static final String EXPORT = SELLER_CLAIMS + "/reshipments/export";

    // ------------------------------------------------------------------ 업로드

    @Test
    @DisplayName("[EX-01] 업로드 — 송장번호가 숫자 셀(12자리)이어도 자릿수 그대로 읽는다")
    void numericTrackingCell() throws Exception {
        Long claimId = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        byte[] file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("접수번호");
            header.createCell(1).setCellValue("택배사");
            header.createCell(2).setCellValue("송장번호");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("CLM-" + claimId);
            row.createCell(1).setCellValue("CJ대한통운");
            row.createCell(2).setCellValue(640012345678d);
            workbook.write(out);
            file = out.toByteArray();
        }

        uploadReshipments(file).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(true))
                .andExpect(jsonPath("$.rows[0].trackingNumber").value("640012345678"));
    }

    @Disabled("판정 대기 — EX-02. 현행은 머리글을 완전 일치로만 찾아 앞뒤 공백이 있으면 CLAIM_UPLOAD_HEADER_MISSING")
    @Test
    @DisplayName("[EX-02] 업로드 — 머리글의 앞뒤 공백은 정리해 인식한다(권장안)")
    void headerWhitespaceTrimmed() throws Exception {
        Long claimId = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));

        uploadReshipments(xlsx(List.of(new String[]{" 접수번호", "택배사 ", " 송장번호 "},
                new String[]{"CLM-" + claimId, "CJ", "640012345678"})))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(1));
    }

    @Test
    @DisplayName("[EX-03] 업로드 — 1,000행 초과는 400 · 머리글만 있으면 0행 · 옛 엑셀(.xls)은 400")
    void uploadBounds() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"접수번호", "택배사", "송장번호"});
        for (int i = 0; i < 1_001; i++) {
            rows.add(new String[]{"CLM-" + (i + 1), "CJ", String.valueOf(640000000000L + i)});
        }
        uploadReshipments(xlsx(rows)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHIPMENT_FILE_TOO_MANY_ROWS"));

        uploadReshipments(xlsx(List.<String[]>of(new String[]{"접수번호", "택배사", "송장번호"})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(0))
                .andExpect(jsonPath("$.validRows").value(0));

        byte[] xls;
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Row header = workbook.createSheet().createRow(0);
            header.createCell(0).setCellValue("접수번호");
            workbook.write(out);
            xls = out.toByteArray();
        }
        uploadReshipments(xls).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHIPMENT_FILE_INVALID"));
    }

    // ------------------------------------------------------------------ 다운로드

    @Test
    @DisplayName("[EX-05] 다운로드 — 교환이 거절된 건(선결제 충당)은 원래 옵션 · 「거절 반송」으로 실린다")
    void rejectedExchangeShipsOriginalOption() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        OrderProduct product = items(group).get(0);
        Map<String, Object> body = claimBody(group, "EXCHANGE", "CHANGE_OF_MIND", product.getId(), null,
                refill.getVariantId());
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", newInvoice()));
        JsonNode created = json(userPost(USER_CLAIMS, body).andExpect(status().isCreated()));
        Long claimId = created.get("claimIds").get(0).asLong();
        completeClaimPayment(created.get("payment").get("paymentId").asText());
        rejectedClaim(claimId);
        assertThat(claimStatus(claimId)).isEqualTo("RESHIP_READY");

        List<List<String>> sheet = export(List.of("CLAIM_NUMBER", "OPTION", "RESHIP_REASON"), null);

        assertThat(sheet).hasSize(2);
        // 받은 옵션(픽스처의 기본 옵션은 옵션명이 없어 빈 칸) — 교환하려던 「리필」이 아니다.
        assertThat(sheet.get(1).subList(0, 3)).containsExactly("CLM-" + claimId,
                java.util.Objects.toString(product.getOptionName(), ""), "반려 반송");
    }

    @Test
    @DisplayName("[EX-06] 다운로드 — 13개 컬럼을 전부 골라도 빈 값은 빈 칸으로 나오고 끝에 택배사 · 송장번호가 붙는다")
    void allColumns() throws Exception {
        Long claimId = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        List<String> all = Arrays.stream(ClaimReshipColumn.values()).map(Enum::name).toList();

        List<List<String>> sheet = export(all, null);

        assertThat(sheet.get(0)).hasSize(all.size() + 2);
        assertThat(sheet.get(0).subList(all.size(), all.size() + 2)).containsExactly("택배사", "송장번호");
        assertThat(sheet.get(1).get(all.indexOf("CLAIM_NUMBER"))).isEqualTo("CLM-" + claimId);
        assertThat(sheet.get(1).get(all.indexOf("QUANTITY"))).isEqualTo("1");
        assertThat(sheet.get(1).get(all.indexOf("CLAIM_TYPE"))).isEqualTo("교환");
    }

    @Disabled("판정 대기 — 보강 시나리오 N6. 현행은 저장된 컬럼 코드가 enum 에 없으면 템플릿 조회가 500")
    @Test
    @DisplayName("[EX-07] 저장된 템플릿에 지금은 없는 컬럼 코드가 있으면 그 컬럼만 버리고 연다(권장안)")
    void unknownTemplateColumnIgnored() throws Exception {
        sellerPut(SELLER_CLAIMS + "/reshipments/export/template", Map.of("columns", List.of("CLAIM_NUMBER", "PHONE")));
        jdbc.update("UPDATE market_purchase_order_template SET columns = 'CLAIM_NUMBER,REMOVED_COLUMN,PHONE' "
                + "WHERE template_type = 'CLAIM_RESHIP'");

        sellerGet(SELLER_CLAIMS + "/reshipments/export/template").andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", contains("CLAIM_NUMBER", "PHONE")));
    }

    @Test
    @DisplayName("[EX-08] 다운로드 — 남의 마켓 claimIds 는 조용히 빠지고, 남는 것이 없으면 400")
    void otherMarketClaimsExcluded() throws Exception {
        Long mine = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        Long others = otherBrandReshipReadyClaim();

        List<List<String>> sheet = export(List.of("CLAIM_NUMBER"), List.of(mine, others));
        assertThat(sheet).hasSize(2);
        assertThat(sheet.get(1).get(0)).isEqualTo("CLM-" + mine);

        sellerPost(EXPORT, Map.of("columns", List.of("CLAIM_NUMBER"), "claimIds", List.of(others)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLAIM_EXPORT_EMPTY"));
    }

    @Test
    @DisplayName("[EX-09] 목록을 내려받은 뒤에는 소비자가 교환받을 배송지를 바꿀 수 없다 — 엑셀의 주소가 그대로다")
    void addressLockedAfterExport() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        Long claimId = passed(exchangeClaim(deliveredGroup(creamVariant, 1), refill));
        List<List<String>> sheet = export(List.of("CLAIM_NUMBER", "ADDRESS"), null);
        Long addressId = json(userPost("/v1/user/delivery-addresses", Map.of("recipientName", "이사간", "zipCode",
                "04524", "address", "서울 중구 세종대로 110", "detailAddress", "3층", "phoneNumber", "010-2222-3333")))
                .get("id").asLong();

        userPatch(USER_CLAIMS + "/" + claimId + "/reship-address", Map.of("addressId", addressId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_ADDRESS_NOT_CHANGEABLE"));
        assertThat(collectionRow(collectionIdOf(claimId)).get("reship_address") + " "
                + collectionRow(collectionIdOf(claimId)).get("reship_detail_address"))
                .isEqualTo(sheet.get(1).get(1));
    }

    // ------------------------------------------------------------------ 보조

    private List<List<String>> export(List<String> columns, List<Long> claimIds) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("columns", columns);
        body.put("claimIds", claimIds);
        return readSheet(sellerPost(EXPORT, body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private void sellerPut(String url, Object body) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk());
    }
}
