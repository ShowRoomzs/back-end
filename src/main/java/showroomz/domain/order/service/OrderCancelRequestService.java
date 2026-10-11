package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.CancelRejectReason;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 취소 요청(34 설계서 1-5 · 1009 기획 수정본 3절) — 소비자가 만들고, 브랜드가 승인 · 거부하고, <b>응답 기한(1영업일)이 지나면
 * 시스템이 자동 승인</b>한다. 승인은 사람이든 시스템이든 같은 메서드를 탄다 — 요청 항목 취소 · 재고 원복 · 환불 큐(PG 즉시 자동).
 *
 * <p>요청이 걸린 하위주문은 <b>전체</b>가 발송 보류된다(결정 13) — 준비 시작 · 송장 등록 · 직권 취소의 조건부 UPDATE 가
 * 「검토 중 요청 없음」을 WHERE 에 넣고 있다.
 */
@Service
@RequiredArgsConstructor
public class OrderCancelRequestService {

    private static final int DETAIL_MAX_LENGTH = 300;

    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderFulfillmentService fulfillmentService;
    private final BusinessDayCalculator businessDayCalculator;
    private final OrderProperties orderProperties;

    // ------------------------------------------------------------------ 소비자 요청

    /**
     * 취소 요청 — 하위주문이 신규 · 상품준비중이고 요청 항목이 전부 그 하위주문의 결제된 항목이어야 한다. 항목 전량이다(수량 쪼개기
     * 없음). 준비 시작 전 단순 취소는 주문 전체 취소 API 가 따로 있다 — 여기는 그 길이 막힌 경우(준비 시작 · 일부 항목)의 길이다.
     */
    @Transactional
    public OrderCancelRequest request(Long userId, Long orderId, Long deliveryGroupId, List<Long> orderProductIds,
                                      CancelRequestReason reasonCode, String reasonDetail, LocalDateTime now) {
        if (orderProductIds == null || orderProductIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "취소할 상품을 선택해 주세요.");
        }
        if (reasonCode == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "취소 사유를 선택해 주세요.");
        }
        String detail = reasonDetail == null || reasonDetail.isBlank() ? null : reasonDetail.trim();
        if (reasonCode == CancelRequestReason.ETC && detail == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "취소 사유를 입력해 주세요.");
        }
        if (detail != null && detail.length() > DETAIL_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "취소 사유는 " + DETAIL_MAX_LENGTH + "자 이내로 입력해 주세요.");
        }
        // 하위주문을 잠근 뒤 본다 — 준비 시작 · 송장 등록과 같은 행을 다툰다.
        OrderDeliveryGroup group = deliveryGroupRepository.findForUpdate(deliveryGroupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        if (!group.getOrder().getId().equals(orderId) || !group.getOrder().isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND);
        }
        if (!FulfillmentStatus.WORKABLE.contains(group.getFulfillmentStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_CHANGED, "이미 발송됐거나 취소된 주문입니다.");
        }
        if (cancelRequestRepository.existsPendingByGroup(deliveryGroupId)) {
            throw new BusinessException(ErrorCode.CANCEL_REQUEST_PENDING_EXISTS);
        }
        Set<Long> ids = new HashSet<>(orderProductIds);
        Map<Long, OrderProduct> products = orderProductRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(OrderProduct::getId, Function.identity()));
        for (Long id : ids) {
            OrderProduct product = products.get(id);
            if (product == null || product.getDeliveryGroup() == null
                    || !product.getDeliveryGroup().getId().equals(deliveryGroupId)) {
                throw new BusinessException(ErrorCode.ORDER_PRODUCT_NOT_FOUND);
            }
            if (product.getStatus() != OrderProductStatus.PAID) {
                throw new BusinessException(ErrorCode.ORDER_STATE_CHANGED, "취소할 수 없는 상품이 있습니다.");
            }
        }
        OrderCancelRequest request = OrderCancelRequest.builder()
                .deliveryGroup(group)
                .order(group.getOrder())
                .requestedBy(userId)
                .reasonCode(reasonCode)
                .reasonDetail(detail)
                .statusAtRequest(group.getFulfillmentStatus())
                .requestedAt(now)
                .respondDueAt(businessDayCalculator.dueAt(now, orderProperties.getCancelRequestRespondBusinessDays()))
                .build();
        for (Long id : orderProductIds.stream().distinct().toList()) {
            OrderProduct product = products.get(id);
            request.addItem(OrderCancelRequestItem.builder()
                    .cancelRequest(request)
                    .orderProduct(product)
                    .quantity(product.getQuantity())
                    .refundAmount(product.getPrice() * product.getQuantity())
                    .build());
        }
        cancelRequestRepository.save(request);
        fulfillmentService.appendHistory(deliveryGroupId, FulfillmentEventType.CANCEL_REQUESTED,
                FulfillmentActorType.CONSUMER, userId, reasonCode.getLabel() + " · " + ids.size() + "건", now);
        return request;
    }

    // ------------------------------------------------------------------ 승인 · 거부 · 자동 승인

    /**
     * 승인 — 요청 항목만 취소 · 남은 항목 발송 · 환불은 PG 즉시 자동(환불 큐). 브랜드 승인과 자동 승인이 같은 길이다.
     *
     * @param sellerId 브랜드 승인이면 그 셀러, 자동 승인이면 null
     * @return 승인한 요청의 하위주문 id
     */
    @Transactional
    public Long approve(Long cancelRequestId, Long sellerId, boolean auto, LocalDateTime now) {
        OrderCancelRequest request = cancelRequestRepository.findWithGroup(cancelRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        OrderDeliveryGroup group = request.getDeliveryGroup();
        Long groupId = group.getId();
        List<OrderProduct> targets = request.getItems().stream().map(OrderCancelRequestItem::getOrderProduct).toList();
        if (cancelRequestRepository.approve(cancelRequestId, sellerId, auto, now) != 1) {
            throw decisionFailure(cancelRequestId);
        }
        List<OrderProduct> cancelled = fulfillmentService.cancelItemsWithRestock(targets,
                OrderCancelType.REQUEST_APPROVED, now);
        int refundAmount = cancelled.stream().mapToInt(item -> item.getPrice() * item.getQuantity()).sum();
        // 전 항목 승인일 때만 취소 탭 — 남은 항목이 없으면 그룹도 내리고 배송비까지 전액이다(§34-8).
        if (orderProductRepository.countActiveByGroup(groupId) == 0
                && deliveryGroupRepository.cancelByRequestApproval(groupId, now) == 1) {
            refundAmount += group.getDeliveryFee();
        }
        OrderDeliveryGroup reloaded = deliveryGroupRepository.findById(groupId).orElseThrow();
        fulfillmentService.enqueueRefund(reloaded, RefundTaskSource.CANCEL_REQUEST_APPROVED, cancelRequestId,
                refundAmount);
        fulfillmentService.appendHistory(groupId, FulfillmentEventType.CANCEL_REQUEST_APPROVED,
                auto ? FulfillmentActorType.SYSTEM : FulfillmentActorType.SELLER, sellerId,
                (auto ? "자동 승인 · 응답 기한 경과 · " : "") + "요청 " + cancelled.size() + "건 취소 · 환불 "
                        + String.format("%,d", refundAmount) + "원(PG 자동)", now);
        return groupId;
    }

    /** 거부 — 사유(드롭다운) 필수 · 기타면 상세 필수 · 소비자에게 그대로 전달 · 환불 없음 · 전 항목 배송 진행. */
    @Transactional
    public Long reject(Long cancelRequestId, Long sellerId, CancelRejectReason reasonCode, String detail,
                       LocalDateTime now) {
        String trimmed = detail == null || detail.isBlank() ? null : detail.trim();
        if (reasonCode == null || (reasonCode == CancelRejectReason.ETC && trimmed == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "거부 사유를 입력해 주세요.");
        }
        OrderCancelRequest request = cancelRequestRepository.findWithGroup(cancelRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        Long groupId = request.getDeliveryGroup().getId();
        if (cancelRequestRepository.reject(cancelRequestId, sellerId, reasonCode, trimmed, now) != 1) {
            throw decisionFailure(cancelRequestId);
        }
        fulfillmentService.appendHistory(groupId, FulfillmentEventType.CANCEL_REQUEST_REJECTED,
                FulfillmentActorType.SELLER, sellerId,
                reasonCode.getLabel() + (trimmed == null ? "" : " · " + trimmed), now);
        return groupId;
    }

    @Transactional(readOnly = true)
    public List<Long> findOverdueIds(LocalDateTime now, int limit) {
        return cancelRequestRepository.findOverdueIds(now, PageRequest.of(0, limit));
    }

    /**
     * 자동 승인 — 응답 기한이 지났고 아직 검토 중일 때만. 그사이 브랜드가 결정했으면 조용히 false 다.
     */
    @Transactional
    public boolean autoApproveIfOverdue(Long cancelRequestId, LocalDateTime now) {
        OrderCancelRequest request = cancelRequestRepository.findById(cancelRequestId).orElse(null);
        if (request == null || request.getStatus() != CancelRequestStatus.PENDING
                || request.getRespondDueAt() == null || !request.getRespondDueAt().isBefore(now)) {
            return false;
        }
        try {
            approve(cancelRequestId, null, true, now);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    /** 승인·거부 0행 — 요청이 이미 결정됐으면 409 ALREADY_DECIDED, 아직 PENDING 이면 그룹이 작업 큐를 벗어난 것이다. */
    private BusinessException decisionFailure(Long cancelRequestId) {
        boolean stillPending = cancelRequestRepository.findStatus(cancelRequestId)
                .map(status -> status == CancelRequestStatus.PENDING)
                .orElse(false);
        return new BusinessException(stillPending ? ErrorCode.ORDER_STATE_CHANGED
                : ErrorCode.CANCEL_REQUEST_ALREADY_DECIDED);
    }
}
