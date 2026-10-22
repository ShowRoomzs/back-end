package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.StallChoice;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 추적 정지 소비자 선택(1021 결정 노트 기-15 【잠정】 · 설계서 5-1). 추적 정지 판정 + N일({@code delivery.tracker.consumer-choice-days})에
 * 선택이 열리고, 소비자가 재발송 · 환불 중 하나를 고르면 브랜드가 처리한다 — 시스템은 자동 집행하지 않는다(14절 Q15).
 *
 * <p>환불 선택은 브랜드의 [소비자 환불 처리]로 분실 판정과 같은 효과(하위주문 취소 · 재고 원복 없음 · 항목 + 배송비 PG 환불)를 낸다.
 * 운영자 분실 판정의 「추적 정지 N일」 조건은 면제된다 — 소비자 선택이 근거다.
 */
@Service
@RequiredArgsConstructor
public class OrderStallChoiceService {

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderFulfillmentService fulfillmentService;

    /** 화면 블록 — 추적 정지가 아니고 선택도 없으면 null. */
    public record View(State state, LocalDateTime opensAt, Integer daysUntilOpen, Integer daysStalled,
                       StallChoice choice, String choiceLabel, LocalDateTime chosenAt) {
    }

    public enum State {
        /** 추적 정지 · 선택 개방 전. */
        WAITING,
        /** 선택 가능. */
        OPEN,
        /** 소비자가 골랐다 — 브랜드 처리 대기 또는 처리됨. */
        CHOSEN
    }

    public static View view(OrderDeliveryGroup group, LocalDateTime now) {
        if (group.getStallChoice() != null) {
            return new View(State.CHOSEN, group.getStallChoiceOpenAt(), null, daysStalled(group, now),
                    group.getStallChoice(), group.getStallChoice().getLabel(), group.getStallChoiceAt());
        }
        if (group.getTrackingAlert() != TrackingAlert.STALLED || group.getStallChoiceOpenAt() == null
                || group.getFulfillmentStatus() != FulfillmentStatus.SHIPPING) {
            return null;
        }
        LocalDateTime opensAt = group.getStallChoiceOpenAt();
        boolean open = !now.isBefore(opensAt);
        Integer daysUntilOpen = open ? 0 : (int) Math.max(1, (Duration.between(now, opensAt).toHours() + 23) / 24);
        return new View(open ? State.OPEN : State.WAITING, opensAt, daysUntilOpen, daysStalled(group, now), null, null,
                null);
    }

    /** 추적 정지 경과 일수 — 마지막 추적(없으면 발송)부터. */
    private static Integer daysStalled(OrderDeliveryGroup group, LocalDateTime now) {
        LocalDateTime basis = group.getLastTrackingAt() != null ? group.getLastTrackingAt() : group.getShippedAt();
        return basis == null ? null : (int) Math.max(0, Duration.between(basis, now).toDays());
    }

    /** 소비자 선택 — 열려 있고 아직 고르지 않았을 때 한 번. */
    @Transactional
    public OrderDeliveryGroup choose(Long userId, Long deliveryGroupId, StallChoice choice, LocalDateTime now) {
        OrderDeliveryGroup group = deliveryGroupRepository.findById(deliveryGroupId)
                .filter(found -> found.getOrder().getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        if (group.getStallChoice() != null) {
            throw new BusinessException(ErrorCode.ORDER_STALL_CHOICE_ALREADY_MADE);
        }
        View view = view(group, now);
        if (view == null || view.state() != State.OPEN
                || deliveryGroupRepository.chooseStall(deliveryGroupId, choice, now) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STALL_CHOICE_NOT_OPEN);
        }
        fulfillmentService.appendHistory(deliveryGroupId, FulfillmentEventType.STALL_CHOICE_MADE,
                FulfillmentActorType.CONSUMER, userId, choice.getLabel(), now);
        return deliveryGroupRepository.findById(deliveryGroupId).orElseThrow();
    }

    /**
     * 소비자 환불 선택의 처리(브랜드 [소비자 환불 처리]) — 분실 판정과 같은 효과. 소비자가 환불을 고른 추적 정지 건만.
     */
    @Transactional
    public void refundByChoice(Long deliveryGroupId, Long marketId, FulfillmentActorType actor, Long actorId,
                               LocalDateTime now) {
        OrderDeliveryGroup group = deliveryGroupRepository.findById(deliveryGroupId)
                .filter(found -> marketId == null || found.getMarketId().equals(marketId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        if (group.getStallChoice() != StallChoice.REFUND) {
            throw new BusinessException(ErrorCode.ORDER_STATE_CHANGED, "소비자가 환불을 선택한 추적 정지 건만 처리할 수 있습니다.");
        }
        List<OrderProduct> paidItems = orderProductRepository.findByDeliveryGroupIds(List.of(deliveryGroupId)).stream()
                .filter(item -> item.getStatus() == OrderProductStatus.PAID).toList();
        if (deliveryGroupRepository.markLostByAdmin(deliveryGroupId, "추적 정지 · 소비자 환불 선택", now) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_CHANGED);
        }
        int cancelled = orderProductRepository.cancelItemsByGroup(deliveryGroupId, OrderCancelType.LOST, now);
        OrderDeliveryGroup reloaded = deliveryGroupRepository.findById(deliveryGroupId).orElseThrow();
        int refundAmount = paidItems.stream().mapToInt(item -> item.getPrice() * item.getQuantity()).sum()
                + reloaded.getDeliveryFee();
        fulfillmentService.enqueueRefund(reloaded, RefundTaskSource.STALL_REFUND_CHOSEN, null, refundAmount);
        fulfillmentService.appendHistory(deliveryGroupId, FulfillmentEventType.LOST_RESOLVED, actor, actorId,
                "추적 정지 · 소비자 환불 선택 · 항목 " + cancelled + "건", now);
    }
}
