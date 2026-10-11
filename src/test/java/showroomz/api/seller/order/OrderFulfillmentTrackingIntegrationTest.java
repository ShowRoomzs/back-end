package showroomz.api.seller.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader.GroupBuyOrderClosure;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderFulfillmentHistory;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.order.type.DeliveredSource;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 배송 추적 판정(34 설계서 3-3) · 구매확정 배치(3-4) · 공구 정산 게이트(5-3).
 *
 * <p>택배 연동 전(Noop 스텁)이라 추적 결과는 {@link TrackSnapshot}으로 주입하고 판정·전이는 운영과 같은
 * {@code OrderFulfillmentService.applyTracking}을 태운다(시나리오 문서 5-2). 배송완료·구매확정은 브랜드 API 에 없다 —
 * 돈의 시점을 바꾸는 전이는 자동 아니면 운영자다(0-3).
 */
@IntegrationTest
class OrderFulfillmentTrackingIntegrationTest extends SellerOrderTestSupport {

    @Autowired private GroupBuySalesReader salesReader;

    @Nested
    @DisplayName("배송완료 자동 전환")
    class Delivered {

        @Test
        @DisplayName("추적 완료 → DELIVERED · 출처 TRACKER(「자동 확인」) · 배송완료 탭 D-7 · 구매확정 예정 = 배송완료 + 7일")
        void deliveredByTracker() throws Exception {
            OrderDeliveryGroup group = shippingGroup("010020003000");
            LocalDateTime deliveredAt = LocalDateTime.now().withNano(0).minusHours(1);

            track(group, new TrackSnapshot(deliveredAt, deliveredAt, false, false), LocalDateTime.now());

            OrderDeliveryGroup result = reload(group);
            assertThat(result.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
            assertThat(result.getDeliveredAt()).isEqualTo(deliveredAt);
            assertThat(result.getDeliveredSource()).isEqualTo(DeliveredSource.TRACKER);
            assertThat(result.getLastTrackingAt()).isEqualTo(deliveredAt);
            OrderFulfillmentHistory latest = history(group).get(0);
            assertThat(latest.getEventType()).isEqualTo(FulfillmentEventType.DELIVERED);
            assertThat(latest.getActorType()).isEqualTo(FulfillmentActorType.TRACKER);
            assertThat(latest.getDetail()).isEqualTo("자동 확인");

            sellerGet(SELLER_ORDERS + "?tab=DELIVERED")
                    .andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()))
                    .andExpect(jsonPath("$.content[0].statusTone").value("SUCCESS"))
                    .andExpect(jsonPath("$.content[0].deliveredSourceLabel").value("자동 확인"))
                    .andExpect(jsonPath("$.content[0].confirmRemainingDays").value(7));
            orderDetail(group.getId())
                    .andExpect(jsonPath("$.timeline.deliveredSourceLabel").value("자동 확인"))
                    .andExpect(jsonPath("$.timeline.confirmDueAt").value(jsonTime(deliveredAt.plusDays(7))))
                    // 브랜드가 할 수 있는 일이 없다 — 배송완료 처리·요청 버튼이 어디에도 없다.
                    .andExpect(jsonPath("$.actions.canUpdateInvoice").value(false))
                    .andExpect(jsonPath("$.actions.canCancelDirectly").value(false));
        }

        @Test
        @DisplayName("같은 결과를 다시 받아도 이력이 늘지 않는다 — 전이는 SHIPPING 에서 1회")
        void deliveredOnce() throws Exception {
            OrderDeliveryGroup group = shippingGroup("010020003001");
            LocalDateTime deliveredAt = LocalDateTime.now().withNano(0).minusHours(2);
            TrackSnapshot snapshot = new TrackSnapshot(deliveredAt, deliveredAt, false, false);

            track(group, snapshot, LocalDateTime.now());
            track(group, snapshot, LocalDateTime.now());

            assertThat(historyCount(group, FulfillmentEventType.DELIVERED)).isEqualTo(1);
        }

