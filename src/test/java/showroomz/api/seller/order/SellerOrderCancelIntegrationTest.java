package showroomz.api.seller.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultMatcher;
import showroomz.api.app.order.service.CheckoutService;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderFulfillmentHistory;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.order.type.SellerCancelReason;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 취소 3경로(34 설계서 3-5) — ① 소비자 취소(준비 시작 전 · PG 자동) ② 취소 요청 → 브랜드 승인/거부 ③ 브랜드 직권 취소.
 * 환불은 브랜드가 집행하지 않는다 — ②③은 환불 큐(order_refund_task) 적재까지만 간다(1-9). 금액 규칙은 1-10:
 * 부분 취소는 항목 합(배송비 재계산 없음) · 전체 취소는 항목 합 + 그룹 배송비 전액.
 */
@IntegrationTest
class SellerOrderCancelIntegrationTest extends SellerOrderTestSupport {

    private static final String SOLD_OUT_MESSAGE = "재고 소진으로 발송이 어렵습니다. 죄송합니다.";

    @Autowired private CheckoutService checkoutService;

    @Nested
    @DisplayName("① 소비자 취소 — 준비 시작 전")
    class ConsumerCancel {

        @Test
        @DisplayName("그룹 CANCELLED(CONSUMER) · 항목 취소 메타 · 이력 · 재고 원복 — PG 가 직접 환불하므로 환불 큐에 쌓이지 않는다")
        void consumerCancelLeavesFacts() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            int stockBefore = stockOf(creamVariant);

            cancel(group.getOrder().getId()).andExpect(status().isOk());

            OrderDeliveryGroup cancelled = reload(group);
            assertThat(cancelled.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
            assertThat(cancelled.getCancelType()).isEqualTo(OrderCancelType.CONSUMER);
            assertThat(cancelled.getStatusAtCancel()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(cancelled.getCancelledAt()).isNotNull();
            assertThat(items(group)).allSatisfy(item -> {
                assertThat(item.getStatus()).isEqualTo(OrderProductStatus.CANCELLED);
                assertThat(item.getCancelType()).isEqualTo(OrderCancelType.CONSUMER);
                assertThat(item.getCancelledAt()).isNotNull();
            });
            OrderFulfillmentHistory latest = history(group).get(0);
            assertThat(latest.getEventType()).isEqualTo(FulfillmentEventType.CANCELLED_BY_CONSUMER);
            assertThat(latest.getActorType()).isEqualTo(FulfillmentActorType.CONSUMER);
            assertThat(stockOf(creamVariant)).isEqualTo(stockBefore + 1);
            assertThat(refundTasks(group)).isEmpty();
            sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.actionBar.prepareStart").value(0));
        }

        @Test
        @DisplayName("소비자 취소 건은 취소 탭에 「소비자 취소 · 준비 시작 전」으로 보인다(A-02)")
        void consumerCancelShowsInCancelledTab() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            cancel(group.getOrder().getId()).andExpect(status().isOk());
            assertThat(order(group.getOrder().getId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);

            sellerGet(SELLER_ORDERS + "?tab=CANCELLED")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].deliveryGroupId", contains(group.getId().intValue())))
                    .andExpect(jsonPath("$.content[0].cancelTypeLabel").value("소비자 취소 · 준비 시작 전"));
            sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.tabCounts.CANCELLED").value(1));
        }

        @Test
        @DisplayName("준비 시작 후에는 409 ORDER_CANCEL_WINDOW_CLOSED — 주문·그룹·결제·재고 불변(A-03)")
        void windowClosesAfterPreparation() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            int stockBefore = stockOf(creamVariant);

            cancel(group.getOrder().getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_CANCEL_WINDOW_CLOSED"));

            assertThat(order(group.getOrder().getId()).getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(payment(group.getOrder().getPaidPaymentId()).getStatus().name()).isEqualTo("PAID");
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(stockOf(creamVariant)).isEqualTo(stockBefore);
            assertThat(historyCount(group, FulfillmentEventType.CANCELLED_BY_CONSUMER)).isZero();
        }
    }

    @Nested
    @DisplayName("③ 브랜드 직권 취소")
    class DirectCancel {

