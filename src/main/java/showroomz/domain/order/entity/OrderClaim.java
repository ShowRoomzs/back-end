package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.ClaimCancelReason;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimRejectReason;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;

import java.time.LocalDateTime;

/**
 * 반품·교환 클레임 — 주문 항목 단위(35 설계서 1-1). 접수번호는 컬럼이 아니라 PK 의 표기다({@link #claimNumber()}).
 *
 * <p>상태 쓰기는 {@code OrderClaimRepository}의 조건부 UPDATE 로만 한다 — 엔티티에 전이 메서드를 두지 않는다(2절).
 * 주문·하위주문·마켓·소비자 id 는 비정규화다 — 타 브랜드 비노출과 앱 조회가 WHERE 한 줄이 된다.
 */
@Entity
@Table(name = "order_claim")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private OrderClaimCollection collection;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_group_id", nullable = false)
    private OrderDeliveryGroup deliveryGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_product_id", nullable = false)
    private OrderProduct orderProduct;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ClaimType type;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 요청의 사본 — 브랜드 목록 필터가 클레임 행을 읽는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40)
    private ClaimReason reasonCode;

    @Column(name = "reason_detail", length = 1000)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_bearer", nullable = false, length = 16)
    private ClaimFeeBearer feeBearer;

    @Column(name = "exchange_variant_id")
    private Long exchangeVariantId;

    @Column(name = "exchange_option_name", length = 255)
    private String exchangeOptionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ClaimStatus status;

    /** 현재 상태 진입 시각 — 「경과」 열과 탭 기본 정렬의 기준. 모든 전이가 같이 쓴다. */
    @Column(name = "stage_entered_at", nullable = false)
    private LocalDateTime stageEnteredAt;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /** 기한 ① — 신청 + N영업일. 발급 뒤 규칙이 바뀌어도 움직이지 않는다(귀책 판정값). */
    @Column(name = "collect_due_at", nullable = false)
    private LocalDateTime collectDueAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "received_by")
    private Long receivedBy;

    /** 기한 ② — 입고 확인 때 발급. */
    @Column(name = "inspect_due_at")
    private LocalDateTime inspectDueAt;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Column(name = "inspected_by")
    private Long inspectedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason_code", length = 30)
    private ClaimRejectReason rejectReasonCode;

    /** 소비자에게 그대로 전달된다(약관 제20조③). */
    @Column(name = "reject_detail", length = 1000)
    private String rejectDetail;

    /** 재발송 사유의 판별자(NULL = 교환 재발송 · 값 = 거절 반송)이자 구매확정 보류 해제의 판별자다. */
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    /** 미결제 고지 횟수 — {@code order_claim_notice}의 집계 캐시. */
    @Column(name = "notice_count", nullable = false)
    private Integer noticeCount = 0;

    /** 최종 고지 시각 — 보관 기한의 기산점. 보관 기한 자체는 저장하지 않는다(계산값). */
    @Column(name = "last_notice_at")
    private LocalDateTime lastNoticeAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reship_carrier", length = 30)
    private DeliveryCarrier reshipCarrier;

    @Column(name = "reship_tracking_number", length = 50)
    private String reshipTrackingNumber;

    @Column(name = "reship_registered_at")
    private LocalDateTime reshipRegisteredAt;

    @Column(name = "reship_registered_by")
    private Long reshipRegisteredBy;

    @Column(name = "reship_last_tracking_at")
    private LocalDateTime reshipLastTrackingAt;

    @Column(name = "reship_delivered_at")
    private LocalDateTime reshipDeliveredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 16)
    private ClaimResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 20)
    private ClaimCancelReason cancelReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 운영자가 집행한 확정액 — 예정액과 다를 수 있다. */
    @Column(name = "refunded_amount")
    private Integer refundedAmount;

    /** 거절 종결이 보관 기간 만료 후 폐기였을 때 — 반송 완료와 구분한다. */
    @Column(name = "disposed_at")
    private LocalDateTime disposedAt;

    @Builder
    public OrderClaim(OrderClaimCollection collection, Long orderId, OrderDeliveryGroup deliveryGroup,
                      OrderProduct orderProduct, Long marketId, Long userId, ClaimType type, Integer quantity,
                      ClaimReason reasonCode, String reasonDetail, ClaimFeeBearer feeBearer, Long exchangeVariantId,
                      String exchangeOptionName, ClaimStatus status, LocalDateTime requestedAt,
                      LocalDateTime collectDueAt) {
        this.collection = collection;
        this.orderId = orderId;
        this.deliveryGroup = deliveryGroup;
        this.orderProduct = orderProduct;
        this.marketId = marketId;
        this.userId = userId;
        this.type = type;
        this.quantity = quantity;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.feeBearer = feeBearer;
        this.exchangeVariantId = exchangeVariantId;
        this.exchangeOptionName = exchangeOptionName;
        this.status = status;
        this.stageEnteredAt = requestedAt;
        this.requestedAt = requestedAt;
        this.collectDueAt = collectDueAt;
    }

    /** 접수번호 — PK 의 표기일 뿐이다(발번 시퀀스도 UNIQUE 도 필요 없다). */
    public String claimNumber() {
        return "CLM-" + id;
    }

    /** 진행 중 — 수량을 점유하고 화면에 진행으로 보인다. */
    public boolean isOpen() {
        return status != ClaimStatus.COMPLETED;
    }

    /** 구매확정을 세우는가 — 진행 중이고 거절되지 않은 것. 거절 보류·거절 반송은 진행 중이지만 보류는 아니다. */
    public boolean blocksPurchaseConfirm() {
        return isOpen() && rejectedAt == null;
    }
}
