package showroomz.domain.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.settlement.type.PayoutGroupStatus;
import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 지급 묶음(60_KG이니시스_지급대행_전환_계획서.md D-1 · 3-2) — 같은 수취자 · 지급일 · 차수의 수취자 행({@link SettlementPayout#getPayoutGroupId})을
 * 합계 1건으로 PG 에 올린다. 이니시스는 지급을 {@code (idMall, dtPay, clService)} 로만 가르고 같은 키로 다시 올리면 마지막 금액이 최종이라,
 * <b>합계는 도메인이 계산하고</b> 우리가 마지막으로 보낸 합계를 여기에 남겨 덮어쓰기 사고를 막는다.
 *
 * <ul>
 *   <li>{@link #intendedAmount} — 보내기 전에 커밋한 합계. {@link #confirmedAmount} — PG 가 받은 마지막 합계(결과 모름이면 이전 값 유지 · 송금 결과
 *   대조 기준). 둘이 어긋나면 마감 전 틱이 다시 올린다(D-1 ④).</li>
 *   <li>묶음은 계좌가 하나다({@link #accountHash}) — 다른 계좌의 행은 합류하지 못한다(D-1 ⑤).</li>
 * </ul>
 * 쓰기는 {@code SettlementPayoutGroupService} 가 묶음 행을 잠근({@code SELECT … FOR UPDATE}) 트랜잭션 안에서만 한다.
 */
@Entity
@Table(name = "settlement_payout_group", uniqueConstraints = @UniqueConstraint(name = "uk_payout_group",
        columnNames = {"provider", "payee", "payee_ref_id", "pay_date", "schedule_type", "seq"}),
        indexes = @Index(name = "idx_payout_group_status_date", columnList = "status, pay_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementPayoutGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** {@code INICIS | SIMULATED}. */
    @Column(name = "provider", nullable = false, length = 16)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee", nullable = false, length = 16)
    private SettlementPayee payee;

    /** BRAND = market_id · CREATOR = creator_id. */
    @Column(name = "payee_ref_id", nullable = false)
    private Long payeeRefId;

    /** 지급일({@code dtPay} · 돈이 나가는 날). */
    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    /** {@code NEXT_DAY | SAME_DAY | IMMEDIATE}. */
    @Column(name = "schedule_type", nullable = false, length = 10)
    private String scheduleType;

    @Column(name = "seq", nullable = false)
    private int seq;

    /** PG 서브몰 id({@code idMall}) — 시뮬레이터는 null. */
    @Column(name = "pg_partner_id", length = 64)
    private String pgPartnerId;

    @Column(name = "account_hash", nullable = false, length = 64)
    private String accountHash;

    @Column(name = "intended_amount", nullable = false)
    private long intendedAmount;

    @Column(name = "confirmed_amount")
    private Long confirmedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PayoutGroupStatus status;

    @Column(name = "result_code", length = 32)
    private String resultCode;

    @Column(name = "remitted_on")
    private LocalDate remittedOn;

    @Column(name = "last_request_id", length = 64)
    private String lastRequestId;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static SettlementPayoutGroup open(String provider, SettlementPayee payee, Long payeeRefId, LocalDate payDate,
                                             String scheduleType, int seq, String pgPartnerId, String accountHash,
                                             LocalDateTime now) {
        SettlementPayoutGroup group = new SettlementPayoutGroup();
        group.provider = provider;
        group.payee = payee;
        group.payeeRefId = payeeRefId;
        group.payDate = payDate;
        group.scheduleType = scheduleType;
        group.seq = seq;
        group.pgPartnerId = pgPartnerId;
        group.accountHash = accountHash;
        group.intendedAmount = 0L;
        group.status = PayoutGroupStatus.OPEN;
        group.createdAt = now;
        group.updatedAt = now;
        return group;
    }

    /** PG 쪽 묶음 키의 표시값 — 수취자 행의 {@code pg_transfer_id} 에 남는다({@code {idMall}:{dtPay}:{차수}} · 시뮬레이터는 묶음 id). */
    public String transferKey() {
        return pgPartnerId == null ? "G" + id : pgPartnerId + ":" + payDate + ":" + scheduleType;
    }

    /** PG 가 받은 합계가 있다(한 번이라도 접수됐다). */
    public boolean isUploaded() {
        return confirmedAmount != null && confirmedAmount > 0;
    }

    /** 확정 합계와 현재 합계가 다른가 — 수렴 대상(D-1 ④). */
    public boolean differsFrom(long total) {
        return confirmedAmount == null ? total > 0 : confirmedAmount != total;
    }

    public void linkPartner(String pgPartnerId, LocalDateTime now) {
        this.pgPartnerId = pgPartnerId;
        this.updatedAt = now;
    }

    /** 보내기 전 — 올리려는 합계를 먼저 커밋한다(D-1 ②). */
    public void intend(long total, LocalDateTime now) {
        this.intendedAmount = total;
        this.updatedAt = now;
    }

    /** PG 가 받았다 — 확정 합계 갱신. 합계 0(삭제 접수)이면 {@code DELETED}. */
    public void confirm(long total, String requestId, LocalDateTime now) {
        this.confirmedAmount = total;
        this.lastRequestId = requestId == null ? lastRequestId : requestId;
        this.submittedAt = now;
        this.resultCode = null;
        if (total == 0) {
            this.status = PayoutGroupStatus.DELETED;
        }
        this.updatedAt = now;
    }

    /** 결과 모름 — 확정 합계는 그대로, 추적 id 만 남긴다. */
    public void recordAttempt(String requestId, String code, LocalDateTime now) {
        this.lastRequestId = requestId == null ? lastRequestId : requestId;
        this.resultCode = code;
        this.updatedAt = now;
    }

    public void markDeleted(String code, LocalDateTime now) {
        this.status = PayoutGroupStatus.DELETED;
        this.resultCode = code;
        this.updatedAt = now;
    }

    public void markPaid(LocalDate remittedOn, LocalDateTime now) {
        this.status = PayoutGroupStatus.PAID;
        this.remittedOn = remittedOn;
        this.resultCode = null;
        this.updatedAt = now;
    }

    public void markFailed(String code, LocalDateTime now) {
        this.status = PayoutGroupStatus.FAILED;
        this.resultCode = code;
        this.updatedAt = now;
    }

    /** 금액 불일치 등 사람을 부르는 표시 — 상태는 그대로. */
    public void flag(String code, LocalDateTime now) {
        this.resultCode = code;
        this.updatedAt = now;
    }
}
