package showroomz.domain.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.settlement.type.SettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정산 — 공구 1건당 1건(공구 1:1 정산).
 * 트리거 = 공구 종료 + 마지막 배송분까지 구매확정 완료 → 시스템 집계·계산.
 * <pre>
 * 정산 대상 금액 S = 구매확정 매출(공구가 × 수량 합계) − 환불분
 * 플랫폼 수수료   = S × 2%
 * 분배 재원       = S × 98%
 * 인플루언서 지급 = Σ(계약 상품 항목별 분배 재원 × 그 상품의 리워드율)  ← 상품별 차등
 * 브랜드 지급     = 분배 재원 − 인플루언서 지급
 * </pre>
 * 이체는 운영자 확인 후 실행(나가는 돈은 운영자 통제 — §3-B).
 * 정산 기한 = 마지막 구매확정 + 영업일 5일(클로백 대비 지급 유예 포함), 보류 시 연장.
 * 자금 흐름(A/B)·세금계산서·원천징수는 [세무 확정 대기].
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "settlement")
public class Settlement extends BaseTimeEntity {

    /** 플랫폼 수수료율 2% */
    public static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.02");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id", nullable = false, unique = true)
    private GroupBuy groupBuy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status = SettlementStatus.PENDING;

    /** 정산 대상 금액 S = 구매확정 매출 − 환불 */
    @Column(name = "target_amount", nullable = false)
    private Long targetAmount;

    /** 플랫폼 수수료 = S × 2% */
    @Column(name = "platform_fee", nullable = false)
    private Long platformFee;

    /** 인플루언서 지급액 = Σ(상품 항목별 분배 재원 × 리워드율) */
    @Column(name = "influencer_payout", nullable = false)
    private Long influencerPayout;

    /** 브랜드 지급액 = 분배 재원 − 인플루언서 지급 */
    @Column(name = "brand_payout", nullable = false)
    private Long brandPayout;

    /** 정산 기한 — 마지막 구매확정 + 영업일 5일. 보류 시 연장 */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "hold_reason", length = 500)
    private String holdReason;

    /** 확인 운영자 식별자 */
    @Column(name = "confirmed_by", length = 64)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "transferred_at")
    private LocalDateTime transferredAt;

    @Builder
    public Settlement(GroupBuy groupBuy, Long targetAmount, Long platformFee,
                      Long influencerPayout, Long brandPayout, LocalDate dueDate) {
        this.groupBuy = groupBuy;
        this.targetAmount = targetAmount;
        this.platformFee = platformFee;
        this.influencerPayout = influencerPayout;
        this.brandPayout = brandPayout;
        this.dueDate = dueDate;
        this.status = SettlementStatus.PENDING;
    }

    /** 운영자 검토·승인 */
    public void confirm(String adminIdentifier) {
        this.confirmedBy = adminIdentifier;
        this.confirmedAt = LocalDateTime.now();
        this.status = SettlementStatus.ADMIN_CONFIRMED;
    }

    /** 이체 실행 완료 → 공구는 '정산완료'로 전환(서비스에서 처리) */
    public void completeTransfer() {
        this.transferredAt = LocalDateTime.now();
        this.status = SettlementStatus.TRANSFERRED;
    }

    /** 분쟁·확인 필요로 보류 — 정산 기한 연장 */
    public void hold(String reason, LocalDate extendedDueDate) {
        this.holdReason = reason;
        this.dueDate = extendedDueDate;
        this.status = SettlementStatus.ON_HOLD;
    }

    /** 보류 해소 → 정산대기 복귀 */
    public void resolveHold() {
        this.holdReason = null;
        this.status = SettlementStatus.PENDING;
    }
}
