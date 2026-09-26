package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.repository.ContractDocumentRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.service.ContractHistoryRecorder;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 계약서 생성본(제출본 PDF) 생성 — 검토 요청(커밋 이후 비동기)과 어드민 다운로드가 같은 단계를 쓴다.
 *
 * <p>두 경로 모두 <b>느린 렌더링(Chromium, 10초 안팎) 동안 트랜잭션·계약 행 잠금을 잡지 않는다.</b>
 * <ol>
 *   <li>잠그고 판정 — 이 제출본의 생성본이 이미 있는지({@link #cachedDraft}), 만들 수 있는 상태인지 보고
 *       렌더링 입력을 만든다({@link #prepare} — 템플릿이 지연 로딩하므로 트랜잭션 안이어야 한다)</li>
 *   <li>렌더링 — 트랜잭션 밖({@link #render})</li>
 *   <li>다시 잠그고 확인 — 렌더링하는 사이 다른 경로가 먼저 만들었거나({@link #cachedDraft}), 브랜드가 요청을 취소·재요청했을 수
 *       있다({@link #isCurrent}). 그대로일 때만 업로드·저장한다({@link #store})</li>
 * </ol>
 *
 * <p>업로드한 객체는 저장 트랜잭션이 롤백되면 {@link ContractDocumentStorage}가 지운다.
 */
@Component
@RequiredArgsConstructor
public class ContractDraftGenerator {
    private final ContractRepository contracts;
    private final PlatformTransactionManager transactionManager;
    private final ContractDocumentRepository documents;
    private final ContractPdfTemplate template;
    private final ContractPdfRenderer renderer;
    private final ContractDocumentStorage storage;
    private final ContractHistoryRecorder history;

    /**
     * 검토 요청이 커밋된 뒤 — {@link ContractSubmitDraftListener}가 별도 스레드에서 부른다. 실패는 호출부가 삼킨다.
     * 실패 원인은 입력이 아니라 인프라(Chromium·메모리·S3)라 이미 끝난 제출과 무관하고, 그 경우 생성본은
     * 어드민이 처음 내려받을 때 만들어진다.
     */
    public void generateOnSubmit(Long contractId) {
        Submission submission = newTransaction().execute(status -> contracts.findForAdminUpdate(contractId)
                .filter(c -> c.getStatus() == ContractStatus.REVIEW_PENDING && cachedDraft(c).isEmpty())
                .map(this::prepare)
                .orElse(null));
        if (submission == null) {
            return;
        }

        byte[] bytes = render(submission);

        newTransaction().executeWithoutResult(status -> contracts.findForAdminUpdate(contractId)
                .filter(c -> isCurrent(c, submission) && cachedDraft(c).isEmpty())
                .ifPresent(c -> store(c, bytes, ContractActorType.SYSTEM, null, null)));
    }

    /** 지금 제출본으로 만든 생성본 — 있으면 다시 만들지 않고 그대로 준다. */
    public Optional<ContractDocument> cachedDraft(Contract c) {
        return documents.findByContractIdAndDocumentType(c.getId(), ContractDocumentType.GENERATED_DRAFT)
                .filter(existing -> Objects.equals(existing.getSourceReviewRequestedAt(), c.getReviewRequestedAt()));
    }

    /** 렌더링 입력 — 계약을 읽은 트랜잭션 안에서 부른다. */
    public Submission prepare(Contract c) {
        return new Submission(c.getReviewRequestedAt(), template.render(c), c.getContractNumber());
    }

    /** 렌더링 — 트랜잭션 밖에서 부른다. 실패하면 그대로 던진다. */
    public byte[] render(Submission submission) {
        return renderer.render(submission.html(), submission.contractNumber());
    }

    /** 렌더링한 제출본이 아직 검토 대기 중인 그 제출본인지 — 아니면 만든 파일이 지금 계약과 다르다. */
    public boolean isCurrent(Contract c, Submission submission) {
        return c.getStatus() == ContractStatus.REVIEW_PENDING
                && submission.reviewRequestedAt() != null
                && submission.reviewRequestedAt().equals(c.getReviewRequestedAt());
    }

    /** 업로드·저장 — 계약 행을 잠근 트랜잭션 안에서 {@link #isCurrent}를 확인한 뒤 부른다. */
    public ContractDocument store(Contract c, byte[] bytes, ContractActorType actorType, Long actorId, String actorName) {
        LocalDateTime now = LocalDateTime.now();
        String key = storage.putGenerated(c.getId(), bytes);
        ContractDocument document = documents.findByContractIdAndDocumentType(c.getId(), ContractDocumentType.GENERATED_DRAFT)
                .orElseGet(() -> ContractDocument.builder().contract(c).documentType(ContractDocumentType.GENERATED_DRAFT).build());
        String oldKey = document.getS3Key();
        String title = c.getTitle().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        document.replace(key, "계약서_" + title + "_" + c.getContractNumber() + ".pdf", bytes.length, actorId, now,
                c.getReviewRequestedAt());
        documents.save(document);
        storage.deleteAfterCommit(oldKey);
        history.record(c, ContractEventType.CONTRACT_PDF_GENERATED, actorType, actorId, actorName,
                "제출본 기준 " + c.getReviewRequestedAt(), now);
        return document;
    }

    /**
     * 판정·저장 단계의 트랜잭션. {@code REQUIRES_NEW} — 호출 스레드에 이미 끝난 트랜잭션이 묶여 있을 수 있다
     * (커밋 직후 동기 실행). {@code READ_COMMITTED} — 잠금을 기다린 뒤 다른 경로가 방금 커밋한 생성본을 봐야 한다.
     */
    public TransactionTemplate newTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }

    /** 렌더링 입력과, 그것이 어느 제출본에서 나왔는지. */
    public record Submission(LocalDateTime reviewRequestedAt, String html, String contractNumber) {
    }
}
