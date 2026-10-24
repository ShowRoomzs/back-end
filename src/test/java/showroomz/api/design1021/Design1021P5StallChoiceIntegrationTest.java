package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.StallChoice;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 P5 — 추적 정지 소비자 선택(5-1 · 기-15 【잠정】) · 직권 취소 주체(5-2 · 기-16).
 */
@DisplayName("1021 P5 — 추적 정지 소비자 선택 · 브랜드 처리 · 직권 취소 주체")
class Design1021P5StallChoiceIntegrationTest extends SettlementTestSupport {

    @Test
    @DisplayName("추적 정지 판정 + 7일에 선택 개방 · 개방 전 409 · 환불 선택 1회 · 브랜드 [소비자 환불 처리] → 분실과 같은 취소 · PG 환불")
    void refundChoice() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup group = stalled(now);
        assertThat(group.getTrackingAlert()).isEqualTo(TrackingAlert.STALLED);
        assertThat(group.getStallChoiceOpenAt()).isCloseTo(now.plusDays(7), within(1, ChronoUnit.SECONDS));

        stallChoice(group).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.daysUntilOpen").value(7));
        choose(group, "REFUND").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STALL_CHOICE_NOT_OPEN"));
        sellerOrder(group).andExpect(jsonPath("$.stallChoice.state").value("WAITING"))
                .andExpect(jsonPath("$.stallChoice.canRefund").value(false));
        sellerPost("/v1/seller/orders/" + group.getId() + "/stall-refund", Map.of()).andExpect(status().isConflict());

        openNow(group);
        stallChoice(group).andExpect(jsonPath("$.state").value("OPEN"));
        choose(group, "REFUND").andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CHOSEN"))
                .andExpect(jsonPath("$.choice").value("REFUND"))
                .andExpect(jsonPath("$.choiceLabel").value("환불"));
        choose(group, "RESHIP").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STALL_CHOICE_ALREADY_MADE"));
        assertThat(fulfillmentEvents(group)).contains("STALL_CHOICE_MADE");

        sellerOrder(group).andExpect(jsonPath("$.stallChoice.choice").value("REFUND"))
                .andExpect(jsonPath("$.stallChoice.canRefund").value(true));
        sellerPost("/v1/seller/orders/" + group.getId() + "/stall-refund", Map.of()).andExpect(status().isOk());

        OrderDeliveryGroup refunded = reloadGroup(group);
        assertThat(refunded.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
        assertThat(refunded.getCancelType()).isEqualTo(OrderCancelType.LOST);
        assertThat(refunded.getStallChoice()).isEqualTo(StallChoice.REFUND);
        assertThat(refundTasks(group)).anySatisfy(task -> {
            assertThat(task.source()).isEqualTo(RefundTaskSource.STALL_REFUND_CHOSEN.name());
            assertThat(task.amount()).isEqualTo(CREAM_PRICE + refunded.getDeliveryFee());
        });
        adminGet("/v1/admin/orders/" + refunded.getOrder().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].stallChoice.choice").value("REFUND"));
    }

    @Test
    @DisplayName("재발송 선택 — 환불 처리 409 · 새 송장 등록 → 배지 해제 · 「추적 정지 후 재발송」 이력 · 선택은 남는다")
    void reshipChoice() throws Exception {
        OrderDeliveryGroup group = stalled(LocalDateTime.now());
        openNow(group);
        choose(group, "RESHIP").andExpect(status().isOk());

        sellerPost("/v1/seller/orders/" + group.getId() + "/stall-refund", Map.of()).andExpect(status().isConflict());
        updateShipment(group, "CJ", nextTrackingNumber()).andExpect(status().isOk());

        OrderDeliveryGroup reshipped = reloadGroup(group);
        assertThat(reshipped.getTrackingAlert()).isNull();
        assertThat(reshipped.getStallChoice()).isEqualTo(StallChoice.RESHIP);
        assertThat(fulfillmentEvents(group)).contains("RESHIPPED_AFTER_STALL");
        stallChoice(group).andExpect(jsonPath("$.state").value("CHOSEN"));
    }

    @Test
    @DisplayName("선택 전 추적이 다시 움직이면 배지와 함께 선택 창이 닫힌다 · 배송 조회 `stall`")
    void movementClosesWindow() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup group = stalled(now);
        appTracking(group).andExpect(jsonPath("$.stall.state").value("WAITING"));

        OrderDeliveryGroup moved = track(group, new TrackSnapshot(now.minusHours(1), null, false, false), now);
        assertThat(moved.getTrackingAlert()).isNull();
        assertThat(moved.getStallChoiceOpenAt()).isNull();
        stallChoice(group).andExpect(jsonPath("$.state").value("NONE"));
        appTracking(group).andExpect(jsonPath("$.stall").doesNotExist());
    }

    @Test
    @DisplayName("기-16 — 브랜드 직권 취소는 cancel_actor_type = SELLER · 라벨 「직권 취소」")
    void directCancelActor() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        directCancel(List.of(group.getId()), "SOLD_OUT", "품절").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT cancel_actor_type FROM order_delivery_group WHERE delivery_group_id = ?",
                String.class, group.getId())).isEqualTo("SELLER");
        assertThat(OrderCancelType.SELLER_DIRECT.getLabel()).isEqualTo("직권 취소");
    }

    // ------------------------------------------------------------------ helpers

    /** 배송중 → 마지막 스캔 8일 전 → 추적 정지 판정. */
    private OrderDeliveryGroup stalled(LocalDateTime now) {
        OrderDeliveryGroup group = shippingGroup(nextTrackingNumber());
        return track(group, new TrackSnapshot(now.minusDays(8), null, false, false), now);
    }

    private void openNow(OrderDeliveryGroup group) {
        jdbc.update("UPDATE order_delivery_group SET stall_choice_open_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusMinutes(1), group.getId());
    }

    private ResultActions stallChoice(OrderDeliveryGroup group) throws Exception {
        return mockMvc.perform(get("/v1/user/orders/groups/" + group.getId() + "/stall-choice")
                .header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions choose(OrderDeliveryGroup group, String choice) throws Exception {
        return mockMvc.perform(post("/v1/user/orders/groups/" + group.getId() + "/stall-choice")
                .header(HttpHeaders.AUTHORIZATION, consumerToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("choice", choice))));
    }

    private ResultActions appTracking(OrderDeliveryGroup group) throws Exception {
        Long itemId = itemsOf(group).get(0).getId();
        return mockMvc.perform(get("/v1/user/orders/" + group.getOrder().getId() + "/items/" + itemId + "/tracking")
                .header(HttpHeaders.AUTHORIZATION, consumerToken)).andExpect(status().isOk());
    }
}
