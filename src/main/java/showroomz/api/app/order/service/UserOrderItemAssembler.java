package showroomz.api.app.order.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.domain.order.service.ShipDuePolicy;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.DeliveryArrivalEstimator;
import showroomz.domain.order.service.UserClaimPresenter;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
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
 * <p>반품·교환 클레임(35 설계서)이 원천인 표시(1-1 #2~#4 · {@code claim} · {@code claimRejection} · {@code todo} ·
 * 구매확정 보류)는 {@link Context#claims()}에서 온다. 단계 문구의 정본은 {@link UserClaimPresenter}다(앱 클레임 설계서 4-2).
 */
@Component
public class UserOrderItemAssembler {

    /** 지금 서버에 API 가 있는 액션만(0-5) — 취소 요청 · 취소 상세는 1009 기획 수정본 3-1 에서 켰다. */
    static final Set<UserOrderAction> ENABLED_ACTIONS = EnumSet.of(UserOrderAction.CANCEL,
            UserOrderAction.CANCEL_REQUEST, UserOrderAction.CANCEL_DETAIL,
            UserOrderAction.TRACK_DELIVERY, UserOrderAction.RETURN_EXCHANGE, UserOrderAction.RETURN_REQUEST,
            UserOrderAction.EXCHANGE_REQUEST, UserOrderAction.CLAIM_DETAIL);

    /** 요청이 걸린 하위주문의 나머지 항목 — 하위주문 전체가 발송 보류된다(결정 13 · C10-1 1b). */
    static final String SHIPMENT_HOLD_SUB = "취소 요청이 처리될 때까지 이 주문의 상품은 발송되지 않아요";

    /** 취소 반려 줄의 노출 구간(1-4) — 구매확정에서 사라진다(반품 창이 닫힌다). */
    private static final Set<UserOrderItemStatus> REJECTION_VISIBLE = EnumSet.of(UserOrderItemStatus.PAID,
            UserOrderItemStatus.PREPARING, UserOrderItemStatus.SHIPPING, UserOrderItemStatus.DELIVERED);

    private static final String EXCHANGE_SOLD_OUT_LABEL = "교환 불가 (재고 없음)";
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

    /** 그 액션을 지금 내리는가 — 호출자가 꺼진 액션을 위한 조회를 하지 않게 한다. */
    public boolean isEnabled(UserOrderAction action) {
        return enabledActions.contains(action);
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
     * @param today                     조회일 — 도착 예정일 · 할 일 기한이 지났는지의 기준
     * @param claims                    그 주문들의 반품·교환 클레임 — 없으면 {@link UserOrderClaimContext#EMPTY}
     * @param exchangeUnavailableProductIds 교환할 수 있는 옵션의 재고가 하나도 없는 항목 — 상세에서만 판정한다
     */
    public record Context(Map<Long, Long> pendingRequestIdByProduct,
                          Set<Long> pendingRequestGroupIds,
                          Map<Long, OrderCancelRequest> latestRejectedByProduct,
                          Set<Long> refundPendingGroupIds,
                          Set<Long> cancellableOrderIds,
                          LocalDate today,
                          UserOrderClaimContext claims,
                          Set<Long> exchangeUnavailableProductIds) {

        public Context withClaims(UserOrderClaimContext claims) {
            return new Context(pendingRequestIdByProduct, pendingRequestGroupIds, latestRejectedByProduct,
                    refundPendingGroupIds,
                    cancellableOrderIds, today, claims, exchangeUnavailableProductIds);
        }

        public Context withExchangeUnavailable(Set<Long> exchangeUnavailableProductIds) {
            return new Context(pendingRequestIdByProduct, pendingRequestGroupIds, latestRejectedByProduct,
                    refundPendingGroupIds,
                    cancellableOrderIds, today, claims, exchangeUnavailableProductIds);
        }

        public static Context of(Collection<OrderCancelRequest> requests, Set<Long> refundPendingGroupIds,
                                 Set<Long> cancellableOrderIds) {
            return of(requests, refundPendingGroupIds, cancellableOrderIds, LocalDate.now());
        }

        /** {@code requests}는 PENDING · REJECTED 요청(항목 포함) — 다른 상태는 무시한다. */
        public static Context of(Collection<OrderCancelRequest> requests, Set<Long> refundPendingGroupIds,
                                 Set<Long> cancellableOrderIds, LocalDate today) {
            Map<Long, Long> pending = new HashMap<>();
            Set<Long> pendingGroups = new java.util.HashSet<>();
            Map<Long, OrderCancelRequest> rejected = new HashMap<>();
            for (OrderCancelRequest request : requests) {
                for (OrderCancelRequestItem item : request.getItems()) {
                    Long productId = item.getOrderProduct().getId();
                    if (request.getStatus() == CancelRequestStatus.PENDING) {
                        pending.put(productId, request.getId());
                        if (item.getOrderProduct().getDeliveryGroup() != null) {
                            pendingGroups.add(item.getOrderProduct().getDeliveryGroup().getId());
                        }
                    } else if (request.getStatus() == CancelRequestStatus.REJECTED) {
                        rejected.merge(productId, request, (a, b) -> a.getId() > b.getId() ? a : b);
                    }
                }
            }
            return new Context(pending, pendingGroups, rejected, refundPendingGroupIds, cancellableOrderIds, today,
                    UserOrderClaimContext.EMPTY, Set.of());
        }
    }

    public UserOrderDto.ItemRow toRow(OrderProduct product, Context context, View view) {
        OrderDeliveryGroup group = product.getDeliveryGroup();
        Long pendingRequestId = context.pendingRequestIdByProduct().get(product.getId());
        UserOrderClaimContext claims = context.claims();
        OrderClaim openClaim = claims.displayedOpenClaim(product.getId());
        UserOrderItemStatus status = deriveStatus(product, group, pendingRequestId != null, openClaim);
        // 같은 하위주문의 다른 항목에 검토 중 취소 요청이 걸렸다 — 이 항목도 발송 보류다(버튼 없음 · 안내 한 줄).
        boolean shipmentHeld = pendingRequestId == null && group != null
                && context.pendingRequestGroupIds().contains(group.getId())
                && (status == UserOrderItemStatus.PAID || status == UserOrderItemStatus.PREPARING);
        UserClaimPresenter.View claimView = openClaim == null ? null
                : UserClaimPresenter.present(openClaim, claims.rejectChargeOf(openClaim), context.today());
        // RETURNED 는 검수 통과 순간이고 클레임은 환불 집행 전까지 진행 중이다 — 그때도 [반품 상세]의 대상이 있어야 한다.
        OrderClaim shownClaim = openClaim != null ? openClaim
                : status == UserOrderItemStatus.RETURNED ? latestReturnPassed(claims.claimsOf(product.getId())) : null;
        UserOrderDto.ClaimRejection claimRejection = claimRejection(status, claims.claimsOf(product.getId()));
        LocalDateTime confirmDueAt = status == UserOrderItemStatus.DELIVERED ? confirmDueAt(group, claims) : null;
        LocalDate arrivalDueDate = status == UserOrderItemStatus.SHIPPING ? arrivalDueDate(group, context.today()) : null;
        long amount = status == UserOrderItemStatus.RETURNED ? returnedAmount(product, claims.claimsOf(product.getId()))
                : (long) product.getPrice() * product.getQuantity();

        return UserOrderDto.ItemRow.builder()
                .orderProductId(product.getId())
                .productId(product.getVariant() != null && product.getVariant().getProduct() != null
                        ? product.getVariant().getProduct().getProductId() : null)
                .variantId(product.getVariantId())
                .brandName(group != null ? group.getMarketName() : null)
                .productName(product.getProductName())
                .optionName(product.getOptionName())
                .quantity(product.getQuantity())
                .returnedQuantity(product.getReturnedQuantity())
                .thumbnailUrl(product.getImageUrl())
                .status(status)
                .statusLabel(status.getLabel())
                .statusTone(status.getTone())
                .statusSub(claimView != null ? claimView.listSub() : shipmentHeld ? SHIPMENT_HOLD_SUB
                        : statusSub(status, product, group, context, view, confirmDueAt, arrivalDueDate))
                // 검수 반려 단계는 탈색하지 않는다 — 반려된 상품은 고객에게 돌아오는 물건이라 끝난 주문이 아니다.
                .dimmed(status.isDimmed() && !(claimView != null && claimView.phase().isRejectedStage()))
                .amount(amount)
                .amountLabel(amountLabel(status, product, amount))
                // 반려 줄이 겹치면 클레임 쪽 하나만 — 더 나중 사건이다.
                .cancelRejection(claimRejection != null ? null : cancelRejection(status, product, context))
                .cancelRequestId(status == UserOrderItemStatus.CANCEL_REQUESTED ? pendingRequestId : null)
                .claim(shownClaim == null ? null : UserOrderDto.Claim.builder()
                        .claimId(shownClaim.getId())
                        .type(shownClaim.getType().name())
                        .claimStatus(shownClaim.getStatus().name())
                        .quantity(shownClaim.getQuantity())
                        .exchangeOptionName(shownClaim.getExchangeOptionName())
                        .build())
                .claimRejection(claimRejection)
                .todo(claimView == null || claimView.todo() == null ? null : UserOrderDto.Todo.builder()
                        .type(UserOrderDto.TodoType.valueOf(claimView.todo().type().name()))
                        .label(claimView.todo().label())
                        .dueDate(claimView.todo().dueDate())
                        .claimId(openClaim.getId())
                        .build())
                .dates(UserOrderDto.Dates.builder()
                        .shipDueAt(group != null ? group.getShipDueAt() : null)
                        .shippedAt(group != null ? group.getShippedAt() : null)
                        .arrivalDueDate(arrivalDueDate)
                        .deliveredAt(group != null ? group.getDeliveredAt() : null)
                        .confirmDueAt(confirmDueAt)
                        .confirmedAt(group != null ? group.getConfirmedAt() : null)
                        .cancelledAt(product.getCancelledAt())
                        .build())
                .actions(shipmentHeld ? List.of() : actions(status, product, group, context, view, shownClaim))
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
    private UserOrderItemStatus deriveStatus(OrderProduct product, OrderDeliveryGroup group, boolean pendingRequest,
                                             OrderClaim openClaim) {
        if (product.getStatus() == OrderProductStatus.CANCELLED) {
            return UserOrderItemStatus.CANCELLED;
        }
        // 전량 반품이 진행 중 클레임보다 앞이다 — 검수 통과 순간 RETURNED 가 되고 클레임은 환불 집행 전이라 아직 열려 있다.
        if (product.getStatus() == OrderProductStatus.RETURNED) {
            return UserOrderItemStatus.RETURNED;
        }
        // 클레임은 배송완료 위의 오버레이다 — 진행 중이면 「배송완료」(와 거절 뒤의 「구매확정」) 대신 클레임을 말한다.
        if (openClaim != null) {
            return openClaim.getType() == ClaimType.EXCHANGE
                    ? UserOrderItemStatus.EXCHANGE_IN_PROGRESS : UserOrderItemStatus.RETURN_IN_PROGRESS;
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
            // 공구 진행 중이면 기한이 아직 없다 — 약정 문구(마감 후 N영업일)를 보여 준다(1009 기획 1-3).
            case PAID, PREPARING -> group == null ? null : group.getShipDueAt() == null
                    ? ShipDuePolicy.noticeText(group.getShipDueBusinessDays())
                    : day(group.getShipDueAt(), " 발송 예정");
            case SHIPPING -> arrivalDueDate == null ? null : arrivalDueDate.format(DAY) + " 도착 예정";
            case RETURNING -> "반송 처리 중";
            case DELIVERED -> view == View.LIST
                    ? day(confirmDueAt, " 구매확정 예정")
                    : group.getDeliveredAt() == null ? null : group.getDeliveredAt().format(DAY_TIME);
            case CONFIRMED -> group == null ? null : day(group.getConfirmedAt(), " 확정");
            case CANCEL_REQUESTED -> "브랜드 확인 중";
            case CANCELLED -> cancelledSub(product, group, context);
            case RETURNED -> context.claims().claimsOf(product.getId()).stream()
                    .anyMatch(claim -> claim.getStatus() == ClaimStatus.REFUND_PENDING) ? "환불 처리 중" : "완료";
            default -> null;
        };
    }

    /**
     * 소비자 취소(PG 자동)는 항목이 CANCELLED 로 내려간 시점이 곧 PG 취소 확인 뒤라 항상 완료다. 브랜드 승인·직권 취소는
     * 환불 큐(PG 자동 부분 취소)를 탄다. 취소 유형이 없는 항목(결제 전 취소·만료)은 환불할 것이 없어 문구도 없다.
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
    private LocalDateTime confirmDueAt(OrderDeliveryGroup group, UserOrderClaimContext claims) {
        // 보류 클레임이 구매확정을 그룹째 세운다 — 신청 밖 「배송완료」 항목도 날짜가 사라진다. 보류 중인 날짜를 약속하지 않는다.
        if (group.confirmBaseAt() == null || claims.confirmBlockingGroupIds().contains(group.getId())) {
            return null;
        }
        return group.confirmBaseAt().plusDays(orderProperties.getPurchaseConfirmDays());
    }

    // ------------------------------------------------------------------ 반품·교환(1-1 #2~#4 · 1-3 · 1-4)

    /** 검수를 통과한 반품 클레임 중 가장 최근 것 — 환불 대기이거나 환불로 끝난 것. */
    private static OrderClaim latestReturnPassed(List<OrderClaim> claims) {
        OrderClaim latest = null;
        for (OrderClaim claim : claims) {
            if (claim.getStatus() == ClaimStatus.REFUND_PENDING || claim.getResult() == ClaimResult.REFUNDED) {
                latest = claim;
            }
        }
        return latest;
    }

    /**
     * 반품 환불액 — 집행됐으면 확정액, 아니면 그 항목의 상품 금액. 배송비 차감은 요청 단위라 항목에 얹지 않는다
     * (반품·교환 상세가 말한다).
     */
    private static long returnedAmount(OrderProduct product, List<OrderClaim> claims) {
        long amount = 0;
        for (OrderClaim claim : claims) {
            if (claim.getStatus() == ClaimStatus.REFUND_PENDING || claim.getResult() == ClaimResult.REFUNDED) {
                amount += claim.getRefundedAmount() != null ? claim.getRefundedAmount()
                        : (long) product.getPrice() * claim.getQuantity();
            }
        }
        return amount;
    }

    /**
     * 반품·교환 반려 줄 — 검수 거절이 종결돼 「배송완료」로 돌아온 항목에만. 그 뒤에 신청한 진행 중 클레임이 있으면 상태가
     * 이미 그것을 말하므로(표시 상태가 배송완료가 아니다) 싣지 않는다. 구매확정에서 사라진다.
     */
    private static UserOrderDto.ClaimRejection claimRejection(UserOrderItemStatus status, List<OrderClaim> claims) {
        if (status != UserOrderItemStatus.DELIVERED) {
            return null;
        }
        OrderClaim latest = null;
        for (OrderClaim claim : claims) {
            if (claim.getResult() == ClaimResult.REJECTED) {
                latest = claim;
            }
        }
        return latest == null ? null : UserOrderDto.ClaimRejection.builder()
                .claimId(latest.getId())
                .type(latest.getType().name())
                .rejectedAt(latest.getRejectedAt())
                .build();
    }

    /** 다시 신청할 수 있는 수량 — 거절된 수량은 종결 뒤에도 돌아오지 않는다(시안 「반려된 상품은 반품 · 교환할 수 없어요」). */
    private static int claimableQuantity(OrderProduct product, List<OrderClaim> claims) {
        int rejected = claims.stream().filter(claim -> claim.getRejectedAt() != null)
                .mapToInt(OrderClaim::getQuantity).sum();
        return product.getQuantity() - product.getReturnedQuantity() - rejected;
    }

    // ------------------------------------------------------------------ 금액(1-3)

    /** 「환불은 배지가 아니라 결과」 — 결제된 뒤 취소된 항목만 환불이다. 배송비는 항목의 금액이 아니라 얹지 않는다. */
    private String amountLabel(UserOrderItemStatus status, OrderProduct product, long amount) {
        String won = NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
        boolean refund = (status == UserOrderItemStatus.CANCELLED && product.getCancelType() != null)
                || status == UserOrderItemStatus.RETURNED;
        return refund ? "환불 " + won : won;
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

    /** [반품 상세] / [교환 상세] — 클레임 유형으로 라벨이 갈린다. */
    private static String labelOf(UserOrderAction action, OrderClaim shownClaim) {
        return action == UserOrderAction.CLAIM_DETAIL && shownClaim != null
                && shownClaim.getType() == ClaimType.EXCHANGE
                ? UserOrderAction.EXCHANGE_DETAIL_LABEL : action.getLabel();
    }

    private List<UserOrderDto.Action> actions(UserOrderItemStatus status, OrderProduct product,
                                              OrderDeliveryGroup group, Context context, View view,
                                              OrderClaim shownClaim) {
        List<UserOrderAction> target = switch (status) {
            // CANCEL 의 게이트는 취소 API 와 같아야 한다 — 주문 전체 · 전 그룹 NEW. 준비 시작된 그룹이 섞인 주문의
            // NEW 항목은 「결제완료」로 보이되 버튼이 없다(앱이 그리고 서버가 409 를 내는 것보다 낫다).
            // 다른 하위주문이 준비 시작돼 전액 취소가 막힌 주문의 결제완료 항목은 요청으로 취소한다(1009 기획 3-1).
            case PAID -> group == null ? List.<UserOrderAction>of()
                    : context.cancellableOrderIds().contains(product.getOrder().getId())
                    ? List.of(UserOrderAction.CANCEL) : List.of(UserOrderAction.CANCEL_REQUEST);
            case PREPARING -> List.of(UserOrderAction.CANCEL_REQUEST);
            case SHIPPING, CONFIRMED -> group != null
                    ? List.of(UserOrderAction.TRACK_DELIVERY) : List.<UserOrderAction>of();
            // 전량이 거절 종결된 항목은 배송완료로 돌아와도 다시 신청할 수 없다 — 반품·교환 버튼을 붙이지 않는다.
            case DELIVERED -> claimableQuantity(product, context.claims().claimsOf(product.getId())) < 1
                    ? List.of(UserOrderAction.TRACK_DELIVERY)
                    : view == View.LIST
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
                .map(action -> {
                    // 교환할 옵션의 재고가 하나도 없으면 버튼을 지우지 않고 눌리지 않게 내린다 — 왜 안 되는지 보여야 한다.
                    boolean soldOut = action == UserOrderAction.EXCHANGE_REQUEST
                            && context.exchangeUnavailableProductIds().contains(product.getId());
                    return UserOrderDto.Action.builder()
                            .type(action)
                            .label(soldOut ? EXCHANGE_SOLD_OUT_LABEL : labelOf(action, shownClaim))
                            .enabled(!soldOut)
                            .build();
                })
                .toList();
    }
}
