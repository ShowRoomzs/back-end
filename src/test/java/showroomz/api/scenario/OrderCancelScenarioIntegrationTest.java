package showroomz.api.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.support.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오 3절 — 취소 3경로(E2E-A 소비자 취소 · E2E-B 취소 요청 승인/거부 · E2E-C 브랜드 직권 취소).
 *
 * <p>세 경로는 「누가 · 언제 · 환불을 누가 집행하나」가 다르다. 각 경로에서 <b>그룹 상태 · 항목 상태 · 재고 · 환불 큐 ·
 * 탭 이동 · 소비자 앱 버튼</b>이 같은 답을 내는지를 한 흐름으로 본다.
 */
@IntegrationTest
@DisplayName("[시나리오 E2E-A·B·C] 취소 3경로")
class OrderCancelScenarioIntegrationTest extends OrderFlowTestSupport {

    // ================================================================== E2E-A

    @Nested
    @DisplayName("E2E-A 소비자 취소 — 준비 시작 전")
    class ConsumerCancel {

        @Test
        @DisplayName("[A-01·A-02] PG 자동 취소 — 그룹 CANCELLED(CONSUMER) · 재고 원복 · 환불 큐 없음 · 취소 탭 「소비자 취소」")
        void cancelBeforePreparation() throws Exception {
            Purchase purchase = purchase(creamVariant, 1);
            int stockBefore = stockOf(creamVariant);

            cancel(purchase.orderId()).andExpect(status().isOk());

            assertThat(order(purchase.orderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(payment(purchase.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(fake.cancelCalls()).containsExactly(purchase.paymentId());
            OrderDeliveryGroup group = reloadGroup(purchase.group());
            assertThat(group.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
            assertThat(group.getCancelType()).isEqualTo(OrderCancelType.CONSUMER);
            assertThat(group.getStatusAtCancel()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(itemsOf(group)).allSatisfy(item -> {
                assertThat(item.getStatus()).isEqualTo(OrderProductStatus.CANCELLED);
                assertThat(item.getCancelType()).isEqualTo(OrderCancelType.CONSUMER);
            });
            assertThat(stockOf(creamVariant)).isEqualTo(stockBefore + 1);
            // 환불은 PG 가 이미 했다 — 운영자 큐에 쌓이면 이중 환불이다.
            assertThat(refundTasks(group)).isEmpty();
            assertThat(fulfillmentEvents(group)).containsExactly("CANCELLED_BY_CONSUMER", "PAID");

            sellerOrders("tab=CANCELLED")
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].cancelTypeLabel").value("소비자 취소 · 준비 시작 전"));
            sellerOrders("tab=NEW").andExpect(jsonPath("$.content.length()").value(0));
            sellerSummary()
                    .andExpect(jsonPath("$.actionBar.prepareStart").value(0))
                    .andExpect(jsonPath("$.tabCounts.NEW").value(0))
                    .andExpect(jsonPath("$.tabCounts.CANCELLED").value(1));
            appOrder(purchase.orderId())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancellable").value(false));
        }

        @Test
        @DisplayName("[A-03] 준비 시작 후 앱 취소 — 버튼이 닫히고 API 도 409 · 결제·재고·하위주문 불변 · PG 호출 없음")
        void cancelAfterPreparationIsBlocked() throws Exception {
            Purchase purchase = purchase(creamVariant, 1);
            appOrder(purchase.orderId()).andExpect(jsonPath("$.cancellable").value(true));
            preparing(purchase.group());
            int stockBefore = stockOf(creamVariant);

            appOrder(purchase.orderId()).andExpect(jsonPath("$.cancellable").value(false));
            cancel(purchase.orderId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_CANCEL_WINDOW_CLOSED"));

            assertThat(payment(purchase.paymentId()).getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(paymentCancelRepository.count()).isZero();
            assertThat(fake.cancelCalls()).isEmpty();
            assertThat(stockOf(creamVariant)).isEqualTo(stockBefore);
            assertThat(reloadGroup(purchase.group()).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        }
    }

    // ================================================================== E2E-B

    @Nested
    @DisplayName("E2E-B 취소 요청 → 승인 / 거부 — 준비 시작 후")
    class CancelRequestFlow {

        @Test
        @DisplayName("[B-01~B-05] 일부 항목 요청 — 취소 요청 탭 이동 · 선처리 가드 · 요청 항목만 승인 · 원래 탭 복귀 · 남은 항목 발송")
        void partialRequestApprovedThenRestShips() throws Exception {
            OrderDeliveryGroup group = preparing(paidGroupWithTwoItemsAndFee());
            OrderProduct creamItem = itemOf(group, cream);
            OrderCancelRequest request = seedCancelRequest(group, List.of(creamItem));

            // [B-01] 이행 상태는 그대로 PREPARING 인데 작업 큐에서 빠지고 취소 요청 탭으로 간다.
            assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            sellerOrders("tab=PREPARING").andExpect(jsonPath("$.content.length()").value(0));
            sellerOrders("tab=CANCEL_REQUESTED")
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].status").value("PREPARING"))
                    .andExpect(jsonPath("$.content[0].overlays.cancelRequested").value(true))
                    .andExpect(jsonPath("$.content[0].cancelRequest.reasonLabel").value("단순 변심"))
                    .andExpect(jsonPath("$.content[0].cancelRequest.summary").value("2건 중 1건 요청 · 남은 1건 발송 대기"));
            sellerSummary()
                    .andExpect(jsonPath("$.tabCounts.CANCEL_REQUESTED").value(1))
                    .andExpect(jsonPath("$.tabCounts.PREPARING").value(0))
                    .andExpect(jsonPath("$.actionBar.invoiceRegister").value(0));

            // [B-03] 판단은 상세에서만 — 근거(요청 항목 · 환불 예정 · 남은 항목)와 가능한 액션.
            sellerOrder(group)
                    .andExpect(jsonPath("$.cancelRequest.cancelRequestId").value(request.getId()))
                    .andExpect(jsonPath("$.cancelRequest.statusAtRequestLabel").value("상품준비중"))
                    .andExpect(jsonPath("$.cancelRequest.items.length()").value(1))
                    .andExpect(jsonPath("$.cancelRequest.totalRefundAmount").value(CREAM_PRICE))
                    .andExpect(jsonPath("$.cancelRequest.remainingItemCount").value(1))
                    .andExpect(jsonPath("$.amounts.cancelRequestedAmount").value(CREAM_PRICE))
                    .andExpect(jsonPath("$.actions.canDecideCancelRequest").value(true))
                    .andExpect(jsonPath("$.actions.canRegisterInvoice").value(false))
                    .andExpect(jsonPath("$.actions.canCancelDirectly").value(false));

            // [B-02] 요청이 걸린 동안 다른 액션은 선처리 요구로 막힌다 — 상태·환불 큐 불변.
            registerShipment(group, "CJ", "100020003000")
                    .andExpect(jsonPath("$.skipped[0].code").value("CANCEL_REQUEST_PENDING_EXISTS"));
            directCancel(List.of(group.getId()), "SOLD_OUT", "품절")
                    .andExpect(jsonPath("$.skipped[0].code").value("CANCEL_REQUEST_PENDING_EXISTS"));
            assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(refundTasks(group)).isEmpty();

            // [B-04] 승인 — 요청 항목만 취소 · 재고는 그 항목만 · 그룹은 원래 탭으로 · 부분 취소는 배송비 재계산 없음.
            int creamStock = stockOf(creamVariant);
            int serumStock = stockOf(serumVariant);
            approveRequest(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PREPARING"))
                    .andExpect(jsonPath("$.cancelRequest").isEmpty())
                    .andExpect(jsonPath("$.amounts.cancelledAmount").value(CREAM_PRICE));
            assertThat(itemOf(group, cream).getStatus()).isEqualTo(OrderProductStatus.CANCELLED);
            assertThat(itemOf(group, cream).getCancelType()).isEqualTo(OrderCancelType.REQUEST_APPROVED);
            assertThat(itemOf(group, serum).getStatus()).isEqualTo(OrderProductStatus.PAID);
            assertThat(stockOf(creamVariant)).isEqualTo(creamStock + 1);
            assertThat(stockOf(serumVariant)).isEqualTo(serumStock);
            assertThat(refundTasks(group)).containsExactly(new RefundTask("CANCEL_REQUEST_APPROVED", CREAM_PRICE, "DONE"));
            sellerOrders("tab=PREPARING").andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()));
            sellerOrders("tab=CANCEL_REQUESTED").andExpect(jsonPath("$.content.length()").value(0));

            // [B-05] 재승인은 이미 처리됨 — 환불 큐·이력 중복 없음.
            approveRequest(request)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_ALREADY_DECIDED"));
            assertThat(refundTasks(group)).hasSize(1);
            assertThat(fulfillmentEvents(group)).filteredOn("CANCEL_REQUEST_APPROVED"::equals).hasSize(1);

            // 남은 항목은 발송된다.
            registerShipment(group, "CJ", "100020003000").andExpect(jsonPath("$.succeeded").value(1));
            assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
        }

        @Test
        @DisplayName("[B-06] 전 항목 요청 승인 — 그때만 그룹이 취소 탭으로 · 환불 예정액은 배송비까지 전액")
        void fullRequestApproved() throws Exception {
            OrderDeliveryGroup group = preparing(paidGroupWithTwoItemsAndFee());
            OrderCancelRequest request = seedCancelRequest(group, itemsOf(group));

            approveRequest(request).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

            OrderDeliveryGroup cancelled = reloadGroup(group);
            assertThat(cancelled.getCancelType()).isEqualTo(OrderCancelType.REQUEST_APPROVED);
            assertThat(cancelled.getStatusAtCancel()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(refundTasks(group)).containsExactly(
                    new RefundTask("CANCEL_REQUEST_APPROVED", CREAM_PRICE + SERUM_PRICE + DELIVERY_FEE, "DONE"));
            sellerOrders("tab=CANCELLED")
                    .andExpect(jsonPath("$.content[0].cancelTypeLabel").value("취소 요청 승인 · 브랜드 승인"));
        }

        @Test
        @DisplayName("[B-07] 거부 — 사유 필수 · 소비자 전달용 사유 보존 · 원래 탭 복귀 · 이후 발송 정상")
        void rejectedRequestShipsNormally() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            OrderCancelRequest request = seedCancelRequest(group, itemsOf(group));

            rejectRequest(request, "  ").andExpect(status().isBadRequest());
            sellerOrders("tab=CANCEL_REQUESTED").andExpect(jsonPath("$.content.length()").value(1));

            String reason = "이미 포장이 끝나 오늘 발송됩니다. 수령 후 반품으로 진행해 주세요.";
            rejectRequest(request, reason)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PREPARING"))
                    .andExpect(jsonPath("$.cancelRequest").isEmpty());
            assertThat(jdbc.queryForObject("SELECT reject_reason FROM order_cancel_request WHERE cancel_request_id = ?",
                    String.class, request.getId())).isEqualTo(reason);
            assertThat(fulfillmentHistoryRepository.findByDeliveryGroupId(group.getId()))
                    .filteredOn(h -> h.getEventType().name().equals("CANCEL_REQUEST_REJECTED"))
                    .singleElement()
                    // 구 FE 처럼 사유 코드 없이 상세만 보내면 「기타」로 받는다(1009 기획 수정본 3-3).
                    .satisfies(h -> assertThat(h.getDetail()).isEqualTo("기타 · " + reason));
            assertThat(refundTasks(group)).isEmpty();

            sellerOrders("tab=PREPARING").andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()));
            registerShipment(group, "CJ", "200030004000").andExpect(jsonPath("$.succeeded").value(1));
        }
    }

    // ================================================================== E2E-C

    @Nested
    @DisplayName("E2E-C 브랜드 직권 취소")
    class SellerDirectCancel {

        @Test
        @DisplayName("[C-01·C-04] 신규에서 직권 취소 — 전 항목 취소 · 재고 원복 · 환불 큐(전액+배송비) · 사유·설명 보존 · 취소 탭 표기")
        void directCancelFromNew() throws Exception {
            Purchase purchase = purchase(creamVariant, 1);
            OrderDeliveryGroup group = purchase.group();
            int stockBefore = stockOf(creamVariant);
            String message = "입고가 지연되어 발송이 어렵습니다. 결제는 전액 환불됩니다.";

            directCancel(List.of(group.getId()), "SOLD_OUT", message)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.succeeded").value(1));

            OrderDeliveryGroup cancelled = reloadGroup(group);
            assertThat(cancelled.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
            assertThat(cancelled.getCancelType()).isEqualTo(OrderCancelType.SELLER_DIRECT);
            assertThat(cancelled.getStatusAtCancel()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(itemsOf(group)).allSatisfy(item ->
                    assertThat(item.getCancelType()).isEqualTo(OrderCancelType.SELLER_DIRECT));
            assertThat(stockOf(creamVariant)).isEqualTo(stockBefore + 1);
            assertThat(refundTasks(group)).containsExactly(
                    new RefundTask("SELLER_DIRECT_CANCEL", CREAM_PRICE + DELIVERY_FEE, "DONE"));
            // 환불은 PG 즉시 자동(1009 기획 수정본 2절) — 브랜드가 아니라 시스템이 부분 취소로 돌려준다. 전액이라 결제가 닫힌다.
            assertThat(fake.cancelCalls()).isEmpty();
            assertThat(fake.partialCancelCalls()).containsExactly(purchase.paymentId() + ":" + (CREAM_PRICE + DELIVERY_FEE));
            assertThat(payment(purchase.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);

            sellerOrder(group)
                    .andExpect(jsonPath("$.timeline.cancelTypeLabel").value("브랜드 직권 취소"))
                    .andExpect(jsonPath("$.timeline.cancelReasonLabel").value("품절"))
                    .andExpect(jsonPath("$.timeline.cancelReasonDetail").value(message));
            sellerOrders("tab=CANCELLED").andExpect(jsonPath("$.content[0].cancelTypeLabel").value("브랜드 직권 취소"));
            appOrder(purchase.orderId()).andExpect(jsonPath("$.cancellable").value(false));
        }

        @Test
        @DisplayName("[C-02] 상품준비중에서 직권 취소 — 취소 당시 상태가 PREPARING 으로 남는다(§34-13 #1 확정 대비)")
        void directCancelFromPreparing() throws Exception {
            OrderDeliveryGroup group = preparingGroup();

            directCancel(List.of(group.getId()), "DEFECT", "입고 검수에서 하자가 발견되었습니다.")
                    .andExpect(jsonPath("$.succeeded").value(1));

            assertThat(reloadGroup(group).getStatusAtCancel()).isEqualTo(FulfillmentStatus.PREPARING);
        }

        @Test
        @DisplayName("[C-03] 발송 후에는 직권 취소 불가 — 섞어 보내면 배송중 행만 빠지고 나머지는 취소된다")
        void shippedGroupIsSkipped() throws Exception {
            OrderDeliveryGroup shipped = shippingGroup("300040005000");
            OrderDeliveryGroup fresh = paidGroup();

            directCancel(List.of(shipped.getId(), fresh.getId()), "SOLD_OUT", "품절로 발송이 어렵습니다.")
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped.length()").value(1))
                    .andExpect(jsonPath("$.skipped[0].deliveryGroupId").value(shipped.getId()))
                    .andExpect(jsonPath("$.skipped[0].code").value("ORDER_STATE_CHANGED"));

            assertThat(reloadGroup(shipped).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(refundTasks(shipped)).isEmpty();
            assertThat(reloadGroup(fresh).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
        }
    }

    // ------------------------------------------------------------------ 픽스처

    /** 크림 + 세럼 — 무료배송 기준을 올려 배송비 3,000이 붙게 한다(부분/전체 취소의 배송비 차이를 보려고). */
    private OrderDeliveryGroup paidGroupWithTwoItemsAndFee() {
        jdbc.update("UPDATE market SET free_shipping_threshold = ? WHERE market_id = ?", 100_000, brand.marketId());
        OrderDeliveryGroup group = paidGroupWithTwoItems();
        assertThat(group.getDeliveryFee()).isEqualTo(DELIVERY_FEE);
        return group;
    }
}
