package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.AdminOrderSearchCondition;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderFulfillmentHistoryRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.type.AdminOrderTab;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 어드민 주문 조회(06a · 1009 기획 수정본 8-1) — 전 브랜드 · 전 공구 주문의 조회 입구. 운영자 조치는 상세의 {@code actions}로만
 * 시작한다. 금액 · 상태 · 기한은 파트너센터와 같은 원천(하위주문 · 취소 요청 · 환불 큐)을 읽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderRefundTaskRepository refundTaskRepository;
    private final OrderFulfillmentHistoryRepository historyRepository;
    private final PaymentRepository paymentRepository;
    private final OrderProperties orderProperties;
    private final ActOnBehalfPolicy actOnBehalfPolicy;

    public PageResponse<AdminOrderDto.ListItem> getOrders(AdminOrderDto.SearchParams params, LocalDate from,
                                                          LocalDate to, PagingRequest paging) {
        int size = paging.getSize();
        if (size < 1 || size > orderProperties.getListPageSizeMax()) {
            throw new BusinessException(ErrorCode.ORDER_PAGE_SIZE_INVALID);
        }
        Pageable pageable = PageRequest.of(Math.max(paging.getPage() - 1, 0), size);
        AdminOrderSearchCondition condition = condition(params, from, to);
        Page<Long> ids = deliveryGroupRepository.searchOrderIdsForAdmin(condition, pageable);
        LocalDateTime now = LocalDateTime.now();
        List<Long> orderIds = ids.getContent();
        Map<Long, List<OrderDeliveryGroup>> groupsByOrder = orderIds.isEmpty() ? Map.of()
                : orderIds.stream().collect(Collectors.toMap(id -> id, deliveryGroupRepository::findByOrderId));
        Set<Long> pendingGroupIds = pendingCancelGroupIds(groupsByOrder.values().stream().flatMap(List::stream).toList());
        Map<Long, Order> orders = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, order -> order));
        List<AdminOrderDto.ListItem> rows = orderIds.stream().map(id -> {
            Order order = orders.get(id);
            List<AdminOrderDto.GroupSummary> groups = groupsByOrder.getOrDefault(id, List.of()).stream()
                    .map(group -> summary(group, pendingGroupIds.contains(group.getId()), now)).toList();
            int attention = (int) groups.stream().filter(g -> g.trackingAlert() != null || g.shipOverdue()
                    || g.cancelRequested() || g.status() == FulfillmentStatus.RETURNING).count();
            return new AdminOrderDto.ListItem(order.getId(), order.getOrderNumber(), order.getPaidAt(),
                    order.getRecipientName(), order.getTotalAmount(), groups, attention);
        }).toList();
        return PageResponse.of(new PageImpl<>(rows, pageable, ids.getTotalElements()));
    }

    /** 탭 건수 — 브랜드 · 기간 · 검색은 같고 탭만 바꿔 센다(상태 셀렉트는 무시). */
    public AdminOrderDto.SummaryResponse getSummary(AdminOrderDto.SearchParams params, LocalDate from, LocalDate to) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AdminOrderTab tab : AdminOrderTab.values()) {
            AdminOrderDto.SearchParams byTab = new AdminOrderDto.SearchParams(tab, null, params.marketId(),
                    params.keyword());
            counts.put(tab.name(), deliveryGroupRepository.countOrdersForAdmin(condition(byTab, from, to)));
        }
        return new AdminOrderDto.SummaryResponse(counts);
    }

    public AdminOrderDto.DetailResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(found -> found.getPaidAt() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderId(orderId);
        Map<Long, List<OrderProduct>> itemsByGroup = orderProductRepository
                .findByDeliveryGroupIds(groups.stream().map(OrderDeliveryGroup::getId).toList()).stream()
                .collect(Collectors.groupingBy(item -> item.getDeliveryGroup().getId()));
        Map<Long, OrderCancelRequest> pending = groups.isEmpty() ? Map.of()
                : cancelRequestRepository.findPendingByDeliveryGroupIds(
                        groups.stream().map(OrderDeliveryGroup::getId).toList()).stream()
                .collect(Collectors.toMap(request -> request.getDeliveryGroup().getId(), request -> request,
                        (a, b) -> a));
        Users user = order.getUser();
        AdminOrderDto.Payment payment = order.getPaidPaymentId() == null ? null
                : paymentRepository.findById(order.getPaidPaymentId()).map(p -> new AdminOrderDto.Payment(
                        p.getPaymentId(), p.getStatus().name(), p.methodLabel(), p.getAmount(), p.getCancelledAmount(),
                        p.getPaidAt())).orElse(null);
        return new AdminOrderDto.DetailResponse(
                order.getId(), order.getOrderNumber(), order.getStatus().name(), order.getPaidAt(),
                new AdminOrderDto.Consumer(user == null ? null : user.getId(),
                        user == null ? null : (user.getName() != null ? user.getName() : user.getNickname()),
                        user == null ? null : user.getEmail()),
                new AdminOrderDto.Recipient(order.getRecipientName(), order.getRecipientPhone(), order.getZipCode(),
                        order.getAddress(), order.getDetailAddress(), order.getDeliveryMemo()),
                payment,
                groups.stream().map(group -> detail(group, itemsByGroup.getOrDefault(group.getId(), List.of()),
                        pending.get(group.getId()), now)).toList());
    }

    // ------------------------------------------------------------------ 조립

    private AdminOrderDto.GroupSummary summary(OrderDeliveryGroup group, boolean cancelRequested, LocalDateTime now) {
        FulfillmentStatus status = group.getFulfillmentStatus();
        return new AdminOrderDto.GroupSummary(group.getId(), group.getSubOrderNumber(), group.getMarketName(), status,
                status.getLabel(), status.getTone(), group.getTrackingAlert(),
                group.getTrackingAlert() == null ? null : group.getTrackingAlert().getLabel(), group.getShipDueAt(),
                ActOnBehalfPolicy.isShipOverdue(group, now), cancelRequested);
    }

    private AdminOrderDto.GroupDetail detail(OrderDeliveryGroup group, List<OrderProduct> items,
                                             OrderCancelRequest pendingRequest, LocalDateTime now) {
        FulfillmentStatus status = group.getFulfillmentStatus();
        int confirmDays = orderProperties.getPurchaseConfirmDays();
        boolean paused = group.getConfirmPausedAt() != null;
        AdminOrderDto.PurchaseConfirm purchaseConfirm = group.confirmBaseAt() == null ? null
                : new AdminOrderDto.PurchaseConfirm(paused, group.confirmRemainingDays(confirmDays, now),
                paused || group.getConfirmedAt() != null ? null : group.confirmBaseAt().plusDays(confirmDays),
                group.getConfirmedAt());
        List<OrderRefundTask> refunds = refundTaskRepository.findByDeliveryGroupIdOrderByIdAsc(group.getId());
        return new AdminOrderDto.GroupDetail(
                group.getId(), group.getSubOrderNumber(), group.getMarketId(), group.getMarketName(),
                group.getGroupBuyNumber(), status, status.getLabel(), status.getTone(),
                new AdminOrderDto.Shipping(group.getShipDueAt(), group.getShipDueBusinessDays(),
                        ActOnBehalfPolicy.isShipOverdue(group, now), group.getOverdueNoticeCount(), group.getPrepareStartedAt(),
                        group.getShippedAt(), group.getCarrier(),
                        group.getCarrier() == null ? null : group.getCarrier().getLabel(), group.getTrackingNumber(),
                        group.getTrackingAlert(),
                        group.getTrackingAlert() == null ? null : group.getTrackingAlert().getLabel(),
                        group.getLastTrackingAt(), group.getReturnDetectedAt(), group.getReturnCompletedAt(),
                        group.getDeliveredAt(),
                        group.getDeliveredSource() == null ? null : group.getDeliveredSource().getLabel()),
                purchaseConfirm,
                group.getCancelledAt() == null ? null : new AdminOrderDto.Cancel(group.getCancelledAt(),
                        group.getCancelType() == null ? null : group.getCancelType().getLabel(),
                        group.getCancelReasonCode() == null ? null : group.getCancelReasonCode().getLabel(),
                        group.getCancelReasonDetail()),
                pendingRequest == null ? null : new AdminOrderDto.CancelRequest(pendingRequest.getId(),
                        pendingRequest.getReasonCode().getLabel(), pendingRequest.getReasonDetail(),
                        pendingRequest.getRequestedAt(), pendingRequest.getRespondDueAt(),
                        pendingRequest.getItems().stream().map(OrderCancelRequestItem::getOrderProduct)
                                .map(OrderProduct::getId).toList()),
                items.stream().sorted(Comparator.comparing(OrderProduct::getId)).map(item -> new AdminOrderDto.Item(
                        item.getId(), item.getProductName(), item.getOptionName(), item.getQuantity(),
                        item.getReturnedQuantity(), item.getPrice(), item.getStatus().name(),
                        item.getCancelType() == null ? null : item.getCancelType().getLabel())).toList(),
                refunds.stream().map(task -> new AdminOrderDto.Refund(task.getId(), task.getSource(), task.getOrigin(),
                        task.getOrigin().getLabel(), task.getRefundAmount(), task.getStatus(), task.getLastError(),
                        task.getExecutedAt(), task.getCreatedAt())).toList(),
                historyRepository.findByDeliveryGroupId(group.getId()).stream()
                        .map(h -> new AdminOrderDto.History(h.getEventType().name(), h.getEventType().getLabel(),
                                h.getActorType().name(), h.getDetail(), h.getOccurredAt())).toList(),
                actions(group, pendingRequest != null, now));
    }

    AdminOrderDto.Actions actions(OrderDeliveryGroup group, boolean cancelRequested, LocalDateTime now) {
        FulfillmentStatus status = group.getFulfillmentStatus();
        boolean actOnBehalf = actOnBehalfPolicy.canActOnBehalf(group, now);
        boolean deliveredWithin3Months = group.getDeliveredAt() != null
                && !group.getDeliveredAt().plusMonths(3).isBefore(now);
        return new AdminOrderDto.Actions(
                status == FulfillmentStatus.DELIVERED,
                status == FulfillmentStatus.PREPARING && actOnBehalf && !cancelRequested,
                FulfillmentStatus.WORKABLE.contains(status) && !cancelRequested,
                status == FulfillmentStatus.SHIPPING || status == FulfillmentStatus.DELIVERED
                        || status == FulfillmentStatus.CONFIRMED || status == FulfillmentStatus.RETURNING,
                (status == FulfillmentStatus.CONFIRMED || status == FulfillmentStatus.DELIVERED)
                        && deliveredWithin3Months);
    }

    private Set<Long> pendingCancelGroupIds(List<OrderDeliveryGroup> groups) {
        if (groups.isEmpty()) {
            return Set.of();
        }
        return cancelRequestRepository.findPendingByDeliveryGroupIds(groups.stream().map(OrderDeliveryGroup::getId)
                .toList()).stream().map(request -> request.getDeliveryGroup().getId()).collect(Collectors.toSet());
    }

    private static AdminOrderSearchCondition condition(AdminOrderDto.SearchParams params, LocalDate from,
                                                       LocalDate to) {
        return new AdminOrderSearchCondition(params.tab() == null ? AdminOrderTab.ALL : params.tab(), params.status(),
                params.marketId(), params.keyword(), from == null ? null : from.atStartOfDay(),
                to == null ? null : to.atTime(23, 59, 59));
    }
}
