package showroomz.domain.contract.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.contract.type.ContractDraftSubmissionStatus;

import java.time.LocalDateTime;

/**
 * 사전 검수 초안 제출(수정요청 B-13 · 계약서 제8조) — 인플루언서가 제출하는 순간이 브랜드의 수령이다. 브랜드는 수령 + 3영업일 안에 승인 ·
 * 수정 요청으로 회신하고, 회신이 없으면 시스템이 승인 간주를 기록한다. 상태 전이는 리포지토리의 조건부 UPDATE 로만 한다.
 */
@Entity
@Table(name = "contract_draft_submission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractDraftSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_id")
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "submitted_by_creator_id", nullable = false)
    private Long submittedByCreatorId;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "thread_message_id")
    private Long threadMessageId;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "review_due_at", nullable = false)
    private LocalDateTime reviewDueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ContractDraftSubmissionStatus status;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    @Column(name = "approval_id")
    private Long approvalId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ContractDraftSubmission submit(Long contractId, Long creatorId, String title, Long threadMessageId,
                                                 String note, LocalDateTime submittedAt, LocalDateTime reviewDueAt) {
        ContractDraftSubmission s = new ContractDraftSubmission();
        s.contractId = contractId;
        s.submittedByCreatorId = creatorId;
        s.title = title;
        s.threadMessageId = threadMessageId;
        s.note = note;
        s.submittedAt = submittedAt;
        s.reviewDueAt = reviewDueAt;
        s.status = ContractDraftSubmissionStatus.PENDING;
        s.createdAt = submittedAt;
        return s;
    }
}
