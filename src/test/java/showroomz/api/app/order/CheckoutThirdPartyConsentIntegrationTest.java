package showroomz.api.app.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.domain.order.entity.Order;
import showroomz.global.config.properties.OrderProperties;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 화면 「브랜드 개인정보 제3자 제공」 필수 동의 · 이력(수정요청 C). 운영 기본값은 필수다 — 테스트 공통 설정은 기존 시나리오 때문에 끄고
 * 있어 여기서 켠다.
 */
@DisplayName("주문 — 브랜드 개인정보 제3자 제공 필수 동의")
class CheckoutThirdPartyConsentIntegrationTest extends OrderPaymentTestSupport {

    @Autowired private OrderProperties orderProperties;

    @BeforeEach
    void requireConsent() {
        orderProperties.setThirdPartyConsentRequired(true);
    }

    @AfterEach
    void restore() {
        orderProperties.setThirdPartyConsentRequired(false);
    }

    @Test
    @DisplayName("동의 없음 · false 는 400 ORDER_THIRD_PARTY_CONSENT_REQUIRED · 주문 없음 / 동의하면 생성 · 동의 시각 · 제공받는 자(브랜드명) 이력")
    void requiresConsentAndRecordsHistory() throws Exception {
        long before = orderRepository.count();
        createOrder(directOrder(newKey(), creamVariant, 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_THIRD_PARTY_CONSENT_REQUIRED"));
        Map<String, Object> declined = new HashMap<>(directOrder(newKey(), creamVariant, 1));
        declined.put("thirdPartyProvisionAgreed", false);
        createOrder(declined).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_THIRD_PARTY_CONSENT_REQUIRED"));
        assertThat(orderRepository.count()).isEqualTo(before);

        Map<String, Object> agreed = new HashMap<>(directOrder(newKey(), creamVariant, 1));
        agreed.put("thirdPartyProvisionAgreed", true);
        Created created = created(createOrder(agreed).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        Order order = order(created.orderId());
        assertThat(order.getThirdPartyConsentAt()).isNotNull();
        String brandName = jdbc.queryForObject("SELECT m.market_name FROM product_variant v "
                + "JOIN product p ON p.product_id = v.product_id JOIN market m ON m.market_id = p.market_id "
                + "WHERE v.variant_id = ?", String.class, creamVariant.getVariantId());
        assertThat(order.getThirdPartyRecipients()).isEqualTo(brandName);
    }
}
