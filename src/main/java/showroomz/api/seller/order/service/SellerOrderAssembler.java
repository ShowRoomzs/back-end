package showroomz.api.seller.order.service;

import org.springframework.stereotype.Component;
import showroomz.api.seller.order.dto.SellerOrderDetailResponse;
import showroomz.api.seller.order.dto.SellerOrderListItem;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderFulfillmentHistory;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 목록 행·상세 모달 응답 조립(34 설계서 4-1 · 4-3). 라벨·톤은 enum 에서 그대로 읽는다 — FE 가 매핑하지 않는다.
 * 발송기한 경과·구매확정 예정 D-N 은 파생값이라 여기서 계산한다(설계서 0-5).
 */
@Component
public class SellerOrderAssembler {

    public SellerOrderListItem toListItem(OrderDeliveryGroup group, String orderNumber, LocalDateTime paidAt,
                                          String recipientName, String groupBuyTitle, List<OrderProduct> items,
                                          OrderCancelRequest pendingRequest, LocalDateTime now, int confirmDays) {
        FulfillmentStatus status = group.getFulfillmentStatus();
        Set<Long> requestedProductIds = requestedProductIds(pendingRequest);
        String firstName = items.isEmpty() ? null : items.get(0).getProductName();
        String productSummary = firstName == null ? null
                : items.size() == 1 ? firstName : firstName + " 외 " + (items.size() - 1) + "건";
        int totalQuantity = items.stream().mapToInt(OrderProduct::getQuantity).sum();
        return new SellerOrderListItem(
                group.getId(),
                orderNumber,
                group.getSubOrderNumber(),
                groupBuyTitle,
                recipientName,
                productSummary,
                totalQuantity,
                status,
                status.getLabel(),
                status.getTone(),
                overlays(group, pendingRequest != null, now),
                paidAt,
                group.getShipDueAt(),
                group.getCarrier(),
                group.getCarrier() == null ? null : group.getCarrier().getLabel(),
                group.getTrackingNumber(),
                group.getLastTrackingAt(),
                group.getDeliveredAt(),
                group.getDeliveredSource() == null ? null : group.getDeliveredSource().getLabel(),
                confirmRemainingDays(group, now, confirmDays),
                group.getConfirmedAt(),
                group.getProductTotal() + group.getDeliveryFee(),
                null, // 정산 — 정산 모듈 전이라 null. 0이 아니다(설계서 0-6)
                group.getCancelledAt(),
                group.getCancelType() == null ? null : group.getCancelType().getLabel(),
                cancelRequestSummary(pendingRequest, items, now),
                items.stream().map(item -> toItem(item, status, requestedProductIds)).toList());
    }

    public SellerOrderDetailResponse toDetail(OrderDeliveryGroup group, String groupBuyTitle, String paymentMethod,
                                              List<OrderProduct> items, OrderCancelRequest pendingRequest,
                                              List<OrderFulfillmentHistory> history, LocalDateTime now,
                                              int confirmDays) {
        FulfillmentStatus status = group.getFulfillmentStatus();
        Set<Long> requestedProductIds = requestedProductIds(pendingRequest);
        var order = group.getOrder();
        boolean pendingCancel = pendingRequest != null;
        int cancelledAmount = items.stream()
                .filter(item -> item.getStatus() == OrderProductStatus.CANCELLED)
                .mapToInt(item -> item.getPrice() * item.getQuantity())
                .sum();
        return new SellerOrderDetailResponse(
                group.getId(),
                order.getOrderNumber(),
                group.getSubOrderNumber(),
                order.getPaidAt(),
                group.getGroupBuyId(),
                groupBuyTitle,
                paymentMethod,
                status,
                status.getLabel(),
                status.getTone(),
                overlays(group, pendingCancel, now),
                new SellerOrderDetailResponse.Recipient(order.getRecipientName(), order.getRecipientPhone(),
                        order.getZipCode(), order.getAddress(), order.getDetailAddress(), order.getDeliveryMemo()),
                items.stream().map(item -> toItem(item, status, requestedProductIds)).toList(),
                new SellerOrderDetailResponse.Amounts(
                        group.getProductTotal(),
                        group.getDeliveryFee(),
                        group.getProductTotal() + group.getDeliveryFee(),
                        pendingRequest == null ? null : pendingRequest.totalRefundAmount(),
                        cancelledAmount),
                timeline(group, now, confirmDays),
                cancelRequestBlock(group, pendingRequest, items),
                actions(group, pendingCancel),
                history.stream().map(h -> new SellerOrderDetailResponse.HistoryItem(
                        h.getEventType().name(), h.getEventType().getLabel(), h.getActorType().name(),
                        h.getActorType().getLabel(), h.getDetail(), h.getOccurredAt())).toList());
    }

    // ------------------------------------------------------------------ 내부

    private SellerOrderListItem.Overlays overlays(OrderDeliveryGroup group, boolean pendingCancel, LocalDateTime now) {
        // 발송기한 경과와 배송 이상은 별 축 — 한 행에 둘 다 뜰 수 있다(§34-6). 발송된 건은 경과를 따지지 않는다(판정 = 송장 등록 시각).
        boolean shipOverdue = FulfillmentStatus.WORKABLE.contains(group.getFulfillmentStatus())
                && group.getShipDueAt() != null && group.getShipDueAt().isBefore(now);
        return new SellerOrderListItem.Overlays(
                pendingCancel,
                group.getTrackingAlert(),
                group.getTrackingAlert() == null ? null : group.getTrackingAlert().getLabel(),
                shipOverdue);
    }

