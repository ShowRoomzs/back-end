package showroomz.api.app.post;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약 가격 기준 판매 — 게시물에서 본 공구가가 C7 → 옵션 시트 → 장바구니까지 같은 값으로 이어지고,
 * 상품 관리가 정가를 고쳐도 변하지 않는다(가격 계획서 4·5·7절).
 *
 * <p>픽스처: 크림(정가 34,000 · 공구가 27,200) 옵션 셋 — 기본 옵션(재고 0) · 「기본」(재고 5) · 「2개 세트」(정가 60,000 ·
 * 재고 10). 2개 세트의 옵션 판매가 = 27,200 + (60,000 − 34,000) = 53,200.
 */
@DisplayName("[통합] 계약 가격 기준 판매 — C7 · 옵션 시트 · 장바구니")
class GroupBuyPricingIntegrationTest extends GroupBuyPostTestSupport {

    private static final String PRODUCTS = "/v1/common/products/";
    private static final String CART = "/v1/user/cart";

    private ProductVariant set;
    private GroupBuy ongoing;

    @BeforeEach
    void setUpPricing() {
        set = productVariantRepository.save(new ProductVariant(cream, "2개 세트", 60_000, 60_000, 10, false));
        ongoing = seedIn(GroupBuyStatus.IN_PROGRESS);
        markSelling(cream, serum);
    }

    // ── C7 상세 · 옵션 시트 ─────────────────────────────────────────────────

    @Test
    @DisplayName("공구 id로 들어오면 그 계약의 가격 — 상품 가격은 대표 옵션, 옵션마다 공구가 + 옵션가")
    void detailUsesContractPrices() throws Exception {
        detail(cream, ongoing.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuyId").value(ongoing.getId()))
                .andExpect(jsonPath("$.groupBuyNumber").value(ongoing.getGroupBuyNumber()))
                .andExpect(jsonPath("$.regularPrice").value(34_000))
                .andExpect(jsonPath("$.salePrice").value(27_200))
                .andExpect(jsonPath("$.discountRate").value(20))
                .andExpect(jsonPath("$.variants[?(@.variantId == %d)].salePrice".formatted(set.getVariantId()),
                        hasItem(53_200)))
                .andExpect(jsonPath("$.variants[?(@.variantId == %d)].regularPrice".formatted(set.getVariantId()),
                        hasItem(60_000)));

        variantStocks(cream, set, ongoing.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].price.salePrice").value(53_200))
                .andExpect(jsonPath("$.variants[0].price.regularPrice").value(60_000))
                .andExpect(jsonPath("$.variants[0].isOutOfStock").value(false));
    }

    @Test
    @DisplayName("공구 id 없이 들어와도 판매 중 공구가 하나면 그 공구로 정하고 id를 돌려준다")
    void detailFallsBackToSoleSellingGroupBuy() throws Exception {
        detail(cream, null)
                .andExpect(jsonPath("$.groupBuyId").value(ongoing.getId()))
                .andExpect(jsonPath("$.salePrice").value(27_200));
    }

    @Test
    @DisplayName("판매 중 공구가 둘이면 서버가 고르지 않는다 — 공구 id를 실으면 그 공구로 정해진다")
    void detailDoesNotGuessBetweenTwoGroupBuys() throws Exception {
        GroupBuy second = seedIn(GroupBuyStatus.IN_PROGRESS);

        detail(cream, null)
                .andExpect(jsonPath("$.groupBuyId").doesNotExist())
                // 계약 가격이 아니다 — 상품 가격 그대로다.
                .andExpect(jsonPath("$.salePrice").value(34_000));

        detail(cream, second.getId()).andExpect(jsonPath("$.groupBuyId").value(second.getId()));
    }

    @Test
    @DisplayName("시작 전 공구·끝난 공구는 가격을 정하지 않는다")
    void detailIgnoresNotSellingGroupBuy() throws Exception {
        GroupBuy preparing = seedIn(GroupBuyStatus.PREPARING);
        detail(cream, preparing.getId()).andExpect(jsonPath("$.groupBuyId").doesNotExist());

        moveTo(ongoing.getId(), GroupBuyStatus.ENDED);
        detail(cream, ongoing.getId()).andExpect(jsonPath("$.groupBuyId").doesNotExist());
    }

    @Test
    @DisplayName("계약 뒤에 생긴 옵션은 그 공구에서 살 수 없다 — 옵션 시트에 품절로 보인다")
    void optionAddedAfterContractIsNotSold() throws Exception {
        markSellable(cream);
        ProductVariant refill = productVariantRepository.save(
                new ProductVariant(cream, "리필", 20_000, 20_000, 50, false));

        detail(cream, ongoing.getId())
                .andExpect(jsonPath("$.variants[?(@.variantId == %d)].isOutOfStock".formatted(refill.getVariantId()),
                        hasItem(true)));
        variantStocks(cream, refill, ongoing.getId())
                .andExpect(jsonPath("$.variants[0].isOutOfStock").value(true));
    }

    // ── 장바구니 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("담은 공구의 계약 가격으로 합계를 내고, 공구 단위로 묶어 공구명·D-day를 붙인다")
    void cartUsesContractPriceGroupedByGroupBuy() throws Exception {
        addToCart(set, ongoing.getId(), 2).andExpect(status().isOk());

        cart()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].groupBuyId").value(ongoing.getId()))
                .andExpect(jsonPath("$.groups[0].groupBuyTitle").value("글로우 크림 앵콜 공구"))
                .andExpect(jsonPath("$.groups[0].dDay").value(4))
                .andExpect(jsonPath("$.groups[0].isClosed").value(false))
                .andExpect(jsonPath("$.groups[0].items[0].price.regularPrice").value(60_000))
                .andExpect(jsonPath("$.groups[0].items[0].price.salePrice").value(53_200))
                .andExpect(jsonPath("$.summary.regularTotal").value(120_000))
                .andExpect(jsonPath("$.summary.saleTotal").value(106_400));
    }

