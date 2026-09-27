package showroomz.api.app.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 선행 수정 계획서 3-8 — 셀러 재고 수정과 주문 예약의 충돌. 예약 3건(100 → 97) 사이에 셀러가 "100" 을 저장하면
 * DB 는 97 · 셀러 조회는 100 · 만료 복원 뒤 DB 100 이어야 한다. 예약 0건이면 저장·조회가 입력값 그대로다.
 */
@DisplayName("[통합] 셀러 재고 수정 × 결제 대기 예약")
class SellerStockReservationIntegrationTest extends OrderPaymentTestSupport {

    private static final String SELLER_PRODUCTS = "/v1/seller/products";

    @Test
    @DisplayName("예약 3건 사이에 셀러가 100 을 저장하면 DB 97 · 셀러 조회 100 · 만료 복원 뒤 DB 100")
    void sellerStockSaveSubtractsReservation() throws Exception {
        // 재고만 수정하는 요청은 옵션명으로 옵션을 찾는다 — 기본 옵션에 이름을 붙인다.
        jdbc.update("UPDATE product_variant SET name = '기본' WHERE variant_id = ?", creamVariant.getVariantId());
        setStock(creamVariant, 100);

        Created reserved = placeCardOrder(creamVariant, 3);
        assertThat(stockOf(creamVariant)).isEqualTo(97);

        mockMvc.perform(get(SELLER_PRODUCTS + "/" + cream.getProductId()).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].stock").value(100));

        mockMvc.perform(put(SELLER_PRODUCTS + "/" + cream.getProductId()).header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("variants", List.of(Map.of("optionNames", List.of("기본"), "regularPrice", 34000, "stock", 100))))))
                .andExpect(status().isOk());
        assertThat(stockOf(creamVariant)).isEqualTo(97);
        mockMvc.perform(get(SELLER_PRODUCTS + "/" + cream.getProductId()).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(jsonPath("$.variants[0].stock").value(100));
        mockMvc.perform(get(SELLER_PRODUCTS).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk());

        fake.willReturnNotFound(reserved.paymentId());
        expirationService.expire(reserved.orderId(), LocalDateTime.now().plusMinutes(31));
        assertThat(stockOf(creamVariant)).isEqualTo(100);

        // 예약이 없으면 입력값 그대로다.
        mockMvc.perform(put(SELLER_PRODUCTS + "/" + cream.getProductId()).header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("variants", List.of(Map.of("optionNames", List.of("기본"), "regularPrice", 34000, "stock", 50))))))
                .andExpect(status().isOk());
        assertThat(stockOf(creamVariant)).isEqualTo(50);
    }
}
