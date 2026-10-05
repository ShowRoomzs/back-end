package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimChargeType;

import java.time.LocalDateTime;

/**
 * 소비자 추가 결제(35 설계서 1-9 · 앱 클레임 설계서 1-5) — 클레임 때문에 내는 재발송 배송비. 배송비는 박스(요청)에 붙는다.
 * 한 요청에 미정산 {@code REJECT_RESHIP}은 1건까지다 — 같은 박스에서 두 항목이 거절되면 한 건에 합류한다.
 */
@Entity
@Table(name = "order_claim_charge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderClaimCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "charge_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private OrderClaimCollection collection;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ClaimChargeType type;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ClaimChargeStatus status;

    /** {@code REJECT_RESHIP}의 결제 기한 — 지나면 미결제 고지가 시작된다. */
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "paid_payment_id", length = 64)
    private String paidPaymentId;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
