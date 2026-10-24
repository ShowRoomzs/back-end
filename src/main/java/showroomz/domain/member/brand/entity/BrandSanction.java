package showroomz.domain.member.brand.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.brand.type.BrandSuspensionReason;
import showroomz.domain.member.brand.type.SanctionReleaseReason;

import java.time.LocalDateTime;

/**
 * 브랜드 정지 1건 = 1행(52 설계서 0-3 · 1-2) — 해제까지. 열린 정지는 {@code released_at IS NULL} 행이고 마켓당 최대 1건이다(운영 DB 는
 * 생성 컬럼 {@code active_key} UNIQUE · 엔티티 미매핑 — 서비스도 같은 판정을 먼저 한다). {@code market.status = SUSPENDED}는
 * 이 행에서 파생해 저장한다.
 *
 * <p>{@code payout_held}는 사유에서 파생한 값의 <b>스냅샷</b>이다 — 뒤에 사유별 규칙이 바뀌어도 이미 내린 정지의 효과는 그대로다.
 */
@Entity
@Table(name = "brand_sanction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sanction_id")
    private Long id;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private BrandSuspensionReason reason;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @Column(name = "payout_held", nullable = false)
    private boolean payoutHeld;

    @Column(name = "suspended_at", nullable = false)
    private LocalDateTime suspendedAt;

    @Column(name = "suspended_by", nullable = false)
    private Long suspendedBy;

    @Column(name = "suspended_by_name", nullable = false, length = 64)
    private String suspendedByName;

    @Column(name = "suspend_card_id")
    private Long suspendCardId;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    /** 시스템 종결(탈퇴 승인)은 null. */
    @Column(name = "released_by")
    private Long releasedBy;

    @Column(name = "released_by_name", length = 64)
    private String releasedByName;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_reason", length = 40)
    private SanctionReleaseReason releaseReason;

    @Column(name = "release_note", length = 500)
    private String releaseNote;

    @Column(name = "release_card_id")
    private Long releaseCardId;

    /** 정지 종료일(포함) — 다음 날 00:00 자동 해제(1021 기-02). null = 기간 없음(기존 행 · 수동 해제). */
    @Column(name = "ends_on")
    private java.time.LocalDate endsOn;

    /** 이의 신청 기한 — 통지 + N영업일 23:59:59(표시용 · 접수 차단 안 함). */
    @Column(name = "appeal_deadline_at")
    private LocalDateTime appealDeadlineAt;

    @org.hibernate.annotations.ColumnDefault("false")
    @Column(name = "auto_released", nullable = false)
    private boolean autoReleased;

    public static BrandSanction suspend(Long marketId, BrandSuspensionReason reason, String reasonDetail,
                                        Long operatorId, String operatorName, LocalDateTime now) {
        BrandSanction sanction = new BrandSanction();
        sanction.marketId = marketId;
        sanction.reason = reason;
        sanction.reasonDetail = reasonDetail;
        sanction.payoutHeld = reason.isHoldsPayout();
        sanction.suspendedAt = now;
        sanction.suspendedBy = operatorId;
        sanction.suspendedByName = operatorName;
        return sanction;
    }

    /** 정지 기간 · 이의 신청 기한(1021 기-02) — 정지 때 한 번. */
    public void applyTerm(java.time.LocalDate endsOn, LocalDateTime appealDeadlineAt) {
        this.endsOn = endsOn;
        this.appealDeadlineAt = appealDeadlineAt;
    }

    /** 기간이 끝났는가 — 종료일 다음 날부터. */
    public boolean isTermEnded(java.time.LocalDate today) {
        return endsOn != null && endsOn.isBefore(today);
    }

    public void markAutoReleased() {
        this.autoReleased = true;
    }

    public boolean isOpen() {
        return releasedAt == null;
    }

    public void release(SanctionReleaseReason reason, String note, Long operatorId, String operatorName,
                        LocalDateTime now) {
        this.releasedAt = now;
        this.releaseReason = reason;
        this.releaseNote = note;
        this.releasedBy = operatorId;
        this.releasedByName = operatorName;
    }

    public void recordSuspendCard(Long messageId) {
        this.suspendCardId = messageId;
    }

    public void recordReleaseCard(Long messageId) {
        this.releaseCardId = messageId;
    }

    /** 운영팀 스레드 카드 · 레일에 쓰는 사유 문장 — 「정산 정보 불일치 — 상세」. */
    public String reasonText() {
        if (reasonDetail == null || reasonDetail.isBlank()) {
            return reason.getLabel();
        }
        return reason.getLabel() + " — " + reasonDetail;
    }
}