    @Test
    @DisplayName("상품 관리에서 정가·옵션가를 고쳐도 담긴 금액은 변하지 않는다 — 가격은 계약 스냅샷이다")
    void sellerPriceChangeDoesNotMoveCartPrice() throws Exception {
        addToCart(set, ongoing.getId(), 1).andExpect(status().isOk());

        jdbc.update("UPDATE product_variant SET regular_price = 90000, sale_price = 90000 WHERE variant_id = ?",
                set.getVariantId());
        jdbc.update("UPDATE product SET regular_price = 50000, sale_price = 50000 WHERE product_id = ?",
                cream.getProductId());

        cart()
                .andExpect(jsonPath("$.groups[0].items[0].price.regularPrice").value(60_000))
                .andExpect(jsonPath("$.groups[0].items[0].price.salePrice").value(53_200))
                .andExpect(jsonPath("$.summary.saleTotal").value(53_200));
        detail(cream, ongoing.getId())
                .andExpect(jsonPath("$.salePrice").value(27_200))
                .andExpect(jsonPath("$.variants[?(@.variantId == %d)].salePrice".formatted(set.getVariantId()),
                        hasItem(53_200)));
    }

    @Test
    @DisplayName("공구 없이 담을 수 없고, 다른 브랜드 공구를 실어 와도 그 계약에 옵션이 없어 막힌다")
    void addRequiresGroupBuyOfThisOption() throws Exception {
        mockMvc.perform(post(CART).header(HttpHeaders.AUTHORIZATION, consumer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"variantId\": %d, \"quantity\": 1}]".formatted(set.getVariantId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("공구 ID는 필수입니다."));

        GroupBuy foreign = ongoingGroupBuyOf("타브랜드", creator);
        addToCart(set, foreign.getId(), 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_PURCHASABLE"))
                .andExpect(jsonPath("$.message").value("이 공구에서 판매하지 않는 옵션이에요"));

        moveTo(ongoing.getId(), GroupBuyStatus.ENDED);
        addToCart(set, ongoing.getId(), 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_PURCHASABLE"));
    }

    @Test
    @DisplayName("같은 옵션도 공구가 다르면 두 줄 · 두 그룹이다 — 가격·리워드 귀속이 다르다")
    void sameOptionFromTwoGroupBuysStaysSeparate() throws Exception {
        GroupBuy second = seedIn(GroupBuyStatus.IN_PROGRESS);

        addToCart(set, ongoing.getId(), 1).andExpect(status().isOk());
        addToCart(set, second.getId(), 1).andExpect(status().isOk());
        addToCart(set, ongoing.getId(), 1).andExpect(status().isOk());

        cart()
                .andExpect(jsonPath("$.groups.length()").value(2))
                .andExpect(jsonPath("$.summary.totalCount").value(2))
                .andExpect(jsonPath("$.groups[?(@.groupBuyId == %d)].items[0].quantity".formatted(ongoing.getId()),
                        hasItem(2)));
    }

    @Test
    @DisplayName("담은 뒤 공구가 끝나면 마감으로 남고 합계에서 빠진다 — 계약 가격은 그대로 보인다")
    void endedGroupBuyLineIsClosed() throws Exception {
        addToCart(set, ongoing.getId(), 1).andExpect(status().isOk());
        moveTo(ongoing.getId(), GroupBuyStatus.ENDED);

        cart()
                .andExpect(jsonPath("$.groups[0].isClosed").value(true))
                .andExpect(jsonPath("$.groups[0].dDay").doesNotExist())
                .andExpect(jsonPath("$.groups[0].items[0].availability.reason").value("GROUP_BUY_CLOSED"))
                .andExpect(jsonPath("$.groups[0].items[0].price.salePrice").value(53_200))
                .andExpect(jsonPath("$.summary.saleTotal").value(0));
    }

    @Test
    @DisplayName("옵션 변경은 담은 공구 안에서만 — 계약에 없는 옵션으로는 바꿀 수 없다")
    void optionChangeStaysInGroupBuy() throws Exception {
        markSellable(cream);
        addToCart(set, ongoing.getId(), 1).andExpect(status().isOk());
        long cartId = firstCartId();
        ProductVariant refill = productVariantRepository.save(
                new ProductVariant(cream, "리필", 20_000, 20_000, 50, false));

        mockMvc.perform(patch(CART + "/" + cartId).header(HttpHeaders.AUTHORIZATION, consumer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\": %d}".formatted(refill.getVariantId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_PURCHASABLE"));

        ProductVariant basic = variantNamed(cream, "기본");
        mockMvc.perform(patch(CART + "/" + cartId).header(HttpHeaders.AUTHORIZATION, consumer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\": %d}".formatted(basic.getVariantId())))
                .andExpect(status().isOk());

        cart()
                .andExpect(jsonPath("$.groups[0].groupBuyId").value(ongoing.getId()))
                .andExpect(jsonPath("$.groups[0].items[0].variantId").value(basic.getVariantId()))
                .andExpect(jsonPath("$.groups[0].items[0].price.salePrice").value(27_200));
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    /** 공구 생성 경로(동기화)를 SQL로 옮긴 상태 — 상품이 진행 중 공구에 걸려 있어야 상세·구매가 열린다. */
    private void markSelling(Product... products) {
        for (Product product : products) {
            markSellable(product);
        }
    }

    private void markSellable(Product product) {
        jdbc.update("UPDATE product SET group_buy_status = 'IN_PROGRESS' WHERE product_id = ?", product.getProductId());
    }

    private ResultActions detail(Product product, Long groupBuyId) throws Exception {
        return mockMvc.perform(get(PRODUCTS + product.getProductId()
                + (groupBuyId == null ? "" : "?groupBuyId=" + groupBuyId)));
    }

    private ResultActions variantStocks(Product product, ProductVariant variant, Long groupBuyId) throws Exception {
        return mockMvc.perform(get(PRODUCTS + product.getProductId() + "/variants")
                .param("variantIds", String.valueOf(variant.getVariantId()))
                .param("groupBuyId", String.valueOf(groupBuyId)));
    }

    private ResultActions addToCart(ProductVariant variant, Long groupBuyId, int quantity) throws Exception {
        return mockMvc.perform(post(CART).header(HttpHeaders.AUTHORIZATION, consumer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"variantId\": %d, \"groupBuyId\": %d, \"quantity\": %d}]"
                        .formatted(variant.getVariantId(), groupBuyId, quantity)));
    }

    private ResultActions cart() throws Exception {
        return mockMvc.perform(get(CART).header(HttpHeaders.AUTHORIZATION, consumer));
    }

    private long firstCartId() throws Exception {
        String body = cart().andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.groups[0].items[0].cartId")).longValue();
    }

    private ProductVariant variantNamed(Product product, String name) {
        List<ProductVariant> variants = productVariantRepository.findByProductIdsOrderByVariantId(
                List.of(product.getProductId()));
        return variants.stream().filter(v -> name.equals(v.getName())).findFirst().orElseThrow();
    }
}