        @Test
        @DisplayName("신규에서 — 사유·소비자 설명 보존 · 항목 SELLER_DIRECT · 환불 큐(항목 합 + 배송비) · 취소 탭 표기(C-01)")
        void fromNew() throws Exception {
            OrderDeliveryGroup group = paidGroup();

            directCancel(List.of(group.getId()), "SOLD_OUT", SOLD_OUT_MESSAGE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped", empty()));

            OrderDeliveryGroup cancelled = reload(group);
            assertThat(cancelled.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
            assertThat(cancelled.getCancelType()).isEqualTo(OrderCancelType.SELLER_DIRECT);
            assertThat(cancelled.getStatusAtCancel()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(cancelled.getCancelReasonCode()).isEqualTo(SellerCancelReason.SOLD_OUT);
            assertThat(cancelled.getCancelReasonDetail()).isEqualTo(SOLD_OUT_MESSAGE);
            assertThat(items(group)).allSatisfy(item -> {
                assertThat(item.getStatus()).isEqualTo(OrderProductStatus.CANCELLED);
                assertThat(item.getCancelType()).isEqualTo(OrderCancelType.SELLER_DIRECT);
            });
            assertThat(refundTasks(group)).singleElement().satisfies(task -> {
                assertThat(task.get("source")).isEqualTo("SELLER_DIRECT_CANCEL");
                assertThat(task.get("source_id")).isNull();
                assertThat(((Number) task.get("refund_amount")).intValue()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
                assertThat(task.get("status")).isEqualTo("PENDING");
            });
            OrderFulfillmentHistory latest = history(group).get(0);
            assertThat(latest.getEventType()).isEqualTo(FulfillmentEventType.CANCELLED_BY_SELLER);
            assertThat(latest.getDetail()).isEqualTo("품절 · " + SOLD_OUT_MESSAGE);
            // 주문 결제는 그대로다 — 환불은 운영자가 큐를 보고 집행한다.
            assertThat(order(group.getOrder().getId()).getStatus()).isEqualTo(OrderStatus.PAID);

            sellerGet(SELLER_ORDERS + "?tab=CANCELLED")
                    .andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()))
                    .andExpect(jsonPath("$.content[0].cancelTypeLabel").value("브랜드 직권 취소"))
                    .andExpect(jsonPath("$.content[0].items[*].itemStatusLabel", contains("취소")));
            orderDetail(group.getId())
                    .andExpect(jsonPath("$.timeline.cancelReasonLabel").value("품절"))
                    .andExpect(jsonPath("$.timeline.cancelReasonDetail").value(SOLD_OUT_MESSAGE))
                    .andExpect(jsonPath("$.amounts.cancelledAmount").value(CREAM_PRICE));
        }

        @Test
        @DisplayName("상품준비중에서 — 취소 당시 상태 PREPARING · 전 항목 재고 원복 · 환불 = 2항목 합 + 배송비(C-02)")
        void fromPreparingWithTwoItems() throws Exception {
            OrderDeliveryGroup group = preparingTwoItemGroup();
            int creamBefore = stockOf(creamVariant);
            int serumBefore = stockOf(serumVariant);

            directCancel(List.of(group.getId()), "DEFECT", "출고 검수에서 용기 파손이 확인되었습니다.")
                    .andExpect(jsonPath("$.succeeded").value(1));

            assertThat(reload(group).getStatusAtCancel()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(stockOf(creamVariant)).isEqualTo(creamBefore + 1);
            assertThat(stockOf(serumVariant)).isEqualTo(serumBefore + 1);
            assertThat(((Number) refundTasks(group).get(0).get("refund_amount")).intValue())
                    .isEqualTo(CREAM_PRICE + SERUM_PRICE + DELIVERY_FEE);
        }

        @Test
        @DisplayName("다건 부분 성공 — 배송중·이미 취소는 ORDER_STATE_CHANGED · 없는 id 는 ORDER_GROUP_NOT_FOUND · 실패 행엔 부수 효과 없음")
        void batchPartialSuccess() throws Exception {
            OrderDeliveryGroup ok = paidGroup();
            OrderDeliveryGroup shipping = shippingGroup("900010002000");
            OrderDeliveryGroup alreadyCancelled = paidGroup();
            cancel(alreadyCancelled.getOrder().getId()).andExpect(status().isOk());
            int stockBefore = stockOf(creamVariant);

            directCancel(List.of(ok.getId(), shipping.getId(), alreadyCancelled.getId(), 999_999L, ok.getId()),
                    "SOLD_OUT", SOLD_OUT_MESSAGE)
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped.length()").value(3))
                    .andExpect(skippedCode(shipping.getId(), "ORDER_STATE_CHANGED"))
                    .andExpect(skippedCode(alreadyCancelled.getId(), "ORDER_STATE_CHANGED"))
                    .andExpect(skippedCode(999_999L, "ORDER_GROUP_NOT_FOUND"));

            assertThat(stockOf(creamVariant)).isEqualTo(stockBefore + 1); // ok 1건만 — 중복 id 도 한 번
            assertThat(refundTasks(ok)).hasSize(1);
            assertThat(refundTasks(shipping)).isEmpty();
            assertThat(refundTasks(alreadyCancelled)).isEmpty();
            assertThat(reload(shipping).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(reload(alreadyCancelled).getCancelType()).isEqualTo(OrderCancelType.CONSUMER);
        }

        @Test
        @DisplayName("되돌릴 수 없다 — 두 번째 직권 취소는 0행 · 환불 큐·재고·이력이 늘지 않는다")
        void secondCancelIsNoop() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            directCancel(List.of(group.getId()), "SOLD_OUT", SOLD_OUT_MESSAGE).andExpect(status().isOk());
            int stockAfterFirst = stockOf(creamVariant);

            directCancel(List.of(group.getId()), "SOLD_OUT", SOLD_OUT_MESSAGE)
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(skippedCode(group.getId(), "ORDER_STATE_CHANGED"));

            assertThat(stockOf(creamVariant)).isEqualTo(stockAfterFirst);
            assertThat(refundTasks(group)).hasSize(1);
            assertThat(historyCount(group, FulfillmentEventType.CANCELLED_BY_SELLER)).isEqualTo(1);
        }

        @Test
        @DisplayName("소비자 취소가 PG 응답 대기(결제 CANCEL_REQUESTED)인 주문은 직권 취소 0행 — 재고 이중 원복·환불 이중 적재가 없다")
        void consumerCancelInFlightBlocksDirectCancel() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            checkoutService.claimUserCancel(consumer.getId(), group.getOrder().getId(), "단순 변심",
                    LocalDateTime.now());
            int stockBefore = stockOf(creamVariant);

            directCancel(List.of(group.getId()), "SOLD_OUT", SOLD_OUT_MESSAGE)
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(skippedCode(group.getId(), "ORDER_STATE_CHANGED"));

            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(stockOf(creamVariant)).isEqualTo(stockBefore);
            assertThat(refundTasks(group)).isEmpty();
        }

        @Test
        @DisplayName("사유 ETC · 배송 불가 지역도 소비자 설명과 함께 보존된다")
        void otherReasons() throws Exception {
            OrderDeliveryGroup etc = paidGroup();
            OrderDeliveryGroup area = paidGroup();

            directCancel(List.of(etc.getId()), "ETC", "공급처 사정으로 출고가 중단되었습니다.").andExpect(status().isOk());
            directCancel(List.of(area.getId()), "UNDELIVERABLE_AREA", "도서산간 지역 배송 불가").andExpect(status().isOk());

            assertThat(reload(etc).getCancelReasonCode()).isEqualTo(SellerCancelReason.ETC);
            assertThat(history(etc).get(0).getDetail()).isEqualTo("기타 · 공급처 사정으로 출고가 중단되었습니다.");
            assertThat(history(area).get(0).getDetail()).isEqualTo("배송 불가 지역 · 도서산간 지역 배송 불가");
        }

        @Test
        @DisplayName("요청 형식 — 소비자 설명 필수(공백 불가 · 300자) · 정의된 사유만 · 빈 목록 불가 → 400, 상태 불변")
        void requestValidation() throws Exception {
            OrderDeliveryGroup group = paidGroup();

            sellerPost(SELLER_ORDERS + "/cancel", Map.of("deliveryGroupIds", List.of(group.getId()), "reasonCode", "SOLD_OUT"))
                    .andExpect(status().isBadRequest());
            directCancel(List.of(group.getId()), "SOLD_OUT", "   ").andExpect(status().isBadRequest());
            directCancel(List.of(group.getId()), "SOLD_OUT", "가".repeat(301)).andExpect(status().isBadRequest());
            directCancel(List.of(group.getId()), "OUT_OF_STOCK", SOLD_OUT_MESSAGE)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            directCancel(List.of(), "SOLD_OUT", SOLD_OUT_MESSAGE).andExpect(status().isBadRequest());

            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(refundTasks(group)).isEmpty();
        }
    }

    @Nested
    @DisplayName("② 취소 요청 → 승인 / 거부")
    class CancelRequest {

        @Test
        @DisplayName("부분 승인 — 요청 항목만 취소 · 그룹은 원래 상태로 복귀 · 환불은 항목 합만(배송비 재계산 없음)(B-04)")
        void partialApprovalKeepsGroupAlive() throws Exception {
            OrderDeliveryGroup group = preparingTwoItemGroup();
            OrderCancelRequest request = seedCancelRequest(group, List.of(itemOf(group, creamVariant)),
                    CancelRequestReason.CHANGE_OF_MIND, null, LocalDateTime.now().withNano(0));
            int creamBefore = stockOf(creamVariant);
            int serumBefore = stockOf(serumVariant);

            approve(request.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PREPARING"))
                    .andExpect(jsonPath("$.cancelRequest").value(nullValue()))
                    .andExpect(jsonPath("$.amounts.cancelledAmount").value(CREAM_PRICE))
                    .andExpect(jsonPath("$.items[?(@.productName == '글로우 크림 50ml')].itemStatusLabel", contains("취소")))
                    .andExpect(jsonPath("$.items[?(@.productName == '글로우 세럼 30ml')].itemStatusLabel", contains("상품준비중")))
                    .andExpect(jsonPath("$.actions.canRegisterInvoice").value(true))
                    .andExpect(jsonPath("$.history[0].eventType").value("CANCEL_REQUEST_APPROVED"))
                    .andExpect(jsonPath("$.history[0].detail").value("요청 1건 취소 · 환불 예정 " + CREAM_PRICE + "원"));

            OrderProduct cream = itemOf(group, creamVariant);
            assertThat(cream.getStatus()).isEqualTo(OrderProductStatus.CANCELLED);
            assertThat(cream.getCancelType()).isEqualTo(OrderCancelType.REQUEST_APPROVED);
            assertThat(itemOf(group, serumVariant).getStatus()).isEqualTo(OrderProductStatus.PAID);
            OrderDeliveryGroup alive = reload(group);
            assertThat(alive.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(alive.getCancelType()).isNull();
            assertThat(stockOf(creamVariant)).isEqualTo(creamBefore + 1);
            assertThat(stockOf(serumVariant)).isEqualTo(serumBefore);
            assertThat(refundTasks(group)).singleElement().satisfies(task -> {
                assertThat(task.get("source")).isEqualTo("CANCEL_REQUEST_APPROVED");
                assertThat(((Number) task.get("source_id")).longValue()).isEqualTo(request.getId());
                assertThat(((Number) task.get("refund_amount")).intValue()).isEqualTo(CREAM_PRICE);
            });
            OrderCancelRequest decided = cancelRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(decided.getStatus()).isEqualTo(CancelRequestStatus.APPROVED);
            assertThat(decided.getDecidedBy()).isEqualTo(brand.seller().getId());
            assertThat(decided.getDecidedAt()).isNotNull();

            // 작업 큐로 복귀 — 남은 항목은 그대로 발송한다.
            sellerGet(SELLER_ORDERS + "?tab=PREPARING")
                    .andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()));
            sellerGet(SELLER_ORDERS + "?tab=CANCEL_REQUESTED").andExpect(jsonPath("$.content", empty()));
            registerShipment(group.getId(), "CJ", "910020003000").andExpect(jsonPath("$.succeeded").value(1));
        }

        @Test
        @DisplayName("전 항목 승인 — 그룹 CANCELLED(REQUEST_APPROVED) · 환불 = 항목 합 + 배송비 전액 · 취소 탭(B-06)")
        void fullApprovalCancelsGroup() throws Exception {
            OrderDeliveryGroup group = paidTwoItemGroup();
            OrderCancelRequest request = seedCancelRequest(group);

            approve(request.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            OrderDeliveryGroup cancelled = reload(group);
            assertThat(cancelled.getCancelType()).isEqualTo(OrderCancelType.REQUEST_APPROVED);
            assertThat(cancelled.getStatusAtCancel()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(cancelled.getCancelledAt()).isNotNull();
            assertThat(items(group)).allMatch(item -> item.getStatus() == OrderProductStatus.CANCELLED);
            assertThat(((Number) refundTasks(group).get(0).get("refund_amount")).intValue())
                    .isEqualTo(CREAM_PRICE + SERUM_PRICE + DELIVERY_FEE);
            sellerGet(SELLER_ORDERS + "?tab=CANCELLED")
                    .andExpect(jsonPath("$.content[0].cancelTypeLabel").value("취소 요청 승인 · 브랜드 승인"));
        }

        @Test
        @DisplayName("같은 요청 재승인은 409 CANCEL_REQUEST_ALREADY_DECIDED — 환불 큐·재고·이력 중복 없음(B-05)")
        void doubleApproveIsConflict() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            OrderCancelRequest request = seedCancelRequest(group);
            approve(request.getId()).andExpect(status().isOk());
            int stockAfterFirst = stockOf(creamVariant);

            approve(request.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_ALREADY_DECIDED"));

            assertThat(refundTasks(group)).hasSize(1);
            assertThat(stockOf(creamVariant)).isEqualTo(stockAfterFirst);
            assertThat(historyCount(group, FulfillmentEventType.CANCEL_REQUEST_APPROVED)).isEqualTo(1);
        }

        @Test
        @DisplayName("거부 사유는 필수 — 빈 값·공백·500자 초과는 400 · 요청은 PENDING 그대로(B-07)")
        void rejectRequiresReason() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            OrderCancelRequest request = seedCancelRequest(group);

            reject(request.getId(), "").andExpect(status().isBadRequest());
            reject(request.getId(), "   ").andExpect(status().isBadRequest());
            reject(request.getId(), "가".repeat(501)).andExpect(status().isBadRequest());
            sellerPost(SELLER_ORDERS + "/cancel-requests/" + request.getId() + "/reject", Map.of())
                    .andExpect(status().isBadRequest());

            assertThat(cancelRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                    .isEqualTo(CancelRequestStatus.PENDING);
        }

        @Test
        @DisplayName("거부 — 사유 보존(소비자 전달) · 항목·그룹 불변 · 이력 · 이후 송장 등록이 정상 진행된다")
        void rejectKeepsEverything() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            OrderCancelRequest request = seedCancelRequest(group);
            String reason = "이미 포장이 완료되어 금일 발송 예정입니다.";

            reject(request.getId(), reason)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PREPARING"))
                    .andExpect(jsonPath("$.cancelRequest").value(nullValue()))
                    .andExpect(jsonPath("$.actions.canRegisterInvoice").value(true))
                    .andExpect(jsonPath("$.history[0].eventType").value("CANCEL_REQUEST_REJECTED"))
                    .andExpect(jsonPath("$.history[0].detail").value(reason));

            OrderCancelRequest decided = cancelRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(decided.getStatus()).isEqualTo(CancelRequestStatus.REJECTED);
            assertThat(decided.getRejectReason()).isEqualTo(reason);
            assertThat(decided.getDecidedBy()).isEqualTo(brand.seller().getId());
            assertThat(items(group)).allMatch(item -> item.getStatus() == OrderProductStatus.PAID);
            assertThat(refundTasks(group)).isEmpty();
            registerShipment(group.getId(), "CJ", "920030004000").andExpect(jsonPath("$.succeeded").value(1));
        }

        @Test
        @DisplayName("결정은 한 번뿐 — 거부 뒤 승인 · 승인 뒤 거부 모두 409")
        void decisionIsFinal() throws Exception {
            OrderDeliveryGroup rejectedGroup = preparingGroup();
            OrderCancelRequest rejected = seedCancelRequest(rejectedGroup);
            reject(rejected.getId(), "발송 완료 예정").andExpect(status().isOk());
            OrderDeliveryGroup approvedGroup = preparingGroup();
            OrderCancelRequest approved = seedCancelRequest(approvedGroup);
            approve(approved.getId()).andExpect(status().isOk());

            approve(rejected.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_ALREADY_DECIDED"));
            reject(approved.getId(), "번복")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_ALREADY_DECIDED"));

            assertThat(items(rejectedGroup)).allMatch(item -> item.getStatus() == OrderProductStatus.PAID);
            assertThat(refundTasks(rejectedGroup)).isEmpty();
            assertThat(cancelRequestRepository.findById(approved.getId()).orElseThrow().getRejectReason()).isNull();
        }

        @Test
        @DisplayName("거부된 뒤 들어온 새 요청은 다시 검토 대상이다 — 검토 중은 늘 1건")
        void newRequestAfterRejection() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            reject(seedCancelRequest(group).getId(), "발송 예정").andExpect(status().isOk());

            OrderCancelRequest second = seedCancelRequest(group);
            sellerGet(SELLER_ORDERS + "?tab=CANCEL_REQUESTED")
                    .andExpect(jsonPath("$.content[0].cancelRequest.cancelRequestId").value(second.getId()));
            approve(second.getId()).andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("없는 요청은 404 ORDER_GROUP_NOT_FOUND")
        void unknownRequest() throws Exception {
            approve(999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));
            reject(999_999L, "사유").andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("승인과 직권 취소는 겹치지 않는다 — 요청이 걸린 동안 직권은 막히고, 승인 뒤 직권은 0행 · 환불 큐 1행(X-05)")
        void approvalAndDirectCancelDoNotOverlap() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            OrderCancelRequest request = seedCancelRequest(group);

            directCancel(List.of(group.getId()), "SOLD_OUT", SOLD_OUT_MESSAGE)
                    .andExpect(skippedCode(group.getId(), "CANCEL_REQUEST_PENDING_EXISTS"));
            approve(request.getId()).andExpect(jsonPath("$.status").value("CANCELLED"));
            directCancel(List.of(group.getId()), "SOLD_OUT", SOLD_OUT_MESSAGE)
                    .andExpect(skippedCode(group.getId(), "ORDER_STATE_CHANGED"));

            assertThat(refundTasks(group)).singleElement()
                    .satisfies(task -> assertThat(task.get("source")).isEqualTo("CANCEL_REQUEST_APPROVED"));
            assertThat(reload(group).getCancelType()).isEqualTo(OrderCancelType.REQUEST_APPROVED);
        }

        @Test
        @DisplayName("요청이 걸린 하위주문은 송장 등록도 선처리 요구로 막힌다(B-02)")
        void pendingRequestBlocksInvoice() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            seedCancelRequest(group);

            registerShipment(group.getId(), "CJ", "930040005000")
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(skippedCode(group.getId(), "CANCEL_REQUEST_PENDING_EXISTS"));
            assertThat(reload(group).getTrackingNumber()).isNull();
        }
    }

    @Nested
    @DisplayName("보강 — 취소 경로끼리의 교차(CX)")
    class Crossings {

        @Test
        @DisplayName("[CX-01] 검토 중 요청이 걸린 채 소비자가 전액 취소 — 요청은 시스템이 닫고(VOIDED) · 카운트=목록 · 승인 409 · 0원 환불 큐 없음(N10)")
        void consumerFullCancelVoidsPendingRequest() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            OrderCancelRequest request = seedCancelRequest(group);

            cancel(group.getOrder().getId()).andExpect(status().isOk());

            OrderCancelRequest voided = cancelRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(voided.getStatus()).isEqualTo(CancelRequestStatus.VOIDED);
            assertThat(voided.getDecidedAt()).isNotNull();
            assertThat(voided.getDecidedBy()).isNull();
            assertThat(reload(group).getCancelType()).isEqualTo(OrderCancelType.CONSUMER);
            sellerGet(SELLER_ORDERS + "/summary")
                    .andExpect(jsonPath("$.tabCounts.CANCEL_REQUESTED").value(0))
                    .andExpect(jsonPath("$.tabCounts.CANCELLED").value(1));
            sellerGet(SELLER_ORDERS + "?tab=CANCEL_REQUESTED").andExpect(jsonPath("$.content", empty()));
            orderDetail(group.getId())
                    .andExpect(jsonPath("$.cancelRequest").value(nullValue()))
                    .andExpect(jsonPath("$.actions.canDecideCancelRequest").value(false));

            approve(request.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_ALREADY_DECIDED"));
            reject(request.getId(), "사유")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_ALREADY_DECIDED"));
            assertThat(refundTasks(group)).isEmpty();
            assertThat(historyCount(group, FulfillmentEventType.CANCEL_REQUEST_APPROVED)).isZero();
        }

        @Test
        @DisplayName("[CX-05] 발송 뒤(배송중·배송완료)에 남은 요청은 승인·거부 모두 409 ORDER_STATE_CHANGED — 반품 경로 우회 없음 · 카운트=목록(N10)")
        void requestAfterShippingCannotBeDecided() throws Exception {
            OrderDeliveryGroup shipping = shippingGroup("710020003000");
            OrderDeliveryGroup deliveredGroup = delivered(shippingGroup("710020003001"), LocalDateTime.now().withNano(0));
            int stockBefore = stockOf(creamVariant);

            for (OrderDeliveryGroup group : List.of(shipping, deliveredGroup)) {
                OrderCancelRequest request = seedCancelRequest(group);

                approve(request.getId())
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));
                reject(request.getId(), "이미 발송되었습니다.")
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));
                orderDetail(group.getId()).andExpect(jsonPath("$.actions.canDecideCancelRequest").value(false));

