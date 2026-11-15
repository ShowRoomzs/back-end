package showroomz.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractDraftApproval;
import showroomz.domain.contract.entity.ContractDraftSubmission;
import showroomz.domain.contract.repository.ContractDraftApprovalRepository;
import showroomz.domain.contract.repository.ContractDraftSubmissionRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractDraftSubmissionStatus;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.order.service.BusinessDayCalculator;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사전 검수(수정요청 B-13 · 계약서 제8조) — 「갑은 초안 수령일부터 3영업일 이내에 회신하여야 하고, 기간 내 회신이 없는 경우 확인한 것으로
 * 본다」. 인플루언서 제출 = 브랜드 수령이고, 회신은 승인(초안 승인 기록) 또는 수정 요청이다. 기한이 지나면 시스템이 승인 간주를 기록한다.
 * 기록 전용이다 — 계약 · 공구 상태는 바꾸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ContractDraftReviewService {

    /** 회신 기한 — 수령 + N영업일의 끝(계약서 제8조). */
    public static final int REVIEW_BUSINESS_DAYS = 3;
    static final String DEEMED_NOTE = "초안 수령 후 3영업일 무회신 · 승인 간주(계약서 제8조)";

    private final ContractRepository contracts;
    private final ContractDraftSubmissionRepository submissions;
    private final ContractDraftApprovalRepository approvals;
    private final ContractHistoryRecorder history;
    private final ContractNotifier notifier;
    private final BusinessDayCalculator businessDayCalculator;

    /** 초안 제출(인플루언서) — 사전 검수 계약 ∧ 계약 성립 이후만. */
    @Transactional
    public ContractDraftSubmission submit(Contract contract, Long creatorId, String title, Long threadMessageId,
                                          String note, LocalDateTime now) {
        if (!Boolean.TRUE.equals(contract.getBrandPreReview())) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT, "브랜드 사전 검수가 없는 계약입니다.");
        }
        if (contract.getEstablishedAt() == null) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT, "계약 성립 뒤에 초안을 제출할 수 있습니다.");
        }
        ContractDraftSubmission saved = submissions.save(ContractDraftSubmission.submit(contract.getId(), creatorId,
                title.trim(), threadMessageId, blankToNull(note), now,
                businessDayCalculator.dueAt(now, REVIEW_BUSINESS_DAYS)));
        history.record(contract, ContractEventType.DRAFT_SUBMITTED, ContractActorType.CREATOR, creatorId, null,
                saved.getTitle(), now);
        notifier.notifySeller(contract, "DRAFT_SUBMITTED");
        return saved;
    }

    /** 승인 회신(브랜드) — 초안 승인 기록과 이어 둔다. 회신 대기가 아니면 409. */
    @Transactional
    public void approve(Contract contract, Long submissionId, Long approvalId, LocalDateTime now) {
        ContractDraftSubmission submission = load(contract, submissionId);
        if (submissions.decide(submission.getId(), ContractDraftSubmissionStatus.APPROVED, now, null, approvalId) != 1) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT, "이미 회신했거나 승인 간주된 초안입니다.");
        }
    }

    /** 수정 요청 회신(브랜드) — 내용 필수. 회신 대기가 아니면 409. */
    @Transactional
    public void requestRevision(Contract contract, Long submissionId, Long marketId, String note, LocalDateTime now) {
        String trimmed = blankToNull(note);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "수정 요청 내용을 입력해 주세요.");
        }
        ContractDraftSubmission submission = load(contract, submissionId);
        if (submissions.decide(submission.getId(), ContractDraftSubmissionStatus.REVISION_REQUESTED, now, trimmed,
                null) != 1) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT, "이미 회신했거나 승인 간주된 초안입니다.");
        }
        // 조건부 UPDATE 가 영속성 컨텍스트를 비웠다 — 계약을 다시 읽는다.
        Contract fresh = contracts.findById(contract.getId()).orElseThrow();
        history.record(fresh, ContractEventType.DRAFT_REVISION_REQUESTED, ContractActorType.SELLER, marketId, null,
                submission.getTitle() + " · " + trimmed, now);
        notifier.notifyCounterparty(fresh, "DRAFT_REVISION_REQUESTED");
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsDueForDeemedApproval(LocalDateTime now, int limit) {
        return submissions.findIdsDueForDeemedApproval(now, PageRequest.of(0, limit));
    }

    /** 승인 간주(배치) — 회신 대기 ∧ 기한 경과일 때만. 승인 시각은 회신 기한이다. */
    @Transactional
    public boolean deemApproveIfDue(Long submissionId, LocalDateTime now) {
        ContractDraftSubmission submission = submissions.findById(submissionId).orElse(null);
        if (submission == null || submission.getStatus() != ContractDraftSubmissionStatus.PENDING
                || !submission.getReviewDueAt().isBefore(now)) {
            return false;
        }
        Contract contract = contracts.findById(submission.getContractId()).orElse(null);
        if (contract == null) {
            return false;
        }
        ContractDraftApproval approval = approvals.save(ContractDraftApproval.deemed(contract.getId(),
                contract.getMarket().getId(), submission.getTitle(), submission.getReviewDueAt(),
                submission.getThreadMessageId(), DEEMED_NOTE, now));
        if (submissions.decide(submissionId, ContractDraftSubmissionStatus.DEEMED_APPROVED, now, null,
                approval.getId()) != 1) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        }
        Contract fresh = contracts.findById(contract.getId()).orElseThrow();
        history.record(fresh, ContractEventType.DRAFT_DEEMED_APPROVED, ContractActorType.SYSTEM, null, null,
                submission.getTitle(), now);
        notifier.notifyBothParties(fresh, "DRAFT_DEEMED_APPROVED");
        return true;
    }

    private ContractDraftSubmission load(Contract contract, Long submissionId) {
        return submissions.findByIdAndContractId(submissionId, contract.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
