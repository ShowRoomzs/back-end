package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.SellerCancelReason;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.ValidationResult;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 주문 조치(06a B3~B6 · 1009 기획 수정본 8-1). 운영자는 이해당사자의 일을 대신하지 않는다 — 대행은 조건(발송 기한 경과 ·
 * 자동 알림 3회 무응답)이 찼을 때만, 돈은 편입만 하고 집행은 환불 관리의 재확인에서만 나간다.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderCommandService {

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderRefundTaskRepository refundTaskRepository;
    private final PaymentRepository paymentRepository;
    private final OrderFulfillmentService fulfillmentService;
    private final OrderClaimService claimService;
    private final AdminOrderQueryService queryService;
    private final ActOnBehalfPolicy actOnBehalfPolicy;
    private final DeliveryTrackerPort tracker;

    /** B3 배송완료일 정정 — 소비자 수령일 이의. 「직권 배송완료」는 없다. 구매확정 기산점도 같은 만큼 옮긴다. */
    @Transactional
    public AdminOrderDto.DetailResponse correctDeliveredAt(Long adminId, Long deliveryGroupId,
                                                           AdminOrderDto.CorrectDeliveredAtRequest request) {
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup group = loadGroup(deliveryGroupId);
        if (group.getFulfillmentStatus() != FulfillmentStatus.DELIVERED || group.getDeliveredAt() == null) {
            throw new BusinessException(ErrorCode.ORDER_STATE_CHANGED, "배송완료 상태에서만 정정할 수 있습니다.");
        }
        if (request.deliveredAt().isAfter(now) || (group.getShippedAt() != null
                && request.deliveredAt().isBefore(group.getShippedAt()))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "수령일은 발송 이후 · 지금 이전이어야 합니다.");
        }
        LocalDateTime before = group.getDeliveredAt();
        LocalDateTime restart = group.getConfirmRestartAt();
        Long orderId = group.getOrder().getId();
        if (deliveryGroupRepository.correctDeliveredAt(deliveryGroupId, request.deliveredAt(), adminId) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_CHANGED);
        }
        if (restart != null) {
            deliveryGroupRepository.setConfirmRestartAt(deliveryGroupId,
                    restart.plus(Duration.between(before, request.deliveredAt())));
        }
        fulfillmentService.appendHistory(deliveryGroupId, FulfillmentEventType.DELIVERED_AT_CORRECTED,
                FulfillmentActorType.ADMIN, adminId, before.toLocalDate() + " → " + request.deliveredAt().toLocalDate()
                        + " · " + request.reason(), now);
        return queryService.getOrder(orderId);
    }

    /** B4 운영자 대행 송장 등록 — 상품준비중 ∧ 대행 조건(발송 기한 경과 · 자동 알림 무응답). 브랜드 등록과 같은 전이다. */
    @Transactional
    public AdminOrderDto.DetailResponse registerShipment(Long adminId, Long deliveryGroupId,
                                                         AdminOrderDto.ShipmentRequest request) {
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup group = loadGroup(deliveryGroupId);
        if (!actOnBehalfPolicy.canActOnBehalf(group, now)) {
            throw new BusinessException(ErrorCode.ORDER_ACT_ON_BEHALF_NOT_ALLOWED);
        }
        String trackingNumber = request.trackingNumber().replaceAll("[^0-9]", "");
        if (!request.carrier().isSelectable() || trackingNumber.isEmpty()
                || tracker.validateInvoice(request.carrier(), trackingNumber) == ValidationResult.INVALID) {
            throw new BusinessException(ErrorCode.INVOICE_FORMAT_INVALID);
        }
        boolean duplicate = deliveryGroupRepository.findActiveByInvoice(request.carrier(), trackingNumber,
                FulfillmentStatus.INVOICE_ACTIVE).stream().anyMatch(other -> !other.getId().equals(deliveryGroupId));
        if (duplicate) {
            throw new BusinessException(ErrorCode.INVOICE_DUPLICATE);
        }
        Long orderId = group.getOrder().getId();
        if (deliveryGroupRepository.registerInvoiceByAdmin(deliveryGroupId, request.carrier(), trackingNumber, now)
                != 1) {
            throw new BusinessException(cancelRequestRepository.existsPendingByGroup(deliveryGroupId)
                    ? ErrorCode.CANCEL_REQUEST_PENDING_EXISTS : ErrorCode.ORDER_STATE_CHANGED);
        }
        fulfillmentService.appendHistory(deliveryGroupId, FulfillmentEventType.INVOICE_REGISTERED,
                FulfillmentActorType.ADMIN, adminId, "운영자 대행 · " + request.carrier().getLabel() + " " + trackingNumber
                        + (request.note() == null || request.note().isBlank() ? "" : " · " + request.note()), now);
        return queryService.getOrder(orderId);
    }

    /**
     * B4 · B5 대행 직권 취소(미발송분) — 대행 조건을 채웠거나 위해성 사유(상품 하자)일 때. 브랜드 직권 취소와 같은 전이 · 재고 원복 ·
     * PG 즉시 자동 환불(배송비 포함 전액).
     */
    @Transactional
    public AdminOrderDto.DetailResponse cancel(Long adminId, Long deliveryGroupId, AdminOrderDto.CancelCommand request) {
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup group = loadGroup(deliveryGroupId);
        if (request.reasonCode() != SellerCancelReason.DEFECT && !actOnBehalfPolicy.canActOnBehalf(group, now)) {
            throw new BusinessException(ErrorCode.ORDER_ACT_ON_BEHALF_NOT_ALLOWED);
        }
        Long orderId = group.getOrder().getId();
        List<OrderProduct> paidItems = orderProductRepository.findByDeliveryGroupIds(List.of(deliveryGroupId)).stream()
                .filter(item -> item.getStatus() == OrderProductStatus.PAID).toList();
        if (deliveryGroupRepository.cancelByAdmin(deliveryGroupId, request.reasonCode(), request.consumerMessage(), now)
                != 1) {
            throw new BusinessException(cancelRequestRepository.existsPendingByGroup(deliveryGroupId)
                    ? ErrorCode.CANCEL_REQUEST_PENDING_EXISTS : ErrorCode.ORDER_STATE_CHANGED);
        }
        List<OrderProduct> cancelled = fulfillmentService.cancelItemsWithRestock(paidItems,
                OrderCancelType.SELLER_DIRECT, now);
        OrderDeliveryGroup reloaded = loadGroup(deliveryGroupId);
        int refundAmount = cancelled.stream().mapToInt(item -> item.getPrice() * item.getQuantity()).sum()
                + reloaded.getDeliveryFee();
        fulfillmentService.enqueueRefund(reloaded, RefundTaskSource.SELLER_DIRECT_CANCEL, null, refundAmount);
        fulfillmentService.appendHistory(deliveryGroupId, FulfillmentEventType.CANCELLED_BY_SELLER,
                FulfillmentActorType.ADMIN, adminId, "운영자 대행 · " + request.reasonCode().getLabel() + " · "
                        + request.consumerMessage(), now);
        return queryService.getOrder(orderId);
    }

    /**
     * B5 운영자 사유 환불 편입 — 위해성 리콜(발송분) · 구매확정 후 하자 등. <b>편입만</b> 한다 — 돈은 환불 관리(06c)의 재확인
     * 다이얼로그에서만 나간다. 결제의 취소 가능 잔액(아직 나가지 않은 환불 포함)을 넘지 못한다.
     */
    @Transactional
    public AdminOrderDto.DetailResponse enqueueRefund(Long adminId, Long deliveryGroupId,
                                                      AdminOrderDto.OperatorRefundRequest request) {
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup group = deliveryGroupRepository.findForUpdate(deliveryGroupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        FulfillmentStatus status = group.getFulfillmentStatus();
        if (status == FulfillmentStatus.PENDING || status == FulfillmentStatus.NEW
                || status == FulfillmentStatus.PREPARING || status == FulfillmentStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_STATE_CHANGED, "발송 전 주문은 직권 취소로 환불합니다.");
        }
        checkRefundable(group, request.amount());
        Long orderId = group.getOrder().getId();
        fulfillmentService.enqueueOperatorRefund(group, null, request.amount(), request.reason(),
                request.detail(), adminId, now);
        return queryService.getOrder(orderId);
    }

    /** B6 구매확정 후 하자 · 반품 대신 열기 — 이후는 일반 반품과 같다(회수 송장은 소비자 · 검수는 브랜드). */
    @Transactional
    public AdminOrderDto.DefectClaimResponse openDefectClaim(Long adminId, Long deliveryGroupId,
                                                             AdminOrderDto.DefectClaimRequest request) {
        OrderClaimService.RequestResult result = claimService.openDefectClaimByOperator(adminId, deliveryGroupId,
                request.items().stream()
                        .map(item -> new OrderClaimService.Item(item.orderProductId(), item.quantity())).toList(),
                request.reasonCode(), request.detail(), request.evidenceImageUrls(), LocalDateTime.now());
        return new AdminOrderDto.DefectClaimResponse(result.collectionId(), result.claimIds());
    }

    /** 운영자 사유 환불의 상한 — 결제의 취소 가능 잔액 − 아직 나가지 않은 환불. */
    void checkRefundable(OrderDeliveryGroup group, int amount) {
        String paymentId = group.getOrder().getPaidPaymentId();
        if (paymentId == null) {
            return; // 결제 밖 주문 — 운영자가 수동 기록한다.
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        long available = payment.cancellableAmount() - refundTaskRepository.sumOutstandingByPayment(paymentId);
        if (amount > available) {
            throw new BusinessException(ErrorCode.REFUND_AMOUNT_EXCEEDED,
                    "환불액이 취소 가능 잔액(" + String.format("%,d", Math.max(0, available)) + "원)보다 큽니다.");
        }
    }

    private OrderDeliveryGroup loadGroup(Long deliveryGroupId) {
        return deliveryGroupRepository.findById(deliveryGroupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
    }
}