    private SellerOrderListItem.Item toItem(OrderProduct item, FulfillmentStatus groupStatus,
                                            Set<Long> requestedProductIds) {
        boolean cancelled = item.getStatus() == OrderProductStatus.CANCELLED;
        String label = cancelled ? "취소"
                : item.getStatus() == OrderProductStatus.PURCHASE_CONFIRMED ? "구매확정"
                : groupStatus.getLabel();
        return new SellerOrderListItem.Item(
                item.getId(), item.getProductName(), item.getOptionName(), item.getQuantity(), item.getPrice(),
                item.getPrice() * item.getQuantity(), label, cancelled, requestedProductIds.contains(item.getId()));
    }

    private SellerOrderListItem.CancelRequestSummary cancelRequestSummary(OrderCancelRequest request,
                                                                          List<OrderProduct> items,
                                                                          LocalDateTime now) {
        if (request == null) {
            return null;
        }
        long requested = request.getItems().size();
        long remaining = items.stream()
                .filter(item -> item.getStatus() != OrderProductStatus.CANCELLED)
                .filter(item -> !requestedProductIds(request).contains(item.getId()))
                .count();
        String summary = "%d건 중 %d건 요청 · 남은 %d건 발송 대기".formatted(items.size(), requested, remaining);
        return new SellerOrderListItem.CancelRequestSummary(
                request.getId(),
                request.getReasonCode().getLabel(),
                request.getReasonDetail(),
                request.getRequestedAt(),
                Math.max(0, Duration.between(request.getRequestedAt(), now).toHours()),
                summary);
    }

    private SellerOrderDetailResponse.CancelRequestBlock cancelRequestBlock(OrderDeliveryGroup group,
                                                                            OrderCancelRequest request,
                                                                            List<OrderProduct> items) {
        if (request == null) {
            return null;
        }
        Set<Long> requestedIds = requestedProductIds(request);
        long remaining = items.stream()
                .filter(item -> item.getStatus() != OrderProductStatus.CANCELLED)
                .filter(item -> !requestedIds.contains(item.getId()))
                .count();
        Long hoursSincePrepare = group.getPrepareStartedAt() == null ? null
                : Math.max(0, Duration.between(group.getPrepareStartedAt(), request.getRequestedAt()).toHours());
        return new SellerOrderDetailResponse.CancelRequestBlock(
                request.getId(),
                request.getReasonCode().getLabel(),
                request.getReasonDetail(),
                request.getRequestedAt(),
                request.getStatusAtRequest().getLabel(),
                hoursSincePrepare,
                request.getItems().stream().map(this::toRequestItem).toList(),
                request.totalRefundAmount(),
                remaining);
    }

    private SellerOrderDetailResponse.CancelRequestBlock.RequestItem toRequestItem(OrderCancelRequestItem item) {
        OrderProduct product = item.getOrderProduct();
        return new SellerOrderDetailResponse.CancelRequestBlock.RequestItem(
                product.getId(), product.getProductName(), product.getOptionName(), item.getQuantity(),
                item.getRefundAmount());
    }

    private SellerOrderDetailResponse.Timeline timeline(OrderDeliveryGroup group, LocalDateTime now, int confirmDays) {
        return new SellerOrderDetailResponse.Timeline(
                group.getShipDueAt(),
                group.getPrepareStartedAt(),
                group.getShippedAt(),
                group.getCarrier(),
                group.getCarrier() == null ? null : group.getCarrier().getLabel(),
                group.getTrackingNumber(),
                group.getLastTrackingAt(),
                group.getReturnDetectedAt(),
                group.getDeliveredAt(),
                group.getDeliveredSource() == null ? null : group.getDeliveredSource().getLabel(),
                group.getDeliveredAt() == null ? null : group.getDeliveredAt().plusDays(confirmDays),
                group.getConfirmedAt(),
                group.getCancelledAt(),
                group.getCancelType() == null ? null : group.getCancelType().getLabel(),
                group.getCancelReasonCode() == null ? null : group.getCancelReasonCode().getLabel(),
                group.getCancelReasonDetail());
    }

    /**
     * 버튼 노출 규칙의 정본(§34-3). 배송완료 처리·요청 액션은 아예 없다 — 돈의 시점을 바꾸는 전이는
     * 브랜드 API 표면에 없다(설계서 0-3).
     */
    private SellerOrderDetailResponse.Actions actions(OrderDeliveryGroup group, boolean pendingCancel) {
        FulfillmentStatus status = group.getFulfillmentStatus();
        return new SellerOrderDetailResponse.Actions(
                status == FulfillmentStatus.NEW && !pendingCancel,
                status == FulfillmentStatus.PREPARING && !pendingCancel,
                status == FulfillmentStatus.SHIPPING,
                FulfillmentStatus.WORKABLE.contains(status) && !pendingCancel,
                pendingCancel);
    }

    private Integer confirmRemainingDays(OrderDeliveryGroup group, LocalDateTime now, int confirmDays) {
        if (group.getFulfillmentStatus() != FulfillmentStatus.DELIVERED || group.getDeliveredAt() == null) {
            return null;
        }
        long hours = Duration.between(now, group.getDeliveredAt().plusDays(confirmDays)).toHours();
        return (int) Math.max(0, (hours + 23) / 24);
    }

    private Set<Long> requestedProductIds(OrderCancelRequest request) {
        if (request == null) {
            return Set.of();
        }
        return request.getItems().stream()
                .map(item -> item.getOrderProduct().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
