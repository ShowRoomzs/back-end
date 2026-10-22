package showroomz.api.test.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.test.dto.TestOrderDto;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 테스트 전용: 배송 추적의 배송완료 확인(#5 SHIPPING → DELIVERED)을 대신 일으킨다. 테스트 송장은 택배 조회에 이벤트가 없어
 * 자동 전환이 되지 않고, 운영자 배송완료 판정은 추적 정지 배지가 있어야 열린다.
 */
@Service
@RequiredArgsConstructor
public class TestOrderService {

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderFulfillmentService fulfillmentService;

    /** 배송중 하위주문만 전이한다 — 자동 확인과 같은 UPDATE 라 출처 · 구매확정 타이머도 실제 배송완료와 같다. */
    @Transactional
    public TestOrderDto.DeliveredResponse markDelivered(String orderNumber) {
        List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderNumber(orderNumber);
        if (groups.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        List<String> delivered = new ArrayList<>();
        for (OrderDeliveryGroup group : groups) {
            if (group.getFulfillmentStatus() != FulfillmentStatus.SHIPPING) {
                continue;
            }
            if (deliveryGroupRepository.markDeliveredByTracker(group.getId(), group.getCarrier(),
                    group.getTrackingNumber(), now) == 1) {
                fulfillmentService.appendHistory(group.getId(), FulfillmentEventType.DELIVERED,
                        FulfillmentActorType.TRACKER, null, "테스트 처리", now);
                delivered.add(group.getSubOrderNumber());
            }
        }
        if (delivered.isEmpty()) {
            String current = groups.stream()
                    .map(group -> group.getSubOrderNumber() + " " + group.getFulfillmentStatus())
                    .collect(Collectors.joining(", "));
            throw new BusinessException(ErrorCode.ORDER_STATE_CHANGED,
                    "배송중(SHIPPING)인 하위주문이 없습니다. 현재: " + current);
        }
        return new TestOrderDto.DeliveredResponse(orderNumber, delivered, now);
    }
}
