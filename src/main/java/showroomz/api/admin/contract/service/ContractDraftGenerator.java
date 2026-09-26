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

/**
 * 계약서 생성본(제출본 PDF) 생성 — 검토 요청(커밋 이후 비동기)과 어드민 다운로드가 같은 로직을 쓴다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 어드민 다운로드는 호출부 트랜잭션에 참여하고,
 * 검토 요청 경로({@link #generateOnSubmit})는 렌더링을 트랜잭션 밖에 두려고 트랜잭션을 직접 나눈다.
 *
 * <p>업로드한 객체는 바깥 트랜잭션이 롤백되면 {@link ContractDocumentStorage}가 지운다.
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

    /** 어드민 다운로드의 캐시 미스 — 실패하면 그대로 던진다. */
    public ContractDocument generate(Contract c, Long operatorId, String operatorName, LocalDateTime now) {
        byte[] bytes = renderer.render(template.render(c), c.getContractNumber());
        String key = storage.putGenerated(c.getId(), bytes);
        return store(c, key, bytes.length, ContractActorType.ADMIN, operatorId, operatorName, now);
    }

    /**
     * 검토 요청이 커밋된 뒤 — {@link ContractSubmitDraftListener}가 별도 스레드에서 부른다. 실패는 호출부가 삼킨다.
     * 실패 원인은 입력이 아니라 인프라(Chromium·메모리·S3)라 이미 끝난 제출과 무관하고, 그 경우 생성본은
     * 어드민이 처음 내려받을 때 만들어진다.
     *
     * <p>세 단계로 나눠 <b>느린 렌더링 동안 트랜잭션·계약 행 잠금을 잡지 않는다.</b>
     * <ol>
     *   <li>읽기 — 아직 검토 대기인지, 이 제출본이 이미 있는지 보고 HTML을 만든다(지연 로딩이 필요하다)</li>
     *   <li>렌더링 — 트랜잭션 밖</li>
     *   <li>쓰기 — 계약 행을 잠그고 다시 확인한 뒤 업로드·저장한다. 렌더링하는 사이 브랜드가 요청을 취소했거나,
     *       어드민 다운로드가 먼저 만들었을 수 있다</li>
     * </ol>
     * 호출 스레드에 이미 끝난 트랜잭션이 묶여 있을 수 있어(커밋 직후 동기 실행) 두 단계 모두 {@code REQUIRES_NEW}다.
     */
    public void generateOnSubmit(Long contractId) {
        Submission submission = newTransaction().execute(status -> contracts.findForAdminUpdate(contractId)
                .filter(c -> needsDraft(c, c.getReviewRequestedAt()))
                .map(c -> new Submission(c.getReviewRequestedAt(), template.render(c), c.getContractNumber()))
                .orElse(null));
        if (submission == null) {
            return;
        }

        byte[] bytes = renderer.render(submission.html(), submission.contractNumber());

        newTransaction().executeWithoutResult(status -> contracts.findForAdminUpdate(contractId)
                .filter(c -> needsDraft(c, submission.reviewRequestedAt()))
                .ifPresent(c -> store(c, storage.putGenerated(c.getId(), bytes), bytes.length,
                        ContractActorType.SYSTEM, null, null, LocalDateTime.now())));
    }

    /** 그 제출본이 아직 검토 대기이고, 같은 제출본으로 만든 생성본이 없을 때만 만든다(어드민 다운로드와 같은 판정). */
    private boolean needsDraft(Contract c, LocalDateTime reviewRequestedAt) {
        if (c.getStatus() != ContractStatus.REVIEW_PENDING
                || reviewRequestedAt == null
                || !reviewRequestedAt.equals(c.getReviewRequestedAt())) {
            return false;
        }
        return documents.findByContractIdAndDocumentType(c.getId(), ContractDocumentType.GENERATED_DRAFT)
                .map(existing -> !reviewRequestedAt.equals(existing.getSourceReviewRequestedAt()))
                .orElse(true);
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private record Submission(LocalDateTime reviewRequestedAt, String html, String contractNumber) {
    }

    private ContractDocument store(Contract c, String key, long size, ContractActorType actorType,
                                   Long actorId, String actorName, LocalDateTime now) {
        ContractDocument document = documents.findByContractIdAndDocumentType(c.getId(), ContractDocumentType.GENERATED_DRAFT)
                .orElseGet(() -> ContractDocument.builder().contract(c).documentType(ContractDocumentType.GENERATED_DRAFT).build());
        String oldKey = document.getS3Key();
        String title = c.getTitle().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        document.replace(key, "계약서_" + title + "_" + c.getContractNumber() + ".pdf", size, actorId, now, c.getReviewRequestedAt());
        documents.save(document);
        storage.deleteAfterCommit(oldKey);
        history.record(c, ContractEventType.CONTRACT_PDF_GENERATED, actorType, actorId, actorName,
                "제출본 기준 " + c.getReviewRequestedAt(), now);
        return document;
    }
}
