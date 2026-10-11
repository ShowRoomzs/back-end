package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.order.dto.UserCancelDto;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.service.OrderCancelRequestService;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 소비자 취소 요청 · 취소 상세(C10 1b~1d · 1009 기획 수정본 3-1). 상세는 항목 기준이다 — 주문 내역의 [취소 상세] 버튼이 항목에
 * 붙고, 그 항목이 어떤 경로로 취소됐는지(요청 · 직접 취소 · 직권 취소)에 따라 같은 화면이 다른 값을 그린다.
 */
@Service
@RequiredArgsConstructor
public class UserCancelService {

    static final String NOTICE_AUTO_APPROVE = "브랜드가 1영업일(주말·공휴일 제외) 안에 확인하지 않으면 자동으로 취소돼요";
    static final String NOTICE_SHIPMENT_HOLD = "취소 요청이 처리될 때까지 이 주문의 상품은 발송되지 않아요";
    static final String NOTICE_REJECTED = "브랜드가 취소 요청을 거부해 상품이 발송돼요. 받은 뒤 반품을 신청할 수 있어요";

    private final OrderCancelRequestService cancelRequestService;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderRefundTaskRepository refundTaskRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public UserCancelDto.DetailResponse request(Long userId, Long orderId, UserCancelDto.CreateRequest request) {
        OrderCancelRequest created = cancelRequestService.request(userId, orderId, request.getDeliveryGroupId(),
                request.getOrderProductIds(), request.getReasonCode(), request.getReasonDetail(), LocalDateTime.now());
        return toRequestDetail(created.getOrder(), created);
    }

    @Transactional(readOnly = true)
    public UserCancelDto.DetailResponse getDetail(Long userId, Long orderId, Long orderProductId) {
        Order order = orderRepository.findById(orderId)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        OrderProduct product = orderProductRepository.findById(orderProductId)
                .filter(found -> found.getOrder().getId().equals(orderId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_PRODUCT_NOT_FOUND));
        // 요청 경로 — 그 항목이 들어간 가장 최근 요청(검토 중 · 승인 · 거부). 소비자 전액 취소로 닫힌 요청(VOIDED)은 건너뛴다.
        OrderCancelRequest request = cancelRequestRepository.findByOrderProductIdLatestFirst(orderProductId).stream()
                .filter(found -> found.getStatus() != CancelRequestStatus.VOIDED)
                .findFirst().orElse(null);
        if (request != null && (product.getCancelType() == null
                || product.getCancelType() == OrderCancelType.REQUEST_APPROVED)) {
            return toRequestDetail(order, request);
        }
        if (product.getStatus() != OrderProductStatus.CANCELLED || product.getCancelType() == null) {
            throw new BusinessException(ErrorCode.ORDER_PRODUCT_NOT_FOUND, "취소 내역이 없는 상품입니다.");
        }
        return product.getCancelType() == OrderCancelType.SELLER_DIRECT
                ? toSellerCancelDetail(order, product)
                : toConsumerCancelDetail(order, product);
    }

    // ------------------------------------------------------------------ 조립

    private UserCancelDto.DetailResponse toRequestDetail(Order order, OrderCancelRequest request) {
        UserCancelDto.Phase phase = switch (request.getStatus()) {
            case PENDING -> UserCancelDto.Phase.REVIEWING;
            case REJECTED -> UserCancelDto.Phase.REJECTED;
            default -> UserCancelDto.Phase.CANCELLED;
        };
        List<String> notices = new ArrayList<>();
        if (phase == UserCancelDto.Phase.REVIEWING) {
            notices.add(NOTICE_AUTO_APPROVE);
            notices.add(NOTICE_SHIPMENT_HOLD);
        } else if (phase == UserCancelDto.Phase.REJECTED) {
            notices.add(NOTICE_REJECTED);
        }
        List<UserCancelDto.Item> items = request.getItems().stream().map(this::toItem).toList();
        UserCancelDto.Refund refund = phase != UserCancelDto.Phase.CANCELLED ? null
                : refundOf(order, refundTaskRepository.findByDeliveryGroupIdOrderByIdAsc(request.getDeliveryGroup().getId())
                .stream()
                .filter(task -> task.getSource() == RefundTaskSource.CANCEL_REQUEST_APPROVED
                        && Objects.equals(task.getSourceId(), request.getId()))
                .findFirst().orElse(null), request.totalRefundAmount());
        return UserCancelDto.DetailResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .cancelRequestId(request.getId())
                .kind(UserCancelDto.Kind.REQUEST)
                .phase(phase)
                .phaseLabel(switch (phase) {
                    case REVIEWING -> "확인 중";
                    case REJECTED -> "취소 요청 거부";
                    case CANCELLED -> "취소 완료";
                })
                .autoApproved(request.isAutoApproved())
                .notices(notices)
                .requestedAt(request.getRequestedAt())
                .respondDueAt(phase == UserCancelDto.Phase.REVIEWING ? request.getRespondDueAt() : null)
                .decidedAt(request.getDecidedAt())
                .reasonLabel(request.getReasonCode().getLabel())
                .reasonDetail(request.getReasonDetail())
                .rejection(phase != UserCancelDto.Phase.REJECTED ? null : UserCancelDto.Rejection.builder()
                        .reasonLabel(request.getRejectReasonCode() != null ? request.getRejectReasonCode().getLabel()
                                : "기타")
                        .detail(request.getRejectReason())
                        .build())
                .items(items)
                .refund(refund)
                .build();
    }

