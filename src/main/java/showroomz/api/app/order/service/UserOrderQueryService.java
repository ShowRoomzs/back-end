package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 소비자 앱 주문 내역 목록(C10 설계서 2절). 페이지 단위는 <b>주문</b>, 행은 주문 항목이다 — 하위주문 층은 응답에 드러내지 않고
 * 항목이 자기 그룹의 이행 상태를 물려받는다(0-2). 쿼리는 테이블당 IN 1번씩 고정이다(2-4) — 주문 수에 비례하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserOrderQueryService {

    /** 취소 요청(검토 중·반려)이 표시에 영향을 주는 그룹 상태 — 그 밖의 페이지는 요청을 읽지 않는다. */
    private static final Set<FulfillmentStatus> REQUEST_RELEVANT = EnumSet.of(FulfillmentStatus.NEW,
            FulfillmentStatus.PREPARING, FulfillmentStatus.SHIPPING, FulfillmentStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderRefundTaskRepository refundTaskRepository;
    private final PaymentRepository paymentRepository;
    private final OrderAssembler orderAssembler;
    private final UserOrderItemAssembler itemAssembler;
    private final OrderProperties orderProperties;

    public PageResponse<UserOrderDto.OrderCard> getOrders(Long userId, int page, int size) {
        // page 는 1 미만이면 첫 페이지로 보정하지만 size 는 보정하지 않는다(파트너센터 목록과 같은 규칙).
        int max = orderProperties.getUserListPageSizeMax();
        if (size < 1 || size > max) {
            throw new BusinessException(ErrorCode.ORDER_PAGE_SIZE_INVALID,
                    "페이지 크기는 1~" + max + " 사이로 입력해 주세요.");
        }
        LocalDateTime since = LocalDateTime.now().minusMonths(orderProperties.getUserListMonths());
        Page<Order> orders = orderRepository.findPaidByUser(userId, since, PageRequest.of(Math.max(page - 1, 0), size));
        if (orders.isEmpty()) {
            return new PageResponse<>(List.of(), orders);
        }

        List<Long> orderIds = orders.getContent().stream().map(Order::getId).toList();
        Map<Long, List<OrderProduct>> productsByOrder = orderProductRepository.findByOrderIdsWithGroup(orderIds).stream()
                .collect(Collectors.groupingBy(p -> p.getOrder().getId(), LinkedHashMap::new, Collectors.toList()));
        List<OrderProduct> products = productsByOrder.values().stream().flatMap(List::stream).toList();

        UserOrderItemAssembler.Context context = UserOrderItemAssembler.Context.of(
                loadCancelRequests(products), loadRefundPendingGroupIds(products),
                cancellableOrderIds(orders.getContent(), productsByOrder));

        List<UserOrderDto.OrderCard> cards = orders.getContent().stream()
                .map(order -> UserOrderDto.OrderCard.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .orderedAt(order.getCreatedAt())
                        .items(productsByOrder.getOrDefault(order.getId(), List.of()).stream()
                                .map(p -> itemAssembler.toRow(p, context, UserOrderItemAssembler.View.LIST))
                                .toList())
                        .build())
                .toList();
        return new PageResponse<>(cards, orders);
    }

    /** 살아 있는 항목이 요청 가능·반려 노출 구간에 있을 때만 — 전부 취소·구매확정인 페이지는 빈 IN 을 보내지 않는다. */
    private List<OrderCancelRequest> loadCancelRequests(List<OrderProduct> products) {
        Set<Long> orderIds = products.stream()
                .filter(p -> p.getStatus() != OrderProductStatus.CANCELLED && p.getDeliveryGroup() != null
                        && REQUEST_RELEVANT.contains(p.getDeliveryGroup().getFulfillmentStatus()))
                .map(p -> p.getOrder().getId())
                .collect(Collectors.toSet());
        return orderIds.isEmpty() ? List.of() : cancelRequestRepository.findOpenOrRejectedByOrderIds(orderIds);
    }

    /** 운영자 환불 큐를 타는 취소(브랜드 승인·직권)가 있는 주문만 — 소비자 취소는 PG 자동이라 큐가 없다. */
    private Set<Long> loadRefundPendingGroupIds(List<OrderProduct> products) {
        Set<Long> orderIds = products.stream()
                .filter(p -> p.getStatus() == OrderProductStatus.CANCELLED
                        && p.getCancelType() != null && p.getCancelType() != OrderCancelType.CONSUMER)
                .map(p -> p.getOrder().getId())
                .collect(Collectors.toSet());
        return orderIds.isEmpty() ? Set.of() : new HashSet<>(refundTaskRepository.findPendingGroupIdsByOrderIds(orderIds));
    }

    /**
     * 전액 취소가 되는 주문 — 상세의 {@code cancellable}과 같은 판정({@code OrderAssembler.isCancellable}).
     * 결제 행은 PAID 주문의 것만 한 번에 읽는다. 그룹은 항목에서 모은다 — 항목 없는 하위주문은 없다.
     */
    private Set<Long> cancellableOrderIds(List<Order> orders, Map<Long, List<OrderProduct>> productsByOrder) {
        List<Order> candidates = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PAID && o.getPaidPaymentId() != null)
                .toList();
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Map<String, Payment> payments = paymentRepository
                .findAllById(candidates.stream().map(Order::getPaidPaymentId).toList()).stream()
                .collect(Collectors.toMap(Payment::getPaymentId, Function.identity()));
        Set<Long> cancellable = new HashSet<>();
        for (Order order : candidates) {
            List<OrderProduct> products = productsByOrder.getOrDefault(order.getId(), List.of());
            Set<OrderDeliveryGroup> groups = products.stream()
                    .map(OrderProduct::getDeliveryGroup).filter(Objects::nonNull).collect(Collectors.toSet());
            if (orderAssembler.isCancellable(order, payments.get(order.getPaidPaymentId()), products, groups)) {
                cancellable.add(order.getId());
            }
        }
        return cancellable;
    }
}
