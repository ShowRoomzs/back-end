package showroomz.api.seller.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약 상품 옵션 — 옵션별 최소 물량 · 옵션 판매가(공구가 + 옵션가) · 옵션 구조 변경 차단(옵션 계획서 3절 · 6-1).
 *
 * <p>픽스처 상품은 옵션 없는 상품(이름 없는 기본 옵션 1행)이다. 옵션이 둘인 장면은 세럼에 「2개 세트」를 더해 만든다.
 */
@DisplayName("[통합] 파트너센터 계약 — 옵션별 최소 물량 · 옵션 판매가")
class SellerContractOptionIntegrationTest extends SellerContractTestSupport {

    private static final String PRODUCTS = "/v1/seller/products";

    // ── 저장 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("옵션 행은 상품의 옵션 전량이고, 옵션 판매가 = 공구가 + (옵션 정가 − 상품 정가)다")
    void savesOptionsWithDerivedSalePrice() throws Exception {
        ProductVariant single = variantsOf(serum).get(0);
        ProductVariant set = addVariant(serum, "2개 세트", 60_000);
        long contractId = createDraft();

        saveOk(contractId, validForm(0L).items(new ContractUpdateRequest.Item(
                null, serum.getProductId(), 22_000, new BigDecimal("15.0"),
                List.of(new ContractUpdateRequest.Option(single.getVariantId(), 200),
                        new ContractUpdateRequest.Option(set.getVariantId(), 100)))));

        detail(contractId)
                // 상품 단위 최소 물량은 옵션 합계다 — 따로 저장하지 않는다.
                .andExpect(jsonPath("$.items[0].minQuantity").value(300))
                .andExpect(jsonPath("$.items[0].options.length()").value(2))
                .andExpect(jsonPath("$.items[0].options[0].variantId").value(single.getVariantId()))
                .andExpect(jsonPath("$.items[0].options[0].variantName").doesNotExist())
                .andExpect(jsonPath("$.items[0].options[0].optionExtraPrice").value(0))
                .andExpect(jsonPath("$.items[0].options[0].salePrice").value(22_000))
                .andExpect(jsonPath("$.items[0].options[0].minQuantity").value(200))
                .andExpect(jsonPath("$.items[0].options[1].variantName").value("2개 세트"))
                .andExpect(jsonPath("$.items[0].options[1].regularPrice").value(60_000))
                .andExpect(jsonPath("$.items[0].options[1].optionExtraPrice").value(28_000))
                .andExpect(jsonPath("$.items[0].options[1].salePrice").value(50_000))
                .andExpect(jsonPath("$.items[0].options[1].minQuantity").value(100));
    }

    @Test
    @DisplayName("보내지 않은 옵션도 행으로 남고 수량만 비어 있다 — 상품 단위 합계는 그래서 null이다")
    void keepsUnsentOptionsWithNullQuantity() throws Exception {
        ProductVariant single = variantsOf(serum).get(0);
        addVariant(serum, "2개 세트", 60_000);
        long contractId = createDraft();

        saveOk(contractId, validForm(0L).items(new ContractUpdateRequest.Item(
                null, serum.getProductId(), 22_000, new BigDecimal("15.0"),
                List.of(new ContractUpdateRequest.Option(single.getVariantId(), 200)))));

        detail(contractId)
                .andExpect(jsonPath("$.items[0].minQuantity").doesNotExist())
                .andExpect(jsonPath("$.items[0].options.length()").value(2))
                .andExpect(jsonPath("$.items[0].options[1].minQuantity").doesNotExist());
    }

    @Test
    @DisplayName("공구가가 비어 있으면 옵션 판매가도 비어 있다 — 옵션가는 정가 스냅샷만으로 계산된다")
    void salePriceIsNullWithoutGroupBuyPrice() throws Exception {
        ProductVariant set = addVariant(serum, "2개 세트", 60_000);
        long contractId = createDraft();

        saveOk(contractId, validForm(0L).items(new ContractUpdateRequest.Item(
                null, serum.getProductId(), null, null,
                List.of(new ContractUpdateRequest.Option(set.getVariantId(), 100)))));

        detail(contractId)
                .andExpect(jsonPath("$.items[0].options[1].optionExtraPrice").value(28_000))
                .andExpect(jsonPath("$.items[0].options[1].salePrice").doesNotExist());
    }

