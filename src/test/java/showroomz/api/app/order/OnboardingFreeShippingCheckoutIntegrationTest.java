package showroomz.api.app.order;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.member.seller.entity.Seller;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 06 설계서 D-1 · 테스트 시나리오 OB-03 ② — 온보딩에서 무료배송 기준금액을 비운 브랜드는 「무료배송 없음」이어야 한다. 예전에는 0 으로
 * 저장되어 「합계 ≥ 0」 판정으로 모든 주문이 무료배송이 됐다. 온보딩 API 로 저장한 값이 실제 주문서 배송비 계산까지 이어지는지 본다.
 */
@DisplayName("[통합] 온보딩 무료배송 기준금액 미입력 → 주문서 배송비 부과")
class OnboardingFreeShippingCheckoutIntegrationTest extends OrderPaymentTestSupport {

    @Test
    @DisplayName("[OB-03 ②] 무료배송 기준금액을 비우고 온보딩 — 54,400원을 사도 기본 배송비 3,000 부과 · 대조군 50,000 기준이면 무료")
    void omittedThresholdChargesDeliveryFee() throws Exception {
        onboardWithoutFreeShippingThreshold();
        assertThat(marketRepository.findById(brand.marketId()).orElseThrow().getFreeShippingThreshold()).isNull();

        directCheckout(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].shipping.isFreeShipping").value(false))
                .andExpect(jsonPath("$.groups[0].shipping.deliveryFee").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.summary.deliveryFeeTotal").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.summary.totalAmount").value(CREAM_PRICE * 2 + DELIVERY_FEE));

        // 대조군 — 기준금액이 있으면 같은 장바구니가 무료배송이다(판정식 자체는 그대로).
        jdbc.update("UPDATE market SET free_shipping_threshold = ? WHERE market_id = ?", 50_000, brand.marketId());
        directCheckout(2)
                .andExpect(jsonPath("$.groups[0].shipping.isFreeShipping").value(true))
                .andExpect(jsonPath("$.summary.deliveryFeeTotal").value(0));
    }

    @Test
    @DisplayName("[OV-06] 기준금액 0 을 명시한 브랜드는 1개만 사도 무료배송 — 0 은 「항상 무료」이고 미입력(null)과 다르다")
    void explicitZeroThresholdIsAlwaysFree() throws Exception {
        onboard(Map.of("freeShippingThreshold", 0));

        directCheckout(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].shipping.isFreeShipping").value(true))
                .andExpect(jsonPath("$.summary.deliveryFeeTotal").value(0));
    }

    // ------------------------------------------------------------------ helpers

    private void onboardWithoutFreeShippingThreshold() throws Exception {
        onboard(Map.of());
    }

    private void onboard(Map<String, Object> overrides) throws Exception {
        Seller seller = brand.seller();
        seller.setIsNewMember(true);
        sellerRepository.save(seller);

        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/v1/seller/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", brand.email(), "password", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String registerToken = login.path("registerToken").asText();
        assertThat(registerToken).isNotBlank();

        Map<String, Object> body = new HashMap<>();
        body.put("recipientName", "김담당");
        body.put("contact", "010-1234-5678");
        body.put("address", "서울특별시 강남구 테헤란로 123");
        body.put("detailAddress", "4층 401호");
        body.put("defaultDeliveryFee", DELIVERY_FEE);
        body.put("shippingLeadDays", 3);
        body.putAll(overrides);
        mockMvc.perform(post("/v1/seller/auth/complete-registration")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions directCheckout(int quantity) throws Exception {
        return checkout(Map.of("direct", Map.of("variantId", creamVariant.getVariantId(), "quantity", quantity,
                "groupBuyId", groupBuy.getId())));
    }
}
