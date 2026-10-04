package showroomz.api.seller.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 주문 관리(34 설계서) — PAID 훅(5-1) · 작업 큐 · 송장 · 취소 3경로 · 구매확정의 통합 검증.
 * 결제는 FakePaymentGateway 로 실제 PAID 전이를 태운다 — 하위주문의 탄생이 운영 경로 그대로다.
 *
 * <p>대표 흐름만 둔다. 기능별 세부 케이스는 {@code SellerOrderQueryIntegrationTest} · {@code SellerOrderPreparationIntegrationTest} ·
 * {@code SellerOrderShipmentIntegrationTest} · {@code SellerOrderCancelIntegrationTest} · {@code OrderFulfillmentTrackingIntegrationTest}가 맡는다.
 */
@IntegrationTest
class SellerOrderIntegrationTest extends SellerOrderTestSupport {

    // ------------------------------------------------------------------ PAID 훅(5-1)

    @Test
    @DisplayName("결제 완료가 하위주문을 만든다 — NEW 전이 · 하위주문번호 · 발송기한 스냅샷 · 이력")
    void paidActivatesDeliveryGroup() throws Exception {
        OrderDeliveryGroup group = paidGroup();

        assertThat(group.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        assertThat(group.getSubOrderNumber()).isEqualTo(group.getOrder().getOrderNumber() + "-01");
        assertThat(group.getShipDueAt())
                .isEqualTo(group.getOrder().getPaidAt().plusDays(SHIPPING_LEAD_DAYS));

        sellerGet(SELLER_ORDERS + "?tab=NEW")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()))
                .andExpect(jsonPath("$.content[0].status").value("NEW"))
                .andExpect(jsonPath("$.content[0].statusTone").value("WARNING"))
                .andExpect(jsonPath("$.content[0].recipientName").value("김수민"));
        sellerGet(SELLER_ORDERS + "/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionBar.prepareStart").value(1))
                .andExpect(jsonPath("$.actionBar.incomingCheck").isEmpty())
                .andExpect(jsonPath("$.tabCounts.NEW").value(1));
    }

    // ------------------------------------------------------------------ 준비 시작 · 소비자 취소권(5-2)

    @Test
    @DisplayName("준비 시작은 소비자 단순 취소권을 닫는다 — 이후 앱 취소는 409 ORDER_CANCEL_WINDOW_CLOSED")
    void prepareStartClosesConsumerCancelWindow() throws Exception {
        OrderDeliveryGroup group = paidGroup();

        sellerPost(SELLER_ORDERS + "/prepare-start",
                Map.of("deliveryGroupIds", List.of(group.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1));
        assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);

        cancel(group.getOrder().getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CANCEL_WINDOW_CLOSED"));
        // 두 번째 준비 시작은 0행 — 부분 성공 응답으로 떨어진다(전이표 #2).
        sellerPost(SELLER_ORDERS + "/prepare-start",
                Map.of("deliveryGroupIds", List.of(group.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.skipped[0].code").value("ORDER_STATE_CHANGED"));
    }

    @Test
    @DisplayName("준비 시작 전 소비자 취소는 그대로 된다 — 그룹 CANCELLED(CONSUMER) · 재고 원복")
    void consumerCancelBeforePreparation() throws Exception {
        OrderDeliveryGroup group = paidGroup();
        int stockBefore = stockOf(creamVariant);

        cancel(group.getOrder().getId()).andExpect(status().isOk());

        OrderDeliveryGroup cancelled = reload(group);
        assertThat(cancelled.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
        assertThat(cancelled.getCancelType().name()).isEqualTo("CONSUMER");
        assertThat(stockOf(creamVariant)).isEqualTo(stockBefore + 1);
    }

    // ------------------------------------------------------------------ 송장(3-2)

    @Test
    @DisplayName("송장 등록이 배송중으로 올린다 — 하이픈·공백은 정제되어 숫자만 남는다")
    void registerInvoice() throws Exception {
        OrderDeliveryGroup group = preparingGroup();

        sellerPost(SELLER_ORDERS + "/shipments", Map.of("rows", List.of(
                Map.of("deliveryGroupId", group.getId(), "carrier", "CJ", "trackingNumber", "1234-5678-9012"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1));
        OrderDeliveryGroup shipped = reload(group);
        assertThat(shipped.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
        assertThat(shipped.getTrackingNumber()).isEqualTo("123456789012");
        assertThat(shipped.getShippedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 송장번호는 다른 하위주문에 등록되지 않는다 — INVOICE_DUPLICATE · 겹치는 주문 지목")
    void duplicateInvoiceRejected() throws Exception {
        OrderDeliveryGroup first = preparingGroup();
        OrderDeliveryGroup second = preparingGroup();

        sellerPost(SELLER_ORDERS + "/shipments", Map.of("rows", List.of(
                Map.of("deliveryGroupId", first.getId(), "carrier", "CJ", "trackingNumber", "111122223333"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerPost(SELLER_ORDERS + "/shipments", Map.of("rows", List.of(
                Map.of("deliveryGroupId", second.getId(), "carrier", "CJ", "trackingNumber", "1111 2222 3333"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.skipped[0].code").value("INVOICE_DUPLICATE"));
        assertThat(reload(second).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
    }

    @Test
    @DisplayName("송장 수정은 배송중에서만 — 이력에 구→신이 남고 shipped_at 은 유지된다")
    void updateInvoiceKeepsShippedAt() throws Exception {
        OrderDeliveryGroup group = shippingGroup("444455556666");
        LocalDateTime shippedAt = reload(group).getShippedAt();

        mockMvc.perform(patch(SELLER_ORDERS + "/" + group.getId() + "/shipment")
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("carrier", "HANJIN", "trackingNumber", "777788889999"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline.trackingNumber").value("777788889999"));

        OrderDeliveryGroup updated = reload(group);
        assertThat(updated.getShippedAt()).isEqualTo(shippedAt);
        assertThat(updated.getCarrier().name()).isEqualTo("HANJIN");
    }

    // ------------------------------------------------------------------ 직권 취소 · 취소 요청(3-5)

    @Test
    @DisplayName("직권 취소 — 전 항목 취소 · 재고 원복 · 환불 큐 적재 · 취소 당시 상태 기록")
    void directCancel() throws Exception {
        OrderDeliveryGroup group = paidGroup();
        int stockBefore = stockOf(creamVariant);

        sellerPost(SELLER_ORDERS + "/cancel", Map.of(
                "deliveryGroupIds", List.of(group.getId()),
                "reasonCode", "SOLD_OUT",
                "consumerMessage", "재고 소진으로 발송이 어렵습니다. 죄송합니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1));

        OrderDeliveryGroup cancelled = reload(group);
        assertThat(cancelled.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
        assertThat(cancelled.getCancelType().name()).isEqualTo("SELLER_DIRECT");
        assertThat(cancelled.getStatusAtCancel()).isEqualTo(FulfillmentStatus.NEW);
        assertThat(stockOf(creamVariant)).isEqualTo(stockBefore + 1);
        Integer refundTasks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_refund_task WHERE delivery_group_id = ? AND status = 'PENDING'",
                Integer.class, group.getId());
        assertThat(refundTasks).isEqualTo(1);
    }

    @Test
    @DisplayName("취소 요청 승인 — 요청 항목만 취소 · 전 항목이면 그룹도 취소 탭 · 환불 큐")
    void approveCancelRequest() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        OrderCancelRequest request = seedCancelRequest(group);

        // 요청이 걸린 하위주문은 작업 큐에서 빠지고 취소 요청 탭에 들어간다(0-2 오버레이).
        sellerGet(SELLER_ORDERS + "?tab=PREPARING")
                .andExpect(jsonPath("$.content.length()").value(0));
        sellerGet(SELLER_ORDERS + "?tab=CANCEL_REQUESTED")
                .andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()))
                .andExpect(jsonPath("$.content[0].overlays.cancelRequested").value(true));

        sellerPost(SELLER_ORDERS + "/cancel-requests/" + request.getId() + "/approve", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(reload(group).getCancelType().name()).isEqualTo("REQUEST_APPROVED");
        // 두 번째 승인은 이미 처리됨 — 조건부 UPDATE 0행.
        sellerPost(SELLER_ORDERS + "/cancel-requests/" + request.getId() + "/approve", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("취소 요청 거부 — 사유 필수 · 그룹 상태는 바뀐 적이 없어 원래 탭으로 복귀한다")
    void rejectCancelRequestReturnsToOriginalTab() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        OrderCancelRequest request = seedCancelRequest(group);

        sellerPost(SELLER_ORDERS + "/cancel-requests/" + request.getId() + "/reject",
                Map.of("reason", "이미 포장이 완료되어 발송 예정입니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));

        sellerGet(SELLER_ORDERS + "?tab=PREPARING")
                .andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()));
        sellerGet(SELLER_ORDERS + "?tab=CANCEL_REQUESTED")
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("취소 요청이 걸린 하위주문은 준비 시작·직권 취소가 선처리 요구로 막힌다")
    void pendingRequestBlocksActions() throws Exception {
        OrderDeliveryGroup group = paidGroup();
        seedCancelRequest(group);

        sellerPost(SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of(group.getId())))
                .andExpect(jsonPath("$.skipped[0].code").value("CANCEL_REQUEST_PENDING_EXISTS"));
        sellerPost(SELLER_ORDERS + "/cancel", Map.of(
                "deliveryGroupIds", List.of(group.getId()),
                "reasonCode", "SOLD_OUT", "consumerMessage", "품절"))
                .andExpect(jsonPath("$.skipped[0].code").value("CANCEL_REQUEST_PENDING_EXISTS"));
    }

    // ------------------------------------------------------------------ 구매확정(3-4)

    @Test
    @DisplayName("구매확정 — 배송완료 + 7일 자동 · 항목도 PURCHASE_CONFIRMED · 반송중은 대상이 아니다")
    void purchaseConfirm() throws Exception {
        OrderDeliveryGroup group = shippingGroup("555566667777");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        transactionTemplate.executeWithoutResult(tx -> deliveryGroupRepository.markDeliveredByTracker(
                group.getId(), group.getCarrier(), group.getTrackingNumber(), now.minusDays(8)));

        boolean confirmed = fulfillmentService.confirmPurchase(group.getId(), now, now.minusDays(7));

        assertThat(confirmed).isTrue();
        OrderDeliveryGroup result = reload(group);
        assertThat(result.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
        List<OrderProduct> items = orderProductRepository.findByDeliveryGroupIds(List.of(group.getId()));
        assertThat(items).allMatch(item -> item.getStatus() == OrderProductStatus.PURCHASE_CONFIRMED);
        // 두 번째 호출은 0행 — 멱등.
        assertThat(fulfillmentService.confirmPurchase(group.getId(), now, now.minusDays(7))).isFalse();
    }
}