                assertThat(cancelRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                        .isEqualTo(CancelRequestStatus.PENDING);
                assertThat(items(group)).allMatch(item -> item.getStatus() == OrderProductStatus.PAID);
                assertThat(refundTasks(group)).isEmpty();
            }
            assertThat(reload(shipping).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(stockOf(creamVariant)).isEqualTo(stockBefore);
            sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.tabCounts.CANCEL_REQUESTED").value(0));
            sellerGet(SELLER_ORDERS + "?tab=CANCEL_REQUESTED").andExpect(jsonPath("$.content", empty()));
        }

        @Test
        @DisplayName("[CX-02] 부분 승인 뒤 직권 취소 — 남은 항목만 SELLER_DIRECT · 환불 큐 2행(요청분 · 남은 항목+배송비) · 재고는 항목마다 1회")
        void directCancelAfterPartialApproval() throws Exception {
            OrderDeliveryGroup group = preparingTwoItemGroup();
            OrderCancelRequest request = seedCancelRequest(group, List.of(itemOf(group, creamVariant)),
                    CancelRequestReason.CHANGE_OF_MIND, null, LocalDateTime.now().withNano(0));
            int creamBefore = stockOf(creamVariant);
            int serumBefore = stockOf(serumVariant);
            approve(request.getId()).andExpect(status().isOk());

            directCancel(List.of(group.getId()), "SOLD_OUT", SOLD_OUT_MESSAGE)
                    .andExpect(jsonPath("$.succeeded").value(1));

            OrderDeliveryGroup cancelled = reload(group);
            assertThat(cancelled.getCancelType()).isEqualTo(OrderCancelType.SELLER_DIRECT);
            assertThat(cancelled.getStatusAtCancel()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(itemOf(group, creamVariant).getCancelType()).isEqualTo(OrderCancelType.REQUEST_APPROVED);
            assertThat(itemOf(group, serumVariant).getCancelType()).isEqualTo(OrderCancelType.SELLER_DIRECT);
            assertThat(refundTasks(group))
                    .extracting(task -> task.get("source"), task -> ((Number) task.get("refund_amount")).intValue())
                    .containsExactly(tuple("CANCEL_REQUEST_APPROVED", CREAM_PRICE),
                            tuple("SELLER_DIRECT_CANCEL", SERUM_PRICE + DELIVERY_FEE));
            assertThat(stockOf(creamVariant)).isEqualTo(creamBefore + 1);
            assertThat(stockOf(serumVariant)).isEqualTo(serumBefore + 1);
            sellerGet(SELLER_ORDERS + "?tab=CANCELLED")
                    .andExpect(jsonPath("$.content[0].cancelTypeLabel").value("브랜드 직권 취소"));
        }

        @Test
        @DisplayName("[CX-03] 부분 승인 뒤 남은 항목으로 새 요청을 승인 — 그때 그룹 취소 · 둘째 환불은 남은 항목 + 배송비 전액")
        void secondRequestCompletesCancellation() throws Exception {
            OrderDeliveryGroup group = preparingTwoItemGroup();
            approve(seedCancelRequest(group, List.of(itemOf(group, creamVariant)), CancelRequestReason.CHANGE_OF_MIND,
                    null, LocalDateTime.now().withNano(0)).getId()).andExpect(jsonPath("$.status").value("PREPARING"));

            OrderCancelRequest second = seedCancelRequest(group, List.of(itemOf(group, serumVariant)),
                    CancelRequestReason.ORDER_MISTAKE, null, LocalDateTime.now().withNano(0));
            approve(second.getId())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.history[0].detail")
                            .value("요청 1건 취소 · 환불 예정 " + (SERUM_PRICE + DELIVERY_FEE) + "원"));

            OrderDeliveryGroup cancelled = reload(group);
            assertThat(cancelled.getCancelType()).isEqualTo(OrderCancelType.REQUEST_APPROVED);
            assertThat(cancelled.getCancelledAt()).isNotNull();
            assertThat(refundTasks(group))
                    .extracting(task -> ((Number) task.get("refund_amount")).intValue())
                    .containsExactly(CREAM_PRICE, SERUM_PRICE + DELIVERY_FEE);
        }

