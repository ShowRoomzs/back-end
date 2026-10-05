package showroomz.api.app.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.support.IntegrationTest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 배송지 변경(C10 설계서 3-6 · 6절 #29 · #31) · 발주서 = 준비 시작(34 설계서 3-1) · 주문 시점 배송비 스냅숏
 * (앱 클레임 설계서 1-4 · 7절 #26).
 */
@IntegrationTest
class UserOrderAddressChangeIntegrationTest extends SellerOrderTestSupport {

    private static final String ADDRESSES = "/v1/user/delivery-addresses";

    @Test
    @DisplayName("전 하위주문이 준비 시작 전이면 배송지가 바뀐다 — 스냅숏 6필드를 복사하고 마스킹해 돌려준다(#29)")
    void changesAddressBeforePreparation() throws Exception {
        OrderDeliveryGroup group = paidGroup();
        Long orderId = group.getOrder().getId();
        detail(orderId).andExpect(jsonPath("$.addressChangeable").value(true));

        Long newAddressId = addAddress();
        changeAddress(orderId, newAddressId).andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedAddress.recipientName").value("이하*"))
                .andExpect(jsonPath("$.maskedAddress.phoneNumber").value("010-****-4321"))
                .andExpect(jsonPath("$.maskedAddress.address").value("부산 해운대구 센텀로 11"))
                .andExpect(jsonPath("$.maskedAddress.memo").value("경비실에 맡겨주세요"));

        Map<String, Object> order = jdbc.queryForMap("SELECT recipient_name, recipient_phone, zip_code, address, "
                + "detail_address, delivery_memo FROM orders WHERE order_id = ?", orderId);
        assertThat(order.values()).containsExactly("이하늘", "010-9876-4321", "48058", "부산 해운대구 센텀로 11",
                "101동 202호", "경비실에 맡겨주세요");
        // 그 뒤에 받는 발주서는 새 주소다.
        List<String> row = readSheet(sellerPost(SELLER_ORDERS + "/purchase-order", Map.of(
                "deliveryGroupIds", List.of(group.getId()), "columns", List.of("RECIPIENT", "ZIP_CODE", "ADDRESS")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get(1);
        assertThat(row).containsExactly("이하늘", "48058", "부산 해운대구 센텀로 11 101동 202호");
    }

    @Test
    @DisplayName("브랜드가 준비를 시작했으면 409 — 주소는 그대로이고 상세의 addressChangeable 도 거짓이다(#29)")
    void rejectedAfterPreparation() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        Long orderId = group.getOrder().getId();

        detail(orderId).andExpect(jsonPath("$.addressChangeable").value(false));
        changeAddress(orderId, addAddress()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ADDRESS_NOT_CHANGEABLE"));
        assertThat(recipientOf(orderId)).isEqualTo("김수민");
    }

    @Test
    @DisplayName("발주서를 내려받으면 준비 시작이라 그 뒤 배송지 변경은 409 — 파일의 주소와 주문의 주소가 어긋나지 않는다(#31)")
    void rejectedAfterPurchaseOrderDownload() throws Exception {
        OrderDeliveryGroup group = paidGroup();
        Long orderId = group.getOrder().getId();
        Long newAddressId = addAddress();

        List<String> row = readSheet(sellerPost(SELLER_ORDERS + "/purchase-order", Map.of(
                "deliveryGroupIds", List.of(group.getId()), "columns", List.of("RECIPIENT", "ADDRESS")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get(1);

        assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        changeAddress(orderId, newAddressId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ADDRESS_NOT_CHANGEABLE"));
        assertThat(row.get(0)).isEqualTo(recipientOf(orderId));
    }

    @Test
    @DisplayName("결제 전 주문 · 남의 주문 · 남의 배송지 · 배송지 누락은 각각 409 · 403 · 404 · 400(#29)")
    void guards() throws Exception {
        Created unpaid = placeCardOrder(creamVariant, 1);
        OrderDeliveryGroup mine = paidGroup();
        Long myOrderId = mine.getOrder().getId();
        Users stranger = createConsumer("stranger", "박타인");
        String strangerToken = bearerToken(stranger.getUsername(), RoleType.USER, stranger.getId());
        DeliveryAddress strangersAddress = deliveryAddressRepository.save(DeliveryAddress.builder()
                .user(stranger).recipientName("박타인").zipCode("04524").address("서울 중구 세종대로 110")
                .detailAddress("1층").phoneNumber("010-0000-0000").isDefault(true).build());

        detail(unpaid.orderId()).andExpect(jsonPath("$.addressChangeable").value(false));
        changeAddress(unpaid.orderId(), address.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ADDRESS_NOT_CHANGEABLE"));
        mockMvc.perform(patch(ORDERS + "/" + myOrderId + "/delivery-address")
                        .header(HttpHeaders.AUTHORIZATION, strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("addressId", strangersAddress.getId()))))
                .andExpect(status().isForbidden());
        changeAddress(myOrderId, strangersAddress.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        mockMvc.perform(patch(ORDERS + "/" + myOrderId + "/delivery-address")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(recipientOf(myOrderId)).isEqualTo("김수민");
    }

    @Test
    @DisplayName("주문 시점 기본 배송비 — 유료배송은 부과액과 같고, 무료배송은 부과액 0 이어도 원래 배송비가 남는다(#26)")
    void baseDeliveryFeeSnapshot() throws Exception {
        OrderDeliveryGroup charged = paidGroup();
        OrderDeliveryGroup free = paidGroup(creamVariant, 4);

        assertThat(feesOf(charged)).containsExactly(DELIVERY_FEE, DELIVERY_FEE, false);
        assertThat(feesOf(free)).containsExactly(0, DELIVERY_FEE, true);

        // 브랜드가 그 뒤 배송비를 바꿔도 이미 들어온 주문의 값은 그대로다.
        jdbc.update("UPDATE market SET default_delivery_fee = 4000 WHERE market_id = ?", brand.marketId());
        assertThat(reload(free).getBaseDeliveryFee()).isEqualTo(DELIVERY_FEE);
    }

    private List<Object> feesOf(OrderDeliveryGroup group) {
        OrderDeliveryGroup fresh = reload(group);
        return List.of(fresh.getDeliveryFee(), fresh.getBaseDeliveryFee(), fresh.isFreeShippingApplied());
    }

    private String recipientOf(Long orderId) {
        return jdbc.queryForObject("SELECT recipient_name FROM orders WHERE order_id = ?", String.class, orderId);
    }

    /** 배송지 추가 — 응답의 id 로 방금 만든 배송지를 고른다(C13-2). */
    private Long addAddress() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientName", "이하늘");
        body.put("zipCode", "48058");
        body.put("address", "부산 해운대구 센텀로 11");
        body.put("detailAddress", "101동 202호");
        body.put("phoneNumber", "010-9876-4321");
        body.put("memo", "경비실에 맡겨주세요");
        body.put("isDefault", false);
        String json = mockMvc.perform(post(ADDRESSES).header(HttpHeaders.AUTHORIZATION, consumerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private ResultActions changeAddress(Long orderId, Long addressId) throws Exception {
        return mockMvc.perform(patch(ORDERS + "/" + orderId + "/delivery-address")
                .header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("addressId", addressId))));
    }
}