    @Test
    @DisplayName("상품의 옵션이 아닌 variantId · 같은 옵션 두 번은 400이다")
    void rejectsForeignAndDuplicatedOptions() throws Exception {
        ProductVariant creamVariant = variantsOf(cream).get(0);
        ProductVariant serumVariant = variantsOf(serum).get(0);
        long contractId = createDraft();

        save(contractId, validForm(0L).items(new ContractUpdateRequest.Item(
                null, serum.getProductId(), 22_000, new BigDecimal("15.0"),
                List.of(new ContractUpdateRequest.Option(creamVariant.getVariantId(), 200)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_ITEM_OPTION_NOT_OF_PRODUCT"));

        save(contractId, validForm(0L).items(new ContractUpdateRequest.Item(
                null, serum.getProductId(), 22_000, new BigDecimal("15.0"),
                List.of(new ContractUpdateRequest.Option(serumVariant.getVariantId(), 200),
                        new ContractUpdateRequest.Option(serumVariant.getVariantId(), 100)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_ITEM_OPTION_DUPLICATED"));

        // 미선택 행에 옵션을 실어도 400 — 어느 상품의 옵션인지 판정할 수 없다.
        save(contractId, validForm(0L).items(new ContractUpdateRequest.Item(
                null, null, null, null,
                List.of(new ContractUpdateRequest.Option(serumVariant.getVariantId(), 200)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_ITEM_OPTION_NOT_OF_PRODUCT"));
    }

    @Test
    @DisplayName("작성 폼 선택지는 상품마다 옵션 전량(정가·재고·대표 여부)을 동봉한다")
    void formSourcesIncludeVariants() throws Exception {
        addVariant(serum, "2개 세트", 60_000);

        mockMvc.perform(get(CONTRACTS + "/form-sources").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[?(@.productId == %d)].options[1].variantName".formatted(serum.getProductId()),
                        hasItem("2개 세트")))
                .andExpect(jsonPath("$.products[?(@.productId == %d)].options[2]".formatted(serum.getProductId())).isEmpty())
                .andExpect(jsonPath("$.products[?(@.productId == %d)].options[1].regularPrice".formatted(serum.getProductId()),
                        hasItem(60_000)))
                .andExpect(jsonPath("$.products[?(@.productId == %d)].options[0].isRepresentative".formatted(serum.getProductId()),
                        hasItem(true)))
                .andExpect(jsonPath("$.products[?(@.productId == %d)].options[0].stock".formatted(serum.getProductId()),
                        hasItem(500)));
    }

    // ── 검증 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("옵션별 최소 물량이 비면 옵션 index까지 붙은 REQUIRED로 내려간다")
    void requiresOptionMinQuantity() throws Exception {
        long contractId = createDraft();
        saveOk(contractId, validForm(0L).items(item(serum, 28_000, "15.0", null)));

        validate(contractId)
                .andExpect(jsonPath("$.canSubmit").value(false))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'ITEM_OPTION_MIN_QUANTITY_REQUIRED')].kind",
                        hasItem("REQUIRED")))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'ITEM_OPTION_MIN_QUANTITY_REQUIRED')].field",
                        hasItem("items[0].options[0].minQuantity")));

        reviewRequest(contractId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.hardViolations[*].code", hasItem("ITEM_OPTION_MIN_QUANTITY_REQUIRED")));
    }

    @Test
    @DisplayName("저장 뒤 상품에 옵션이 늘면 ITEM_OPTIONS_MISMATCH — 다시 저장하면 새 옵션 행이 생겨 풀린다")
    void flagsOptionMismatchUntilResaved() throws Exception {
        long contractId = createDraft();
        saveOk(contractId, validForm(0L));

        ProductVariant set = addVariant(serum, "2개 세트", 60_000);

        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'ITEM_OPTIONS_MISMATCH')].field",
                        hasItem("items[0].options")));

        // 서버는 수량을 새 옵션에 임의로 배분하지 않는다 — 브랜드가 다시 저장한다.
        ProductVariant single = variantsOf(serum).get(0);
        saveOk(contractId, validForm(currentVersion(contractId)).items(new ContractUpdateRequest.Item(
                null, serum.getProductId(), 28_000, new BigDecimal("15.0"),
                List.of(new ContractUpdateRequest.Option(single.getVariantId(), 200),
                        new ContractUpdateRequest.Option(set.getVariantId(), 100)))));

        validate(contractId)
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'ITEM_OPTIONS_MISMATCH')]").isEmpty());
    }

    @Test
    @DisplayName("옵션 정가가 상품 정가보다 공구가 이상 낮으면 옵션 판매가가 음수라 막는다")
    void rejectsNegativeOptionSalePrice() throws Exception {
        ProductVariant single = variantsOf(serum).get(0);
        ProductVariant sample = addVariant(serum, "샘플 5ml", 5_000);
        long contractId = createDraft();

        saveOk(contractId, validForm(0L).items(new ContractUpdateRequest.Item(
                null, serum.getProductId(), 20_000, new BigDecimal("15.0"),
                List.of(new ContractUpdateRequest.Option(single.getVariantId(), 200),
                        new ContractUpdateRequest.Option(sample.getVariantId(), 50)))));

        // 20,000 + (5,000 − 32,000) = −7,000
        detail(contractId).andExpect(jsonPath("$.items[0].options[1].salePrice").value(-7_000));
        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'ITEM_OPTION_SALE_PRICE_NEGATIVE')].field",
                        hasItem("items[0].options[1].variantId")));
    }

    // ── 스냅샷 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("검토 요청은 옵션 정가 스냅샷을 현재 값으로 다시 맞춘다 — 상품 정가만 갱신하면 옵션가가 어긋난다")
    void reviewRequestRefreshesOptionSnapshot() throws Exception {
        ProductVariant set = addVariant(serum, "2개 세트", 60_000);
        ProductVariant single = variantsOf(serum).get(0);
        long contractId = createDraft();
        saveOk(contractId, validForm(0L).items(new ContractUpdateRequest.Item(
                null, serum.getProductId(), 22_000, new BigDecimal("15.0"),
                List.of(new ContractUpdateRequest.Option(single.getVariantId(), 200),
                        new ContractUpdateRequest.Option(set.getVariantId(), 100)))));

        changeVariantRegularPrice(set, 64_000);

        reviewRequestOk(contractId);

        detail(contractId)
                .andExpect(jsonPath("$.items[0].options[1].regularPrice").value(64_000))
                .andExpect(jsonPath("$.items[0].options[1].optionExtraPrice").value(32_000))
                .andExpect(jsonPath("$.items[0].options[1].salePrice").value(54_000));
    }

    @Test
    @DisplayName("체결 뒤에는 옵션 정가를 고쳐도 옵션 판매가가 변하지 않는다 — 소비자 가격은 계약 스냅샷이다")
    void concludedContractKeepsOptionPrices() throws Exception {
        Contract concluded = seedInStatus(ContractStatus.CONCLUDED);
        ProductVariant single = variantsOf(serum).get(0);

        changeVariantRegularPrice(single, 40_000);
        changeRegularPrice(serum, 40_000);

        detail(concluded.getId())
                .andExpect(jsonPath("$.items[0].regularPrice").value(32_000))
                .andExpect(jsonPath("$.items[0].options[0].regularPrice").value(32_000))
                .andExpect(jsonPath("$.items[0].options[0].salePrice").value(28_000));
    }

    @Test
    @DisplayName("재작성은 옵션 행도 복사한다")
    void duplicateCopiesOptions() throws Exception {
        Contract source = seedInStatus(ContractStatus.CONCLUDED);

        String response = duplicate(source.getId())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        detail(readLong(response, "$.contractId"))
                .andExpect(jsonPath("$.items[0].options.length()").value(1))
                .andExpect(jsonPath("$.items[0].options[0].minQuantity").value(300))
                .andExpect(jsonPath("$.items[0].minQuantity").value(300));
    }

    // ── 상품 관리 보호 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("진행 중 계약에 묶인 상품은 옵션 구조를 바꿀 수 없다 — 종결된 계약만 남으면 바꿀 수 있다")
    void locksOptionStructureWhileContractIsOpen() throws Exception {
        long draftId = createDraft();
        saveOk(draftId, validForm(0L));

        updateOptionStructure(serum)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_OPTION_LOCKED_BY_CONTRACT"));

        deleteContract(draftId).andExpect(status().isNoContent());
        seedInStatus(ContractStatus.DECLINED);

        updateOptionStructure(serum).andExpect(status().isOk());
    }

    @Test
    @DisplayName("체결된 계약도 공구가 끝나기 전에는 상품 옵션 구조를 잠근다")
    void concludedContractWithoutEndedGroupBuyLocks() throws Exception {
        seedInStatus(ContractStatus.CONCLUDED);

        updateOptionStructure(serum)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_OPTION_LOCKED_BY_CONTRACT"));
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private List<ProductVariant> variantsOf(Product product) {
        return productVariantRepository.findByProductIdsOrderByVariantId(List.of(product.getProductId()));
    }

    private ProductVariant addVariant(Product product, String name, int regularPrice) {
        return productVariantRepository.save(new ProductVariant(product, name, regularPrice, regularPrice, 180, false));
    }

    private void changeVariantRegularPrice(ProductVariant variant, int regularPrice) {
        ProductVariant managed = productVariantRepository.findByVariantId(variant.getVariantId()).orElseThrow();
        managed.setRegularPrice(regularPrice);
        managed.setSalePrice(regularPrice);
        productVariantRepository.save(managed);
    }

    private org.springframework.test.web.servlet.ResultActions updateOptionStructure(Product product) throws Exception {
        return mockMvc.perform(put(PRODUCTS + "/" + product.getProductId())
                .header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"optionGroups":[{"name":"용량","options":[{"name":"30ml","price":0},{"name":"50ml","price":8000}]}],
                         "variants":[{"optionNames":["30ml"],"regularPrice":32000,"stock":10,"isRepresentative":true},
                                     {"optionNames":["50ml"],"regularPrice":40000,"stock":5}]}
                        """));
    }
}
