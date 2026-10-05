package showroomz.api.app.claim.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.claim.dto.UserClaimDto;
import showroomz.api.app.claim.dto.UserClaimDto.TrackingEventSource;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.api.app.order.dto.UserOrderDto.TrackingContext;
import showroomz.api.app.order.dto.UserOrderDto.TrackingState;
import showroomz.api.app.order.service.OrderAddressMasker;
import showroomz.domain.order.entity.DeliveryTrackingEvent;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.entity.OrderClaimHistory;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.DeliveryTrackingEventRepository;
import showroomz.domain.order.repository.OrderClaimHistoryRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.service.DeliveryArrivalEstimator;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 클레임의 송장 조회(앱 클레임 설계서 5-2 · 5-3) — 회수 조회(C10-4)와 재발송 배송 조회(C10-2).
 * <b>저장해 둔 스캔 이력만 읽는다</b> — 화면이 택배 API 를 부르지 않는다. 경로는 소유자(클레임)로 연다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserClaimTrackingService {

    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    /** 연동 업체의 진행 단계 — 이 값까지는 「접수」(집화)이고 넘어가면 「이동 중」이다. */
    private static final int LEVEL_PICKED_UP = 2;

    private final OrderClaimRepository claimRepository;
    private final OrderClaimHistoryRepository historyRepository;
    private final DeliveryTrackingEventRepository trackingEventRepository;
    private final DeliveryArrivalEstimator arrivalEstimator;

    // ------------------------------------------------------------------ 회수 조회(5-3)

    public UserClaimDto.CollectionTrackingResponse trackCollection(Long userId, Long claimId) {
        OrderClaim claim = requireOwned(userId, claimId);
        OrderClaimCollection collection = claim.getCollection();
        OrderProduct product = claim.getOrderProduct();
        Order order = claim.getDeliveryGroup().getOrder();
        boolean exchange = claim.getType() == ClaimType.EXCHANGE;
        DeliveryCarrier carrier = collection.getCarrier();
        String trackingNumber = collection.getTrackingNumber();
        boolean hasInvoice = collection.hasInvoice();

        List<DeliveryTrackingEvent> scans = hasInvoice
                ? trackingEventRepository.findByCarrierAndTrackingNumberOrderBySeqDesc(carrier, trackingNumber)
                : List.of();
        Stage stage = collectionStage(claim, collection, scans, hasInvoice);

        List<UserClaimDto.CollectionEvent> events = new ArrayList<>();
        for (DeliveryTrackingEvent scan : scans) {
            events.add(new UserClaimDto.CollectionEvent(TrackingEventSource.COURIER, scan.getLocation(),
                    scan.getDescription(), scan.getOccurredAt()));
        }
        for (OrderClaimHistory history : historyRepository.findByClaimId(claimId)) {
            String description = switch (history.getEventType()) {
                case RECEIVED -> "입고 · 검수 시작";
                case INSPECTION_PASSED -> exchange ? "검수 완료 · 새 상품 발송 준비" : "검수 완료 · 환불 진행";
                case INSPECTION_REJECTED -> "검수 완료 · 반려";
                default -> null;
            };
            if (description != null) {
                events.add(new UserClaimDto.CollectionEvent(TrackingEventSource.BRAND, null, description,
                        history.getOccurredAt()));
            }
        }
        events.sort(Comparator.comparing(UserClaimDto.CollectionEvent::getOccurredAt).reversed());

        return UserClaimDto.CollectionTrackingResponse.builder()
                .type(claim.getType())
                .trackable(stage.index() >= 0)
                .stageIndex(stage.index())
                .stages(List.of("접수", "이동 중", "도착", "검수", exchange ? "새 상품" : "환불"))
                .headline(stage.headline())
                .requestedAt(claim.getRequestedAt())
                .claimId(claimId)
                .item(new UserClaimDto.CollectionItem(claim.getDeliveryGroup().getMarketName(),
                        product.getProductName(),
                        exchange && claim.getExchangeOptionName() != null
                                ? product.getOptionName() + " → " + claim.getExchangeOptionName()
                                : product.getOptionName(),
                        product.getImageUrl()))
                .carrier(hasInvoice ? carrierOf(carrier, trackingNumber) : null)
                .trackingNumber(hasInvoice ? trackingNumber : null)
                .invoiceRegisteredAt(collection.getInvoiceRegisteredAt())
                .sender(senderOf(order))
                .receiver(claim.getDeliveryGroup().getMarketName() + " 반품센터")
                .events(events)
                // 수정 조건(3-4) — 회수 중이고 아직 택배 이력이 없으며 등록 기한 전.
                .invoiceEditable(claim.getStatus() == ClaimStatus.COLLECTING && hasInvoice && scans.isEmpty()
                        && !LocalDateTime.now().isAfter(collection.getInvoiceDueAt()))
                .build();
    }

    private record Stage(int index, UserOrderDto.TrackingHeadline headline) {
    }

    /**
     * 5칸 바의 현재 칸 — 접수(0) · 이동 중(1) · 도착(2) · 검수(3) · 환불/새 상품(4). 검수 거절은 3에서 멈춘다 —
     * 다섯 번째 칸을 채우면 거짓이 된다.
     */
    private Stage collectionStage(OrderClaim claim, OrderClaimCollection collection,
                                  List<DeliveryTrackingEvent> scans, boolean hasInvoice) {
        boolean exchange = claim.getType() == ClaimType.EXCHANGE;
        if (claim.getRejectedAt() != null) {
            return new Stage(3, headline(claim.getRejectedAt().toLocalDate(), "검수에서 반려되었어요",
                    (exchange ? "교환" : "반품") + " 상세에서 사유를 확인해 주세요"));
        }
        ClaimStatus status = claim.getStatus();
        boolean passed = status == ClaimStatus.REFUND_PENDING || status == ClaimStatus.RESHIP_READY
                || status == ClaimStatus.RESHIPPING || claim.getResult() == ClaimResult.REFUNDED
                || claim.getResult() == ClaimResult.EXCHANGED;
        if (passed) {
            LocalDate date = claim.getInspectedAt() == null ? null : claim.getInspectedAt().toLocalDate();
            String text = !exchange ? "검수가 끝나 환불이 진행돼요"
                    : status == ClaimStatus.RESHIP_READY ? "검수가 끝나 새 상품을 준비하고 있어요"
                    : "검수가 끝나 새 상품이 출발했어요";
            return new Stage(4, headline(date, text, null));
        }
        if (status == ClaimStatus.RECEIVED) {
            return new Stage(3, headline(claim.getReceivedAt() == null ? null : claim.getReceivedAt().toLocalDate(),
                    "도착해서 검수 중이에요", null));
        }
        if (status == ClaimStatus.ARRIVED) {
            return new Stage(2, headline(collection.getArrivedAt() == null ? null
                    : collection.getArrivedAt().toLocalDate(), "브랜드에 도착했어요", null));
        }
        if (!hasInvoice) {
            return new Stage(-1, headline(null, "회수 송장을 등록해 주세요", null));
        }
        if (scans.isEmpty()) {
            return new Stage(-1, headline(null, "아직 조회되지 않아요", null));
        }
        String lastScan = lastScan(scans.get(0));
        int level = scans.stream().mapToInt(scan -> scan.getLevel() == null ? 0 : scan.getLevel()).max().orElse(0);
        if (level <= LEVEL_PICKED_UP) {
            return new Stage(0, headline(null, "접수되었어요", lastScan));
        }
        LocalDate due = arrivalDueDate(collection.getCarrier(), scans);
        return new Stage(1, headline(due, due != null ? "도착 예정이에요" : "브랜드로 이동 중이에요", lastScan));
    }

    /** 「누가 보냈는지」의 표기 — 이름(마스킹)과 시·구까지만. 실제 발송지가 아니다. */
    private static String senderOf(Order order) {
        String name = OrderAddressMasker.maskName(order.getRecipientName());
        String address = order.getAddress();
        if (address == null || address.isBlank()) {
            return name;
        }
        String[] parts = address.trim().split("\\s+");
        return name + " · " + (parts.length >= 2 ? parts[0] + " " + parts[1] : parts[0]);
    }

    // ------------------------------------------------------------------ 재발송 배송 조회(5-2)

    /**
     * 재발송 송장 — 교환 새 상품 또는 반려 상품의 반송. 응답은 주문 배송 조회와 같은 모양이다.
     * 재발송 단계에 온 적이 없는 클레임(검수 전 · 환불로 끝난 반품 · 폐기)은 조회할 것이 없다.
     */
    public UserOrderDto.TrackingResponse trackReship(Long userId, Long claimId) {
        OrderClaim claim = requireOwned(userId, claimId);
        boolean hasInvoice = claim.getReshipCarrier() != null && claim.getReshipTrackingNumber() != null;
        if (!hasInvoice && claim.getStatus() != ClaimStatus.RESHIP_READY) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        boolean rejected = claim.getRejectedAt() != null;
        OrderProduct product = claim.getOrderProduct();
        Order order = claim.getDeliveryGroup().getOrder();
        DeliveryCarrier carrier = claim.getReshipCarrier();
        String trackingNumber = claim.getReshipTrackingNumber();

        List<DeliveryTrackingEvent> scans = hasInvoice
                ? trackingEventRepository.findByCarrierAndTrackingNumberOrderBySeqDesc(carrier, trackingNumber)
                : List.of();
        // 운영자 직권 완료는 도착 시각 없이 닫힌다 — 종결 시각을 도착으로 본다.
        LocalDateTime deliveredAt = claim.getReshipDeliveredAt() != null ? claim.getReshipDeliveredAt()
                : claim.getStatus() == ClaimStatus.COMPLETED ? claim.getCompletedAt() : null;
        TrackingState state = deliveredAt != null ? TrackingState.DELIVERED
                : hasInvoice ? TrackingState.IN_TRANSIT : TrackingState.NOT_SHIPPED;

        UserOrderDto.TrackingHeadline headline = switch (state) {
            case NOT_SHIPPED -> headline(null, "배송 준비 중이에요", null);
            case IN_TRANSIT -> {
                LocalDate due = arrivalDueDate(carrier, scans);
                yield headline(due, due != null ? "도착 예정이에요" : "상품이 배송중이에요",
                        scans.isEmpty() ? null : lastScan(scans.get(0)));
            }
            case DELIVERED -> headline(deliveredAt.toLocalDate(), "상품 배송이 완료되었어요", null);
        };
        return UserOrderDto.TrackingResponse.builder()
                .state(state)
                .context(rejected ? TrackingContext.REJECT_RESHIP : TrackingContext.EXCHANGE_RESHIP)
                .contextLabel(rejected ? "반려 상품 재발송" : "교환 상품 발송")
                .contextNote(rejected ? "검수에서 반려되어 다시 보내드린 상품이에요. 반려된 상품은 반품 · 교환할 수 없어요."
                        : "교환한 새 상품이에요. 배송이 완료되면 교환이 끝나요.")
                .headline(headline)
                .stageIndex(state == TrackingState.DELIVERED ? 2
                        : state == TrackingState.NOT_SHIPPED ? -1 : scans.isEmpty() ? 0 : 1)
                .orderedAt(order.getCreatedAt())
                .orderId(order.getId())
                // 실제로 가는 물건 — 교환이면 새 옵션, 반려면 원래 옵션.
                .item(UserOrderDto.TrackingItem.builder()
                        .brandName(claim.getDeliveryGroup().getMarketName())
                        .productName(product.getProductName())
                        .optionName(rejected || claim.getExchangeOptionName() == null ? product.getOptionName()
                                : claim.getExchangeOptionName())
                        .quantity(claim.getQuantity())
                        .amount((long) product.getPrice() * claim.getQuantity())
                        .thumbnailUrl(product.getImageUrl())
                        .build())
                .carrier(hasInvoice ? carrierOf(carrier, trackingNumber) : null)
                .trackingNumber(hasInvoice ? trackingNumber : null)
                .scans(scans.stream()
                        .map(scan -> UserOrderDto.TrackingScan.builder()
                                .location(scan.getLocation())
                                .description(scan.getDescription())
                                .occurredAt(scan.getOccurredAt())
                                .build())
                        .toList())
                .build();
    }

    // ------------------------------------------------------------------ 공통

    /** 결제 대기는 아직 접수 전이다 — 상세와 같이 없는 것으로 답한다. */
    private OrderClaim requireOwned(Long userId, Long claimId) {
        return claimRepository.findOwnedByUser(claimId, userId)
                .filter(claim -> claim.getStatus() != ClaimStatus.PAYMENT_PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
    }

    /** 집화 시각은 저장된 첫 스캔에서 읽는다 — 클레임 송장은 집화 시각을 따로 들지 않는다. 지난 날짜는 내리지 않는다. */
    private LocalDate arrivalDueDate(DeliveryCarrier carrier, List<DeliveryTrackingEvent> scans) {
        if (scans.isEmpty()) {
            return null;
        }
        LocalDate due = arrivalEstimator.estimate(carrier, scans.get(scans.size() - 1).getOccurredAt());
        return due == null || due.isBefore(LocalDate.now()) ? null : due;
    }

    private static UserOrderDto.TrackingHeadline headline(LocalDate date, String text, String sub) {
        return UserOrderDto.TrackingHeadline.builder().date(date).text(text).sub(sub).build();
    }

    private static UserOrderDto.TrackingCarrier carrierOf(DeliveryCarrier carrier, String trackingNumber) {
        return UserOrderDto.TrackingCarrier.builder()
                .code(carrier.name())
                .label(carrier.getLabel())
                .tel(carrier.getTel())
                .trackingUrl(carrier.trackingUrl(trackingNumber))
                .build();
    }

    private static String lastScan(DeliveryTrackingEvent event) {
        String at = event.getOccurredAt().format(DAY_TIME);
        return event.getLocation() == null || event.getLocation().isBlank() ? at : event.getLocation() + " · " + at;
    }
}