        @Test
        @DisplayName("[CX-04] 이미 취소된 항목이 섞인 요청을 승인 — 실제 취소분만 환불 · 재고 이중 원복 없음")
        void requestIncludingCancelledItem() throws Exception {
            OrderDeliveryGroup group = preparingTwoItemGroup();
            approve(seedCancelRequest(group, List.of(itemOf(group, creamVariant)), CancelRequestReason.CHANGE_OF_MIND,
                    null, LocalDateTime.now().withNano(0)).getId()).andExpect(status().isOk());
            int creamAfterFirst = stockOf(creamVariant);
            int serumBefore = stockOf(serumVariant);

            OrderCancelRequest second = seedCancelRequest(group, items(group), CancelRequestReason.CHANGE_OF_MIND,
                    null, LocalDateTime.now().withNano(0));
            // 상세의 요청분은 요청 시점 스냅샷이다 — 실제 환불 예정액과 다를 수 있다.
            orderDetail(group.getId())
                    .andExpect(jsonPath("$.cancelRequest.totalRefundAmount").value(CREAM_PRICE + SERUM_PRICE));
            approve(second.getId())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.history[0].detail")
                            .value("요청 1건 취소 · 환불 예정 " + (SERUM_PRICE + DELIVERY_FEE) + "원"));

