package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.OrderStallChoiceService;
import showroomz.domain.order.type.StallChoice;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.api.app.auth.entity.RoleType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 5-1 · 5-2 — 추적 정지 소비자 선택의 표시 · 권한 · 알림 경계와 직권 취소 주체(운영자 대행).
 */
@DisplayName("1021 5절 추적 정지 상세 — 개방 전 표시 · 브랜드 알림 · 남의 주문 · 입력 검증 · 배송완료 뒤 · 운영자 대행 직권 취소")
class Design1021StallChoiceDetailIntegrationTest extends SettlementTestSupport {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OrderStallChoiceService stallChoiceService;

    @Test
    @DisplayName("개방 전 — 소비자 · 브랜드 · 어드민이 같은 「선택 개방까지 N일」 · 추적 정지 경과 일수 · 선택 없음")
    void waitingStateIsSameOnAllSurfaces() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup group = stalled(now);

        stallChoice(group, consumerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.daysUntilOpen").value(7))
                .andExpect(jsonPath("$.daysStalled").value(8))
                .andExpect(jsonPath("$.choice").doesNotExist());
        sellerOrder(group).andExpect(jsonPath("$.stallChoice.state").value("WAITING"))
                .andExpect(jsonPath("$.stallChoice.daysUntilOpen").value(7));
        adminGet(ADMIN_ORDERS + "/" + group.getOrder().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].stallChoice.state").value("WAITING"))
                .andExpect(jsonPath("$.groups[0].stallChoice.daysUntilOpen").value(7))
                .andExpect(jsonPath("$.groups[0].shipping.trackingAlert").value("STALLED"));
    }

    @Test
    @DisplayName("선택하면 브랜드 알림함에 「소비자가 환불을 선택했어요」 · 이력 detail = 선택 · 어드민에도 선택이 보인다")
    void choiceNotifiesBrand() throws Exception {
        OrderDeliveryGroup group = stalled(LocalDateTime.now());
        openNow(group);

        choose(group, "REFUND", consumerToken).andExpect(status().isOk());

        List<Notification> notices = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.ORDER_STALL_CHOICE_MADE).toList();
        assertThat(notices).singleElement().satisfies(n -> {
            assertThat(n.getRecipientType()).isEqualTo(NotificationRecipientType.SELLER);
            assertThat(n.getTitle()).contains("환불");
        });
        assertThat(jdbc.queryForObject("SELECT detail FROM order_fulfillment_history WHERE delivery_group_id = ? "
                + "AND event_type = 'STALL_CHOICE_MADE'", String.class, group.getId())).isEqualTo("환불");
        adminGet(ADMIN_ORDERS + "/" + group.getOrder().getId())
                .andExpect(jsonPath("$.groups[0].stallChoice.state").value("CHOSEN"))
                .andExpect(jsonPath("$.groups[0].stallChoice.choiceLabel").value("환불"));
    }

    @Test
    @DisplayName("남의 주문은 없는 것과 같다(404) · choice 누락 400 · 추적 정지가 아닌 주문은 NONE · 선택하면 409")
    void ownershipAndValidation() throws Exception {
        OrderDeliveryGroup group = stalled(LocalDateTime.now());
        openNow(group);
        Users stranger = createConsumer("stranger", "남");
        String strangerToken = bearerToken(stranger.getUsername(), RoleType.USER, stranger.getId());

        stallChoice(group, strangerToken).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));
        choose(group, "REFUND", strangerToken).andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/user/orders/groups/" + group.getId() + "/stall-choice")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(reloadGroup(group).getStallChoice()).isNull();

        OrderDeliveryGroup moving = shippingGroup(nextTrackingNumber());
        stallChoice(moving, consumerToken).andExpect(jsonPath("$.state").value("NONE"));
        choose(moving, "RESHIP", consumerToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STALL_CHOICE_NOT_OPEN"));
    }

    @Test
    @DisplayName("선택 뒤 배송완료가 잡히면 환불 처리는 409(배송중 아님) · 선택 기록은 남는다")
    void deliveredAfterChoiceBlocksRefund() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup group = stalled(now);
        openNow(group);
        choose(group, "REFUND", consumerToken).andExpect(status().isOk());

        track(group, new TrackSnapshot(now.minusHours(1), now.minusHours(1), false, false), now);

        sellerPost("/v1/seller/orders/" + group.getId() + "/stall-refund", Map.of()).andExpect(status().isConflict());
        assertThat(reloadGroup(group).getStallChoice()).isEqualTo(StallChoice.REFUND);
        sellerOrder(group).andExpect(jsonPath("$.stallChoice.canRefund").value(false));
    }

    @Test
    @DisplayName("다른 브랜드의 주문은 환불 처리할 수 없다 — 도메인도 마켓을 본다")
    void refundByChoiceChecksMarket() {
        OrderDeliveryGroup group = stalled(LocalDateTime.now());
        openNow(group);
        inTransaction(() -> stallChoiceService.choose(consumer.getId(), group.getId(), StallChoice.REFUND,
                LocalDateTime.now()));

        assertThatThrownBy(() -> inTransaction(() -> {
            stallChoiceService.refundByChoice(group.getId(), brand.marketId() + 999, null, null, LocalDateTime.now());
            return null;
        })).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_GROUP_NOT_FOUND));
    }

    @Test
    @DisplayName("기-16 — 운영자 대행 직권 취소(하자 · 위해성은 대행 조건 없이)는 cancel_actor_type = ADMIN · 라벨은 같은 「직권 취소」")
    void adminDirectCancelActor() throws Exception {
        OrderDeliveryGroup group = preparingGroup();

        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/cancel",
                Map.of("reasonCode", "DEFECT", "consumerMessage", "위해성 확인으로 취소합니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].cancel.cancelTypeLabel").value("직권 취소"));

        assertThat(jdbc.queryForObject("SELECT cancel_actor_type FROM order_delivery_group WHERE delivery_group_id = ?",
                String.class, group.getId())).isEqualTo("ADMIN");
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

    private ResultActions stallChoice(OrderDeliveryGroup group, String token) throws Exception {
        return mockMvc.perform(get("/v1/user/orders/groups/" + group.getId() + "/stall-choice")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions choose(OrderDeliveryGroup group, String choice, String token) throws Exception {
        return mockMvc.perform(post("/v1/user/orders/groups/" + group.getId() + "/stall-choice")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("choice", choice))));
    }
}
