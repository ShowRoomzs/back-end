package showroomz.api.app.order.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.DeliveryArrivalEstimator;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.UserOrderAction;
import showroomz.domain.order.type.UserOrderItemStatus;
import showroomz.domain.order.type.UserOrderTone;
import showroomz.global.config.properties.OrderProperties;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 소비자 앱 주문 항목 행 조립(C10 설계서 1절) — 표시 상태 유도 · 보조 문구 · 반려 줄 · 액션. <b>목록과 상세가 같은 메서드를 탄다.</b>
 * 입력은 호출자가 미리 읽어 둔 {@link Context}다 — 여기서 쿼리하지 않는다. 항목의 배송 그룹은 fetch 돼 있어야 한다.
 *
 * <p>반품·교환 클레임(35 설계서)이 원천인 표시(1-1 #2~#4 · {@code claim} · {@code claimRejection} · 구매확정 보류)는
 * 클레임 테이블이 생긴 뒤에 붙는다(설계서 P5) — 그때 {@link Context}에 항목별 클레임 · 보류 그룹이 더해진다.
 */
@Component
public class UserOrderItemAssembler {

    /** 지금 서버에 API 가 있는 액션만(0-5) — 후속 설계(취소 요청 · 배송 조회 · 반품/교환)가 각자 자기 액션을 켠다. */
    static final Set<UserOrderAction> ENABLED_ACTIONS = EnumSet.of(UserOrderAction.CANCEL);

    /** 취소 반려 줄의 노출 구간(1-4) — 구매확정에서 사라진다(반품 창이 닫힌다). */
    private static final Set<UserOrderItemStatus> REJECTION_VISIBLE = EnumSet.of(UserOrderItemStatus.PAID,
            UserOrderItemStatus.PREPARING, UserOrderItemStatus.SHIPPING, UserOrderItemStatus.DELIVERED);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM.dd");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final OrderProperties orderProperties;
    private final DeliveryArrivalEstimator arrivalEstimator;
    private final Set<UserOrderAction> enabledActions;

    @Autowired
    public UserOrderItemAssembler(OrderProperties orderProperties, DeliveryArrivalEstimator arrivalEstimator) {
        this(orderProperties, arrivalEstimator, ENABLED_ACTIONS);
    }

    /** 노출 규칙표(1-6) 자체를 검증하는 테스트용 — 운영은 {@link #ENABLED_ACTIONS}다. */
    UserOrderItemAssembler(OrderProperties orderProperties, DeliveryArrivalEstimator arrivalEstimator,
                           Set<UserOrderAction> enabledActions) {
        this.orderProperties = orderProperties;
        this.arrivalEstimator = arrivalEstimator;
        this.enabledActions = enabledActions;
    }

    /** 목록과 상세는 DELIVERED 의 보조 문구와 버튼 구성만 다르다(1-2 · 1-6). */
    public enum View {
        LIST, DETAIL
    }

    /**
     * 조립에 필요한 사전 조회 결과.
     *
     * @param pendingRequestIdByProduct 항목 id → 검토 중(PENDING) 취소 요청 id
     * @param latestRejectedByProduct   항목 id → 가장 최근 반려(REJECTED) 요청
     * @param refundPendingGroupIds     환불 큐에 PENDING 이 남은 배송 그룹
     * @param cancellableOrderIds       지금 전액 취소가 되는 주문 — {@code OrderAssembler.isCancellable}
     * @param today                     조회일 — 도착 예정일이 지났는지의 기준
     */
    public record Context(Map<Long, Long> pendingRequestIdByProduct,
                          Map<Long, OrderCancelRequest> latestRejectedByProduct,
                          Set<Long> refundPendingGroupIds,
                          Set<Long> cancellableOrderIds,
                          LocalDate today) {

        public static Context of(Collection<OrderCancelRequest> requests, Set<Long> refundPendingGroupIds,
                                 Set<Long> cancellableOrderIds) {
            return of(requests, refundPendingGroupIds, cancellableOrderIds, LocalDate.now());
        }

        /** {@code requests}는 PENDING · REJECTED 요청(항목 포함) — 다른 상태는 무시한다. */
        public static Context of(Collection<OrderCancelRequest> requests, Set<Long> refundPendingGroupIds,
                                 Set<Long> cancellableOrderIds, LocalDate today) {
            Map<Long, Long> pending = new HashMap<>();
            Map<Long, OrderCancelRequest> rejected = new HashMap<>();
            for (OrderCancelRequest request : requests) {
                for (OrderCancelRequestItem item : request.getItems()) {
                    Long productId = item.getOrderProduct().getId();
                    if (request.getStatus() == CancelRequestStatus.PENDING) {
                        pending.put(productId, request.getId());
                    } else if (request.getStatus() == CancelRequestStatus.REJECTED) {
                        rejected.merge(productId, request, (a, b) -> a.getId() > b.getId() ? a : b);
                    }
                }
            }
            return new Context(pending, rejected, refundPendingGroupIds, cancellableOrderIds, today);
        }
    }

    public UserOrderDto.ItemRow toRow(OrderProduct product, Context context, View view) {
        OrderDeliveryGroup group = product.getDeliveryGroup();
        Long pendingRequestId = context.pendingRequestIdByProduct().get(product.getId());
        UserOrderItemStatus status = deriveStatus(product, group, pendingRequestId != null);
        LocalDateTime confirmDueAt = status == UserOrderItemStatus.DELIVERED ? confirmDueAt(group) : null;
        LocalDate arrivalDueDate = status == UserOrderItemStatus.SHIPPING ? arrivalDueDate(group, context.today()) : null;
        long amount = (long) product.getPrice() * product.getQuantity();

        return UserOrderDto.ItemRow.builder()
                .orderProductId(product.getId())
                .productId(product.getVariant() != null && product.getVariant().getProduct() != null
                        ? product.getVariant().getProduct().getProductId() : null)
                .variantId(product.getVariantId())
                .brandName(group != null ? group.getMarketName() : null)
                .productName(product.getProductName())
                .optionName(product.getOptionName())
                .quantity(product.getQuantity())
                .returnedQuantity(0)
                .thumbnailUrl(product.getImageUrl())
                .status(status)
                .statusLabel(status.getLabel())
                .statusTone(status.getTone())
                .statusSub(statusSub(status, product, group, context, view, confirmDueAt, arrivalDueDate))
                .dimmed(status.isDimmed())
                .amount(amount)
                .amountLabel(amountLabel(status, product, amount))
                .cancelRejection(cancelRejection(status, product, context))
                .cancelRequestId(status == UserOrderItemStatus.CANCEL_REQUESTED ? pendingRequestId : null)
                .dates(UserOrderDto.Dates.builder()
                        .shipDueAt(group != null ? group.getShipDueAt() : null)
                        .shippedAt(group != null ? group.getShippedAt() : null)
                        .arrivalDueDate(arrivalDueDate)
                        .deliveredAt(group != null ? group.getDeliveredAt() : null)
                        .confirmDueAt(confirmDueAt)
                        .confirmedAt(group != null ? group.getConfirmedAt() : null)
                        .cancelledAt(product.getCancelledAt())
                        .build())
                .actions(actions(status, product, group, context, view))
                .build();
    }

    /** 상세 상단 안내(3-3) — 순서는 기한이 있는 쪽 먼저. */
    public List<UserOrderDto.Notice> notices(List<UserOrderDto.ItemRow> rows) {
        List<UserOrderDto.Notice> notices = new ArrayList<>();
        rows.stream()
                .filter(row -> row.getStatus() == UserOrderItemStatus.DELIVERED)
                .map(row -> row.getDates().getConfirmDueAt())
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .ifPresent(date -> notices.add(UserOrderDto.Notice.builder()
                        .type(UserOrderDto.NoticeType.CONFIRM_DUE).tone(UserOrderTone.ACTIVE).date(date).build()));
        // 취소 요청 API 가 없는 동안 「요청으로 접수돼요」라고 안내하면 거짓이다(0-5).
        if (enabledActions.contains(UserOrderAction.CANCEL_REQUEST)
                && rows.stream().anyMatch(row -> row.getStatus() == UserOrderItemStatus.PREPARING)) {
            notices.add(UserOrderDto.Notice.builder()
                    .type(UserOrderDto.NoticeType.CANCEL_BY_REQUEST).tone(UserOrderTone.MUTED).build());
        }
        return notices;
    }

    // ------------------------------------------------------------------ 표시 상태(1-1)

    /** 위에서부터 먼저 맞는 것 — 순서가 곧 규칙이다. */
    private UserOrderItemStatus deriveStatus(OrderProduct product, OrderDeliveryGroup group, boolean pendingRequest) {
        if (product.getStatus() == OrderProductStatus.CANCELLED) {
            return UserOrderItemStatus.CANCELLED;
        }
        if (pendingRequest) {
            return UserOrderItemStatus.CANCEL_REQUESTED;
        }
        if (group == null) {
            // 그룹 없는 옛 행(백필 전)은 항목 상태만으로 판정한다.
            return switch (product.getStatus()) {
                case PURCHASE_CONFIRMED -> UserOrderItemStatus.CONFIRMED;
                case PAID -> UserOrderItemStatus.PAID;
                default -> UserOrderItemStatus.PAYMENT_PENDING;
            };
        }
        return switch (group.getFulfillmentStatus()) {
            case NEW -> UserOrderItemStatus.PAID;
            case PREPARING -> UserOrderItemStatus.PREPARING;
            case SHIPPING -> UserOrderItemStatus.SHIPPING;
            case RETURNING -> UserOrderItemStatus.RETURNING;
            case DELIVERED -> UserOrderItemStatus.DELIVERED;
            case CONFIRMED -> UserOrderItemStatus.CONFIRMED;
            default -> product.getStatus() == OrderProductStatus.PURCHASE_CONFIRMED
                    ? UserOrderItemStatus.CONFIRMED : UserOrderItemStatus.PAYMENT_PENDING;
        };
    }

    // ------------------------------------------------------------------ 보조 문구(1-2)

    private String statusSub(UserOrderItemStatus status, OrderProduct product, OrderDeliveryGroup group,
                             Context context, View view, LocalDateTime confirmDueAt, LocalDate arrivalDueDate) {
        return switch (status) {
            case PAID, PREPARING -> group == null ? null : day(group.getShipDueAt(), " 발송 예정");
            case SHIPPING -> arrivalDueDate == null ? null : arrivalDueDate.format(DAY) + " 도착 예정";
            case RETURNING -> "반송 처리 중";
            case DELIVERED -> view == View.LIST
                    ? day(confirmDueAt, " 구매확정 예정")
                    : group.getDeliveredAt() == null ? null : group.getDeliveredAt().format(DAY_TIME);
            case CONFIRMED -> group == null ? null : day(group.getConfirmedAt(), " 확정");
            case CANCEL_REQUESTED -> "브랜드 확인 중";
            case CANCELLED -> cancelledSub(product, group, context);
            default -> null;
        };
    }

    /**
     * 소비자 취소(PG 자동)는 항목이 CANCELLED 로 내려간 시점이 곧 PG 취소 확인 뒤라 항상 완료다. 브랜드 승인·직권 취소는
     * 운영자 환불 큐를 탄다. 취소 유형이 없는 항목(결제 전 취소·만료)은 환불할 것이 없어 문구도 없다.
     */
    private String cancelledSub(OrderProduct product, OrderDeliveryGroup group, Context context) {
        OrderCancelType cancelType = product.getCancelType();
        if (cancelType == null) {
            return null;
        }
        if (cancelType != OrderCancelType.CONSUMER && group != null
                && context.refundPendingGroupIds().contains(group.getId())) {
            return "환불 처리 중";
        }
        return "완료";
    }

    private static String day(LocalDateTime at, String suffix) {
        return at == null ? null : at.format(DAY) + suffix;
    }

    /**
     * 도착 예정 — 집화일 + N배송일. 집화 전에는 약속할 날짜가 없고, 예정일이 지났는데 아직 배송중이면 틀린 날짜를
     * 계속 보여 주지 않는다 — 둘 다 null(「배송중」만).
     */
    private LocalDate arrivalDueDate(OrderDeliveryGroup group, LocalDate today) {
        LocalDate due = arrivalEstimator.estimate(group.getCarrier(), group.getPickedUpAt());
        return due == null || due.isBefore(today) ? null : due;
    }

    /**
     * 구매확정 예정 — 배송완료 + N일. 셀러 화면과 같은 식이어야 한다. 반품·교환 모듈이 공용 계산(재발송 도착일 재기산 ·
     * 클레임 보류 — 35 설계서 3-6 · 5-1)을 내놓으면 그것을 부르도록 바꾼다.
     */
    private LocalDateTime confirmDueAt(OrderDeliveryGroup group) {
        return group.getDeliveredAt() == null ? null
                : group.getDeliveredAt().plusDays(orderProperties.getPurchaseConfirmDays());
    }

    // ------------------------------------------------------------------ 금액(1-3)

    /** 「환불은 배지가 아니라 결과」 — 결제된 뒤 취소된 항목만 환불이다. 배송비는 항목의 금액이 아니라 얹지 않는다. */
    private String amountLabel(UserOrderItemStatus status, OrderProduct product, long amount) {
        String won = NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
        return status == UserOrderItemStatus.CANCELLED && product.getCancelType() != null ? "환불 " + won : won;
    }

    // ------------------------------------------------------------------ 반려 줄(1-4)

    /** 그 뒤의 PENDING·APPROVED 요청은 상태를 CANCEL_REQUESTED·CANCELLED 로 옮기므로 노출 구간이 걸러 준다. */
    private UserOrderDto.CancelRejection cancelRejection(UserOrderItemStatus status, OrderProduct product,
                                                         Context context) {
        if (!REJECTION_VISIBLE.contains(status)) {
            return null;
        }
        OrderCancelRequest rejected = context.latestRejectedByProduct().get(product.getId());
        return rejected == null ? null : UserOrderDto.CancelRejection.builder()
                .cancelRequestId(rejected.getId())
                .rejectedAt(rejected.getDecidedAt())
                .build();
    }

    // ------------------------------------------------------------------ 액션(1-6)

    private List<UserOrderDto.Action> actions(UserOrderItemStatus status, OrderProduct product,
                                              OrderDeliveryGroup group, Context context, View view) {
        List<UserOrderAction> target = switch (status) {
            // CANCEL 의 게이트는 취소 API 와 같아야 한다 — 주문 전체 · 전 그룹 NEW. 준비 시작된 그룹이 섞인 주문의
            // NEW 항목은 「결제완료」로 보이되 버튼이 없다(앱이 그리고 서버가 409 를 내는 것보다 낫다).
            case PAID -> group != null && context.cancellableOrderIds().contains(product.getOrder().getId())
                    ? List.of(UserOrderAction.CANCEL) : List.<UserOrderAction>of();
            case PREPARING -> List.of(UserOrderAction.CANCEL_REQUEST);
            case SHIPPING, CONFIRMED -> group != null
                    ? List.of(UserOrderAction.TRACK_DELIVERY) : List.<UserOrderAction>of();
            case DELIVERED -> view == View.LIST
                    ? List.of(UserOrderAction.TRACK_DELIVERY, UserOrderAction.RETURN_EXCHANGE)
                    : List.of(UserOrderAction.TRACK_DELIVERY, UserOrderAction.RETURN_REQUEST,
                    UserOrderAction.EXCHANGE_REQUEST);
            // 결제 전 취소·만료(취소 유형 없음)는 취소 상세가 없다.
            case CANCELLED -> product.getCancelType() != null
                    ? List.of(UserOrderAction.CANCEL_DETAIL) : List.<UserOrderAction>of();
            case CANCEL_REQUESTED -> List.of(UserOrderAction.CANCEL_DETAIL);
            case RETURN_IN_PROGRESS, EXCHANGE_IN_PROGRESS, RETURNED -> List.of(UserOrderAction.CLAIM_DETAIL);
            case RETURNING, PAYMENT_PENDING -> List.<UserOrderAction>of();
        };
        return target.stream()
                .filter(enabledActions::contains)
                .map(action -> UserOrderDto.Action.builder()
                        .type(action).label(action.getLabel()).enabled(true).build())
                .toList();
    }
}
