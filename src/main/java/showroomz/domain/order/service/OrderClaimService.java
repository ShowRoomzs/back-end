package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.market.entity.Market;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimAttachment;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.entity.OrderClaimHistory;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.DeliveryTrackingEventRepository;
import showroomz.domain.order.repository.OrderClaimAttachmentRepository;
import showroomz.domain.order.repository.OrderClaimChargeRepository;
import showroomz.domain.order.repository.OrderClaimCollectionRepository;
import showroomz.domain.order.repository.OrderClaimHistoryRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.type.ClaimAttachmentOwner;
import showroomz.domain.order.type.ClaimCancelReason;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimEventType;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimRejectReason;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.ValidationResult;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 반품·교환 클레임의 도메인 진입점(35 설계서 3-7) — 앱 · 파트너센터 · 어드민 · 배치가 여기를 부른다. 전이는 전부
 * {@link OrderClaimRepository}의 조건부 UPDATE 이고, 이력은 전이와 같은 트랜잭션에서 남긴다.
 *
 * <p>지금 있는 것은 신청(반품) · 회수 송장 · 철회 · 자동 취소 · 직권 종결 · 입고 확인 · 검수 판정 · 판정 종료(환불 큐) ·
 * 환불 집행 기록 · 재발송비 결제 반영이다. 교환 신청 · 재발송 송장은 구현 계획서의 뒤 단계가 더한다.
 */
@Service
@RequiredArgsConstructor
public class OrderClaimService {

    /** 아직 검수 판정을 받지 않은 단계 — 이것이 남아 있으면 요청의 판정이 끝나지 않았다. */
    private static final Set<ClaimStatus> AWAITING_JUDGEMENT = EnumSet.of(ClaimStatus.REQUESTED,
            ClaimStatus.COLLECTING, ClaimStatus.ARRIVED, ClaimStatus.RECEIVED);

    private final OrderClaimRepository claimRepository;
    private final OrderClaimCollectionRepository collectionRepository;
    private final OrderClaimHistoryRepository historyRepository;
    private final OrderClaimAttachmentRepository attachmentRepository;
    private final OrderClaimChargeRepository chargeRepository;
    private final OrderRefundTaskRepository refundTaskRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final DeliveryTrackingEventRepository trackingEventRepository;
    private final OrderFulfillmentService fulfillmentService;
    private final BusinessDayCalculator businessDayCalculator;
    private final ClaimFeePolicy feePolicy;
    private final DeliveryTrackerPort tracker;
    private final OrderProperties orderProperties;

    // ------------------------------------------------------------------ 값 타입

    /**
     * @param items          신청 항목 — 같은 하위주문의 항목들. 한 번의 신청은 한 하위주문 · 한 유형 · 한 사유다
     * @param invoice        회수 송장 — 같이 내면 회수 중으로 시작한다. null 이면 「나중에 입력」
     * @param idempotencyKey 재시도가 요청을 둘 만들지 않게 한다 — null 이면 검사하지 않는다
     */
    public record RequestCommand(Long userId, Long deliveryGroupId, ClaimType type, ClaimReason reasonCode,
                                 String reasonDetail, List<String> imageUrls, List<Item> items, Invoice invoice,
                                 String idempotencyKey) {
    }

    public record Item(Long orderProductId, int quantity) {
    }

    public record Invoice(DeliveryCarrier carrier, String trackingNumber) {
    }

    /** @param created 이번 호출이 요청을 만들었는가 — 같은 키의 재시도면 false */
    public record RequestResult(Long collectionId, List<Long> claimIds, boolean created) {
    }

    // ------------------------------------------------------------------ 신청(3-1 · 전이 #1)

    @Transactional
    public RequestResult request(RequestCommand command, LocalDateTime now) {
        if (command.idempotencyKey() != null) {
            var existing = collectionRepository.findByUserIdAndIdempotencyKey(command.userId(), command.idempotencyKey());
            if (existing.isPresent()) {
                return new RequestResult(existing.get().getId(), claimIdsOf(existing.get().getId()), false);
            }
        }
        if (command.type() != ClaimType.RETURN) {
            // 교환(옵션 검증 · 재고 선점 · 재발송비 선결제)은 구현 계획서 S7 에서 더한다.
            throw new UnsupportedOperationException("교환 신청은 아직 지원하지 않습니다.");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "신청할 상품을 선택해 주세요.");
        }