    private UserCancelDto.DetailResponse toSellerCancelDetail(Order order, OrderProduct product) {
        OrderDeliveryGroup group = product.getDeliveryGroup();
        List<OrderProduct> cancelled = orderProductRepository.findByDeliveryGroupIds(List.of(group.getId())).stream()
                .filter(item -> item.getCancelType() == OrderCancelType.SELLER_DIRECT)
                .toList();
        OrderRefundTask task = refundTaskRepository.findByDeliveryGroupIdOrderByIdAsc(group.getId()).stream()
                .filter(found -> found.getSource() == RefundTaskSource.SELLER_DIRECT_CANCEL)
                .findFirst().orElse(null);
        long goods = cancelled.stream().mapToLong(item -> (long) item.getPrice() * item.getQuantity()).sum();
        return UserCancelDto.DetailResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .kind(UserCancelDto.Kind.SELLER_CANCEL)
                .phase(UserCancelDto.Phase.CANCELLED)
                .phaseLabel("판매 취소")
                .notices(List.of())
                .decidedAt(group.getCancelledAt())
                .reasonLabel(group.getCancelReasonCode() != null ? group.getCancelReasonCode().getLabel() : "판매 취소")
                .reasonDetail(group.getCancelReasonDetail())
                .items(cancelled.stream().map(this::toItem).toList())
                .refund(refundOf(order, task, goods + group.getDeliveryFee()))
                .build();
    }

    private UserCancelDto.DetailResponse toConsumerCancelDetail(Order order, OrderProduct product) {
        List<OrderProduct> cancelled = orderProductRepository.findByOrderIdWithVariant(order.getId()).stream()
                .filter(item -> item.getCancelType() == OrderCancelType.CONSUMER)
                .toList();
        Payment payment = order.getPaidPaymentId() == null ? null
                : paymentRepository.findById(order.getPaidPaymentId()).orElse(null);
        boolean done = payment != null && (payment.getStatus() == PaymentStatus.CANCELLED);
        return UserCancelDto.DetailResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .kind(UserCancelDto.Kind.CONSUMER_CANCEL)
                .phase(UserCancelDto.Phase.CANCELLED)
                .phaseLabel("취소 완료")
                .notices(List.of())
                .decidedAt(product.getCancelledAt())
                .reasonLabel(order.getCancelReason() != null ? order.getCancelReason() : "주문 취소")
                .items(cancelled.stream().map(this::toItem).toList())
                .refund(UserCancelDto.Refund.builder()
                        .amount(payment == null ? null : payment.getAmount().longValue())
                        .status(done ? "DONE" : "PROCESSING")
                        .statusLabel(done ? "환불 완료" : "환불 처리 중")
                        .methodLabel(payment == null ? null : payment.methodLabel() + " 결제 취소")
                        .refundedAt(done ? product.getCancelledAt() : null)
                        .build())
                .build();
    }

    private UserCancelDto.Refund refundOf(Order order, OrderRefundTask task, long expected) {
        Payment payment = order.getPaidPaymentId() == null ? null
                : paymentRepository.findById(order.getPaidPaymentId()).orElse(null);
        boolean done = task != null && task.getStatus() == RefundTaskStatus.DONE;
        return UserCancelDto.Refund.builder()
                .amount(task != null ? task.getRefundAmount().longValue() : expected)
                .status(done ? "DONE" : "PROCESSING")
                .statusLabel(done ? "환불 완료" : "환불 처리 중")
                .methodLabel(payment == null ? null : payment.methodLabel() + " 결제 취소")
                .refundedAt(done ? task.getExecutedAt() : null)
                .build();
    }

    private UserCancelDto.Item toItem(OrderCancelRequestItem item) {
        OrderProduct product = item.getOrderProduct();
        return UserCancelDto.Item.builder()
                .orderProductId(product.getId())
                .productName(product.getProductName())
                .optionName(product.getOptionName())
                .thumbnailUrl(product.getImageUrl())
                .quantity(item.getQuantity())
                .amount(item.getRefundAmount().longValue())
                .build();
    }

    private UserCancelDto.Item toItem(OrderProduct product) {
        return UserCancelDto.Item.builder()
                .orderProductId(product.getId())
                .productName(product.getProductName())
                .optionName(product.getOptionName())
                .thumbnailUrl(product.getImageUrl())
                .quantity(product.getQuantity())
                .amount((long) product.getPrice() * product.getQuantity())
                .build();
    }
}
