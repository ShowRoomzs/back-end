package showroomz.api.seller.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 주문 API 진입(보강 시나리오 AUTH) — 인증 없음 401 · 소비자 403 · 인플루언서 403 · 마켓 없는 셀러 404.
 * 거부된 요청은 어느 것도 이력·반출 로그·환불 큐·상태를 바꾸지 않는다.
 */
@IntegrationTest
class SellerOrderAccessIntegrationTest extends SellerOrderTestSupport {

    @Test
    @DisplayName("[AUTH-01] 토큰 없이 14개 요청 전부 401 — 부수 효과 없음")
    void anonymousIsUnauthorized() throws Exception {
        Fixture f = seedFixture();

        for (MockHttpServletRequestBuilder request : allEndpoints(f)) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }

        assertUntouched(f);
    }

    @Test
    @DisplayName("[AUTH-02] 소비자(USER) 토큰은 14개 요청 전부 403 — 부수 효과 없음")
    void consumerIsForbidden() throws Exception {
        Fixture f = seedFixture();

        for (MockHttpServletRequestBuilder request : allEndpoints(f)) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, consumerToken)).andExpect(status().isForbidden());
        }

        assertUntouched(f);
    }

    @Test
    @DisplayName("[AUTH-03] 인플루언서(CREATOR) 토큰도 403 — 권한 오류가 404 SELLER_NOT_FOUND 로 포장되지 않는다(N5)")
    void creatorIsForbidden() throws Exception {
        Fixture f = seedFixture();
        String creatorToken = bearerToken(consumer.getUsername(), RoleType.CREATOR, consumer.getId());

        for (MockHttpServletRequestBuilder request : allEndpoints(f)) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().isForbidden());
        }

        assertUntouched(f);
    }

    @Test
    @DisplayName("[AUTH-04] 마켓이 없는 셀러는 404 MARKET_NOT_FOUND — 500 이 아니다")
    void sellerWithoutMarket() throws Exception {
        Seller seller = new Seller("no-market@showroomz.test", passwordEncoder.encode(RAW_PASSWORD), "박담당",
                "010-3333-4444", LocalDateTime.now());
        seller.setRoleType(RoleType.SELLER);
        String token = sellerToken(sellerRepository.save(seller));

        sellerGet(token, SELLER_ORDERS)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MARKET_NOT_FOUND"));
        sellerGet(token, SELLER_ORDERS + "/summary")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MARKET_NOT_FOUND"));
        sellerPost(token, SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MARKET_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ 보조

    private Fixture seedFixture() throws Exception {
        OrderDeliveryGroup fresh = paidGroup();
        OrderDeliveryGroup shipping = shippingGroup("610020003000");
        OrderDeliveryGroup requested = preparingGroup();
        OrderCancelRequest request = seedCancelRequest(requested);
        return new Fixture(fresh, shipping, requested, request);
    }

    /** 컨트롤러의 14개 매핑 — 본문은 유효한 값으로 채운다(보안 필터가 먼저 거르는지 보려는 것이다). */
    private List<MockHttpServletRequestBuilder> allEndpoints(Fixture f) {
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        requests.add(get(SELLER_ORDERS));
        requests.add(get(SELLER_ORDERS + "/summary"));
        requests.add(get(SELLER_ORDERS + "/" + f.fresh().getId()));
        requests.add(json(post(SELLER_ORDERS + "/prepare-start"), Map.of("deliveryGroupIds", List.of(f.fresh().getId()))));
        requests.add(json(post(SELLER_ORDERS + "/purchase-order"),
                Map.of("deliveryGroupIds", List.of(f.fresh().getId()), "columns", List.of("RECIPIENT", "PHONE"))));
        requests.add(get(SELLER_ORDERS + "/purchase-order/template"));
        requests.add(json(put(SELLER_ORDERS + "/purchase-order/template"), Map.of("columns", List.of("PHONE"))));
        requests.add(json(post(SELLER_ORDERS + "/shipments"), Map.of("rows", List.of(
                shipmentRow(f.requested().getId(), "CJ", "610020003001")))));
        requests.add(multipart(SELLER_ORDERS + "/shipments/parse")
                .file(new MockMultipartFile("file", "s.xlsx", XLSX, shipmentXlsx(List.<String[]>of()))));
        requests.add(get(SELLER_ORDERS + "/shipments/template"));
        requests.add(json(patch(SELLER_ORDERS + "/" + f.shipping().getId() + "/shipment"),
                Map.of("carrier", "HANJIN", "trackingNumber", "610020003002")));
        requests.add(json(post(SELLER_ORDERS + "/cancel"), Map.of("deliveryGroupIds", List.of(f.fresh().getId()),
                "reasonCode", "SOLD_OUT", "consumerMessage", "품절")));
        requests.add(json(post(SELLER_ORDERS + "/cancel-requests/" + f.request().getId() + "/approve"), Map.of()));
        requests.add(json(post(SELLER_ORDERS + "/cancel-requests/" + f.request().getId() + "/reject"),
                Map.of("reason", "발송 예정")));
        return requests;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
    }

    private record Fixture(OrderDeliveryGroup fresh, OrderDeliveryGroup shipping, OrderDeliveryGroup requested,
                           OrderCancelRequest request) {
    }

    private void assertUntouched(Fixture f) {
        assertThat(reload(f.fresh()).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        assertThat(history(f.fresh())).hasSize(1);
        assertThat(reload(f.shipping()).getTrackingNumber()).isEqualTo("610020003000");
        assertThat(historyCount(f.shipping(), FulfillmentEventType.INVOICE_UPDATED)).isZero();
        assertThat(reload(f.requested()).getTrackingNumber()).isNull();
        assertThat(cancelRequestRepository.findById(f.request().getId()).orElseThrow().getStatus())
                .isEqualTo(CancelRequestStatus.PENDING);
        assertThat(refundTasks(f.fresh())).isEmpty();
        assertThat(refundTasks(f.requested())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_purchase_order_template", Integer.class)).isZero();
    }
}
