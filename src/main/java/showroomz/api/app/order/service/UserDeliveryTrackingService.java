package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.api.app.order.dto.UserOrderDto.TrackingContext;
import showroomz.api.app.order.dto.UserOrderDto.TrackingState;
import showroomz.domain.order.service.ShipDuePolicy;
import showroomz.domain.order.entity.DeliveryTrackingEvent;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.DeliveryTrackingEventRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.service.DeliveryArrivalEstimator;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * C10-2 배송 조회(앱 클레임 설계서 5-2) — 주문 항목이 든 하위주문의 송장. <b>저장해 둔 스캔 이력만 읽는다</b> —
 * 화면이 택배 API 를 부르지 않는다(1-6). 송장이 키인 화면이지만 경로는 소유자(주문 항목)로 연다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDeliveryTrackingService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM.dd");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final OrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;
    private final DeliveryTrackingEventRepository trackingEventRepository;
    private final DeliveryArrivalEstimator arrivalEstimator;

    public UserOrderDto.TrackingResponse trackOrderItem(Long userId, Long orderId, Long orderProductId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        OrderProduct product = orderProductRepository.findById(orderProductId)
                .filter(item -> item.getOrder().getId().equals(orderId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_PRODUCT_NOT_FOUND));

        OrderDeliveryGroup group = product.getDeliveryGroup();
        DeliveryCarrier carrier = group == null ? null : group.getCarrier();
        String trackingNumber = group == null ? null : group.getTrackingNumber();
        boolean hasInvoice = carrier != null && trackingNumber != null;

        List<DeliveryTrackingEvent> events = hasInvoice
                ? trackingEventRepository.findByCarrierAndTrackingNumberOrderBySeqDesc(carrier, trackingNumber)
                : List.of();
        TrackingState state = group != null && group.getDeliveredAt() != null ? TrackingState.DELIVERED
                : hasInvoice ? TrackingState.IN_TRANSIT : TrackingState.NOT_SHIPPED;

        return UserOrderDto.TrackingResponse.builder()
                .state(state)
                .context(TrackingContext.ORDER)
                .headline(headline(state, group, events))
                .stageIndex(stageIndex(state, events))
                .orderedAt(order.getCreatedAt())
                .orderId(order.getId())
                .item(UserOrderDto.TrackingItem.builder()
                        .brandName(group != null ? group.getMarketName() : null)
                        .productName(product.getProductName())
                        .optionName(product.getOptionName())
                        .quantity(product.getQuantity())
                        .amount((long) product.getPrice() * product.getQuantity())
                        .thumbnailUrl(product.getImageUrl())
                        .build())
                .carrier(hasInvoice ? UserOrderDto.TrackingCarrier.builder()
                        .code(carrier.name())
                        .label(carrier.getLabel())
                        .tel(carrier.getTel())
                        .trackingUrl(carrier.trackingUrl(trackingNumber))
                        .build() : null)
                .trackingNumber(hasInvoice ? trackingNumber : null)
                .scans(events.stream()
                        .map(event -> UserOrderDto.TrackingScan.builder()
                                .location(event.getLocation())
                                .description(event.getDescription())
                                .occurredAt(event.getOccurredAt())
                                .build())
                        .toList())
                .shipDueAt(state == TrackingState.NOT_SHIPPED && group != null ? group.getShipDueAt() : null)
                .groupBuyEndAt(state == TrackingState.NOT_SHIPPED && group != null && group.getGroupBuy() != null
                        ? group.getGroupBuy().getEndAt() : null)
                .build();
    }

    private UserOrderDto.TrackingHeadline headline(TrackingState state, OrderDeliveryGroup group,
                                                   List<DeliveryTrackingEvent> events) {
        return switch (state) {
            case NOT_SHIPPED -> UserOrderDto.TrackingHeadline.builder()
                    .text("배송 준비 중이에요")
                    // 공구 진행 중이면 기한이 아직 없다 — 약정 문구(마감 후 N영업일)를 그대로 보여 준다.
                    .sub(group == null ? null : group.getShipDueAt() == null
                            ? ShipDuePolicy.noticeText(group.getShipDueBusinessDays())
                            : group.getShipDueAt().format(DAY) + "까지 발송 예정이에요")
                    .build();
            case IN_TRANSIT -> {
                LocalDate due = arrivalDueDate(group);
                yield UserOrderDto.TrackingHeadline.builder()
                        .date(due)
                        .text(due != null ? "도착 예정이에요" : "상품이 배송중이에요")
                        .sub(events.isEmpty() ? null : lastScan(events.get(0)))
                        .build();
            }
            case DELIVERED -> UserOrderDto.TrackingHeadline.builder()
                    .date(group.getDeliveredAt().toLocalDate())
                    .text("상품 배송이 완료되었어요")
                    .build();
        };
    }

    /** 주문 내역의 「MM.dd 도착 예정」과 같은 날짜여야 한다 — {@code UserOrderItemAssembler}와 같은 규칙. */
    private LocalDate arrivalDueDate(OrderDeliveryGroup group) {
        LocalDate due = arrivalEstimator.estimate(group.getCarrier(), group.getPickedUpAt());
        return due == null || due.isBefore(LocalDate.now()) ? null : due;
    }

    private static String lastScan(DeliveryTrackingEvent event) {
        String at = event.getOccurredAt().format(DAY_TIME);
        return event.getLocation() == null || event.getLocation().isBlank() ? at : event.getLocation() + " · " + at;
    }

    /** 3구간 바 — 배송 시작(0) · 배송중(1) · 배송 완료(2). 송장이 없으면 전부 빈 칸(-1). */
    private static int stageIndex(TrackingState state, List<DeliveryTrackingEvent> events) {
        return switch (state) {
            case NOT_SHIPPED -> -1;
            case IN_TRANSIT -> events.isEmpty() ? 0 : 1;
            case DELIVERED -> 2;
        };
    }
}