        @Test
        @DisplayName("구매확정 예정 D-N 은 남은 시간을 올림한다 — 배송완료 5일 12시간 전이면 D-2")
        void confirmRemainingDaysRoundsUp() throws Exception {
            OrderDeliveryGroup group = shippingGroup("010020003002");
            LocalDateTime deliveredAt = LocalDateTime.now().withNano(0).minusDays(5).minusHours(12);

            delivered(group, deliveredAt);

            sellerGet(SELLER_ORDERS + "?tab=DELIVERED")
                    .andExpect(jsonPath("$.content[0].confirmRemainingDays").value(2));
        }
    }

    @Nested
    @DisplayName("배송 이상 — 집화 확인 필요 · 추적 정지")
    class Alerts {

        @Test
        @DisplayName("송장 등록 후 24시간 이벤트 0건이면 집화 확인 필요 — 그 전에는 경고하지 않는다 · 이력 1회")
        void pickupUnconfirmedAfter24Hours() throws Exception {
            OrderDeliveryGroup group = shippingGroup("020030004000");
            LocalDateTime now = batchNow();

            track(group, null, now);
            assertThat(reload(group).getTrackingAlert()).isNull();
            backdateShippedAt(group, now.minusHours(23));
            track(group, null, now);
            assertThat(reload(group).getTrackingAlert()).isNull();

            backdateShippedAt(group, now.minusHours(25));
            track(group, null, now);
            track(group, null, now.plusMinutes(30));

            assertThat(reload(group).getTrackingAlert()).isEqualTo(TrackingAlert.PICKUP_UNCONFIRMED);
            assertThat(historyCount(group, FulfillmentEventType.PICKUP_UNCONFIRMED)).isEqualTo(1);
            assertThat(history(group).get(0).getActorType()).isEqualTo(FulfillmentActorType.TRACKER);
            sellerGet(SELLER_ORDERS + "?tab=SHIPPING")
                    .andExpect(jsonPath("$.content[0].overlays.trackingAlert").value("PICKUP_UNCONFIRMED"))
                    .andExpect(jsonPath("$.content[0].overlays.trackingAlertLabel").value("집화 확인 필요"));
            sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.actionBar.deliveryIssue").value(1));
        }

        @Test
        @DisplayName("이벤트가 오면 배지가 풀린다 — 해제 이력은 남기지 않는다(소음)")
        void alertClearsWhenEventsArrive() throws Exception {
            OrderDeliveryGroup group = shippingGroup("020030004001");
            LocalDateTime now = batchNow();
            backdateShippedAt(group, now.minusHours(25));
            track(group, null, now);
            int historyBefore = history(group).size();

            track(group, new TrackSnapshot(now.minusMinutes(10), null, false, false), now);

            OrderDeliveryGroup cleared = reload(group);
            assertThat(cleared.getTrackingAlert()).isNull();
            assertThat(cleared.getLastTrackingAt()).isEqualTo(now.minusMinutes(10));
            assertThat(history(group)).hasSize(historyBefore);
            sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.actionBar.deliveryIssue").value(0));
        }

        @Test
        @DisplayName("이벤트가 한 번이라도 있던 송장은 조회 실패(empty)로 집화 확인 필요가 되지 않는다")
        void noPickupAlertOnceEventsSeen() throws Exception {
            OrderDeliveryGroup group = shippingGroup("020030004002");
            LocalDateTime now = batchNow();
            track(group, new TrackSnapshot(now.minusHours(2), null, false, false), now);
            backdateShippedAt(group, now.minusHours(30));

            track(group, null, now);

            assertThat(reload(group).getTrackingAlert()).isNull();
        }

        @Test
        @DisplayName("마지막 이벤트 후 7일 갱신 없음 → 추적 정지(위험) · 집화 확인 필요를 덮어쓴다 · 6일이면 아니다")
        void stalledAfterSevenDays() throws Exception {
            OrderDeliveryGroup group = shippingGroup("020030004003");
            LocalDateTime now = batchNow();
            backdateShippedAt(group, now.minusDays(10));
            track(group, null, now);
            assertThat(reload(group).getTrackingAlert()).isEqualTo(TrackingAlert.PICKUP_UNCONFIRMED);

            track(group, new TrackSnapshot(now.minusDays(6), null, false, false), now);
            assertThat(reload(group).getTrackingAlert()).isNull();

            track(group, new TrackSnapshot(now.minusDays(7).minusHours(1), null, false, false), now);
            track(group, new TrackSnapshot(now.minusDays(7).minusHours(1), null, false, false), now.plusHours(1));

            assertThat(reload(group).getTrackingAlert()).isEqualTo(TrackingAlert.STALLED);
            assertThat(historyCount(group, FulfillmentEventType.TRACKING_STALLED)).isEqualTo(1);
            sellerGet(SELLER_ORDERS + "?tab=SHIPPING")
                    .andExpect(jsonPath("$.content[0].overlays.trackingAlertLabel").value("추적 정지"));
        }
    }

    @Nested
    @DisplayName("반송")
    class Returning {

        @Test
        @DisplayName("반송 감지 → RETURNING(위험) · 감지 시각만 · 배지 해제 · 송장 수정 불가")
        void returnDetected() throws Exception {
            OrderDeliveryGroup group = shippingGroup("030040005000");
            LocalDateTime now = batchNow();
            backdateShippedAt(group, now.minusHours(25));
            track(group, null, now);

            track(group, new TrackSnapshot(now.minusHours(1), null, true, false), now);

            OrderDeliveryGroup returning = reload(group);
            assertThat(returning.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.RETURNING);
            assertThat(returning.getReturnDetectedAt()).isEqualTo(now);
            assertThat(returning.getTrackingAlert()).isNull();
            assertThat(history(group).get(0).getEventType()).isEqualTo(FulfillmentEventType.RETURN_DETECTED);
            sellerGet(SELLER_ORDERS + "?tab=RETURNING")
                    .andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()))
                    .andExpect(jsonPath("$.content[0].statusTone").value("DANGER"));
            orderDetail(group.getId())
                    .andExpect(jsonPath("$.timeline.returnDetectedAt").value(jsonTime(now)))
                    .andExpect(jsonPath("$.actions.canUpdateInvoice").value(false));
            sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.actionBar.deliveryIssue").value(1));
        }

        @Test
        @DisplayName("반송 완료(입고) → 환불 큐 1행(항목 합 + 배송비 전액) → PG 즉시 자동 환불 · 재감지에도 중복 없음")
        void returnCompletedEnqueuesRefundOnce() throws Exception {
            OrderDeliveryGroup group = returning(shippingGroup("030040005001"));
            LocalDateTime now = batchNow();
            TrackSnapshot completed = new TrackSnapshot(now.minusMinutes(5), null, true, true);

            track(group, completed, now);
            track(group, completed, now.plusMinutes(30));

            assertThat(reload(group).getReturnCompletedAt()).isEqualTo(now);
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.RETURNING);
            assertThat(refundTasks(group)).singleElement().satisfies(task -> {
                assertThat(task.get("source")).isEqualTo("RETURN_COMPLETED");
                assertThat(((Number) task.get("refund_amount")).intValue()).isEqualTo(CREAM_PRICE + DELIVERY_FEE);
                // 운영자 집행 단계 없이 커밋 직후 PG 부분 취소로 돌려준다(1009 기획 수정본 2절).
                assertThat(task.get("status")).isEqualTo("DONE");
            });
            assertThat(historyCount(group, FulfillmentEventType.RETURN_COMPLETED)).isEqualTo(1);
        }

        @Test
        @DisplayName("반송중은 구매확정되지 않는다 — DELIVERED 가 아니므로 타이머 취소가 구조적이다(E-02)")
        void returningIsNeverConfirmed() throws Exception {
            OrderDeliveryGroup group = returning(shippingGroup("030040005002"));
            LocalDateTime later = LocalDateTime.now().plusDays(30);

            assertThat(fulfillmentService.findIdsToConfirm(later, 100)).doesNotContain(group.getId());
            assertThat(fulfillmentService.confirmPurchase(group.getId(), later, later.minusDays(7))).isFalse();
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.RETURNING);
        }

        @Test
        @DisplayName("추적 대상은 송장이 있는 배송중·반송중뿐 — id 오름차순")
        void trackingTargets() throws Exception {
            paidGroup();
            preparingGroup();
            OrderDeliveryGroup shipping = shippingGroup("030040005003");
            OrderDeliveryGroup returning = returning(shippingGroup("030040005004"));
            delivered(shippingGroup("030040005005"), LocalDateTime.now().withNano(0));

            assertThat(fulfillmentService.findTrackingTargets(10))
                    .extracting(OrderDeliveryGroup::getId)
                    .containsExactly(shipping.getId(), returning.getId());
        }
    }

    @Nested
    @DisplayName("구매확정 — 배송완료 + 7일")
    class PurchaseConfirm {

        @Test
        @DisplayName("7일 전에는 대상이 아니다 — 지나면 CONFIRMED · 항목 PURCHASE_CONFIRMED · 이력(시스템) · 구매확정 탭")
        void confirmAfterSevenDays() throws Exception {
            LocalDateTime deliveredBase = batchNow();
            OrderDeliveryGroup tooEarly = delivered(shippingGroup("040050006000"), deliveredBase.minusDays(6));
            OrderDeliveryGroup due = delivered(shippingGroup("040050006001"), deliveredBase.minusDays(7).minusMinutes(1));
            LocalDateTime now = batchNow(); // 배치 회차는 배송완료 반영 뒤에 돈다
            LocalDateTime threshold = now.minusDays(7);

            assertThat(fulfillmentService.findIdsToConfirm(threshold, 100)).containsExactly(due.getId());
            assertThat(fulfillmentService.confirmPurchase(tooEarly.getId(), now, threshold)).isFalse();
            assertThat(fulfillmentService.confirmPurchase(due.getId(), now, threshold)).isTrue();

            OrderDeliveryGroup confirmed = reload(due);
            assertThat(confirmed.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
            assertThat(confirmed.getConfirmedAt()).isEqualTo(now);
            assertThat(items(due)).allMatch(item -> item.getStatus() == OrderProductStatus.PURCHASE_CONFIRMED);
            OrderFulfillmentHistory latest = history(due).get(0);
            assertThat(latest.getEventType()).isEqualTo(FulfillmentEventType.PURCHASE_CONFIRMED);
            assertThat(latest.getActorType()).isEqualTo(FulfillmentActorType.SYSTEM);
            assertThat(reload(tooEarly).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);

            sellerGet(SELLER_ORDERS + "?tab=CONFIRMED")
                    .andExpect(jsonPath("$.content[*].deliveryGroupId", contains(due.getId().intValue())))
                    .andExpect(jsonPath("$.content[0].paidAmount").value(CREAM_PRICE + DELIVERY_FEE))
                    .andExpect(jsonPath("$.content[0].settlementLabel").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].confirmRemainingDays").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].items[0].itemStatusLabel").value("구매확정"));
        }

        @Test
        @DisplayName("배치 대상은 배송완료가 오래된 순 · 상한까지만")
        void findIdsOrderAndLimit() throws Exception {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            OrderDeliveryGroup middle = delivered(shippingGroup("040050006002"), now.minusDays(9));
            OrderDeliveryGroup oldest = delivered(shippingGroup("040050006003"), now.minusDays(10));
            delivered(shippingGroup("040050006004"), now.minusDays(8));

            assertThat(fulfillmentService.findIdsToConfirm(now.minusDays(7), 2))
                    .containsExactly(oldest.getId(), middle.getId());
        }

        @Test
        @DisplayName("부분 취소 뒤 구매확정 — 취소 항목은 CANCELLED 그대로 · 남은 항목만 PURCHASE_CONFIRMED")
        void partialCancelThenConfirm() throws Exception {
            OrderDeliveryGroup group = preparingTwoItemGroup();
            OrderCancelRequest request = seedCancelRequest(group, List.of(itemOf(group, creamVariant)),
                    CancelRequestReason.CHANGE_OF_MIND, null, LocalDateTime.now().withNano(0));
            approve(request.getId()).andExpect(status().isOk());
            LocalDateTime now = LocalDateTime.now().withNano(0);
            delivered(shipped(group, "CJ", "040050006005"), now.minusDays(8));

            assertThat(fulfillmentService.confirmPurchase(group.getId(), now, now.minusDays(7))).isTrue();

            assertThat(itemOf(group, creamVariant).getStatus()).isEqualTo(OrderProductStatus.CANCELLED);
            assertThat(itemOf(group, serumVariant).getStatus()).isEqualTo(OrderProductStatus.PURCHASE_CONFIRMED);
        }
    }

    @Nested
    @DisplayName("보강 — 송장 수정과의 경합 · 스냅샷 조합(TR)")
    class Crossings {

        @Test
        @DisplayName("[TR-03] 폴링한 뒤 송장이 수정되면 구 송장의 결과는 반영되지 않는다 — 배송완료·반송·이벤트 시각·배지 모두(N11)")
        void staleInvoiceResultIsIgnored() throws Exception {
            OrderDeliveryGroup group = shippingGroup("720010002000");
            LocalDateTime now = batchNow();
            backdateShippedAt(group, now.minusHours(25));
            OrderDeliveryGroup polled = reload(group); // 폴링 시점 스냅샷 — 구 송장

            updateShipment(group.getId(), "HANJIN", "720010002001").andExpect(status().isOk());

            fulfillmentService.applyTracking(polled, Optional.of(new TrackSnapshot(now.minusHours(1),
                    now.minusHours(1), false, false)), now, 24, 7);
            fulfillmentService.applyTracking(polled, Optional.of(new TrackSnapshot(now.minusHours(1), null,
                    true, false)), now, 24, 7);
            fulfillmentService.applyTracking(polled, Optional.empty(), now, 24, 7);

            OrderDeliveryGroup current = reload(group);
            assertThat(current.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(current.getDeliveredAt()).isNull();
            assertThat(current.getReturnDetectedAt()).isNull();
            assertThat(current.getLastTrackingAt()).isNull();
            assertThat(current.getTrackingAlert()).isNull();
            assertThat(historyCount(group, FulfillmentEventType.DELIVERED)).isZero();
            assertThat(historyCount(group, FulfillmentEventType.RETURN_DETECTED)).isZero();
            assertThat(historyCount(group, FulfillmentEventType.PICKUP_UNCONFIRMED)).isZero();

            // 새 송장으로 폴링한 결과는 정상 반영된다.
            track(group, new TrackSnapshot(now.minusMinutes(5), now.minusMinutes(5), false, false), now);
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        }

        @Test
        @DisplayName("[TR-04] 배송중 + 반송·배송완료가 함께 오면 반송이 이긴다")
        void returnWinsOverDelivered() throws Exception {
            OrderDeliveryGroup group = shippingGroup("720010002002");
            LocalDateTime now = batchNow();

            track(group, new TrackSnapshot(now.minusHours(1), now.minusHours(1), true, false), now);

            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.RETURNING);
            assertThat(reload(group).getDeliveredAt()).isNull();
            assertThat(historyCount(group, FulfillmentEventType.DELIVERED)).isZero();
        }

        @Test
        @DisplayName("[TR-04] 배송중에 반송 감지 없이 「입고 완료」만 오면 무시한다 — 환불 큐가 서지 않는다")
        void returnCompletedWithoutDetectionIsIgnored() throws Exception {
            OrderDeliveryGroup group = shippingGroup("720010002003");
            LocalDateTime now = batchNow();

            track(group, new TrackSnapshot(now.minusHours(1), null, false, true), now);

            OrderDeliveryGroup result = reload(group);
            assertThat(result.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(result.getReturnCompletedAt()).isNull();
            assertThat(refundTasks(group)).isEmpty();
        }

        @Test
        @DisplayName("[TR-04] 반송중에서는 나가는 전이가 없다 — 배송완료 무시 · 7일 정체·24시간 미조회 배지도 붙지 않는다")
        void returningIgnoresDeliveryAndAlerts() throws Exception {
            OrderDeliveryGroup group = returning(shippingGroup("720010002004"));
            LocalDateTime now = batchNow();
            backdateShippedAt(group, now.minusDays(10));

            track(group, new TrackSnapshot(now.minusHours(1), now.minusHours(1), false, false), now);
            track(group, new TrackSnapshot(now.minusDays(8), null, true, false), now);
            track(group, null, now);

            OrderDeliveryGroup result = reload(group);
            assertThat(result.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.RETURNING);
            assertThat(result.getDeliveredAt()).isNull();
            assertThat(result.getTrackingAlert()).isNull();
            assertThat(historyCount(group, FulfillmentEventType.DELIVERED)).isZero();
            assertThat(historyCount(group, FulfillmentEventType.TRACKING_STALLED)).isZero();
            assertThat(historyCount(group, FulfillmentEventType.PICKUP_UNCONFIRMED)).isZero();
        }
    }

    @Nested
    @DisplayName("공구 정산 게이트 — 미종결 집계(5-3)")
    class GroupBuyClosure {

        @Test
        @DisplayName("종결 = 구매확정·취소 · 반송중은 미종결 · 결제 전은 집계 밖")
        void closureCountsByFulfillment() throws Exception {
            paidGroup();
            preparingGroup();
            shippingGroup("050060007000");
            returning(shippingGroup("050060007001"));
            LocalDateTime now = LocalDateTime.now().withNano(0);
            OrderDeliveryGroup toConfirm = delivered(shippingGroup("050060007002"), now.minusDays(8));
            fulfillmentService.confirmPurchase(toConfirm.getId(), now, now.minusDays(7));
            OrderDeliveryGroup cancelled = paidGroup();
            directCancel(List.of(cancelled.getId()), "SOLD_OUT", "품절").andExpect(status().isOk());
            placeCardOrder(creamVariant, 1); // 결제 대기

            GroupBuyOrderClosure closure = salesReader.readClosure(groupBuy.getId()).orElseThrow();

            assertThat(closure.totalCount()).isEqualTo(6);
            assertThat(closure.closedCount()).isEqualTo(2);
            assertThat(closure.unclosedCount()).isEqualTo(4);
            // 종결 경로별 — 구매확정 1 · 결제 후 취소(환불) 1. 결제 대기 건은 어디에도 세지 않는다.
            assertThat(closure.purchaseConfirmedCount()).isEqualTo(1);
            assertThat(closure.refundedCount()).isEqualTo(1);
            assertThat(closure.awaitingShipment()).isEqualTo(2);
            assertThat(closure.inReturnOrExchange()).isEqualTo(1);
            assertThat(closure.unclosedStages())
                    .extracting(GroupBuySalesReader.UnclosedStage::stage, GroupBuySalesReader.UnclosedStage::count)
                    .containsExactlyInAnyOrder(tuple("NEW", 1), tuple("PREPARING", 1), tuple("SHIPPING", 1),
                            tuple("RETURNING", 1));
        }

        @Test
        @DisplayName("전부 구매확정되면 미종결 0 — 정산 게이트가 열린다")
        void allConfirmedClosesGate() throws Exception {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            OrderDeliveryGroup group = delivered(shippingGroup("050060007003"), now.minusDays(8));
            assertThat(salesReader.readClosure(groupBuy.getId()).orElseThrow().unclosedCount()).isEqualTo(1);

            fulfillmentService.confirmPurchase(group.getId(), now, now.minusDays(7));

            GroupBuyOrderClosure closure = salesReader.readClosure(groupBuy.getId()).orElseThrow();
            assertThat(closure.unclosedCount()).isZero();
            assertThat(closure.unclosedStages()).isEmpty();
        }
    }
}
