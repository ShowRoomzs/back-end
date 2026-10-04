package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderFulfillmentHistory;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderFulfillmentHistoryRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * 하위주문 이행의 도메인 서비스(34 설계서) — 결제 훅(5-1 · 5-2)과 배치(3-3 · 3-4)가 함께 쓴다.
 * 모든 전이는 리포지토리의 조건부 UPDATE 결과(0행/1행)로 분기하고, 이력은 전이와 같은 트랜잭션에서 append 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderFulfillmentHistoryRepository historyRepository;
    private final OrderRefundTaskRepository refundTaskRepository;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final ProductVariantRepository productVariantRepository;

    // ------------------------------------------------------------------ 이력

    /** 전이를 만든 쪽이 같은 트랜잭션에서 부른다 — 이력이 빠진 전이는 분쟁에서 없던 일이 된다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendHistory(Long deliveryGroupId, FulfillmentEventType eventType, FulfillmentActorType actorType,
                              Long actorId, String detail, LocalDateTime occurredAt) {
        historyRepository.save(OrderFulfillmentHistory.builder()
                .deliveryGroup(deliveryGroupRepository.getReferenceById(deliveryGroupId))
                .eventType(eventType)
                .actorType(actorType)
                .actorId(actorId)
                .detail(detail)
                .occurredAt(occurredAt)
                .build());
    }

    // ------------------------------------------------------------------ PAID 훅(설계서 5-1)

    /**
     * 하위주문의 탄생 — 주문 PAID 전이와 <b>같은 트랜잭션</b>에서 그룹을 NEW 로 올리고
     * 하위주문번호·발송기한을 확정한다. 발송기한은 스냅샷이다 — 이후 마켓 설정이 바뀌어도 불변(귀책 판정값).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void activateOnPaid(Long orderId, LocalDateTime paidAt) {
        // 값을 전부 계산한 뒤에 UPDATE 한다 — 조건부 UPDATE 가 영속성 컨텍스트를 비우므로, 그 뒤에는 아직 읽지 않은
        // 프록시(다른 브랜드 하위주문의 마켓)를 초기화할 수 없다. 여러 브랜드가 섞인 주문에서 결제 확정이 롤백된다.
        List<Activation> plan = new ArrayList<>();
        int seq = 0;
        for (OrderDeliveryGroup group : deliveryGroupRepository.findByOrderId(orderId)) {
            seq++;
            Order order = group.getOrder();
            Integer leadDays = group.getMarket() == null ? null : group.getMarket().getShippingLeadDays();
            plan.add(new Activation(group.getId(), "%s-%02d".formatted(order.getOrderNumber(), seq),
                    leadDays == null ? null : paidAt.plusDays(leadDays)));
        }
        for (Activation activation : plan) {
            if (deliveryGroupRepository.activate(activation.deliveryGroupId(), activation.subOrderNumber(),
                    activation.shipDueAt()) == 1) {
                appendHistory(activation.deliveryGroupId(), FulfillmentEventType.PAID, FulfillmentActorType.SYSTEM,
                        null, null, paidAt);
            }
        }
    }

    private record Activation(Long deliveryGroupId, String subOrderNumber, LocalDateTime shipDueAt) {
    }

    // ------------------------------------------------------------------ 소비자 전액 취소(설계서 5-2)

    /**
     * PG 취소가 확인된 뒤 주문 CANCELLED 전이와 같은 트랜잭션에서 — 그룹·항목에 취소의 사실을 남긴다.
     * 그 주문에 걸린 검토 중 취소 요청은 시스템이 닫는다(VOIDED) — 남겨 두면 취소된 그룹에 「검토 중」이 붙고,
     * 승인하면 0원 환불 큐가 선다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyConsumerCancel(Long orderId, LocalDateTime now) {
        cancelRequestRepository.voidPendingByOrder(orderId, now);
        List<Long> targetIds = deliveryGroupRepository.findByOrderId(orderId).stream()
                .filter(g -> FulfillmentStatus.WORKABLE.contains(g.getFulfillmentStatus()))
                .map(OrderDeliveryGroup::getId)
                .toList();
        if (targetIds.isEmpty()) {
            return;
        }
        deliveryGroupRepository.cancelByConsumer(orderId, now);
        orderProductRepository.fillCancelMetaByOrder(orderId, OrderCancelType.CONSUMER, now);
        for (Long groupId : targetIds) {
            appendHistory(groupId, FulfillmentEventType.CANCELLED_BY_CONSUMER, FulfillmentActorType.CONSUMER, null,
                    null, now);
        }
    }

    // ------------------------------------------------------------------ 항목 취소 + 재고 원복

    /**
     * 항목 단위 취소 확정 — 조건부 UPDATE 1행일 때만 재고를 되돌린다(1회 규칙 · {@code StockReleaser}와 같은 수법).
     * 진행 중 공구면 되돌아간 재고가 다시 팔린다. 호출 전 variantId 오름차순 정렬은 호출자가 한다(잠금 순서).
     *
     * @return 실제로 취소돼 환불 대상이 된 항목만
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OrderProduct> cancelItemsWithRestock(List<OrderProduct> items, OrderCancelType cancelType,
                                                     LocalDateTime now) {
        return items.stream()
                .sorted(Comparator.comparing(OrderProduct::getVariantId))
                .filter(item -> {
                    if (orderProductRepository.cancelItem(item.getId(), cancelType, now) != 1) {
                        return false;
                    }
                    productVariantRepository.restoreStock(item.getVariantId(), item.getQuantity());
                    return true;
                })
                .toList();
    }

    /** 환불 큐 적재 — 집행은 어드민(§34-8 「환불은 브랜드가 절대 실행하지 않는다」). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueRefund(OrderDeliveryGroup group, RefundTaskSource source, Long sourceId, int refundAmount) {
        refundTaskRepository.save(OrderRefundTask.builder()
                .deliveryGroup(group)
                .order(group.getOrder())
                .source(source)
                .sourceId(sourceId)
                .refundAmount(refundAmount)
                .build());
    }

    // ------------------------------------------------------------------ 구매확정(설계서 3-4)

    @Transactional(readOnly = true)
    public List<Long> findIdsToConfirm(LocalDateTime threshold, int limit) {
        return deliveryGroupRepository.findIdsToConfirm(threshold, PageRequest.of(0, limit));
    }

    /** DELIVERED + N일 → CONFIRMED. 미취소 항목을 함께 PURCHASE_CONFIRMED 로 올린다. */
    @Transactional
    public boolean confirmPurchase(Long deliveryGroupId, LocalDateTime now, LocalDateTime threshold) {
        if (deliveryGroupRepository.confirmPurchase(deliveryGroupId, now, threshold) != 1) {
            return false;
        }
        orderProductRepository.transitionByGroup(deliveryGroupId, EnumSet.of(OrderProductStatus.PAID),
                OrderProductStatus.PURCHASE_CONFIRMED);
        appendHistory(deliveryGroupId, FulfillmentEventType.PURCHASE_CONFIRMED, FulfillmentActorType.SYSTEM, null,
                null, now);
        return true;
    }

    // ------------------------------------------------------------------ 배송 추적(설계서 3-3)

    @Transactional(readOnly = true)
    public List<OrderDeliveryGroup> findTrackingTargets(int limit) {
        return findTrackingTargets(0L, limit);
    }

    /** {@code afterId} 초과분을 id 오름차순으로 — 감시 배치가 마지막 id 를 넘기며 대상 전량을 돈다. */
    @Transactional(readOnly = true)
    public List<OrderDeliveryGroup> findTrackingTargets(long afterId, int limit) {
        return deliveryGroupRepository.findTrackingTargets(
                EnumSet.of(FulfillmentStatus.SHIPPING, FulfillmentStatus.RETURNING), afterId, PageRequest.of(0, limit));
    }

    /**
     * 추적 1건 반영 — 포트 호출은 트랜잭션 밖(호출자)이고 여기는 판정·전이만 한다.
     * {@code target}은 폴링 시점의 스냅샷이다 — 전이의 진위는 조건부 UPDATE 가 다시 가린다.
     */
    @Transactional
    public void applyTracking(OrderDeliveryGroup target, Optional<TrackSnapshot> result, LocalDateTime now,
                              int pickupAlertHours, int stallAlertDays) {
        Long id = target.getId();
        // 폴링 당시의 송장 — 전이 WHERE 에 넣어 그사이 송장이 수정됐으면 0행이 된다(N11).
        DeliveryCarrier carrier = target.getCarrier();
        String trackingNumber = target.getTrackingNumber();
        if (result.isEmpty() || result.get().lastEventAt() == null) {
            // 이벤트 없음 — 집화 스캔 전에는 데이터가 없는 게 정상. 24시간이 지나야 1단 경고다(§34-6).
            if (target.getFulfillmentStatus() == FulfillmentStatus.SHIPPING
                    && target.getLastTrackingAt() == null
                    && target.getShippedAt() != null
                    && !target.getShippedAt().plusHours(pickupAlertHours).isAfter(now)
                    && deliveryGroupRepository.setTrackingAlert(id, carrier, trackingNumber,
                    TrackingAlert.PICKUP_UNCONFIRMED) == 1) {
                appendHistory(id, FulfillmentEventType.PICKUP_UNCONFIRMED, FulfillmentActorType.TRACKER, null, null, now);
            }
            return;
        }

        TrackSnapshot snapshot = result.get();
        if (deliveryGroupRepository.touchTracking(id, carrier, trackingNumber, snapshot.lastEventAt()) != 1) {
            return; // 송장이 바뀌었거나 추적 대상 상태를 벗어났다 — 이 결과는 지금 송장의 것이 아니다.
        }

        if (snapshot.returnCompleted() && target.getFulfillmentStatus() == FulfillmentStatus.RETURNING) {
            if (deliveryGroupRepository.markReturnCompleted(id, carrier, trackingNumber, now) == 1) {
                // 반송은 환불로 종결 — 이 주문으로 재발송하지 않는다. 금액은 예정액(왕복 배송비 차감은 약관 근거 대기).
                enqueueRefund(target, RefundTaskSource.RETURN_COMPLETED, null, activeItemsAmount(id) + target.getDeliveryFee());
                appendHistory(id, FulfillmentEventType.RETURN_COMPLETED, FulfillmentActorType.TRACKER, null, null, now);
            }
            return;
        }
        if (snapshot.returnDetected() && target.getFulfillmentStatus() == FulfillmentStatus.SHIPPING) {
            if (deliveryGroupRepository.markReturning(id, carrier, trackingNumber, now) == 1) {
                appendHistory(id, FulfillmentEventType.RETURN_DETECTED, FulfillmentActorType.TRACKER, null, null, now);
            }
            return;
        }
        if (snapshot.deliveredAt() != null && target.getFulfillmentStatus() == FulfillmentStatus.SHIPPING) {
            if (deliveryGroupRepository.markDeliveredByTracker(id, carrier, trackingNumber,
                    snapshot.deliveredAt()) == 1) {
                appendHistory(id, FulfillmentEventType.DELIVERED, FulfillmentActorType.TRACKER, null,
                        "자동 확인", now);
            }
            return;
        }

        // 2단 — 집화 후 N일 갱신 없음. 1단과 달리 이벤트가 있던 송장이다.
        if (target.getFulfillmentStatus() == FulfillmentStatus.SHIPPING
                && !snapshot.lastEventAt().plusDays(stallAlertDays).isAfter(now)) {
            if (deliveryGroupRepository.setTrackingAlert(id, carrier, trackingNumber, TrackingAlert.STALLED) == 1) {
                appendHistory(id, FulfillmentEventType.TRACKING_STALLED, FulfillmentActorType.TRACKER, null, null, now);
            }
            return;
        }
        // 이벤트 재개 — 배지 해제(해제 이력은 두지 않는다 — 소음).
        deliveryGroupRepository.clearTrackingAlert(id, carrier, trackingNumber);
    }

    private int activeItemsAmount(Long deliveryGroupId) {
        return orderProductRepository.findByDeliveryGroupIds(List.of(deliveryGroupId)).stream()
                .filter(item -> item.getStatus() != OrderProductStatus.CANCELLED)
                .mapToInt(item -> item.getPrice() * item.getQuantity())
                .sum();
    }
}
