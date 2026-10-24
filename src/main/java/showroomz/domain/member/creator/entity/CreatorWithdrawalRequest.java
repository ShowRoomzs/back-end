package showroomz.domain.member.creator.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.creator.type.CreatorWithdrawalRejectReason;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 인플루언서 탈퇴 신청 1건 = 1행(53 설계서 0-3 · 1-3). 신청 · 반려 이력은 지우지 않는다 — 재신청은 새 행. 열린 신청({@code PENDING})은
 * 인플루언서당 1건이다(운영 DB 생성 컬럼 {@code pending_key} UNIQUE · 서비스도 먼저 판정).
 *
 * <p>회원 상태는 승인 전까지 {@code ACTIVE}(또는 {@code SUSPENDED}) 그대로다 — 심사는 다른 축이다(§51-0).
 */
@Entity
@Table(name = "creator_withdrawal_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorWithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberWithdrawalStatus status;

    @Column(name = "reason_text", nullable = false, length = 500)
    private String reasonText;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /** 승인 기한 안내(스튜디오 「영업일 5일 이내」) — 어드민은 경과일만 본다. 무응답 자동 승인은 없다. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_by_name", length = 64)
    private String processedByName;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason", length = 40)
    private CreatorWithdrawalRejectReason rejectReason;

    @Column(name = "reject_note", length = 500)
    private String rejectNote;

    @Column(name = "approved_group_buy_count")
    private Integer approvedGroupBuyCount;

    @Column(name = "approved_contract_count")
    private Integer approvedContractCount;

    @Column(name = "approved_unsettled_count")
    private Integer approvedUnsettledCount;

    /** 스튜디오 S8 반려 안내 [확인] 시각 — NULL 인 반려는 계정 탭에 배너로 남는다(50 설계서 2-3). */
    @Column(name = "result_acknowledged_at")
    private LocalDateTime resultAcknowledgedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static CreatorWithdrawalRequest request(Long creatorId, String reasonText, LocalDateTime now,
                                                   LocalDate dueDate) {
        CreatorWithdrawalRequest request = new CreatorWithdrawalRequest();
        request.creatorId = creatorId;
        request.status = MemberWithdrawalStatus.PENDING;
        request.reasonText = reasonText;
        request.requestedAt = now;
        request.dueDate = dueDate;
        return request;
    }

    public boolean isPending() {
        return status == MemberWithdrawalStatus.PENDING;
    }

    public void approve(Long operatorId, String operatorName, int groupBuys, int contracts, int unsettled,
                        LocalDateTime now) {
        this.status = MemberWithdrawalStatus.APPROVED;
        this.processedAt = now;
        this.processedBy = operatorId;
        this.processedByName = operatorName;
        this.approvedGroupBuyCount = groupBuys;
        this.approvedContractCount = contracts;
        this.approvedUnsettledCount = unsettled;
    }

    public void reject(CreatorWithdrawalRejectReason reason, String note, Long operatorId, String operatorName,
                       LocalDateTime now) {
        this.status = MemberWithdrawalStatus.REJECTED;
        this.rejectReason = reason;
        this.rejectNote = note;
        this.processedAt = now;
        this.processedBy = operatorId;
        this.processedByName = operatorName;
    }

    public void cancel(LocalDateTime now) {
        this.status = MemberWithdrawalStatus.CANCELED;
        this.canceledAt = now;
    }

    public boolean isRejected() {
        return status == MemberWithdrawalStatus.REJECTED;
    }

    /** 반려됐고 인플루언서가 아직 [확인]을 누르지 않았다 — 계정 탭 S8 배너. */
    public boolean isUnacknowledgedRejection() {
        return isRejected() && resultAcknowledgedAt == null;
    }

    /** S8 [확인] — 표시 플래그만 바꾼다. 사유 · 이력은 운영자 쪽에 그대로 남는다. 이미 확인했으면 그대로(멱등). */
    public void acknowledgeResult(LocalDateTime now) {
        if (resultAcknowledgedAt == null) {
            this.resultAcknowledgedAt = now;
        }
    }
}
