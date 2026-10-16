package showroomz.domain.member.creator.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.creator.type.CreatorSuspensionReason;

import java.time.LocalDateTime;

/**
 * 인플루언서 정지 1건 = 1행(53 설계서 구현 메모 12절 · 브랜드 {@code BrandSanction}과 같은 골격) — 해제까지. 열린 정지는
 * {@code released_at IS NULL} 행이고 인플루언서당 최대 1건이다(운영 DB 는 생성 컬럼 {@code active_key} UNIQUE · 엔티티 미매핑 — 서비스도
 * 같은 판정을 먼저 한다). {@code creator.status = SUSPENDED}는 이 행에서 파생해 저장한다.
 *
 * <p>{@code payout_held}는 사유에서 파생한 값의 <b>스냅샷</b>이다 — 뒤에 사유별 규칙이 바뀌어도 이미 내린 정지의 효과는 그대로다.
 */
@Entity
@Table(name = "creator_sanction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sanction_id")
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private CreatorSuspensionReason reason;

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

    public static CreatorSanction suspend(Long creatorId, CreatorSuspensionReason reason, String reasonDetail,
                                          Long operatorId, String operatorName, LocalDateTime now) {
        CreatorSanction sanction = new CreatorSanction();
        sanction.creatorId = creatorId;
        sanction.reason = reason;
        sanction.reasonDetail = reasonDetail;
        sanction.payoutHeld = reason.isHoldsPayout();
        sanction.suspendedAt = now;
        sanction.suspendedBy = operatorId;
        sanction.suspendedByName = operatorName;
        return sanction;
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

    /** 운영팀 채널 카드 · 이력에 쓰는 사유 문장 — 「협찬 표기 누락 반복 — 상세」. */
    public String reasonText() {
        if (reasonDetail == null || reasonDetail.isBlank()) {
            return reason.getLabel();
        }
        return reason.getLabel() + " — " + reasonDetail;
    }
}
