package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.order.type.CancelReturnStatus;
import showroomz.domain.order.type.CancelReturnType;

import java.time.LocalDateTime;

/**
 * 취소·반품 — 주문 항목(SKU) 단위의 취소·반품 사유·회수·입고·환불 이력.
 * 수량 부분 취소를 지원하므로 한 주문 항목에 여러 건 발생 가능.
 * <ul>
 *   <li>단순취소(결제완료·준비 전): 소비자 취소 시 PG 자동 취소(운영자 불필요)</li>
 *   <li>반품(배송 후): 요청 → 운영자 승인 → 택배사 회수(문 앞 수거) → 브랜드 입고 확인 → 운영자 PG 취소</li>
 *   <li>환불 시 재고 원복, 환불액 = 공구가 × 취소 수량</li>
 * </ul>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cancel_return")
public class CancelReturn extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancel_return_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_product_id", nullable = false)
    private OrderProduct orderProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CancelReturnType type;

    /** 취소 수량 — 부분 취소 지원 */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CancelReturnStatus status = CancelReturnStatus.REQUESTED;

    /** 반품 거절 시 — 약관상 제한 사유(개봉·훼손·기간 경과 등) */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** 택배사 회수 API 접수 시 자동 발급되는 회수 송장 */
    @Column(name = "pickup_tracking_number", length = 100)
    private String pickupTrackingNumber;

    /** 브랜드 입고 확인 시각 */
    @Column(name = "warehoused_at")
    private LocalDateTime warehousedAt;

    /** 환불 실행(운영자 PG 취소) */
    @Column(name = "refunded_by", length = 64)
    private String refundedBy;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_amount")
    private Integer refundAmount;

    @Builder
    public CancelReturn(OrderProduct orderProduct, CancelReturnType type, Integer quantity, String reason) {
        this.orderProduct = orderProduct;
        this.type = type;
        this.quantity = quantity;
        this.reason = reason;
        this.status = CancelReturnStatus.REQUESTED;
    }

    /** 반품 승인 → 택배사 회수 접수(회수 송장 자동 발급) */
    public void startCollecting(String pickupTrackingNumber) {
        this.pickupTrackingNumber = pickupTrackingNumber;
        this.status = CancelReturnStatus.COLLECTING;
    }

    /** 브랜드 입고 확인 → 반송완료(환불 대기) */
    public void completeWarehousing() {
        this.warehousedAt = LocalDateTime.now();
        this.status = CancelReturnStatus.RETURNED;
    }

    /** 환불 실행 — 단순취소는 PG 자동, 반품은 입고 확인 후 운영자 */
    public void refund(String refundedBy, Integer refundAmount) {
        this.refundedBy = refundedBy;
        this.refundAmount = refundAmount;
        this.refundedAt = LocalDateTime.now();
        this.status = CancelReturnStatus.REFUNDED;
    }

    /** 반품 거절 — 요청(1차)·입고 검수(2차) 시점 모두 가능. 항목은 원상복귀 */
    public void reject(String rejectionReason) {
        this.rejectionReason = rejectionReason;
        this.status = CancelReturnStatus.REJECTED;
    }
}
