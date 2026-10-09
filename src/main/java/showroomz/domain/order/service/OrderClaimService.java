package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
import showroomz.domain.order.entity.OrderClaimNotice;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.event.ClaimHistoryRecordedEvent;
import showroomz.domain.order.repository.DeliveryTrackingEventRepository;
import showroomz.domain.order.repository.OrderClaimAttachmentRepository;
import showroomz.domain.order.repository.OrderClaimChargeRepository;
import showroomz.domain.order.repository.OrderClaimCollectionRepository;
import showroomz.domain.order.repository.OrderClaimHistoryRepository;
import showroomz.domain.order.repository.OrderClaimNoticeRepository;
import showroomz.domain.order.repository.OrderClaimPaymentRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.type.ClaimRejectLegalBasis;
import showroomz.domain.order.type.ClaimAttachmentOwner;
import showroomz.domain.order.type.ClaimCancelReason;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimEventType;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimPaymentStatus;
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
import showroomz.domain.order.type.StoragePhase;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.ValidationResult;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 반품·교환 클레임의 도메인 진입점(35 설계서 3-7) — 앱 · 파트너센터 · 어드민 · 배치가 여기를 부른다. 전이는 전부
 * {@link OrderClaimRepository}의 조건부 UPDATE 이고, 이력은 전이와 같은 트랜잭션에서 남긴다.
 *
 * <p>흐름 — 신청 → 회수 송장 → (추적) 도착 → 입고 확인 → 검수 판정 → 판정 종료(환불 큐 · 재발송비 정산) →
 * 환불 집행 / 재발송 송장 → (추적) 도착 → 종결. 거절 보류는 미결제 고지 → 보관 → 폐기 기록으로 닫힌다.
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
    private final OrderClaimPaymentRepository claimPaymentRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ClaimExchangeOptionReader exchangeOptionReader;
    private final OrderClaimNoticeRepository noticeRepository;
    private final DeliveryTrackingEventRecorder trackingEventRecorder;
    private final ClaimStoragePolicy storagePolicy;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final DeliveryTrackingEventRepository trackingEventRepository;
    private final OrderFulfillmentService fulfillmentService;
    private final BusinessDayCalculator businessDayCalculator;
    private final ClaimFeePolicy feePolicy;
    private final DeliveryTrackerPort tracker;
    private final OrderProperties orderProperties;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------ 값 타입

    /**
     * @param items          신청 항목 — 같은 하위주문의 항목들. 한 번의 신청은 한 하위주문 · 한 유형 · 한 사유다
     * @param invoice        회수 송장 — 같이 내면 회수 중으로 시작한다. null 이면 「나중에 입력」
     * @param idempotencyKey 재시도가 요청을 둘 만들지 않게 한다 — null 이면 검사하지 않는다
     */
    public record RequestCommand(Long userId, Long deliveryGroupId, ClaimType type, ClaimReason reasonCode,
                                 String reasonDetail, List<String> imageUrls, List<Item> items, Invoice invoice,
                                 String idempotencyKey, ReshipAddress reshipAddress) {

        /** 재발송 수취지를 따로 정하지 않는 요청 — 원 주문 배송지의 사본을 쓴다. */
        public RequestCommand(Long userId, Long deliveryGroupId, ClaimType type, ClaimReason reasonCode,
                              String reasonDetail, List<String> imageUrls, List<Item> items, Invoice invoice,
                              String idempotencyKey) {
            this(userId, deliveryGroupId, type, reasonCode, reasonDetail, imageUrls, items, invoice, idempotencyKey,
                    null);
        }
    }

    /** @param exchangeVariantId 교환받을 옵션 — 교환만. 반품은 null */
    public record Item(Long orderProductId, int quantity, Long exchangeVariantId) {

        public Item(Long orderProductId, int quantity) {
            this(orderProductId, quantity, null);
        }
    }

    /** 교환 새 상품 · 반려 상품이 가는 곳 — 고른 배송지의 값을 복사해 든다(스냅샷). */
    public record ReshipAddress(String recipient, String phone, String zipCode, String address,
                                String detailAddress, String memo) {
    }

    public record Invoice(DeliveryCarrier carrier, String trackingNumber) {
    }

    /**
     * @param created         이번 호출이 요청을 만들었는가 — 같은 키의 재시도면 false
     * @param status          접수 상태 — PAYMENT_PENDING 이면 아직 접수 전이다(재발송 배송비 결제가 끝나야 접수된다)
     * @param pendingChargeId 결제해야 하는 청구 — 결제가 필요 없으면 null
     */
    public record RequestResult(Long collectionId, List<Long> claimIds, boolean created, ClaimStatus status,
                                Long pendingChargeId) {
    }

    // ------------------------------------------------------------------ 신청(3-1 · 전이 #1)

    @Transactional
    public RequestResult request(RequestCommand command, LocalDateTime now) {
        if (command.idempotencyKey() != null) {
            var existing = collectionRepository.findByUserIdAndIdempotencyKey(command.userId(), command.idempotencyKey());
            if (existing.isPresent()) {
                Long collectionId = existing.get().getId();
                List<OrderClaim> claims = claimRepository.findByCollectionId(collectionId);
                ClaimStatus status = claims.isEmpty() ? null : claims.get(0).getStatus();
                OrderClaimCharge pending = status == ClaimStatus.PAYMENT_PENDING
                        ? pendingCharge(collectionId, ClaimChargeType.EXCHANGE_RESHIP) : null;
                return new RequestResult(collectionId, claims.stream().map(OrderClaim::getId).toList(), false,
                        status, pending == null ? null : pending.getId());
            }
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "신청할 상품을 선택해 주세요.");
        }
        // 결제하지 않고 남긴 초안이 있으면 먼저 지운다 — 결제 실패 뒤 내용을 고쳐 다시 요청하는 경우 묶인 수량·재고를 푼다.
        for (Long draftId : collectionRepository.findDraftIds(command.userId(), command.deliveryGroupId())) {
            discardDraft(draftId);
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
        boolean exchange = command.type() == ClaimType.EXCHANGE;
        Map<Long, ClaimExchangeOptionReader.Option> exchangeOptions = exchange
                ? reserveExchangeOptions(command.items(), products, feeBearer) : Map.of();
        // 고객 귀책 교환은 재발송 배송비를 결제해야 접수된다 — 그때까지는 브랜드 화면 어디에도 나오지 않는다.
        int exchangeFee = exchange ? feePolicy.exchangeReshipFee(feeBearer, group) : 0;
        boolean paymentRequired = exchangeFee > 0;
        ReshipAddress reshipTo = command.reshipAddress();
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
                // 재발송 수취지 — 기본은 원 주문 배송지의 사본. 교환은 요청 때 다른 배송지를 고를 수 있다.
                .reshipRecipient(reshipTo != null ? reshipTo.recipient() : order.getRecipientName())
                .reshipPhone(reshipTo != null ? reshipTo.phone() : order.getRecipientPhone())
                .reshipZipCode(reshipTo != null ? reshipTo.zipCode() : order.getZipCode())
                .reshipAddress(reshipTo != null ? reshipTo.address() : order.getAddress())
                .reshipDetailAddress(reshipTo != null ? reshipTo.detailAddress() : order.getDetailAddress())
                .reshipMemo(reshipTo != null ? reshipTo.memo() : order.getDeliveryMemo())
                .createdAt(now)
                .build();
        if (invoice != null) {
            collection.registerInvoice(invoice.carrier(), invoice.trackingNumber(), now);
        }
        collectionRepository.save(collection);
        Long pendingChargeId = !paymentRequired ? null : chargeRepository.save(OrderClaimCharge.builder()
                .collection(collection).type(ClaimChargeType.EXCHANGE_RESHIP).amount(exchangeFee)
                .status(ClaimChargeStatus.PENDING).createdAt(now).build()).getId();

        LocalDateTime collectDueAt = businessDayCalculator.dueAt(now, config.getCollectDueBusinessDays());
        ClaimStatus initial = paymentRequired ? ClaimStatus.PAYMENT_PENDING
                : invoice != null ? ClaimStatus.COLLECTING : ClaimStatus.REQUESTED;
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
                    .exchangeVariantId(item.exchangeVariantId())
                    .exchangeOptionName(!exchange ? null : exchangeOptions.get(item.orderProductId()).optionName())
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
            // 결제 대기는 아직 접수 전이다 — 접수 이력은 결제가 확정되는 순간에 남긴다.
            if (!paymentRequired) {
                appendAccepted(claim, command.userId(), invoice, now);
            }
        }
        // 접수 = 구매확정 타이머 정지(요청 시점부터 · 1009 기획 수정본 4-2). 결제 대기는 결제 확정 때 멈춘다.
        if (!paymentRequired) {
            fulfillmentService.pauseConfirmTimer(group.getId(), now);
        }
        return new RequestResult(collection.getId(), claimIds, true, initial, pendingChargeId);
    }

    /** 구매확정 후 하자 — 법정 기간(공급일 기준 3개월 · 전자상거래법 제17조③). 안 날부터 30일은 운영자가 문의 내용으로 판단한다. */
    private static final int DEFECT_CLAIM_MONTHS = 3;

    /**
     * 운영자가 대신 여는 하자 반품(1009 기획 수정본 8-1 B6 · 거래 관리 결정 2) — 구매확정 뒤 하자는 소비자가 직접 신청하지 못하고
     * 1:1 문의 → 운영자가 연다. 브랜드 귀책 사유(파손·불량 · 오배송)만 · 증빙 필수 · 단순 변심 불가 · 배송완료 3개월 안.
     * 연 뒤에는 일반 반품과 같은 흐름이다(회수 송장은 소비자 · 검수는 브랜드 · 통과 시 PG 자동 환불).
     */
    @Transactional
    public RequestResult openDefectClaimByOperator(Long adminId, Long deliveryGroupId, List<Item> items,
                                                   ClaimReason reason, String detail, List<String> evidenceUrls,
                                                   LocalDateTime now) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "반품할 상품을 선택해 주세요.");
        }
        if (reason == null || reason.getFeeBearer() != ClaimFeeBearer.SELLER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "구매확정 후 하자는 파손·불량 · 오배송만 열 수 있습니다.");
        }
        String reasonDetail = blankToNull(detail);
        List<String> evidences = evidenceUrls == null ? List.of()
                : evidenceUrls.stream().filter(url -> url != null && !url.isBlank()).toList();
        if (reasonDetail == null || evidences.isEmpty()) {
            throw new BusinessException(ErrorCode.CLAIM_REASON_DETAIL_REQUIRED, "하자 내용과 증빙을 모두 입력해 주세요.");
        }
        OrderDeliveryGroup group = deliveryGroupRepository.findForUpdate(deliveryGroupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        if (group.getFulfillmentStatus() != FulfillmentStatus.CONFIRMED
                && group.getFulfillmentStatus() != FulfillmentStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE);
        }
        if (group.getDeliveredAt() == null || group.getDeliveredAt().plusMonths(DEFECT_CLAIM_MONTHS).isBefore(now)) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE, "배송완료 후 3개월이 지나 열 수 없습니다.");
        }
        Set<Long> ids = new HashSet<>();
        for (Item item : items) {
            if (item.orderProductId() == null || !ids.add(item.orderProductId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "같은 상품을 두 번 선택할 수 없습니다.");
            }
        }
        Map<Long, OrderProduct> products = orderProductRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(OrderProduct::getId, Function.identity()));
        for (Item item : items) {
            OrderProduct product = products.get(item.orderProductId());
            if (product == null || product.getDeliveryGroup() == null
                    || !product.getDeliveryGroup().getId().equals(deliveryGroupId)) {
                throw new BusinessException(ErrorCode.ORDER_PRODUCT_NOT_FOUND);
            }
            if (product.getStatus() != OrderProductStatus.PURCHASE_CONFIRMED
                    && product.getStatus() != OrderProductStatus.PAID) {
                throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE);
            }
            long remaining = product.getQuantity() - product.getReturnedQuantity()
                    - claimRepository.sumOccupiedQuantity(product.getId());
            if (item.quantity() < 1 || item.quantity() > remaining) {
                throw new BusinessException(ErrorCode.CLAIM_QUANTITY_EXCEEDED);
            }
        }
        Order order = group.getOrder();
        Market market = group.getMarket();
        OrderProperties.Claim config = orderProperties.getClaim();
        OrderClaimCollection collection = collectionRepository.save(OrderClaimCollection.builder()
                .orderId(order.getId())
                .deliveryGroup(group)
                .marketId(group.getMarketId())
                .userId(order.getUser().getId())
                .type(ClaimType.RETURN)
                .reasonCode(reason)
                .reasonDetail(reasonDetail)
                .feeBearer(ClaimFeeBearer.SELLER)
                .invoiceDueAt(BusinessDayCalculator.endOfDayAfter(now, config.getInvoiceDueDays()))
                .returnRecipient(market.getShippingRecipientName())
                .returnContact(market.getShippingContact())
                .returnAddress(market.getShippingAddress())
                .returnDetailAddress(market.getShippingDetailAddress())
                .returnDeduction(0)
                .reshipRecipient(order.getRecipientName())
                .reshipPhone(order.getRecipientPhone())
                .reshipZipCode(order.getZipCode())
                .reshipAddress(order.getAddress())
                .reshipDetailAddress(order.getDetailAddress())
                .reshipMemo(order.getDeliveryMemo())
                .createdAt(now)
                .build());
        LocalDateTime collectDueAt = businessDayCalculator.dueAt(now, config.getCollectDueBusinessDays());
        List<Long> claimIds = new ArrayList<>();
        for (Item item : items) {
            OrderClaim claim = OrderClaim.builder()
                    .collection(collection)
                    .orderId(order.getId())
                    .deliveryGroup(group)
                    .orderProduct(products.get(item.orderProductId()))
                    .marketId(group.getMarketId())
                    .userId(order.getUser().getId())
                    .type(ClaimType.RETURN)
                    .quantity(item.quantity())
                    .reasonCode(reason)
                    .reasonDetail(reasonDetail)
                    .feeBearer(ClaimFeeBearer.SELLER)
                    .status(ClaimStatus.REQUESTED)
                    .requestedAt(now)
                    .collectDueAt(collectDueAt)
                    .build();
            claim.markOpenedByOperator(adminId, "구매확정 후 하자");
            claimRepository.save(claim);
            claimIds.add(claim.getId());
            for (int i = 0; i < evidences.size(); i++) {
                attachmentRepository.save(OrderClaimAttachment.builder()
                        .claim(claim).owner(ClaimAttachmentOwner.CONSUMER).imageUrl(evidences.get(i)).sortOrder(i)
                        .build());
            }
            appendHistory(claim, ClaimEventType.REQUESTED, FulfillmentActorType.ADMIN, adminId,
                    "운영자 개설 · 구매확정 후 하자 · 증빙 " + evidences.size() + "장", now);
        }
        // 클레임 접수 = 구매확정 타이머 정지(1009 수정계획 4-2) — 소비자 접수와 같다. 배송완료 건에서 열었을 때 남은 일수가 보인다.
        fulfillmentService.pauseConfirmTimer(group.getId(), now);
        return new RequestResult(collection.getId(), claimIds, true, ClaimStatus.REQUESTED, null);
    }

    /**
     * 교환 옵션 검증 · 재고 선점(3-8) — 고를 수 있는 옵션(같은 상품 · 같은 공구 · 같은 판매가)이어야 하고, 고객 귀책인데
     * 받은 옵션과 같은 옵션이면 교환할 이유가 없다. 재고는 <b>신청 때</b> 잡는다 — 회수·검수에 며칠이 걸리는 동안
     * 품절되면 「통과시켰는데 보낼 물건이 없는」 막다른 길이 된다. 잠금 순서는 옵션 id 오름차순.
     *
     * @return 주문 항목 id → 고른 교환 옵션
     */
    private Map<Long, ClaimExchangeOptionReader.Option> reserveExchangeOptions(List<Item> items,
                                                                               Map<Long, OrderProduct> products,
                                                                               ClaimFeeBearer feeBearer) {
        Map<Long, ClaimExchangeOptionReader.Option> chosen = new HashMap<>();
        Map<Long, Integer> quantityByVariant = new TreeMap<>();
        for (Item item : items) {
            ClaimExchangeOptionReader.Option option = exchangeOptionReader
                    .optionsOf(products.get(item.orderProductId())).stream()
                    .filter(candidate -> candidate.variantId().equals(item.exchangeVariantId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_EXCHANGE_OPTION_INVALID));
            if (option.current() && feeBearer == ClaimFeeBearer.CONSUMER) {
                throw new BusinessException(ErrorCode.CLAIM_EXCHANGE_SAME_OPTION);
            }
            chosen.put(item.orderProductId(), option);
            quantityByVariant.merge(option.variantId(), item.quantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : quantityByVariant.entrySet()) {
            if (productVariantRepository.reserveStock(entry.getKey(), entry.getValue()) != 1) {
                throw new BusinessException(ErrorCode.CLAIM_EXCHANGE_OUT_OF_STOCK);
            }
        }
        return chosen;
    }

    // ------------------------------------------------------------------ 결제 대기(앱 클레임 설계서 2절 · 전이 0a · 0b)

    /**
     * 청구 결제 확정의 도메인 훅 — 교환 선결제면 결제 대기 요청이 접수되고(송장을 같이 냈으면 회수 중), 반려 재발송비면
     * 거절 보류가 재발송 대기로 간다. 받을 수 없는 결제(요청이 이미 사라짐 · 이미 정산됨)는 예외로 알린다 —
     * 호출자가 그 결제를 자동 취소한다.
     */
    @Transactional
    public void onChargePaid(Long chargeId, String paymentId, LocalDateTime now) {
        OrderClaimCharge found = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_PAYMENT_NOT_REQUIRED));
        Long collectionId = found.getCollection().getId();
        OrderClaimCollection collection = collectionRepository.findForUpdate(collectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_PAYMENT_NOT_REQUIRED));
        if (found.getType() == ClaimChargeType.REJECT_RESHIP) {
            markReshipFeePaid(chargeId, paymentId, collection.getUserId(), now);
            return;
        }
        OrderClaimCharge charge = pendingCharge(collectionId, ClaimChargeType.EXCHANGE_RESHIP);
        if (charge == null || !charge.getId().equals(chargeId)) {
            throw new BusinessException(ErrorCode.CLAIM_PAYMENT_NOT_REQUIRED);
        }
        Long userId = collection.getUserId();
        Invoice invoice = collection.hasInvoice()
                ? new Invoice(collection.getCarrier(), collection.getTrackingNumber()) : null;
        List<Long> draftIds = claimRepository.findByCollectionId(collectionId).stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.PAYMENT_PENDING).map(OrderClaim::getId).toList();
        charge.settle(ClaimChargeStatus.PAID, paymentId, now);
        ClaimStatus to = invoice != null ? ClaimStatus.COLLECTING : ClaimStatus.REQUESTED;
        if (claimRepository.moveByCollection(collectionId, ClaimStatus.PAYMENT_PENDING, to, now) == 0) {
            throw new BusinessException(ErrorCode.CLAIM_PAYMENT_NOT_REQUIRED);
        }
        for (Long claimId : draftIds) {
            appendAccepted(claimRepository.getReferenceById(claimId), userId, invoice, now);
        }
        fulfillmentService.pauseConfirmTimer(collection.getDeliveryGroup().getId(), now);
    }

    /** 결제 없이 남은 오래된 초안 — 삭제 배치의 대상(결제창이 아직 열려 있을 수 있는 것은 빠진다). */
    @Transactional(readOnly = true)
    public List<Long> findStaleDraftIds(LocalDateTime before, int limit) {
        return collectionRepository.findStaleDraftIds(before, PageRequest.of(0, limit));
    }

    /**
     * 결제 대기 초안 삭제(전이 0b) — 아직 접수된 적 없는 요청이라 행을 지운다. 선점한 교환 재고를 되돌린다.
     * 이미 결제돼 접수된 요청은 건드리지 않는다.
     */
    @Transactional
    public void discardDraft(Long collectionId) {
        if (collectionRepository.findForUpdate(collectionId).isEmpty()) {
            return;
        }
        List<OrderClaim> claims = claimRepository.findByCollectionId(collectionId);
        List<OrderClaim> drafts = claims.stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.PAYMENT_PENDING).toList();
        if (drafts.isEmpty() || drafts.size() != claims.size()) {
            return;
        }
        restoreExchangeStock(drafts);
        attachmentRepository.deleteByClaimIds(drafts.stream().map(OrderClaim::getId).toList());
        claimRepository.deletePaymentPendingByCollection(collectionId);
        chargeRepository.deleteByCollectionId(collectionId);
        collectionRepository.deleteById(collectionId);
    }

    // ------------------------------------------------------------------ 교환받을 배송지(앱 클레임 설계서 3-6)

    /**
     * 교환받을 배송지 변경 — 교환이고 그 요청의 진행 중 클레임이 전부 검수 판정 전일 때만. 하나라도 검수를 통과해 재발송
     * 대기가 됐으면 잠긴다 — 브랜드가 재발송 목록을 내려받을 수 있는 시점부터 주소가 바뀌면 구 주소로 나간다.
     */
    @Transactional
    public void changeReshipAddress(Long collectionId, Long userId, ReshipAddress address, LocalDateTime now) {
        OrderClaimCollection collection = requireOwnedCollectionForUpdate(collectionId, userId);
        List<OrderClaim> open = claimRepository.findByCollectionId(collectionId).stream()
                .filter(OrderClaim::isOpen).toList();
        if (collection.getType() != ClaimType.EXCHANGE || open.isEmpty()
                || open.stream().anyMatch(claim -> !AWAITING_JUDGEMENT.contains(claim.getStatus()))) {
            throw new BusinessException(ErrorCode.CLAIM_ADDRESS_NOT_CHANGEABLE);
        }
        collection.changeReshipAddress(address.recipient(), address.phone(), address.zipCode(), address.address(),
                address.detailAddress(), address.memo());
        for (OrderClaim claim : open) {
            appendHistory(claim, ClaimEventType.RESHIP_ADDRESS_CHANGED, FulfillmentActorType.CONSUMER, userId, null,
                    now);
        }
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
        Long collectionId = claim.getCollection().getId();
        if (claimRepository.withdraw(claimId, userId, now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_WITHDRAW_NOT_ALLOWED);
        }
        appendHistory(claimId, ClaimEventType.WITHDRAWN, FulfillmentActorType.CONSUMER, userId, null, now);
        releaseAfterCancel(collectionId, List.of(claimId), now);
        fulfillmentService.resumeConfirmTimerIfIdle(deliveryGroupId, now);
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
        List<Long> expiredIds = new ArrayList<>();
        for (OrderClaim claim : claimRepository.findByCollectionId(collectionId)) {
            if (claim.getCancelReason() == ClaimCancelReason.INVOICE_EXPIRED) {
                appendHistory(claim, ClaimEventType.INVOICE_EXPIRED, FulfillmentActorType.SYSTEM, null, null, now);
                expiredIds.add(claim.getId());
            }
        }
        releaseAfterCancel(collectionId, expiredIds, now);
        fulfillmentService.resumeConfirmTimerIfIdle(deliveryGroupId, now);
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
        Long collectionId = claim.getCollection().getId();
        if (claimRepository.closeByAdmin(claimId, now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        appendHistory(claimId, ClaimEventType.CLOSED_BY_ADMIN, FulfillmentActorType.ADMIN, adminId, reason, now);
        releaseAfterCancel(collectionId, List.of(claimId), now);
        fulfillmentService.resumeConfirmTimerIfIdle(deliveryGroupId, now);
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
     * 대기로 간다. 환불 큐는 여기가 아니라 요청의 판정이 다 끝난 순간에 선다({@link #finalizeCollection}) — 그 순간 PG 가
     * 즉시 자동 환불한다(1009 기획 수정본 2절 · 되돌릴 수 없다).
     */
    @Transactional
    public void passInspection(Long claimId, Long marketId, Long sellerId, LocalDateTime now) {
        OrderClaim claim = claimRepository.findOwned(claimId, marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        Long collectionId = claim.getCollection().getId();
        passInternal(claim, marketId, sellerId, now);
        finalizeCollection(collectionId, now);
    }

    private void passInternal(OrderClaim claim, Long marketId, Long sellerId, LocalDateTime now) {
        Long claimId = claim.getId();
        ClaimType type = claim.getType();
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
    }

    /**
     * 검수 반려 입력 6항목(1009 기획 수정본 5-b · 파트너 11 B1r · 소비자 C10-5와 1:1).
     *
     * @param rejectedQuantity 반려 수량 — null 이거나 신청 수량과 같으면 전체 반려, 작으면 일부 반려(나머지는 통과)
     * @param faultToSeller    귀책 변경 — 브랜드 귀책으로 인정(반품 배송비 차감 환원 · 반려 재발송비 브랜드 부담)
     * @param consumerMessage  소비자에게 보낼 메시지 — 필수
     */
    public record RejectCommand(ClaimRejectReason reasonCode, String detail, ClaimRejectLegalBasis legalBasis,
                                Integer rejectedQuantity, boolean faultToSeller, String consumerMessage,
                                List<String> evidenceImageUrls) {
    }

    /**
     * 검수 반려 — 제출 = 즉시 확정이고 되돌리지 않는다. 사유 · 상세 · 법적 근거 · 소비자 메시지 · 증빙이 전부 있어야 한다.
     * 반려 상품을 다시 받는 배송비 청구가 선다(같은 박스에 미정산 건이 있으면 합류 — 반송도 한 박스다 · 브랜드 귀책 인정이면
     * 0원). <b>반려는 구매확정 정지를 푼다</b> — 진행 중 클레임이 남지 않으면 남은 일수부터 다시 센다.
     *
     * <p>일부 반려 — 원래 행을 통과 수량으로 줄여 통과시키고, 반려 수량은 같은 요청의 새 행으로 갈라 반려한다. 판정 종료가 한
     * 번에 「통과분 환불 − 재발송비」를 계산한다(앱 클레임 설계서 1-4 일부 반려 분기).
     */
    @Transactional
    public void rejectInspection(Long claimId, Long marketId, Long sellerId, RejectCommand command, LocalDateTime now) {
        String rejectDetail = blankToNull(command.detail());
        String consumerMessage = blankToNull(command.consumerMessage());
        List<String> evidenceImageUrls = command.evidenceImageUrls();
        int evidenceCount = evidenceImageUrls == null ? 0 : evidenceImageUrls.size();
        if (command.reasonCode() == null || rejectDetail == null || command.legalBasis() == null
                || consumerMessage == null || evidenceCount < 1
                || evidenceCount > orderProperties.getClaim().getEvidenceMax()
                || evidenceImageUrls.stream().anyMatch(url -> url == null || url.isBlank())) {
            throw new BusinessException(ErrorCode.CLAIM_REJECT_INCOMPLETE);
        }
        OrderClaim claim = claimRepository.findOwned(claimId, marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        if (claim.getStatus() != ClaimStatus.RECEIVED) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        int quantity = claim.getQuantity();
        Integer requested = command.rejectedQuantity();
        if (requested != null && (requested < 1 || requested > quantity)) {
            throw new BusinessException(ErrorCode.CLAIM_QUANTITY_EXCEEDED);
        }
        Long collectionId = claim.getCollection().getId();
        Long deliveryGroupId = claim.getDeliveryGroup().getId();
        OrderClaimCollection collection = collectionRepository.findForUpdate(collectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        // 귀책 변경 — 판정 종료 전에 요청 값을 바꿔 둔다(환불액 · 재발송비가 종료 때 이 값으로 계산된다).
        if (command.faultToSeller()) {
            collection.acceptSellerFault();
        }
        int reshipFee = command.faultToSeller() ? 0 : feePolicy.rejectReshipFee(claim.getDeliveryGroup());
        if (pendingRejectCharge(collectionId) == null) {
            chargeRepository.save(OrderClaimCharge.builder()
                    .collection(collection).type(ClaimChargeType.REJECT_RESHIP).amount(reshipFee)
                    .status(ClaimChargeStatus.PENDING).createdAt(now).build());
        }

        Long rejectTargetId = claimId;
        if (requested != null && requested < quantity) {
            // 일부 반려 — 반려 수량을 새 행으로 가르고 원래 행(통과 수량)은 통과시킨다.
            OrderClaim split = claimRepository.save(OrderClaim.splitOf(claim, requested, now));
            rejectTargetId = split.getId();
            appendHistory(split, ClaimEventType.RECEIVED, FulfillmentActorType.SELLER, sellerId,
                    "일부 반려 · " + claim.claimNumber() + "에서 " + requested + "개 분리", now);
            if (claimRepository.shrinkForPartialReject(claimId, marketId, quantity - requested) != 1) {
                throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
            }
            passInternal(claimRepository.findById(claimId).orElseThrow(), marketId, sellerId, now);
        }
        OrderClaim target = claimRepository.findById(rejectTargetId).orElseThrow();
        Long exchangeVariantId = target.getExchangeVariantId();
        int rejectedQuantity = target.getQuantity();
        if (claimRepository.rejectInspection(rejectTargetId, marketId, command.reasonCode(), rejectDetail,
                command.legalBasis(), consumerMessage, command.faultToSeller(), sellerId, now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        if (command.faultToSeller()) {
            claimRepository.acceptSellerFault(collectionId);
        }
        OrderClaim rejected = claimRepository.getReferenceById(rejectTargetId);
        for (int i = 0; i < evidenceCount; i++) {
            attachmentRepository.save(OrderClaimAttachment.builder()
                    .claim(rejected).owner(ClaimAttachmentOwner.SELLER).imageUrl(evidenceImageUrls.get(i))
                    .sortOrder(i).build());
        }
        appendHistory(rejected, ClaimEventType.INSPECTION_REJECTED, FulfillmentActorType.SELLER, sellerId,
                command.reasonCode().getLabel()
                        + (rejectedQuantity < quantity ? " · 일부 반려 " + rejectedQuantity + "개" : "")
                        + (command.faultToSeller() ? " · 브랜드 귀책 인정" : ""), now);
        // 교환은 무효다 — 받은 상품을 그대로 돌려보내므로 잡아 둔 새 옵션의 재고를 반려 수량만큼 되돌린다.
        if (exchangeVariantId != null) {
            productVariantRepository.restoreStock(exchangeVariantId, rejectedQuantity);
        }
        finalizeCollection(collectionId, now);
        fulfillmentService.resumeConfirmTimerIfIdle(deliveryGroupId, now);
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
        // 브랜드 귀책 인정(재발송비 0원) — 소비자가 결제할 것이 없다. 반려 상품은 바로 재발송 대기로 간다.
        if (rejectCharge != null && rejectCharge.isPending() && rejectCharge.getAmount() == 0) {
            rejectCharge.settle(ClaimChargeStatus.WAIVED, null, now);
            settlement = "브랜드 귀책 · 재발송비 브랜드 부담";
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

    // ------------------------------------------------------------------ 재발송(3-4 · 전이 #9 · #11)

    /**
     * 재발송 송장 처리 결과 — 다건 등록이 행 단위 부분 성공이라 예외가 아니라 값으로 돌려준다.
     *
     * @param code    실패 사유 — 성공이면 null
     * @param message 결과 배너에 그대로 쓰는 문구 — 중복이면 내 마켓 건일 때만 겹치는 번호를 지목한다
     */
    public record ReshipResult(ErrorCode code, String message) {
        public static final ReshipResult OK = new ReshipResult(null, null);

        static ReshipResult fail(ErrorCode code) {
            return new ReshipResult(code, code.getMessage());
        }

        public boolean success() {
            return code == null;
        }
    }

    /**
     * 재발송 송장 등록 — 교환 새 상품이든 반려 상품 반송이든 같은 길이다. <b>등록해도 완료가 아니다</b> — 재발송 중으로
     * 가고 결과는 도착 때 확정된다. 형식 검증 → 전역 중복(종결 전 주문 송장 + 재발송 중인 클레임 송장) → 전이.
     */
    @Transactional
    public ReshipResult registerReshipment(Long claimId, Long marketId, Long sellerId, DeliveryCarrier carrier,
                                           String rawTrackingNumber, LocalDateTime now) {
        String trackingNumber = rawTrackingNumber == null ? "" : rawTrackingNumber.replaceAll("[^0-9]", "");
        if (carrier == null || !carrier.isSelectable() || trackingNumber.isEmpty()
                || tracker.validateInvoice(carrier, trackingNumber) == ValidationResult.INVALID) {
            return ReshipResult.fail(ErrorCode.INVOICE_FORMAT_INVALID);
        }
        String duplicate = duplicateInvoiceMessage(carrier, trackingNumber, claimId, marketId);
        if (duplicate != null) {
            return new ReshipResult(ErrorCode.INVOICE_DUPLICATE, duplicate);
        }
        if (claimRepository.registerReshipment(claimId, marketId, carrier, trackingNumber, sellerId, now) != 1) {
            return ReshipResult.fail(ErrorCode.CLAIM_STATE_CHANGED);
        }
        appendHistory(claimId, ClaimEventType.RESHIP_INVOICE_REGISTERED, FulfillmentActorType.SELLER, sellerId,
                carrier.getLabel() + " " + trackingNumber, now);
        return ReshipResult.OK;
    }

    /** 재발송 송장 수정 — 재발송 중만. 등록 시각은 유지하고 추적 값만 리셋한다. 이력에 구 → 신을 남긴다. */
    @Transactional
    public void updateReshipment(Long claimId, Long marketId, Long sellerId, DeliveryCarrier carrier,
                                 String rawTrackingNumber, LocalDateTime now) {
        OrderClaim claim = claimRepository.findOwned(claimId, marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        String trackingNumber = rawTrackingNumber == null ? "" : rawTrackingNumber.replaceAll("[^0-9]", "");
        if (carrier == null || !carrier.isSelectable() || trackingNumber.isEmpty()
                || tracker.validateInvoice(carrier, trackingNumber) == ValidationResult.INVALID) {
            throw new BusinessException(ErrorCode.INVOICE_FORMAT_INVALID);
        }
        String duplicate = duplicateInvoiceMessage(carrier, trackingNumber, claimId, marketId);
        if (duplicate != null) {
            throw new BusinessException(ErrorCode.INVOICE_DUPLICATE, duplicate);
        }
        String before = claim.getReshipCarrier() == null ? ""
                : claim.getReshipCarrier().getLabel() + " " + claim.getReshipTrackingNumber();
        if (claimRepository.updateReshipment(claimId, marketId, carrier, trackingNumber) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        appendHistory(claimId, ClaimEventType.RESHIP_INVOICE_UPDATED, FulfillmentActorType.SELLER, sellerId,
                before + " → " + carrier.getLabel() + " " + trackingNumber, now);
    }

    /** 업로드 파싱이 같은 중복 판정을 미리 본다 — 확정(등록)이 다시 본다. */
    @Transactional(readOnly = true)
    public String findReshipInvoiceDuplicate(DeliveryCarrier carrier, String trackingNumber, Long selfClaimId,
                                             Long marketId) {
        return duplicateInvoiceMessage(carrier, trackingNumber, selfClaimId, marketId);
    }

    /**
     * 전역 송장 중복 — 종결 전 하위주문의 송장과 재발송 중인 클레임의 재발송 송장 양쪽을 본다.
     *
     * @return 겹치면 안내 문구(내 마켓 건이면 겹치는 번호를 지목 · 남의 건이면 숨긴다), 아니면 null
     */
    private String duplicateInvoiceMessage(DeliveryCarrier carrier, String trackingNumber, Long selfClaimId,
                                           Long marketId) {
        for (OrderDeliveryGroup group : deliveryGroupRepository.findActiveByInvoice(carrier, trackingNumber,
                FulfillmentStatus.INVOICE_ACTIVE)) {
            return marketId.equals(group.getMarketId())
                    ? group.getOrder().getOrderNumber() + "에 이미 등록된 번호입니다."
                    : "다른 주문에 이미 등록된 번호입니다.";
        }
        for (OrderClaim other : claimRepository.findReshippingByInvoice(carrier, trackingNumber)) {
            if (!other.getId().equals(selfClaimId)) {
                return marketId.equals(other.getMarketId())
                        ? other.claimNumber() + "에 이미 등록된 번호입니다."
                        : "다른 주문에 이미 등록된 번호입니다.";
            }
        }
        return null;
    }

    /** 재발송 도착 — 운영자 직권. 추적이 놓친 건의 출구다(0-7). */
    @Transactional
    public void completeReshipByAdmin(Long claimId, Long adminId, LocalDateTime now) {
        OrderClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        boolean rejected = claim.getRejectedAt() != null;
        Long deliveryGroupId = claim.getDeliveryGroup().getId();
        if (claimRepository.completeReshipByAdmin(claimId,
                rejected ? ClaimResult.REJECTED : ClaimResult.EXCHANGED, now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        afterReshipDelivered(claimId, deliveryGroupId, rejected, now, FulfillmentActorType.ADMIN, adminId, now);
    }

    /**
     * 재발송이 도착한 뒤 — 교환 완료면 구매확정 N일을 도착 시각부터 다시 센다(정산 금액은 불변). 거절 반송이면 할 일이
     * 없다 — 구매확정 보류는 거절 시점에 이미 풀렸고 환불도 없다.
     */
    private void afterReshipDelivered(Long claimId, Long deliveryGroupId, boolean rejected, LocalDateTime deliveredAt,
                                      FulfillmentActorType actorType, Long actorId, LocalDateTime now) {
        if (!rejected) {
            fulfillmentService.restartConfirmTimer(deliveryGroupId, deliveredAt, now);
        } else {
            fulfillmentService.resumeConfirmTimerIfIdle(deliveryGroupId, now);
        }
        appendHistory(claimId, ClaimEventType.RESHIP_DELIVERED, actorType, actorId,
                rejected ? "반송 완료 · 원래 상품 도착" : "재발송 도착 · 구매확정 카운트 재시작", now);
    }

    // ------------------------------------------------------------------ 추적 반영(3-5 · 전이 #3 · #11)

    @Transactional(readOnly = true)
    public List<OrderClaimCollection> findCollectionTrackingTargets(long afterId, int limit) {
        return collectionRepository.findCollectionTrackingTargets(afterId, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<OrderClaim> findReshipTrackingTargets(long afterId, int limit) {
        return claimRepository.findReshipTrackingTargets(afterId, PageRequest.of(0, limit));
    }

    /**
     * 회수 송장 추적 1건 반영 — 포트 호출은 트랜잭션 밖(호출자)이다. {@code carrier} · {@code trackingNumber}는 폴링
     * 당시의 송장이다 — 그사이 소비자가 송장을 정정했으면 구 송장의 결과가 덮이지 않는다. 스캔 이력을 저장하고(앱의 회수
     * 조회가 읽는다), 도착이 확인되면 묶음 전체를 「입고 확인 전」으로 옮긴다.
     */
    @Transactional
    public void applyCollectionTracking(Long collectionId, DeliveryCarrier carrier, String trackingNumber,
                                        TrackSnapshot snapshot, LocalDateTime now) {
        if (snapshot == null || snapshot.lastEventAt() == null) {
            return; // 집화 전에는 데이터가 없는 게 정상이다.
        }
        List<TrackEvent> events = snapshot.events();
        String label = events.isEmpty() ? null : events.get(events.size() - 1).description();
        if (collectionRepository.touchTracking(collectionId, carrier, trackingNumber, snapshot.lastEventAt(), label)
                != 1) {
            return;
        }
        trackingEventRecorder.record(carrier, trackingNumber, events);
        if (snapshot.deliveredAt() == null) {
            return;
        }
        collectionRepository.markArrived(collectionId, carrier, trackingNumber, snapshot.deliveredAt());
        List<Long> collectingIds = claimRepository.findByCollectionId(collectionId).stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.COLLECTING).map(OrderClaim::getId).toList();
        if (claimRepository.markArrived(collectionId, carrier, trackingNumber, now) > 0) {
            for (Long claimId : collectingIds) {
                appendHistory(claimId, ClaimEventType.ARRIVED, FulfillmentActorType.TRACKER, null, null, now);
            }
        }
    }

    /** 재발송 송장 추적 1건 반영 — 도착이 확인되면 종결한다(교환 완료 / 거절 종결). 배송 이상·반송은 감지하지 않는다. */
    @Transactional
    public void applyReshipTracking(Long claimId, DeliveryCarrier carrier, String trackingNumber,
                                    TrackSnapshot snapshot, LocalDateTime now) {
        if (snapshot == null || snapshot.lastEventAt() == null) {
            return;
        }
        OrderClaim claim = claimRepository.findById(claimId).orElse(null);
        if (claim == null) {
            return;
        }
        boolean rejected = claim.getRejectedAt() != null;
        Long deliveryGroupId = claim.getDeliveryGroup().getId();
        if (claimRepository.touchReshipTracking(claimId, carrier, trackingNumber, snapshot.lastEventAt()) != 1) {
            return;
        }
        trackingEventRecorder.record(carrier, trackingNumber, snapshot.events());
        if (snapshot.deliveredAt() != null && claimRepository.completeReshipByTracker(claimId, carrier,
                trackingNumber, rejected ? ClaimResult.REJECTED : ClaimResult.EXCHANGED, snapshot.deliveredAt(), now)
                == 1) {
            afterReshipDelivered(claimId, deliveryGroupId, rejected, snapshot.deliveredAt(),
                    FulfillmentActorType.TRACKER, null, now);
        }
    }

    // ------------------------------------------------------------------ 거절 보류 — 고지 · 폐기(1-9 · 전이 #12)

    /**
     * 미결제 고지 기록 — <b>발송 자체는 알림 모듈</b>이고 여기는 발송 성공 뒤의 기록이다. 거절 보류인 동안만 받는다.
     * 고지가 2회 쌓이면 보관 기한(최종 고지일 + 3개월)이 생긴다 — 기한은 저장하지 않는 계산값이다.
     *
     * @return 기록한 회차
     */
    @Transactional
    public int recordStorageNotice(Long claimId, String channel, FulfillmentActorType actorType, Long actorId,
                                   LocalDateTime now) {
        OrderClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        int seq = claim.getNoticeCount() + 1;
        // 읽은 횟수에서 정확히 1 올린다 — 같은 회차를 두 호출이 동시에 기록하면 하나만 통과한다.
        if (claimRepository.recordNotice(claimId, claim.getNoticeCount(), now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        noticeRepository.save(OrderClaimNotice.builder()
                .claim(claimRepository.getReferenceById(claimId)).seq(seq).notifiedAt(now).channel(channel)
                .actorType(actorType).actorId(actorId).build());
        appendHistory(claimId, ClaimEventType.STORAGE_NOTICE_SENT, actorType, actorId, seq + "회차", now);
        return seq;
    }

    /**
     * 보관 기간 만료 후 폐기 기록(어드민) — 가드는 <b>고지 2회 이상 · 보관 기한 경과</b>다. 기한이 지나면 「폐기할 수
     * 있다」이지 「폐기된다」가 아니라, 배치가 기계적으로 닫지 않고 이 기록이 종결을 만든다(대상이 소비자 소유물이다).
     * 가드를 계산한 뒤 고지가 더해져 기한이 밀렸으면 받지 않는다.
     */
    @Transactional
    public void disposeAfterStorage(Long claimId, Long adminId, LocalDateTime now) {
        OrderClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        Long collectionId = claim.getCollection().getId();
        int noticeCount = claim.getNoticeCount();
        if (claim.getStatus() != ClaimStatus.REJECT_HOLD
                || storagePolicy.phase(noticeCount, claim.getLastNoticeAt(), now) != StoragePhase.EXPIRED) {
            throw new BusinessException(ErrorCode.CLAIM_STORAGE_NOT_EXPIRED);
        }
        collectionRepository.findForUpdate(collectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        // 그 박스의 반려 상품이 전부 폐기로 끝나면 재발송비 청구도 소멸한다 — 다른 건이 아직 보류 중이면 남긴다.
        boolean othersOnHold = claimRepository.findByCollectionId(collectionId).stream()
                .anyMatch(other -> !other.getId().equals(claimId) && other.getStatus() == ClaimStatus.REJECT_HOLD);
        OrderClaimCharge charge = othersOnHold ? null : pendingRejectCharge(collectionId);
        if (charge != null) {
            charge.settle(ClaimChargeStatus.VOID, null, now);
        }
        if (claimRepository.dispose(claimId, noticeCount, now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_STORAGE_NOT_EXPIRED);
        }
        appendHistory(claimId, ClaimEventType.DISPOSED, FulfillmentActorType.ADMIN, adminId, null, now);
    }

    /** 반려 이의 인용 결과 — 편입한 환불 큐 · 서버가 계산한 환불액(운영자 입력 없음). */
    public record DisputeAcceptance(Long refundTaskId, int amount) {
    }

    /**
     * 인용 계획 — 환불액과 반려 재발송비 청구의 처리. 같은 박스에 재발송 대상 반려가 더 남아 있으면 청구는 그 건의 것이라
     * {@code charge = null}(건드리지 않는다 · 환원 없음).
     */
    private record DisputePlan(int goods, OrderClaimCharge charge, int restored) {
        int amount() {
            return goods + restored;
        }
    }

    /** 반려 재발송 대상 — 반려 보류(결제 전) · 재발송 대기(결제 · 차감 · 충당 뒤). 송장이 나간 뒤({@code RESHIPPING})는 아니다. */
    private static final Set<ClaimStatus> RESHIP_TARGET = EnumSet.of(ClaimStatus.REJECT_HOLD, ClaimStatus.RESHIP_READY);

    /**
     * 이의를 인용할 수 있는 반려인가 — 반품 ∧ 반려됨 ∧ 아직 반송 전(반려 보류 · 재발송 대기). 재발송비가 결제 · 차감되면 반려 보류가
     * 재발송 대기로 넘어가므로 둘 다 받는다(41 보고 1번 · 2026-10-09 확정). 교환 반려는 인용의 효과가 정의되지 않아 받지 않는다.
     */
    public static boolean isDisputeAcceptable(OrderClaim claim) {
        return claim.getType() == ClaimType.RETURN && claim.getRejectedAt() != null
                && RESHIP_TARGET.contains(claim.getStatus());
    }

    /** 인용 환불액 미리 보기 — 어드민 상세의 수정 불가 금액. 인용할 수 없으면 null. */
    @Transactional(readOnly = true)
    public Integer previewDisputeRefund(Long claimId) {
        return claimRepository.findById(claimId).filter(OrderClaimService::isDisputeAcceptable)
                .map(claim -> planDispute(claim).amount()).orElse(null);
    }

    private DisputePlan planDispute(OrderClaim claim) {
        Long collectionId = claim.getCollection().getId();
        int goods = Math.toIntExact((long) claim.getOrderProduct().getPrice() * claim.getQuantity());
        boolean othersToReship = claimRepository.findByCollectionId(collectionId).stream()
                .anyMatch(other -> !other.getId().equals(claim.getId()) && other.getRejectedAt() != null
                        && RESHIP_TARGET.contains(other.getStatus()));
        OrderClaimCharge charge = othersToReship ? null : chargeRepository.findByCollectionId(collectionId).stream()
                .filter(found -> found.getType() == ClaimChargeType.REJECT_RESHIP)
                .reduce((first, second) -> second).orElse(null);
        int restored = charge != null && charge.getStatus() == ClaimChargeStatus.DEDUCTED && charge.getAmount() != null
                ? charge.getAmount() : 0;
        return new DisputePlan(goods, charge, restored);
    }

    /**
     * 반려 이의 인용(어드민 06b B2 · 38 설계서 2-4 · 41 보고 1번) — 검수 반려에 대한 소비자 이의를 운영자가 받아들였다. 「반품 승인과
     * 같아지는 것」이다: 상품은 브랜드에 있으므로 반품 수량으로 올리고, 귀책을 브랜드로 돌리고, 돈은 <b>운영자 사유 환불로 편입</b>한다
     * (집행은 환불 관리의 재확인에서만).
     *
     * <p>환불액은 <b>서버 계산</b>이다 — {@code 단가 × 수량} + 통과분 환불에서 차감했던 재발송비(환원). 반려 재발송비 청구는 같은
     * 박스에 재발송 대상 반려가 이 건뿐일 때만 정리한다: 결제 전이면 소멸, 결제됐으면 결제 취소(선점만 — 포트원 취소는 커밋 뒤
     * 호출자가 {@code ClaimPaymentService.cancelRequested}로, 실패분은 정리 배치가), 차감됐으면 소멸 + 환불액에 가산.
     */
    @Transactional
    public DisputeAcceptance acceptRejectionDispute(Long claimId, Long adminId, String detail, LocalDateTime now) {
        OrderClaim found = claimRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        Long collectionId = found.getCollection().getId();
        // 같은 박스의 판정 · 재발송비 결제와 직렬화한다 — 잠근 뒤 상태를 다시 본다.
        collectionRepository.findForUpdate(collectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        OrderClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        if (!isDisputeAcceptable(claim)) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED,
                    "반려 보류 · 재발송 대기 중인 반품만 이의를 인용할 수 있습니다.");
        }
        Long orderProductId = claim.getOrderProduct().getId();
        int quantity = claim.getQuantity();
        OrderDeliveryGroup group = claim.getDeliveryGroup();
        DisputePlan plan = planDispute(claim);
        int amount = plan.amount();

        List<String> notes = new ArrayList<>();
        String paymentToCancel = null;
        OrderClaimCharge charge = plan.charge();
        if (charge != null) {
            switch (charge.getStatus()) {
                case PENDING -> {
                    charge.settle(ClaimChargeStatus.VOID, null, now);
                    notes.add("재발송비 결제 요청 취소");
                }
                case PAID -> {
                    charge.refund(now);
                    paymentToCancel = charge.getPaidPaymentId();
                    notes.add("재발송비 결제 취소");
                }
                case DEDUCTED -> {
                    charge.settle(ClaimChargeStatus.VOID, null, now);
                    notes.add(String.format("차감 환원 %,d원", plan.restored()));
                }
                default -> {
                    // WAIVED(브랜드 부담) · 이미 정리된 청구 — 소비자가 낸 돈이 없다.
                }
            }
        }
        OrderRefundTask task = fulfillmentService.enqueueOperatorRefund(group, claimId, amount,
                showroomz.domain.order.type.OperatorRefundReason.DISPUTE_ACCEPTED, detail, adminId, now);
        if (claimRepository.closeRejectedByDispute(claimId, amount, now) != 1) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        orderProductRepository.addReturnedQuantity(orderProductId, quantity);
        orderProductRepository.markReturnedIfFull(orderProductId);
        // 선점 UPDATE 가 영속성 컨텍스트를 비운다 — 청구의 변경이 위의 조건부 UPDATE 로 이미 적힌 뒤에 부른다.
        if (paymentToCancel != null) {
            claimPaymentRepository.requestCancel(paymentToCancel, List.of(ClaimPaymentStatus.PAID), now);
        }
        StringBuilder history = new StringBuilder(String.format("%,d원 · %s", amount, detail));
        notes.forEach(note -> history.append(" · ").append(note));
        appendHistory(claimId, ClaimEventType.DISPUTE_ACCEPTED, FulfillmentActorType.ADMIN, adminId,
                history.toString(), now);
        return new DisputeAcceptance(task.getId(), amount);
    }

    /**
     * 재발송비(추가 결제) 결제 취소 확인의 <b>기록 행</b> — 교환 철회 · 반려 이의 인용 · 중복 결제 자동 취소 등으로 돌려준 추가 결제를
     * 어드민 환불 관리 완료 탭에 보인다(39 설계서 0-4 · P3). 결제 취소를 적은 트랜잭션 안에서 부른다. 집행기는 DONE 행을 집지 않는다.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordClaimPaymentRefund(String paymentId, LocalDateTime now) {
        showroomz.domain.order.entity.OrderClaimPayment payment = claimPaymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getCollectionId() == null || payment.getAmount() == null) {
            return;
        }
        OrderClaimCollection collection = collectionRepository.findById(payment.getCollectionId()).orElse(null);
        if (collection == null) {
            return;
        }
        OrderDeliveryGroup group = collection.getDeliveryGroup();
        refundTaskRepository.save(OrderRefundTask.recorded(group, group.getOrder(),
                RefundTaskSource.CLAIM_PAYMENT_CANCELLED, collection.getId(), payment.getAmount(), paymentId,
                showroomz.domain.order.type.RefundPaymentKind.ADDITIONAL, null, now));
    }

    // ------------------------------------------------------------------ 환불 집행 · 재발송비 결제(3-7 · 전이 #8 · #10)

    /**
     * 환불 수동 기록(어드민) — PG 를 거치지 않고 집행 완료로 적는다(결제 밖에서 돌려준 건 · 결제가 없는 주문). PG 자동 환불은
     * {@code RefundTransitions.complete}가 같은 후속({@link #applyRefundExecuted})을 부른다(1009 기획 수정본 2-4).
     */
    @Transactional
    public void completeRefund(Long refundTaskId, int amount, Long adminId, LocalDateTime now) {
        OrderRefundTask task = refundTaskRepository.findForUpdate(refundTaskId)
                .filter(found -> found.getSource() == RefundTaskSource.CLAIM_RETURN_PASSED)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        if (!task.isExecutable() || amount < 0) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        Long collectionId = task.getSourceId();
        task.markExecuted(amount, adminId, now);
        if (!applyRefundExecuted(collectionId, amount, FulfillmentActorType.ADMIN, adminId, now)) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
    }

    /**
     * 환불 집행 뒤의 클레임 종결 — 환불 큐가 요청 단위라 그 요청의 환불 대기 클레임을 <b>한꺼번에</b> 닫는다. 집행액은 예정액과
     * 다를 수 있다. 항목별 확정액은 상품 금액에서 차감분을 앞 항목부터 빼서 나눠 적는다(합이 집행액과 같다).
     *
     * <p>PG 자동 환불의 완료 트랜잭션 안에서도 불린다 — 돈은 이미 나갔으므로 닫을 클레임이 없어도 예외를 던지지 않는다
     * (던지면 완료 기록까지 롤백된다).
     *
     * @return 닫은 클레임이 있으면 true
     */
    @Transactional
    public boolean applyRefundExecuted(Long collectionId, int amount, FulfillmentActorType actorType, Long actorId,
                                       LocalDateTime now) {
        OrderClaimCollection collection = collectionRepository.findForUpdate(collectionId).orElse(null);
        if (collection == null) {
            return false;
        }
        List<OrderClaim> pending = claimRepository.findByCollectionId(collectionId).stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.REFUND_PENDING).toList();
        Map<Long, Long> goodsByClaim = pending.stream().collect(Collectors.toMap(OrderClaim::getId,
                claim -> (long) claim.getOrderProduct().getPrice() * claim.getQuantity()));
        collection.confirmRefund(amount);
        if (claimRepository.completeRefundByCollection(collectionId, now) == 0) {
            return false;
        }
        long shortfall = Math.max(0, goodsByClaim.values().stream().mapToLong(Long::longValue).sum() - amount);
        for (OrderClaim claim : pending) {
            long goods = goodsByClaim.get(claim.getId());
            long deducted = Math.min(goods, shortfall);
            shortfall -= deducted;
            claimRepository.setRefundedAmount(claim.getId(), (int) (goods - deducted));
            appendHistory(claim.getId(), ClaimEventType.REFUND_EXECUTED, actorType, actorId,
                    String.format("%,d원", amount), now);
        }
        // 반품이 환불로 끝났다 — 남은 항목의 구매확정 타이머를 재개한다.
        fulfillmentService.resumeConfirmTimerIfIdle(collection.getDeliveryGroup().getId(), now);
        return true;
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
        return pendingCharge(collectionId, ClaimChargeType.REJECT_RESHIP);
    }

    private OrderClaimCharge pendingCharge(Long collectionId, ClaimChargeType type) {
        return chargeRepository.findByCollectionId(collectionId).stream()
                .filter(charge -> charge.getType() == type && charge.isPending())
                .findFirst().orElse(null);
    }

    /** 접수 이력 — 접수되는 순간(요청 즉시 또는 결제 확정)에 한 번. 송장을 같이 냈으면 송장 입력 이력도 남긴다. */
    private void appendAccepted(OrderClaim claim, Long userId, Invoice invoice, LocalDateTime now) {
        appendHistory(claim, ClaimEventType.REQUESTED, FulfillmentActorType.CONSUMER, userId, null, now);
        if (invoice != null) {
            appendHistory(claim, ClaimEventType.COLLECTION_INVOICE_REGISTERED, FulfillmentActorType.CONSUMER, userId,
                    invoiceLabel(invoice), now);
        }
    }

    /** 선점한 교환 옵션 재고를 되돌린다 — 차감과 같은 순서(옵션 id 오름차순). 반품 클레임은 잡은 재고가 없다. */
    private void restoreExchangeStock(List<OrderClaim> claims) {
        Map<Long, Integer> quantityByVariant = new TreeMap<>();
        for (OrderClaim claim : claims) {
            if (claim.getExchangeVariantId() != null) {
                quantityByVariant.merge(claim.getExchangeVariantId(), claim.getQuantity(), Integer::sum);
            }
        }
        quantityByVariant.forEach(productVariantRepository::restoreStock);
    }

    /**
     * 요청이 사라진 뒤의 정리(철회 · 자동 취소 · 직권 종결) — 교환이면 선점 재고를 되돌리고, <b>그 요청에 남은 클레임이
     * 하나도 없으면</b> 선결제한 재발송 배송비를 돌려준다. 포트원 취소는 트랜잭션 밖에서 한다 — 여기서는 결제 행을 취소
     * 요청으로 선점만 하고, 커밋 뒤 호출자(또는 정리 배치)가 집행한다.
     */
    private void releaseAfterCancel(Long collectionId, List<Long> cancelledClaimIds, LocalDateTime now) {
        List<OrderClaim> claims = claimRepository.findByCollectionId(collectionId);
        restoreExchangeStock(claims.stream().filter(claim -> cancelledClaimIds.contains(claim.getId())).toList());
        if (claims.stream().anyMatch(claim -> claim.getResult() != ClaimResult.CANCELLED)) {
            return;
        }
        List<String> paymentIds = new ArrayList<>();
        for (OrderClaimCharge charge : chargeRepository.findByCollectionId(collectionId)) {
            if (charge.getType() == ClaimChargeType.EXCHANGE_RESHIP && charge.getStatus() == ClaimChargeStatus.PAID) {
                charge.refund(now);
                if (charge.getPaidPaymentId() != null) {
                    paymentIds.add(charge.getPaidPaymentId());
                }
            }
        }
        // 선점 UPDATE 가 영속성 컨텍스트를 비운다 — 청구의 변경을 다 적어 둔 뒤에 부른다.
        for (String paymentId : paymentIds) {
            claimPaymentRepository.requestCancel(paymentId, List.of(ClaimPaymentStatus.PAID), now);
        }
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
        if (raw.carrier() == null || !raw.carrier().isSelectable() || trackingNumber.isEmpty()
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
        // 알림 모듈 연계 — 수신자는 커밋 뒤에 받는다. 이 모듈은 알림을 보내지 않는다.
        eventPublisher.publishEvent(new ClaimHistoryRecordedEvent(claim.getId(), eventType, detail, now));
    }

    private static String invoiceLabel(Invoice invoice) {
        return invoice.carrier().getLabel() + " " + invoice.trackingNumber();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
