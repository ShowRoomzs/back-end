package showroomz.api.app.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소비자 앱 마이 「주문 현황」 4단계 — {@code GET /v1/user/me} 의 {@code orderStats}. 단계별 <b>주문</b> 수이고,
 * 항목 상태는 C10 주문 내역과 같은 규칙이다.
 */
@IntegrationTest
class UserOrderStatsIntegrationTest extends SellerOrderTestSupport {

    @Test
    @DisplayName("주문이 없으면 네 단계 모두 0")
    void emptyWhenNoOrders() throws Exception {
        me().andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStats.paid").value(0))
                .andExpect(jsonPath("$.orderStats.preparing").value(0))
                .andExpect(jsonPath("$.orderStats.shipping").value(0))
                .andExpect(jsonPath("$.orderStats.delivered").value(0));
    }

    @Test
    @DisplayName("단계별 주문 수 — 항목 2줄 주문도 1건 · 결제 전 · 취소 · 취소 요청 중은 세지 않는다")
    void countsOrdersPerStage() throws Exception {
        paidGroup();
        paidTwoItemGroup();                                    // 항목 2줄이어도 주문 1건
        preparingGroup();
        shippingGroup("100020009101");
        delivered(shippingGroup("100020009102"), LocalDateTime.now().withNano(0).minusDays(1));

        placeCardOrder(creamVariant, 1);                       // 결제 대기 — 세지 않는다
        cancel(paidGroup().getOrder().getId()).andExpect(status().isOk());   // 결제 후 취소 — 세지 않는다
        seedCancelRequest(preparingGroup());                   // 취소 요청 중 — 상품준비중에서 빠진다

        me().andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStats.paid").value(2))
                .andExpect(jsonPath("$.orderStats.preparing").value(1))
                .andExpect(jsonPath("$.orderStats.shipping").value(1))
                .andExpect(jsonPath("$.orderStats.delivered").value(1));
    }

    @Test
    @DisplayName("한 주문의 하위주문이 다른 단계면 각 단계에 한 번씩 센다")
    void orderSpanningStagesCountsInEach() throws Exception {
        OrderDeliveryGroup first = paidTwoItemGroup();
        OrderDeliveryGroup second = addSecondGroup(first);
        OrderProduct serum = itemOf(first, serumVariant);
        jdbc.update("UPDATE order_product SET delivery_group_id = ? WHERE order_product_id = ?",
                second.getId(), serum.getId());
        prepared(second);

        me().andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStats.paid").value(1))
                .andExpect(jsonPath("$.orderStats.preparing").value(1));
    }

    @Test
    @DisplayName("기간 제한이 없다 — 주문 내역 6개월 밖 주문도 진행 중이면 센다")
    void noPeriodLimit() throws Exception {
        OrderDeliveryGroup old = paidGroup();
        jdbc.update("UPDATE orders SET created_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusMonths(7), old.getOrder().getId());

        me().andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStats.paid").value(1));
    }

    @Test
    @DisplayName("남의 주문은 세지 않는다")
    void othersOrdersAreExcluded() throws Exception {
        paidGroup();
        var stranger = createConsumer("stranger", "박타인");
        String strangerToken = bearerToken(stranger.getUsername(), showroomz.api.app.auth.entity.RoleType.USER,
                stranger.getId());

        mockMvc.perform(get("/v1/user/me").header(HttpHeaders.AUTHORIZATION, strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStats.paid").value(0));
    }

    private ResultActions me() throws Exception {
        return mockMvc.perform(get("/v1/user/me").header(HttpHeaders.AUTHORIZATION, consumerToken));
    }
}
