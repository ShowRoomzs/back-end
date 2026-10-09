package showroomz.domain.order.service;

import org.springframework.context.ApplicationEventPublisher;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.OperatorRefundReason;
import showroomz.domain.order.event.RefundTaskEnqueuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
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
import showroomz.global.config.properties.OrderProperties;
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
    private final DeliveryTrackingEventRecorder trackingEventRecorder;
    private final OrderProperties orderProperties;
    private final ShipDuePolicy shipDuePolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final showroomz.domain.payment.repository.PaymentRepository paymentRepository;

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
            plan.add(new Activation(group.getId(), "%s-%02d".formatted(order.getOrderNumber(), seq),
                    shipDueAtOnPaid(group, paidAt)));
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

    /**
     * 결제 시점의 발송기한(1009 기획 수정본 1-2) — 공구가 이미 종결됐으면 마감 + N영업일, 진행 중이면 null(종결 훅이 채운다).
     * 공구 없는 백필 행은 결제 시각을 기산점으로 쓴다.
     */
    private LocalDateTime shipDueAtOnPaid(OrderDeliveryGroup group, LocalDateTime paidAt) {
        GroupBuy groupBuy = group.getGroupBuy();
        int businessDays = group.getShipDueBusinessDays();
        if (groupBuy == null) {
            return shipDuePolicy.dueAt(paidAt, businessDays);
        }
        if (groupBuy.getStatus() != null && groupBuy.getStatus().isTerminal() && groupBuy.getEndedAt() != null) {
            return shipDuePolicy.dueAt(groupBuy.getEndedAt(), businessDays);
        }
        return null;
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

    /**
     * 환불 큐 적재 — <b>PG 즉시 자동 환불</b>(1009 기획 수정본 2-2). 커밋 직후 {@code RefundExecutor}가 포트원 부분 취소로
     * 돌려준다. 브랜드는 여전히 환불을 실행하지 않는다(§34-8) — 큐를 쌓을 뿐 집행 주체는 시스템이다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderRefundTask enqueueRefund(OrderDeliveryGroup group, RefundTaskSource source, Long sourceId,
                                         int refundAmount) {
        return enqueue(OrderRefundTask.builder()
                .deliveryGroup(group)
                .order(group.getOrder())
                .source(source)
                .sourceId(sourceId)
                .refundAmount(refundAmount)
                .origin(RefundTaskOrigin.PG_AUTO)
                .paymentId(group.getOrder().getPaidPaymentId())
                .partialCancel(isPartial(group.getOrder().getPaidPaymentId(), refundAmount))
                .build());
    }

    /**
     * 운영자 사유 환불 편입(어드민 06a B5 · 06b B2) — 반려 이의 인용 · 구매확정 후 하자 · 위해성 리콜. <b>편입만</b> 한다 — 돈은
     * 어드민 환불 관리의 재확인 다이얼로그에서만 나간다(편입과 집행을 나눈다).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderRefundTask enqueueOperatorRefund(OrderDeliveryGroup group, Long sourceId, int refundAmount,
                                                 OperatorRefundReason reason, String detail, Long operatorId,
                                                 LocalDateTime now) {
        OrderRefundTask task = enqueue(OrderRefundTask.builder()
                .deliveryGroup(group)
                .order(group.getOrder())
                .source(RefundTaskSource.OPERATOR_REASON)
                .sourceId(sourceId)
                .refundAmount(refundAmount)
                .origin(RefundTaskOrigin.OPERATOR)
                .paymentId(group.getOrder().getPaidPaymentId())
                .partialCancel(isPartial(group.getOrder().getPaidPaymentId(), refundAmount))
                .reasonCode(reason)
                .reasonDetail(detail)
                .requestedBy(operatorId)
                .build());
        appendHistory(group.getId(), FulfillmentEventType.REFUND_ENQUEUED_BY_OPERATOR, FulfillmentActorType.ADMIN,
                operatorId, String.format("%s · %s · %,d원", task.refundNo(), reason.getLabel(), refundAmount), now);
        return task;
    }

    /** 원래 결제의 부분 취소인가 — 어드민 06c 결제 열 「부분」. 결제가 없는 주문은 거짓. */
    private boolean isPartial(String paymentId, int refundAmount) {
        return paymentId != null && paymentRepository.findById(paymentId)
                .map(payment -> payment.getAmount() != null && refundAmount < payment.getAmount()).orElse(false);
    }

    private OrderRefundTask enqueue(OrderRefundTask task) {
        OrderRefundTask saved = refundTaskRepository.save(task);
        eventPublisher.publishEvent(new RefundTaskEnqueuedEvent(saved.getId(), saved.getOrigin()));
        return saved;
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

    /**
     * 단건 구매확정 시도(35 설계서 3-6) — 배치와 같은 조건부 UPDATE 를 그 자리에서 연다. 클레임이 구매확정 보류를 푸는
     * 순간(검수 거절 · 철회 · 자동 취소)에 부른다 — 기준 시각 + N일이 이미 지났고 다른 보류 클레임이 없으면 배치 회차를
     * 기다리지 않고 확정된다. 조건이 안 맞으면 아무 일도 없다.
     */
    @Transactional
    public boolean confirmIfDue(Long deliveryGroupId, LocalDateTime now) {
        return confirmPurchase(deliveryGroupId, now, now.minusDays(orderProperties.getPurchaseConfirmDays()));
    }

    // ------------------------------------------------------------------ 구매확정 타이머 정지 · 재개(1009 기획 수정본 4절)

    /** 반품·교환 접수 — 그 하위주문의 구매확정 타이머를 멈춘다. 이미 멈춰 있으면 먼저 멈춘 시각을 유지한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void pauseConfirmTimer(Long deliveryGroupId, LocalDateTime now) {
        deliveryGroupRepository.pauseConfirmTimer(deliveryGroupId, now);
    }

    /**
     * 진행 중 클레임이 없어졌으면 재개 — 정지한 시간만큼 기산점을 밀어 「남은 일수부터」 다시 센다(철회 · 자동 취소 · 반려 ·
     * 환불 완료). 재개 뒤 예정이 이미 지났으면 그 자리에서 확정한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resumeConfirmTimerIfIdle(Long deliveryGroupId, LocalDateTime now) {
        OrderDeliveryGroup group = deliveryGroupRepository.findById(deliveryGroupId).orElse(null);
        if (group != null && group.getConfirmPausedAt() != null
                && !deliveryGroupRepository.existsTimerHoldingClaim(deliveryGroupId)) {
            LocalDateTime pausedAt = group.getConfirmPausedAt();
            LocalDateTime base = group.confirmBaseAt();
            if (base != null && now.isAfter(pausedAt)) {
                base = base.plus(java.time.Duration.between(pausedAt, now));
            }
            deliveryGroupRepository.resumeConfirmTimer(deliveryGroupId, pausedAt, base, null);
        }
        confirmIfDue(deliveryGroupId, now);
    }

    /**
     * 교환 재발송 도착 — 구매확정을 도착 시각부터 7일 새로 센다(35 설계서 3-4 · 「교환 완료일부터 7일 새로 시작」). 같은 하위주문에
     * 다른 클레임이 아직 진행 중이면 정지는 유지하되 정지 시계를 지금부터 다시 잰다(새 기산점에 지난 정지 시간을 더하지 않는다).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restartConfirmTimer(Long deliveryGroupId, LocalDateTime deliveredAt, LocalDateTime now) {
        deliveryGroupRepository.restartConfirmTimer(deliveryGroupId, deliveredAt);
        OrderDeliveryGroup group = deliveryGroupRepository.findById(deliveryGroupId).orElse(null);
        if (group == null || group.getConfirmPausedAt() == null) {
            return;
        }
        boolean stillHeld = deliveryGroupRepository.existsTimerHoldingClaim(deliveryGroupId);
        deliveryGroupRepository.resumeConfirmTimer(deliveryGroupId, group.getConfirmPausedAt(), group.confirmBaseAt(),
                stillHeld ? now : null);
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
        // 소비자 앱 배송 조회가 읽는 이력 — 화면은 택배 API 를 부르지 않는다(앱 클레임 설계서 1-6).
        trackingEventRecorder.record(carrier, trackingNumber, snapshot.events());

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
