package showroomz.api.app.claim.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.claim.dto.UserClaimDto;
import showroomz.api.app.claim.dto.UserClaimDto.ActionType;
import showroomz.api.app.claim.dto.UserClaimDto.CourierPayment;
import showroomz.api.app.claim.dto.UserClaimDto.ReshipFeeState;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.api.app.order.service.OrderAddressMasker;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimAttachment;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.entity.OrderClaimPayment;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderClaimAttachmentRepository;
import showroomz.domain.order.repository.OrderClaimChargeRepository;
import showroomz.domain.order.repository.OrderClaimPaymentRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.service.ClaimExchangeOptionReader;
import showroomz.domain.order.service.ClaimFeePolicy;
import showroomz.domain.order.service.ClaimStoragePolicy;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.service.UserClaimPresenter;
import showroomz.domain.order.type.ClaimAttachmentOwner;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.UserClaimPhase;
import showroomz.domain.order.type.UserOrderTone;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 소비자 앱 반품·교환 조회 — 요청 화면의 폼 데이터(앱 클레임 설계서 3-1)와 상세(4-1). 상태 줄 · 할 일 · 버튼은
 * {@link UserClaimPresenter}가 만든 값을 그대로 싣는다 — 주문 내역과 같은 문구가 나와야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserClaimQueryService {

    /** 지금 서버에 받는 API 가 있는 버튼만 내린다. */
    static final Set<ActionType> ENABLED_ACTIONS = EnumSet.of(ActionType.WITHDRAW,
            ActionType.REGISTER_COLLECTION_INVOICE, ActionType.TRACK_COLLECTION, ActionType.TRACK_RESHIP,
            ActionType.INQUIRY);

    /** 아직 검수 판정을 받지 않은 단계 — 교환받을 배송지를 바꿀 수 있는 구간이다. */
    private static final Set<ClaimStatus> AWAITING_JUDGEMENT = EnumSet.of(ClaimStatus.REQUESTED,
            ClaimStatus.COLLECTING, ClaimStatus.ARRIVED, ClaimStatus.RECEIVED);
    private static final String METHOD_LABEL = "고객 직접 발송";
    /** 법적 근거 한 줄 — 사유별 문구는 법무 확정 대기라 한 문구로 둔다(Q11). */
    private static final String LEGAL_NOTE = "전자상거래법상 청약철회 제한 사유에 해당해요";

    private final OrderProductRepository orderProductRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderClaimRepository claimRepository;
    private final OrderClaimChargeRepository chargeRepository;
    private final OrderClaimAttachmentRepository attachmentRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final OrderClaimPaymentRepository claimPaymentRepository;
    private final ClaimExchangeOptionReader exchangeOptionReader;
    private final ClaimFeePolicy feePolicy;
    private final ClaimStoragePolicy storagePolicy;
    private final OrderProperties orderProperties;

    // ------------------------------------------------------------------ 폼(3-1)

    public UserClaimDto.FormResponse getForm(Long userId, Long orderProductId, ClaimType type) {
        OrderProduct entry = orderProductRepository.findById(orderProductId)
                .filter(product -> product.getOrder().isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_PRODUCT_NOT_FOUND));
        OrderDeliveryGroup group = entry.getDeliveryGroup();
        Map<Long, Integer> claimable = group == null ? Map.of() : claimableQuantities(group);
        // 진입 항목이 신청할 수 없으면 폼을 열지 않는다 — 배송완료가 아니거나 남은 수량이 없다.
        if (claimable.getOrDefault(orderProductId, 0) < 1) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE);
        }
        List<UserClaimDto.FormItem> items = orderProductRepository.findByDeliveryGroupIds(List.of(group.getId()))
                .stream()
                .filter(product -> claimable.getOrDefault(product.getId(), 0) >= 1)
                .map(product -> UserClaimDto.FormItem.builder()
                        .orderProductId(product.getId())
                        .productName(product.getProductName())
                        .optionName(product.getOptionName())
                        .thumbnailUrl(product.getImageUrl())
                        .unitPrice(product.getPrice())
                        .claimableQuantity(claimable.get(product.getId()))
                        .preselected(product.getId().equals(orderProductId))
                        .exchangeOptions(type != ClaimType.EXCHANGE ? null
                                : exchangeOptionReader.optionsOf(product).stream()
                                .map(option -> new UserClaimDto.ExchangeOption(option.variantId(),
                                        option.optionName(), option.soldOut(), option.current()))
                                .toList())
                        .build())
                .toList();
        Order order = entry.getOrder();
        Market market = group.getMarket();
        OrderProperties.Claim config = orderProperties.getClaim();
        return UserClaimDto.FormResponse.builder()
                .type(type)
                .deliveryGroupId(group.getId())
                .brandName(group.getMarketName())
                .items(items)
                .reasons(Arrays.stream(ClaimReason.values())
                        .filter(ClaimReason::isConsumerSelectable)
                        .map(reason -> UserClaimDto.FormReason.builder()
                                .code(reason)
                                .label(reason.getLabel())
                                .hint(hintOf(reason, type))
                                .feeBearer(reason.getFeeBearer())
                                .detailRequired(reason.isDetailRequired())
                                .photoAllowed(reason.getFeeBearer() == ClaimFeeBearer.SELLER)
                                .build())
                        .toList())
                .carriers(Arrays.stream(DeliveryCarrier.values())
                        .filter(DeliveryCarrier::isConsumerSelectable)
                        .map(carrier -> new UserClaimDto.FormCarrier(carrier, carrier.getLabel()))
                        .toList())
                .returnTo(new UserClaimDto.ReturnTo(market.getShippingRecipientName(), market.getShippingContact(),
                        market.getShippingAddress(), market.getShippingDetailAddress()))
                // 요청 화면의 「받을 곳」은 지금 고르는 값이라 원문이다.
                .reshipTo(type != ClaimType.EXCHANGE ? null : new UserClaimDto.ReshipTo(order.getRecipientName(),
                        order.getRecipientPhone(), order.getZipCode(), order.getAddress(), order.getDetailAddress(),
                        order.getDeliveryMemo()))
                .fees(new UserClaimDto.Fees(consumerFee(type, ClaimFeeBearer.CONSUMER, group), 0))
                .courierPayment(new UserClaimDto.CourierPayments(CourierPayment.PREPAID, CourierPayment.COLLECT))
                .refundMethodLabel(refundMethodLabel(entry.getOrder()))
                .invoiceDueDays(config.getInvoiceDueDays())
                .detailMaxLength(config.getDetailMaxLength())
                .photoMax(config.getPhotoMax())
                .build();
    }

    /**
     * 그 하위주문의 항목별 신청 가능 수량 — 주문 수량 − 환불된 수량 − 진행 중 수량 − 거절된 수량(앱 클레임 설계서 1-3).
     * 하위주문이 배송완료가 아니면 전부 0 이다. 신청 API 가 잠금 아래에서 같은 식으로 다시 본다.
     */
    Map<Long, Integer> claimableQuantities(OrderDeliveryGroup group) {
        if (group.getFulfillmentStatus() != FulfillmentStatus.DELIVERED) {
            return Map.of();
        }
        Map<Long, Long> occupied = new HashMap<>();
        for (Object[] row : claimRepository.sumOccupiedQuantityByDeliveryGroup(group.getId())) {
            occupied.put((Long) row[0], ((Number) row[1]).longValue());
        }
        Map<Long, Integer> claimable = new HashMap<>();
        for (OrderProduct product : orderProductRepository.findByDeliveryGroupIds(List.of(group.getId()))) {
            if (product.getStatus() != OrderProductStatus.PAID) {
                continue; // 취소·전량 반품된 항목
            }
            claimable.put(product.getId(), (int) (product.getQuantity() - product.getReturnedQuantity()
                    - occupied.getOrDefault(product.getId(), 0L)));
        }
        return claimable;
    }

    /**
     * 신청 전 확인 — 내 하위주문인지 · 지금 계산한 배송비 · 항목별 신청 가능 수량. 잠금 없이 읽는 값이라 도메인이 잠금
     * 아래에서 다시 본다(여기는 앱에 정확한 오류를 돌려주기 위한 앞단이다).
     *
     * @param claimable 하위주문이 배송완료가 아니면 비어 있다
     */
    public record CreatePlan(int fee, Map<Long, Integer> claimable) {
    }

    public CreatePlan planCreate(Long userId, Long deliveryGroupId, ClaimType type, ClaimFeeBearer feeBearer) {
        OrderDeliveryGroup group = deliveryGroupRepository.findById(deliveryGroupId)
                .filter(found -> found.getOrder().isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        return new CreatePlan(consumerFee(type, feeBearer, group), claimableQuantities(group));
    }

    /** 내 배송지의 값을 재발송 수취지로 — 남의 배송지는 있는지도 알리지 않는다. */
    public OrderClaimService.ReshipAddress reshipAddressOf(Long userId, Long addressId) {
        DeliveryAddress address = deliveryAddressRepository.findById(addressId)
                .filter(found -> found.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
        return new OrderClaimService.ReshipAddress(address.getRecipientName(), address.getPhoneNumber(),
                address.getZipCode(), address.getAddress(), address.getDetailAddress(), address.getMemo());
    }

    /** 앱이 다루는 배송비 — 반품은 환불액에서 빼는 최초 배송비, 교환은 요청할 때 결제하는 재발송 배송비. */
    private int consumerFee(ClaimType type, ClaimFeeBearer feeBearer, OrderDeliveryGroup group) {
        return type == ClaimType.EXCHANGE ? feePolicy.exchangeReshipFee(feeBearer, group)
                : feePolicy.returnDeduction(type, feeBearer, group);
    }

    /** 사유의 괄호 설명 — 시안 문구. 브랜드 화면에는 옮기지 않는다(§35-3). */
    private static String hintOf(ClaimReason reason, ClaimType type) {
        return switch (reason) {
            case CHANGE_OF_MIND -> type == ClaimType.EXCHANGE ? "다른 옵션이 더 마음에 들어요" : "상품이 필요 없어짐";
            case ORDER_MISTAKE -> type == ClaimType.EXCHANGE ? "옵션 잘못 선택" : "옵션 · 수량 잘못 선택";
            case WRONG_OR_LATE_DELIVERY -> "다른 상품이 왔어요";
            default -> null;
        };
    }

    private String refundMethodLabel(Order order) {
        return order.getPaidPaymentId() == null ? null
                : paymentRepository.findById(order.getPaidPaymentId())
                .map(payment -> payment.methodLabel() + " 결제 취소").orElse(null);
    }

    // ------------------------------------------------------------------ 상세(4-1)

    public UserClaimDto.DetailResponse getDetail(Long userId, Long claimId) {
        OrderClaim focused = claimRepository.findOwnedByUser(claimId, userId)
                // 결제 대기는 아직 접수 전이다 — 상세가 없다.
                .filter(claim -> claim.getStatus() != ClaimStatus.PAYMENT_PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        OrderClaimCollection collection = focused.getCollection();
        Order order = focused.getDeliveryGroup().getOrder();
        LocalDate today = LocalDate.now();

        // 철회·취소로 사라진 항목은 빼되, 진입한 항목은 취소됐어도 싣는다(알림 딥링크로 열린다).
        List<OrderClaim> shown = claimRepository.findByCollectionId(collection.getId()).stream()
                .filter(claim -> claim.getStatus() != ClaimStatus.PAYMENT_PENDING)
                .filter(claim -> claim.getResult() != ClaimResult.CANCELLED || claim.getId().equals(claimId))
                .toList();
        List<OrderClaimCharge> charges = chargeRepository.findByCollectionId(collection.getId());
        OrderClaimCharge rejectCharge = charges.stream()
                .filter(charge -> charge.getType() == ClaimChargeType.REJECT_RESHIP)
                .reduce((first, second) -> second).orElse(null);
        Map<Long, List<String>> evidences = attachmentRepository
                .findByClaimIds(shown.stream().map(OrderClaim::getId).toList()).stream()
                .filter(attachment -> attachment.getOwner() == ClaimAttachmentOwner.SELLER)
                .collect(Collectors.groupingBy(attachment -> attachment.getClaim().getId(),
                        Collectors.mapping(OrderClaimAttachment::getImageUrl, Collectors.toList())));

        boolean awaitingArrival = shown.stream().anyMatch(claim -> claim.getStatus() == ClaimStatus.REQUESTED
                || claim.getStatus() == ClaimStatus.COLLECTING);
        boolean invoiceMissing = shown.stream().anyMatch(claim -> claim.getStatus() == ClaimStatus.REQUESTED);
        return UserClaimDto.DetailResponse.builder()
                .requestId(collection.getId())
                .type(collection.getType())
                .requestedAt(focused.getRequestedAt())
                .completedAt(focused.getCompletedAt())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .guide(new UserClaimDto.Guide(awaitingArrival,
                        collection.getFeeBearer() == ClaimFeeBearer.SELLER ? CourierPayment.COLLECT
                                : CourierPayment.PREPAID))
                .items(shown.stream()
                        .map(claim -> item(claim, claimId, rejectCharge, evidences, today)).toList())
                .info(UserClaimDto.Info.builder()
                        .reasonLabel(collection.getReasonCode().getLabel())
                        .methodLabel(METHOD_LABEL)
                        .collectionInvoice(!collection.hasInvoice() ? null : new UserClaimDto.Invoice(
                                collection.getCarrier(), collection.getCarrier().getLabel(),
                                collection.getTrackingNumber()))
                        .invoiceDueDate(invoiceMissing ? collection.getInvoiceDueAt().toLocalDate() : null)
                        .reshipInvoice(focused.getReshipTrackingNumber() == null ? null : new UserClaimDto.Invoice(
                                focused.getReshipCarrier(), focused.getReshipCarrier().getLabel(),
                                focused.getReshipTrackingNumber()))
                        // 기록 화면이라 가린다 — 스크린샷으로 돌아다닌다. 브랜드 반품센터 주소는 송장에 적는 값이라 원문.
                        .pickupFrom(collection.getType() == ClaimType.RETURN ? maskedShipTo(collection) : null)
                        .returnTo(new UserClaimDto.ReturnTo(collection.getReturnRecipient(),
                                collection.getReturnContact(), collection.getReturnAddress(),
                                collection.getReturnDetailAddress()))
                        .reshipTo(collection.getType() == ClaimType.EXCHANGE ? maskedShipTo(collection) : null)
                        // 검수 판정 전까지만 — 브랜드가 재발송 목록을 내려받을 수 있는 시점부터 잠근다.
                        .reshipAddressChangeable(collection.getType() == ClaimType.EXCHANGE
                                && shown.stream().anyMatch(OrderClaim::isOpen)
                                && shown.stream().filter(OrderClaim::isOpen)
                                .allMatch(claim -> AWAITING_JUDGEMENT.contains(claim.getStatus())))
                        .build())
                .refund(collection.getType() == ClaimType.RETURN ? refund(collection, shown, rejectCharge, order)
                        : null)
                .exchangePayment(collection.getType() == ClaimType.EXCHANGE ? exchangePayment(charges) : null)
                .reshipFee(reshipFee(collection, shown, rejectCharge, today))
                .build();
    }

    private UserClaimDto.DetailItem item(OrderClaim claim, Long focusedId, OrderClaimCharge rejectCharge,
                                         Map<Long, List<String>> evidences, LocalDate today) {
        OrderProduct product = claim.getOrderProduct();
        UserClaimPresenter.View view = UserClaimPresenter.present(claim, rejectCharge, today);
        return UserClaimDto.DetailItem.builder()
                .claimId(claim.getId())
                .focused(claim.getId().equals(focusedId))
                .brandName(claim.getDeliveryGroup().getMarketName())
                .productName(product.getProductName())
                .optionName(product.getOptionName())
                .exchangeOptionName(claim.getExchangeOptionName())
                .quantity(claim.getQuantity())
                .amount((long) product.getPrice() * claim.getQuantity())
                .thumbnailUrl(product.getImageUrl())
                .phase(view.phase())
                .statusLabel(view.label())
                .statusSub(view.sub())
                .statusSubTone(view.actionRequired() ? UserOrderTone.ACTIVE : UserOrderTone.MUTED)
                .actions(actions(view.phase()))
                .rejection(claim.getRejectedAt() == null ? null : UserClaimDto.Rejection.builder()
                        .reasonLabel(claim.getRejectReasonCode() == null ? null
                                : claim.getRejectReasonCode().getLabel())
                        .legalNote(LEGAL_NOTE)
                        .sellerMessage(claim.getRejectDetail())
                        .evidenceImageUrls(evidences.getOrDefault(claim.getId(), List.of()))
                        .rejectedAt(claim.getRejectedAt())
                        .build())
                .build();
    }

    /** 항목 아래 버튼(4-2 표) — 재발송비 결제는 항목 버튼이 아니라 하단 CTA 라 {@code reshipFee.state}로 그린다. */
    private static List<UserClaimDto.Action> actions(UserClaimPhase phase) {
        List<ActionType> target = switch (phase) {
            case REQUESTED -> List.of(ActionType.WITHDRAW, ActionType.REGISTER_COLLECTION_INVOICE);
            case COLLECTING, INSPECTING -> List.of(ActionType.TRACK_COLLECTION);
            case RESHIPPING, REJECTED_RESHIPPING -> List.of(ActionType.TRACK_RESHIP);
            case REJECTED_WAITING, REJECTED_PAY, REJECTED_PREPARING -> List.of(ActionType.INQUIRY);
            default -> List.<ActionType>of();
        };
        return target.stream().filter(ENABLED_ACTIONS::contains)
                .map(type -> new UserClaimDto.Action(type, labelOf(type))).toList();
    }

    private static String labelOf(ActionType type) {
        return switch (type) {
            case WITHDRAW -> "요청 철회";
            case REGISTER_COLLECTION_INVOICE -> "회수 송장 등록";
            case TRACK_COLLECTION -> "회수 조회";
            case TRACK_RESHIP -> "배송 조회";
            case INQUIRY -> "1:1 문의하기";
        };
    }

    private static UserOrderDto.MaskedAddress maskedShipTo(OrderClaimCollection collection) {
        return UserOrderDto.MaskedAddress.builder()
                .recipientName(OrderAddressMasker.maskName(collection.getReshipRecipient()))
                .phoneNumber(OrderAddressMasker.maskPhone(collection.getReshipPhone()))
                .address(collection.getReshipAddress())
                .detailAddress(OrderAddressMasker.maskDetail(collection.getReshipDetailAddress()))
                .memo(collection.getReshipMemo())
                .build();
    }

    /**
     * 환불 정보 — 요청 단위. 판정이 다 끝났으면 확정된 환불액을, 아니면 「반려·취소되지 않은 상품 금액 − 차감」을 예정액으로
     * 낸다. 전체 반려면 0 이다.
     */
    private UserClaimDto.Refund refund(OrderClaimCollection collection, List<OrderClaim> shown,
                                       OrderClaimCharge rejectCharge, Order order) {
        long rejected = 0;
        long approved = 0;
        boolean anyRejected = false;
        boolean anyPending = false;
        boolean anyRefunded = false;
        for (OrderClaim claim : shown) {
            long amount = (long) claim.getOrderProduct().getPrice() * claim.getQuantity();
            if (claim.getRejectedAt() != null) {
                rejected += amount;
                anyRejected = true;
            } else if (claim.getResult() != ClaimResult.CANCELLED) {
                approved += amount;
                anyPending |= claim.getStatus() != ClaimStatus.COMPLETED;
                anyRefunded |= claim.getResult() == ClaimResult.REFUNDED;
            }
        }
        int reshipDeduction = rejectCharge != null && rejectCharge.getStatus() == ClaimChargeStatus.DEDUCTED
                ? rejectCharge.getAmount() : 0;
        int returnDeduction = approved > 0 ? collection.getReturnDeduction() : 0;
        long amount = collection.getRefundAmount() != null ? collection.getRefundAmount()
                : Math.max(0, approved - returnDeduction - reshipDeduction);
        return UserClaimDto.Refund.builder()
                .rejectedAmount(anyRejected ? rejected : null)
                .returnDeduction(returnDeduction)
                .approvedAmount(approved)
                .reshipDeduction(reshipDeduction)
                .amount(amount)
                .confirmed(anyRefunded && !anyPending)
                .refundMethodLabel(refundMethodLabel(order))
                .build();
    }

    /** 교환 결제 정보 — 요청 때 낸 재발송 배송비. 브랜드 귀책(0원 요청)은 청구가 없다. */
    private UserClaimDto.ExchangePayment exchangePayment(List<OrderClaimCharge> charges) {
        OrderClaimCharge charge = charges.stream()
                .filter(found -> found.getType() == ClaimChargeType.EXCHANGE_RESHIP)
                .reduce((first, second) -> second).orElse(null);
        if (charge == null) {
            return new UserClaimDto.ExchangePayment(0, 0, "결제 없음");
        }
        boolean paid = charge.getStatus() == ClaimChargeStatus.PAID;
        String methodLabel = charge.getPaidPaymentId() == null ? "결제 없음"
                : claimPaymentRepository.findById(charge.getPaidPaymentId())
                .map(OrderClaimPayment::methodLabel).orElse("결제 없음");
        return new UserClaimDto.ExchangePayment(charge.getAmount(), paid ? charge.getAmount() : 0, methodLabel);
    }

    /** 「상품 다시 받기」(4-3) — 반려된 항목이 있을 때만. 결제 기한이 지난 뒤에는 미결제 고지·보관 기한을 함께 내린다. */
    private UserClaimDto.ReshipFee reshipFee(OrderClaimCollection collection, List<OrderClaim> shown,
                                             OrderClaimCharge rejectCharge, LocalDate today) {
        OrderClaim rejected = shown.stream().filter(claim -> claim.getRejectedAt() != null).findFirst().orElse(null);
        if (rejected == null) {
            return null;
        }
        if (collection.getFinalizedAt() == null || rejectCharge == null) {
            return UserClaimDto.ReshipFee.builder().state(ReshipFeeState.WAITING)
                    .amount(rejectCharge == null ? null : rejectCharge.getAmount()).build();
        }
        ReshipFeeState state = switch (rejectCharge.getStatus()) {
            case PAID -> ReshipFeeState.PAID;
            case DEDUCTED -> ReshipFeeState.DEDUCTED;
            case COVERED -> ReshipFeeState.COVERED;
            default -> ReshipFeeState.PAYABLE;
        };
        LocalDate dueDate = rejectCharge.getDueAt() == null ? null : rejectCharge.getDueAt().toLocalDate();
        boolean overdue = state == ReshipFeeState.PAYABLE && dueDate != null && dueDate.isBefore(today);
        LocalDateTime now = LocalDateTime.now();
        return UserClaimDto.ReshipFee.builder()
                .state(state)
                .amount(rejectCharge.getAmount())
                .dueDate(state == ReshipFeeState.PAYABLE ? dueDate : null)
                .settledAt(rejectCharge.getSettledAt())
                .methodLabel(state != ReshipFeeState.PAID || rejectCharge.getPaidPaymentId() == null ? null
                        : claimPaymentRepository.findById(rejectCharge.getPaidPaymentId())
                        .map(OrderClaimPayment::methodLabel).orElse(null))
                .storage(!overdue ? null : new UserClaimDto.Storage(rejected.getNoticeCount(),
                        storagePolicy.storageDueAt(rejected.getNoticeCount(), rejected.getLastNoticeAt()),
                        storagePolicy.phase(rejected.getNoticeCount(), rejected.getLastNoticeAt(), now)))
                .build();
    }
}
