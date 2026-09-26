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
import showroomz.domain.groupbuy.type.SuspensionWithdrawReason;

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

    /**
     * 통지 시점 게시물 판본(32 설계 0-5). 소명이 「이미 삭제했다」고 주장할 때 통지 시점 본문과 현재 본문을 대조할 기준이다.
     * 게시물이 없거나 판본이 아직 없으면 null.
     */
    @Column(name = "notice_revision_no")
    private Integer noticeRevisionNo;

    /** 「통지 후 +N건」의 기준점 — 판매 포트가 비어 있으면 null이다. 0이 아니다(32 설계 0-7). */
    @Column(name = "sales_order_count_at_notice")
    private Integer salesOrderCountAtNotice;

    @Column(name = "sales_amount_at_notice")
    private Long salesAmountAtNotice;

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

    /** 철회 사유 코드 — 제재 이력 연동에서 「귀책 없음」을 가른다(32 설계 1-2). */
    @Enumerated(EnumType.STRING)
    @Column(name = "withdraw_reason_code", length = 32)
    private SuspensionWithdrawReason withdrawReasonCode;

    /** 브랜드에 전달할 철회 설명(M7). */
    @Column(name = "withdraw_detail", length = 1000)
    private String withdrawDetail;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "executed_by")
    private Long executedBy;

    /** 집행 판정 사유 — 소명을 낸 브랜드는 그 소명이 왜 받아들여지지 않았는지를 들어야 한다(32 설계 6-3 ①). */
    @Column(name = "execution_note", length = 1000)
    private String executionNote;

    /** 사전 통지(M6) — 공구는 SUSPENSION_SCHEDULED가 된다. 판매는 멈추지 않는다. */
    public static GroupBuyAdminSuspension notice(GroupBuy groupBuy, SuspensionReasonClause clause, String body,
                                                 LocalDateTime executeScheduledAt, LocalDateTime appealDeadlineAt,
                                                 Integer revisionNo, Integer ordersAtNotice, Long amountAtNotice,
                                                 Long operatorId, LocalDateTime now) {
        return GroupBuyAdminSuspension.builder()
                .groupBuy(groupBuy)
                .kind(AdminSuspensionKind.NOTICE)
                .reasonClause(clause)
                .noticeBody(body)
                .noticedAt(now)
                .noticedBy(operatorId)
                .executeScheduledAt(executeScheduledAt)
                .appealDeadlineAt(appealDeadlineAt)
                .noticeRevisionNo(revisionNo)
                .salesOrderCountAtNotice(ordersAtNotice)
                .salesAmountAtNotice(amountAtNotice)
                .status(AdminSuspensionStatus.NOTICED)
                .build();
    }

    /** 긴급 직권 중단(M4 · 제17조③) — 통지를 거치지 않고 EXECUTED로 바로 생긴다. 소명 창이 없다. */
    public static GroupBuyAdminSuspension emergency(GroupBuy groupBuy, EmergencySuspensionReason reason, String body,
                                                    Integer revisionNo, Integer ordersAtNotice, Long amountAtNotice,
                                                    Long operatorId, LocalDateTime now) {
        return GroupBuyAdminSuspension.builder()
                .groupBuy(groupBuy)
                .kind(AdminSuspensionKind.EMERGENCY)
                .emergencyReason(reason)
                .noticeBody(body)
                .noticedAt(now)
                .noticedBy(operatorId)
                .noticeRevisionNo(revisionNo)
                .salesOrderCountAtNotice(ordersAtNotice)
                .salesAmountAtNotice(amountAtNotice)
                .status(AdminSuspensionStatus.EXECUTED)
                .executedAt(now)
                .executedBy(operatorId)
                .build();
    }

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

    /**
     * 소명 검토 큐 대상 — 통지 중 ∧ (소명 제출됨 ∨ 소명 기한 경과). 기한이 지난 미제출도 최종 판정자는 운영자다
     * (제17조④ · 32 설계 2-3). 큐 판정식({@code AdminGroupBuyQueuePredicate})과 같은 뜻이다.
     */
    public boolean isAwaitingAdminReview(LocalDateTime now) {
        return isNoticed() && (isAppealSubmitted()
                || (appealDeadlineAt != null && now.isAfter(appealDeadlineAt)));
    }

    /** 집행 가능 시각 — 소명 기한이 지났고 <b>통지한 집행 예정 일시가 되었다</b>(32 설계 6-3 ②). */
    public boolean isExecutionDue(LocalDateTime now) {
        return isNoticed()
                && appealDeadlineAt != null && now.isAfter(appealDeadlineAt)
                && executeScheduledAt != null && !now.isBefore(executeScheduledAt);
    }

    /** 이 행들은 조건부 UPDATE가 상태 경합을 막은 뒤, 같은 트랜잭션의 응답이 새 값을 읽게 할 뿐이다. */
    public void applyWithdrawn(SuspensionWithdrawReason reasonCode, String detail, Long operatorId, LocalDateTime now) {
        this.status = AdminSuspensionStatus.WITHDRAWN;
        this.withdrawReasonCode = reasonCode;
        this.withdrawDetail = detail;
        this.withdrawnAt = now;
        this.withdrawnBy = operatorId;
    }

    public void applyExecuted(String executionNote, Long operatorId, LocalDateTime now) {
        this.status = AdminSuspensionStatus.EXECUTED;
        this.executionNote = executionNote;
        this.executedAt = now;
        this.executedBy = operatorId;
    }

    /** 공구 기간이 먼저 끝나 판정할 대상이 사라졌다(30 설계 7-2 #3). */
    public void lapse() {
        this.status = AdminSuspensionStatus.LAPSED;
    }

    /** 통지 중 긴급 집행으로 대체됐다(32 설계 6-5). */
    public void supersede() {
        this.status = AdminSuspensionStatus.SUPERSEDED;
    }

    /** 표시 라벨 — 사전 통지는 호수, 긴급은 긴급 사유. */
    public String basisLabel() {
        if (kind == AdminSuspensionKind.EMERGENCY) {
            return emergencyReason == null ? null : emergencyReason.getLabel();
        }
        return reasonClause == null ? null : reasonClause.getLabel();
    }
}
