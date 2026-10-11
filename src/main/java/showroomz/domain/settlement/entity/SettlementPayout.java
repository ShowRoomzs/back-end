package showroomz.domain.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 3자 분배 — 수취자 1행(44 어드민 설계서 1-4). 정산 상태(분배 실패 · 지급 완료)는 이 행들에서 파생한다(0-4).
 *
 * <p><b>상태 · 계좌 · 결과는 리포지토리 조건부 UPDATE 로만 바꾼다</b>({@code SettlementPayoutRepository}) — 이 클래스에는
 * 생성 외의 쓰기 메서드가 없다. 계좌는 회원 정보의 스냅샷이다(운영자가 계좌를 입력하지 않는다 · 0-5) — 브랜드는 <b>확정 시점</b>
 * (「이미 확정된 정산 회차는 기존 계좌로 지급」 · 기본정보 §16-4), 인플루언서는 지급 <b>지시 시점</b>.
 */
@Entity
@Table(name = "settlement_payout")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payout_id")
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee", nullable = false, length = 10)
    private SettlementPayee payee;

    /** BRAND = brand_payout_amount · CREATOR = creator_payout_amount · PLATFORM = platform_share_amount. */
    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PayoutStatus status;

    /** 수취자별 지급 예정일 — 사업자 인플루언서는 대조 확인일 + N영업일이라 정산의 지급 예정일과 다르다(5-4). */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    /** {@code PersonalDataCipher} 암호문. */
    @Column(name = "account_number_enc", length = 255)
    private String accountNumberEnc;

    @Column(name = "account_holder", length = 64)
    private String accountHolder;

    @Column(name = "pg_reference", length = 100)
    private String pgReference;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "fail_code", length = 50)
    private String failCode;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    /** 재분배 횟수 — 상한 {@code settlement.payout-retry-limit}. */
    @Column(name = "attempt", nullable = false)
    private int attempt;

    /** 생성(2-7) — 금액은 2-4 값 · 확정 전 WAITING. */
    public static SettlementPayout waiting(Long settlementId, SettlementPayee payee, long amount) {
        SettlementPayout payout = new SettlementPayout();
        payout.settlementId = settlementId;
        payout.payee = payee;
        payout.amount = amount;
        payout.status = PayoutStatus.WAITING;
        payout.attempt = 0;
        return payout;
    }

    public boolean hasAccountSnapshot() {
        return accountNumberEnc != null;
    }
}
