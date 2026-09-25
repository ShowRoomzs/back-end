package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.EmergencySuspensionReason;
import showroomz.domain.groupbuy.type.SuspensionReasonClause;

import java.time.LocalDateTime;

/**
 * 직권 중단 통지 · 소명 · 집행(§29-7 · 설계서 1-7).
 *
 * <p>소명은 브랜드에게만 있다 — 제17조②④가 브랜드에게만 통지·소명을 보장한다. 제출 후 수정할 수 없다(C9).
 * 진행 중 통지 1건 제약은 운영 DB의 생성 컬럼 {@code active_group_buy_id} UNIQUE가 건다(엔티티 미매핑).
 */
@Entity
@Table(name = "group_buy_admin_suspension")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GroupBuyAdminSuspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_suspension_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_buy_id", nullable = false)
    private GroupBuy groupBuy;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private AdminSuspensionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_clause", length = 32)
    private SuspensionReasonClause reasonClause;

    @Enumerated(EnumType.STRING)
    @Column(name = "emergency_reason", length = 32)
    private EmergencySuspensionReason emergencyReason;

    @Column(name = "notice_body", nullable = false, length = 2000)
    private String noticeBody;

    @Column(name = "noticed_at", nullable = false)
    private LocalDateTime noticedAt;

    @Column(name = "noticed_by", nullable = false)
    private Long noticedBy;

    @Column(name = "execute_scheduled_at")
    private LocalDateTime executeScheduledAt;

    @Column(name = "appeal_deadline_at")
    private LocalDateTime appealDeadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AdminSuspensionStatus status;

    @Column(name = "appeal_content", length = 2000)
    private String appealContent;

    @Column(name = "appeal_submitted_at")
    private LocalDateTime appealSubmittedAt;

    @Column(name = "appeal_submitted_by")
    private Long appealSubmittedBy;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "withdrawn_by")
    private Long withdrawnBy;

    @Column(name = "withdraw_reason", length = 1000)
    private String withdrawReason;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "executed_by")
    private Long executedBy;

    public boolean isNoticed() {
        return status == AdminSuspensionStatus.NOTICED;
    }

    public boolean isAppealSubmitted() {
        return appealSubmittedAt != null;
    }

    /** 소명 창이 열려 있는지 — 통지 중 · 미제출 · 기한 이내(설계서 4-5 canSubmitAppeal). */
    public boolean isAppealOpen(LocalDateTime now) {
        return isNoticed() && !isAppealSubmitted()
                && appealDeadlineAt != null && !now.isAfter(appealDeadlineAt);
    }

    /**
     * 소명 제출 — 경합 차단은 리포지토리의 조건부 UPDATE가 하고, 이 메서드는 같은 트랜잭션의 응답이
     * 새 값을 읽게 할 뿐이다. 제출 후 수정 경로는 없다(C9).
     */
    public void applyAppealSubmitted(String content, Long sellerId, LocalDateTime now) {
        this.appealContent = content;
        this.appealSubmittedAt = now;
        this.appealSubmittedBy = sellerId;
    }

    /** 집행 전에 공구 기간이 끝났다(설계서 7-2 #3). */
    public void lapse() {
        this.status = AdminSuspensionStatus.LAPSED;
    }
}