            assertThat(stockOf(creamVariant)).isEqualTo(creamAfterFirst);
            assertThat(stockOf(serumVariant)).isEqualTo(serumBefore + 1);
            assertThat(((Number) refundTasks(group).get(1).get("refund_amount")).intValue())
                    .isEqualTo(SERUM_PRICE + DELIVERY_FEE);
        }

        @Test
        @DisplayName("[CX-06] 직권 취소에 같은 id 3번 — 1건 처리 · 환불 큐·재고·이력 1회")
        void duplicateIdsInDirectCancel() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            int stockBefore = stockOf(creamVariant);

            directCancel(List.of(group.getId(), group.getId(), group.getId()), "SOLD_OUT", SOLD_OUT_MESSAGE)
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped", empty()));

            assertThat(refundTasks(group)).hasSize(1);
            assertThat(stockOf(creamVariant)).isEqualTo(stockBefore + 1);
            assertThat(historyCount(group, FulfillmentEventType.CANCELLED_BY_SELLER)).isEqualTo(1);
        }

        @Test
        @DisplayName("[CX-07] 승인·거부 응답은 갱신된 상세다 — 요청분 행이 사라지고 버튼이 상태에 맞게 다시 열린다")
        void decisionResponseContract() throws Exception {
            OrderDeliveryGroup partial = preparingTwoItemGroup();
            approve(seedCancelRequest(partial, List.of(itemOf(partial, creamVariant)), CancelRequestReason.ETC, "변심",
                    LocalDateTime.now().withNano(0)).getId())
                    .andExpect(jsonPath("$.cancelRequest").value(nullValue()))
                    .andExpect(jsonPath("$.amounts.cancelRequestedAmount").value(nullValue()))
                    .andExpect(jsonPath("$.amounts.cancelledAmount").value(CREAM_PRICE))
                    .andExpect(jsonPath("$.overlays.cancelRequested").value(false))
                    .andExpect(actions(false, true, false, true, false));

            OrderDeliveryGroup full = preparingGroup();
            approve(seedCancelRequest(full).getId())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.amounts.cancelledAmount").value(CREAM_PRICE))
                    .andExpect(actions(false, false, false, false, false));

            OrderDeliveryGroup rejected = paidGroup();
            reject(seedCancelRequest(rejected).getId(), "준비 중입니다.")
                    .andExpect(jsonPath("$.status").value("NEW"))
                    .andExpect(jsonPath("$.cancelRequest").value(nullValue()))
                    .andExpect(actions(true, false, false, true, false));
        }
    }

    private static ResultMatcher actions(boolean prepareStart, boolean registerInvoice, boolean updateInvoice,
                                         boolean cancelDirectly, boolean decideCancelRequest) {
        return result -> {
            jsonPath("$.actions.canPrepareStart").value(prepareStart).match(result);
            jsonPath("$.actions.canRegisterInvoice").value(registerInvoice).match(result);
            jsonPath("$.actions.canUpdateInvoice").value(updateInvoice).match(result);
            jsonPath("$.actions.canCancelDirectly").value(cancelDirectly).match(result);
            jsonPath("$.actions.canDecideCancelRequest").value(decideCancelRequest).match(result);
        };
    }

    private static ResultMatcher skippedCode(Long deliveryGroupId, String code) {
        return jsonPath("$.skipped[?(@.deliveryGroupId == " + deliveryGroupId + ")].code", contains(code));
    }
}