        // 하위주문을 잠근 뒤 상태를 본다 — 구매확정 배치와 같은 행을 다툰다. 배치가 먼저면 신청이 진다(3-6).
        OrderDeliveryGroup group = deliveryGroupRepository.findForUpdate(command.deliveryGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        Order order = group.getOrder();
        if (!order.isOwnedBy(command.userId())) {
            throw new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND); // 남의 주문은 있는지도 알리지 않는다
        }
        if (group.getFulfillmentStatus() != FulfillmentStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE);
        }

        ClaimReason reason = command.reasonCode();
        String reasonDetail = blankToNull(command.reasonDetail());
        if (reason.isDetailRequired() && reasonDetail == null) {
            throw new BusinessException(ErrorCode.CLAIM_REASON_DETAIL_REQUIRED);
        }
        Invoice invoice = normalize(command.invoice());

        Map<Long, OrderProduct> products = lockProducts(command.items(), group);
        for (Item item : command.items()) {
            OrderProduct product = products.get(item.orderProductId());
            long remaining = product.getQuantity() - product.getReturnedQuantity()
                    - claimRepository.sumOccupiedQuantity(product.getId());
            if (item.quantity() < 1 || item.quantity() > remaining) {
                throw new BusinessException(ErrorCode.CLAIM_QUANTITY_EXCEEDED);
            }
        }

        ClaimFeeBearer feeBearer = reason.getFeeBearer();
        Market market = group.getMarket();
        OrderProperties.Claim config = orderProperties.getClaim();
        OrderClaimCollection collection = OrderClaimCollection.builder()
                .orderId(order.getId())
                .deliveryGroup(group)
                .marketId(group.getMarketId())
                .userId(command.userId())
                .type(command.type())
                .reasonCode(reason)
                .reasonDetail(reasonDetail)
                .feeBearer(feeBearer)
                .idempotencyKey(command.idempotencyKey())
                .invoiceDueAt(BusinessDayCalculator.endOfDayAfter(now, config.getInvoiceDueDays()))
                // 반품 수취 주소 — 신청 시점의 마켓 출고지(마켓에 반품 전용 주소가 없다 · 35 설계서 7절 N4).
                .returnRecipient(market.getShippingRecipientName())
                .returnContact(market.getShippingContact())
                .returnAddress(market.getShippingAddress())
                .returnDetailAddress(market.getShippingDetailAddress())
                .returnDeduction(feePolicy.returnDeduction(command.type(), feeBearer, group))
                // 재발송 수취지 — 기본은 원 주문 배송지의 사본.
                .reshipRecipient(order.getRecipientName())
                .reshipPhone(order.getRecipientPhone())
                .reshipZipCode(order.getZipCode())
                .reshipAddress(order.getAddress())
                .reshipDetailAddress(order.getDetailAddress())
                .reshipMemo(order.getDeliveryMemo())
                .createdAt(now)
                .build();
        if (invoice != null) {
            collection.registerInvoice(invoice.carrier(), invoice.trackingNumber(), now);
        }
        collectionRepository.save(collection);

        LocalDateTime collectDueAt = businessDayCalculator.dueAt(now, config.getCollectDueBusinessDays());
        ClaimStatus initial = invoice != null ? ClaimStatus.COLLECTING : ClaimStatus.REQUESTED;
        List<String> imageUrls = command.imageUrls() == null ? List.of() : command.imageUrls();
        List<Long> claimIds = new ArrayList<>();
        for (Item item : command.items()) {
            OrderClaim claim = claimRepository.save(OrderClaim.builder()
                    .collection(collection)
                    .orderId(order.getId())
                    .deliveryGroup(group)
                    .orderProduct(products.get(item.orderProductId()))
                    .marketId(group.getMarketId())
                    .userId(command.userId())
                    .type(command.type())
                    .quantity(item.quantity())
                    .reasonCode(reason)
                    .reasonDetail(reasonDetail)
                    .feeBearer(feeBearer)
                    .status(initial)
                    .requestedAt(now)
                    .collectDueAt(collectDueAt)
                    .build());
            claimIds.add(claim.getId());
            // 사진은 요청에 하나지만 브랜드 목록의 「증빙 N장」은 클레임 행을 읽는다 — 항목마다 같은 사진을 단다.
            for (int i = 0; i < imageUrls.size(); i++) {
                attachmentRepository.save(OrderClaimAttachment.builder()
                        .claim(claim).owner(ClaimAttachmentOwner.CONSUMER).imageUrl(imageUrls.get(i)).sortOrder(i)
                        .build());
            }
            appendHistory(claim, ClaimEventType.REQUESTED, FulfillmentActorType.CONSUMER, command.userId(), null, now);
            if (invoice != null) {
                appendHistory(claim, ClaimEventType.COLLECTION_INVOICE_REGISTERED, FulfillmentActorType.CONSUMER,
                        command.userId(), invoiceLabel(invoice), now);
            }
        }
        return new RequestResult(collection.getId(), claimIds, true);
    }

    // ------------------------------------------------------------------ 회수 송장(3-2 · 전이 #2)

    /** 회수 송장 입력 — 묶음 전체가 회수 중으로 간다(박스가 하나다). 등록 기한이 지났으면 자동 취소가 이미 닫았다. */
    @Transactional
    public void registerCollectionInvoice(Long collectionId, Long userId, Invoice rawInvoice, LocalDateTime now) {
        OrderClaimCollection collection = requireOwnedCollectionForUpdate(collectionId, userId);
        Invoice invoice = requireInvoice(rawInvoice);
        if (now.isAfter(collection.getInvoiceDueAt())) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        collection.registerInvoice(invoice.carrier(), invoice.trackingNumber(), now);
        if (claimRepository.moveByCollection(collectionId, ClaimStatus.REQUESTED, ClaimStatus.COLLECTING, now) == 0) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        for (OrderClaim claim : claimRepository.findByCollectionId(collectionId)) {
            if (claim.getStatus() == ClaimStatus.COLLECTING) {
                appendHistory(claim, ClaimEventType.COLLECTION_INVOICE_REGISTERED, FulfillmentActorType.CONSUMER,
                        userId, invoiceLabel(invoice), now);
            }
        }
    }

    /**
     * 오입력 정정 — 회수 중인 동안, <b>그 송장의 추적 이력이 아직 없고 등록 기한 전일 때만</b>(앱 클레임 설계서 3-4).
     * 택배사가 이미 스캔한 송장은 맞는 송장이다. 입력 시각은 유지하고 추적 값만 리셋한다.
     */
    @Transactional
    public void updateCollectionInvoice(Long collectionId, Long userId, Invoice rawInvoice, LocalDateTime now) {
        OrderClaimCollection collection = requireOwnedCollectionForUpdate(collectionId, userId);
        Invoice invoice = requireInvoice(rawInvoice);
        List<OrderClaim> collecting = claimRepository.findByCollectionId(collectionId).stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.COLLECTING).toList();
        if (collecting.isEmpty() || !collection.hasInvoice()) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        boolean scanned = trackingEventRepository.countByCarrierAndTrackingNumber(
                collection.getCarrier(), collection.getTrackingNumber()) > 0;
        if (scanned || now.isAfter(collection.getInvoiceDueAt())) {
            throw new BusinessException(ErrorCode.CLAIM_INVOICE_NOT_EDITABLE);
        }
        String before = invoiceLabel(new Invoice(collection.getCarrier(), collection.getTrackingNumber()));
        collection.updateInvoice(invoice.carrier(), invoice.trackingNumber());
        for (OrderClaim claim : collecting) {
            appendHistory(claim, ClaimEventType.COLLECTION_INVOICE_UPDATED, FulfillmentActorType.CONSUMER, userId,
                    before + " → " + invoiceLabel(invoice), now);
        }
    }

    // ------------------------------------------------------------------ 요청이 사라지는 종결(전이 #13 · #14 · 직권)

    /** 소비자 철회 — 회수 송장을 넣기 전까지만. 항목 단위라 한 요청의 일부만 철회할 수 있다. */
    @Transactional
    public void withdraw(Long claimId, Long userId, LocalDateTime now) {
        OrderClaim claim = claimRepository.findById(claimId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        Long deliveryGroupId = claim.getDeliveryGroup().getId();
        if (claimRepository.withdraw(claimId, userId, now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_WITHDRAW_NOT_ALLOWED);
        }
        appendHistory(claimId, ClaimEventType.WITHDRAWN, FulfillmentActorType.CONSUMER, userId, null, now);
        fulfillmentService.confirmIfDue(deliveryGroupId, now);
    }

    /** 자동 취소 배치의 대상 — 회수 송장 등록 기한이 지났는데 아직 회수 대기인 요청. */
    @Transactional(readOnly = true)
    public List<Long> findCollectionIdsWithExpiredInvoice(LocalDateTime now, int limit) {
        return collectionRepository.findIdsWithExpiredInvoice(now, PageRequest.of(0, limit));
    }

    /**
     * 회수 송장 미등록 자동 취소 — 묶음 전체. 닫는 순간 그 하위주문의 구매확정 보류가 풀린다(대개 7일이 이미 지났다).
     *
     * @return 닫은 클레임 수 — 기한 전이거나 이미 회수 중이면 0
     */
    @Transactional
    public int expireInvoice(Long collectionId, LocalDateTime now) {
        OrderClaimCollection collection = collectionRepository.findForUpdate(collectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        if (!now.isAfter(collection.getInvoiceDueAt())) {
            return 0;
        }
        Long deliveryGroupId = collection.getDeliveryGroup().getId();
        int closed = claimRepository.expireByCollection(collectionId, now);
        if (closed == 0) {
            return 0;
        }
        for (OrderClaim claim : claimRepository.findByCollectionId(collectionId)) {
            if (claim.getCancelReason() == ClaimCancelReason.INVOICE_EXPIRED) {
                appendHistory(claim, ClaimEventType.INVOICE_EXPIRED, FulfillmentActorType.SYSTEM, null, null, now);
            }
        }
        fulfillmentService.confirmIfDue(deliveryGroupId, now);
        return closed;
    }

    /**
     * 운영자 직권 종결 — 송장은 넣었는데 물건이 오지 않는 방치 등. 검수 전(회수 대기 · 회수 중)만 받고, 결과는 거절이
     * 아니라 요청 취소다(검수 거절과 섞이면 거절률 집계가 오염된다).
     */
    @Transactional
    public void closeByAdmin(Long claimId, Long adminId, String reason, LocalDateTime now) {
        OrderClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        Long deliveryGroupId = claim.getDeliveryGroup().getId();
        if (claimRepository.closeByAdmin(claimId, now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        appendHistory(claimId, ClaimEventType.CLOSED_BY_ADMIN, FulfillmentActorType.ADMIN, adminId, reason, now);
        fulfillmentService.confirmIfDue(deliveryGroupId, now);
    }

    // ------------------------------------------------------------------ 입고 확인 · 검수 판정(3-3 · 전이 #4 ~ #7)

    /**
     * 입고 확인 — 검수 기한이 여기서 발급된다. 추적이 꺼져 있는 동안에는 도착 감지가 없어, 설정이 켜져 있으면 회수 중에서도
     * 받는다(0-7).
     *
     * @return 처리됐으면 true — 내 마켓 것이 아니거나 입고 확인할 단계가 아니면 false(다건 액션의 행 제외)
     */
    @Transactional
    public boolean receive(Long claimId, Long marketId, Long sellerId, LocalDateTime now) {
        OrderClaim claim = claimRepository.findOwned(claimId, marketId).orElse(null);
        if (claim == null) {
            return false;
        }
        OrderProperties.Claim config = orderProperties.getClaim();
        Set<ClaimStatus> from = config.isReceiveBeforeArrival()
                ? EnumSet.of(ClaimStatus.ARRIVED, ClaimStatus.COLLECTING) : EnumSet.of(ClaimStatus.ARRIVED);
        // 기산점 — 시안은 입고 확인, 약관 제20조①은 「입고된 날」(추적상 도착)이다. 설정으로 뺀다(§35-9 A-5).
        LocalDateTime arrivedAt = claim.getCollection().getArrivedAt();
        LocalDateTime basis = "ARRIVED".equalsIgnoreCase(config.getInspectDueBasis()) && arrivedAt != null
                ? arrivedAt : now;
        LocalDateTime inspectDueAt = businessDayCalculator.dueAt(basis, config.getInspectDueBusinessDays());
        if (claimRepository.markReceived(claimId, marketId, from, sellerId, inspectDueAt, now) != 1) {
            return false;
        }
        appendHistory(claimId, ClaimEventType.RECEIVED, FulfillmentActorType.SELLER, sellerId, null, now);
        return true;
    }

    /**
     * 검수 통과 — 반품이면 환불 대기로 가고 <b>판매가 무효가 된 시점이 여기</b>라 항목의 반품 수량을 지금 올린다(전량이면
     * 항목이 RETURNED). 돌아온 물건의 재고는 원복하지 않는다 — 다시 팔 수 있는지는 브랜드의 판단이다. 교환이면 재발송
     * 대기로 간다. 환불 큐는 여기가 아니라 요청의 판정이 다 끝난 순간에 선다({@link #finalizeCollection}).
     */
    @Transactional
    public void passInspection(Long claimId, Long marketId, Long sellerId, LocalDateTime now) {
        OrderClaim claim = claimRepository.findOwned(claimId, marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        ClaimType type = claim.getType();
        Long collectionId = claim.getCollection().getId();
        Long orderProductId = claim.getOrderProduct().getId();
        int quantity = claim.getQuantity();
        ClaimStatus to = type == ClaimType.RETURN ? ClaimStatus.REFUND_PENDING : ClaimStatus.RESHIP_READY;
        if (claimRepository.passInspection(claimId, marketId, type, to, sellerId, now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        if (type == ClaimType.RETURN) {
            if (orderProductRepository.addReturnedQuantity(orderProductId, quantity) != 1) {
                // 신청 때 잔여 수량을 잠금 아래에서 봤으므로 올 수 없는 길이다 — 조용히 넘기지 않는다.
                throw new IllegalStateException("반품 수량이 주문 수량을 넘는다 - orderProductId: " + orderProductId);
            }
            orderProductRepository.markReturnedIfFull(orderProductId);
        }
        appendHistory(claimId, ClaimEventType.INSPECTION_PASSED, FulfillmentActorType.SELLER, sellerId, null, now);
        finalizeCollection(collectionId, now);
    }

    /**
     * 검수 거절 — 제출 = 즉시 확정이고 되돌리지 않는다. 사유 · 설명 · 증빙이 전부 있어야 한다. 반려 상품을 다시 받는
     * 배송비 청구가 선다(같은 박스에 미정산 건이 있으면 합류 — 반송도 한 박스다). <b>거절은 구매확정 보류를 푼다</b> —
     * 기준 시각 + N일이 이미 지났으면 그 자리에서 확정된다.
     */
    @Transactional
    public void rejectInspection(Long claimId, Long marketId, Long sellerId, ClaimRejectReason reasonCode,
                                 String detail, List<String> evidenceImageUrls, LocalDateTime now) {
        String rejectDetail = blankToNull(detail);
        int evidenceCount = evidenceImageUrls == null ? 0 : evidenceImageUrls.size();
        if (reasonCode == null || rejectDetail == null || evidenceCount < 1
                || evidenceCount > orderProperties.getClaim().getEvidenceMax()
                || evidenceImageUrls.stream().anyMatch(url -> url == null || url.isBlank())) {
            throw new BusinessException(ErrorCode.CLAIM_REJECT_INCOMPLETE);
        }
        OrderClaim claim = claimRepository.findOwned(claimId, marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        Long collectionId = claim.getCollection().getId();
        Long deliveryGroupId = claim.getDeliveryGroup().getId();
        int reshipFee = feePolicy.rejectReshipFee(claim.getDeliveryGroup());
        if (claimRepository.rejectInspection(claimId, marketId, reasonCode, rejectDetail, sellerId, now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        OrderClaim rejected = claimRepository.getReferenceById(claimId);
        for (int i = 0; i < evidenceCount; i++) {
            attachmentRepository.save(OrderClaimAttachment.builder()
                    .claim(rejected).owner(ClaimAttachmentOwner.SELLER).imageUrl(evidenceImageUrls.get(i))
                    .sortOrder(i).build());
        }
        OrderClaimCollection collection = collectionRepository.findForUpdate(collectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        if (pendingRejectCharge(collectionId) == null) {
            chargeRepository.save(OrderClaimCharge.builder()
                    .collection(collection).type(ClaimChargeType.REJECT_RESHIP).amount(reshipFee)
                    .status(ClaimChargeStatus.PENDING).createdAt(now).build());
        }
        appendHistory(rejected, ClaimEventType.INSPECTION_REJECTED, FulfillmentActorType.SELLER, sellerId,
                reasonCode.getLabel(), now);
        finalizeCollection(collectionId, now);
        fulfillmentService.confirmIfDue(deliveryGroupId, now);
    }

    /**
     * 판정 종료(앱 클레임 설계서 1-4) — 그 요청의 클레임이 전부 검수 판정을 받은 순간 한 번. 판정이 남았으면 아무 일도 없다.
     *
     * <p>① 반품 통과분이 있으면 환불액을 계산해 환불 큐에 <b>요청당 1행</b>을 세운다. 통과 순간이 아니라 여기서 세우는
     * 이유 — 배송비 차감이 요청 단위이고, 일부 반려의 재발송비 차감은 나머지 판정을 봐야 정해진다.
     * ② 반려분의 재발송비를 정산한다 — 환불액에서 뺄 수 있으면 차감, 교환 선결제가 있으면 충당, 둘 다 아니면 결제 기한을
     * 발급한다. 차감·충당이면 반려 클레임이 그 자리에서 재발송 대기로 간다 — 거절 보류에 남는 것은 결제가 필요한 건뿐이다.
     */
    @Transactional
    public void finalizeCollection(Long collectionId, LocalDateTime now) {
        OrderClaimCollection collection = collectionRepository.findForUpdate(collectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        if (collection.isFinalized()) {
            return;
        }
        // 철회·취소로 사라진 것과 아직 접수 전인 것은 빼고 센다.
        List<OrderClaim> claims = claimRepository.findByCollectionId(collectionId).stream()
                .filter(claim -> claim.getResult() != ClaimResult.CANCELLED
                        && claim.getStatus() != ClaimStatus.PAYMENT_PENDING)
                .toList();
        if (claims.isEmpty() || claims.stream().anyMatch(claim -> AWAITING_JUDGEMENT.contains(claim.getStatus()))) {
            return;
        }
        List<Long> rejectedIds = claims.stream().filter(claim -> claim.getRejectedAt() != null)
                .map(OrderClaim::getId).toList();
        OrderClaimCharge rejectCharge = rejectedIds.isEmpty() ? null : pendingRejectCharge(collectionId);
        OrderDeliveryGroup group = collection.getDeliveryGroup();

        Integer refundAmount = null;
        String settlement = null;
        if (collection.getType() == ClaimType.RETURN) {
            long passedGoods = claims.stream().filter(claim -> claim.getRejectedAt() == null)
                    .mapToLong(claim -> (long) claim.getOrderProduct().getPrice() * claim.getQuantity()).sum();
            if (passedGoods > 0) {
                ClaimRefundCalculator.Result result = ClaimRefundCalculator.calculate(passedGoods,
                        collection.getReturnDeduction(), rejectCharge == null ? null : rejectCharge.getAmount());
                refundAmount = result.refund();
                if (result.reshipDeducted()) {
                    rejectCharge.settle(ClaimChargeStatus.DEDUCTED, null, now);
                    settlement = "환불액 차감";
                }
                fulfillmentService.enqueueRefund(group, RefundTaskSource.CLAIM_RETURN_PASSED, collectionId,
                        refundAmount);
            }
        } else if (rejectCharge != null && hasPaidExchangeCharge(collectionId)) {
            rejectCharge.settle(ClaimChargeStatus.COVERED, null, now);
            settlement = "교환 결제분 충당";
        }
        if (rejectCharge != null && rejectCharge.isPending()) {
            // 전체 반려이거나 환불액이 재발송비보다 작다 — 소비자가 결제해야 한다. 이 날이 지나면 미결제 고지가 시작된다.
            rejectCharge.openForPayment(BusinessDayCalculator.endOfDayAfter(now,
                    orderProperties.getClaim().getReshipPayDueDays()));
        }
        collection.finalizeWith(refundAmount, now);

        if (settlement != null) {
            claimRepository.moveByCollection(collectionId, ClaimStatus.REJECT_HOLD, ClaimStatus.RESHIP_READY, now);
            for (Long rejectedId : rejectedIds) {
                appendHistory(rejectedId, ClaimEventType.RESHIP_FEE_SETTLED, FulfillmentActorType.SYSTEM, null,
                        settlement, now);
            }
        }
    }

    // ------------------------------------------------------------------ 환불 집행 · 재발송비 결제(3-7 · 전이 #8 · #10)

    /**
     * 환불 집행 기록(어드민) — 환불 큐가 요청 단위라 그 요청의 환불 대기 클레임을 <b>한꺼번에</b> 닫는다. 집행액은 예정액과
     * 다를 수 있다. 항목별 확정액은 상품 금액에서 차감분을 앞 항목부터 빼서 나눠 적는다(합이 집행액과 같다).
     */
    @Transactional
    public void completeRefund(Long refundTaskId, int amount, Long adminId, LocalDateTime now) {
        OrderRefundTask task = refundTaskRepository.findForUpdate(refundTaskId)
                .filter(found -> found.getSource() == RefundTaskSource.CLAIM_RETURN_PASSED)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        if (!task.isPending() || amount < 0) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        Long collectionId = task.getSourceId();
        OrderClaimCollection collection = collectionRepository.findForUpdate(collectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        List<OrderClaim> pending = claimRepository.findByCollectionId(collectionId).stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.REFUND_PENDING).toList();
        Map<Long, Long> goodsByClaim = pending.stream().collect(Collectors.toMap(OrderClaim::getId,
                claim -> (long) claim.getOrderProduct().getPrice() * claim.getQuantity()));
        task.markExecuted(amount, adminId, now);
        collection.confirmRefund(amount);
        if (claimRepository.completeRefundByCollection(collectionId, now) == 0) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        long shortfall = Math.max(0, goodsByClaim.values().stream().mapToLong(Long::longValue).sum() - amount);
        for (OrderClaim claim : pending) {
            long goods = goodsByClaim.get(claim.getId());
            long deducted = Math.min(goods, shortfall);
            shortfall -= deducted;
            claimRepository.setRefundedAmount(claim.getId(), (int) (goods - deducted));
            appendHistory(claim.getId(), ClaimEventType.REFUND_EXECUTED, FulfillmentActorType.ADMIN, adminId,
                    String.format("%,d원", amount), now);
        }
    }

    /**
     * 반려 상품 재발송비 결제 반영 — 거절 보류가 재발송 대기로 간다(박스 전체). 보관 기한이 지났어도 폐기 기록 전이면
     * 받는다 — 물건이 아직 있는데 돌려줄 길을 막을 이유가 없다(35 설계서 2절).
     */
    @Transactional
    public void markReshipFeePaid(Long chargeId, String paymentId, Long actorId, LocalDateTime now) {
        OrderClaimCharge found = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        Long collectionId = found.getCollection().getId();
        // 판정 종료 · 폐기 기록과 같은 요청을 다툰다 — 요청 행을 잠근 뒤 청구를 다시 읽는다.
        collectionRepository.findForUpdate(collectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        OrderClaimCharge charge = pendingRejectCharge(collectionId);
        if (charge == null || !charge.getId().equals(chargeId) || charge.getDueAt() == null) {
            throw new BusinessException(ErrorCode.CLAIM_PAYMENT_NOT_REQUIRED);
        }
        List<Long> holdIds = claimRepository.findByCollectionId(collectionId).stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.REJECT_HOLD).map(OrderClaim::getId).toList();
        charge.settle(ClaimChargeStatus.PAID, paymentId, now);
        if (claimRepository.moveByCollection(collectionId, ClaimStatus.REJECT_HOLD, ClaimStatus.RESHIP_READY, now)
                == 0) {
            throw new BusinessException(ErrorCode.CLAIM_PAYMENT_NOT_REQUIRED);
        }
        for (Long claimId : holdIds) {
            appendHistory(claimId, ClaimEventType.RESHIP_FEE_PAID, FulfillmentActorType.CONSUMER, actorId, null, now);
        }
    }

    // ------------------------------------------------------------------ 내부

    /** 그 요청의 미정산 반려 재발송비 — 한 요청에 1건까지다. */
    private OrderClaimCharge pendingRejectCharge(Long collectionId) {
        return chargeRepository.findByCollectionId(collectionId).stream()
                .filter(charge -> charge.getType() == ClaimChargeType.REJECT_RESHIP && charge.isPending())
                .findFirst().orElse(null);
    }

    /** 교환 요청 때 낸 재발송비가 있는가 — 있으면 반려 상품을 그 돈으로 돌려보낸다. */
    private boolean hasPaidExchangeCharge(Long collectionId) {
        return chargeRepository.findByCollectionId(collectionId).stream()
                .anyMatch(charge -> charge.getType() == ClaimChargeType.EXCHANGE_RESHIP
                        && charge.getStatus() == ClaimChargeStatus.PAID);
    }

    /** 항목을 id 오름차순으로 잠근다 — 동시 신청 2건이 같은 잔여 수량을 나눠 갖지 못하게. */
    private Map<Long, OrderProduct> lockProducts(List<Item> items, OrderDeliveryGroup group) {
        Set<Long> ids = new HashSet<>();
        for (Item item : items) {
            if (item.orderProductId() == null || !ids.add(item.orderProductId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "같은 상품을 두 번 선택할 수 없습니다.");
            }
        }
        Map<Long, OrderProduct> products = orderProductRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(OrderProduct::getId, Function.identity()));
        for (Long id : ids) {
            OrderProduct product = products.get(id);
            if (product == null || product.getDeliveryGroup() == null
                    || !product.getDeliveryGroup().getId().equals(group.getId())) {
                throw new BusinessException(ErrorCode.ORDER_PRODUCT_NOT_FOUND);
            }
            if (product.getStatus() != OrderProductStatus.PAID) {
                throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE); // 취소·전량 반품된 항목
            }
        }
        return products;
    }

    private OrderClaimCollection requireOwnedCollectionForUpdate(Long collectionId, Long userId) {
        return collectionRepository.findForUpdate(collectionId)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
    }

    private Invoice requireInvoice(Invoice raw) {
        Invoice invoice = normalize(raw);
        if (invoice == null) {
            throw new BusinessException(ErrorCode.INVOICE_FORMAT_INVALID);
        }
        return invoice;
    }

    /**
     * 송장번호는 숫자만 저장한다(하이픈·공백 제거 — 브랜드 송장과 같은 규칙). 형식 검증은 연동이 켜져 있을 때만 판정이
     * 나온다 — 꺼져 있으면 오타 송장도 통과하고, 「조회되지 않는 송장」 화면이 안전망이다(앱 클레임 설계서 3-4).
     *
     * @return 입력이 없으면 null
     */
    private Invoice normalize(Invoice raw) {
        if (raw == null) {
            return null;
        }
        String trackingNumber = raw.trackingNumber() == null ? "" : raw.trackingNumber().replaceAll("[^0-9]", "");
        if (raw.carrier() == null || trackingNumber.isEmpty()
                || tracker.validateInvoice(raw.carrier(), trackingNumber) == ValidationResult.INVALID) {
            throw new BusinessException(ErrorCode.INVOICE_FORMAT_INVALID);
        }
        return new Invoice(raw.carrier(), trackingNumber);
    }

    private List<Long> claimIdsOf(Long collectionId) {
        return claimRepository.findByCollectionId(collectionId).stream().map(OrderClaim::getId).toList();
    }

    private void appendHistory(Long claimId, ClaimEventType eventType, FulfillmentActorType actorType, Long actorId,
                               String detail, LocalDateTime now) {
        appendHistory(claimRepository.getReferenceById(claimId), eventType, actorType, actorId, detail, now);
    }

    private void appendHistory(OrderClaim claim, ClaimEventType eventType, FulfillmentActorType actorType,
                               Long actorId, String detail, LocalDateTime now) {
        historyRepository.save(OrderClaimHistory.builder()
                .claim(claim).eventType(eventType).actorType(actorType).actorId(actorId).detail(detail)
                .occurredAt(now).build());
    }

    private static String invoiceLabel(Invoice invoice) {
        return invoice.carrier().getLabel() + " " + invoice.trackingNumber();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
