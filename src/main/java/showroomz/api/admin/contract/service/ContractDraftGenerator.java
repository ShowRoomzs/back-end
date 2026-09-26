package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.repository.ContractDocumentRepository;
import showroomz.domain.contract.service.ContractHistoryRecorder;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractEventType;

import java.time.LocalDateTime;

/**
 * 계약서 생성본(제출본 PDF) 생성 — 검토 요청과 어드민 다운로드가 같은 로직을 쓴다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 검토 요청은 생성 실패를 삼키고 진행해야 하는데,
 * 이 빈이 트랜잭션 프록시면 안에서 난 예외가 바깥 트랜잭션을 rollback-only로 만들어 삼켜도 커밋이 깨진다.
 * 실패할 수 있는 일(렌더링·S3 업로드)을 DB 쓰기보다 먼저 끝내는 것도 같은 이유다 —
 * 리포지토리 호출은 그 자체로 트랜잭션에 참여하므로 거기서 난 예외는 삼킬 수 없다.
 *
 * <p>업로드한 객체는 바깥 트랜잭션이 롤백되면 {@link ContractDocumentStorage}가 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractDraftGenerator {
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
     * 검토 요청 직후 — 실패해도 요청은 성공시킨다. 실패 원인은 입력이 아니라 인프라(Chromium·메모리·S3)라
     * 브랜드의 제출을 막을 이유가 없다. 이 경우 생성본은 어드민이 처음 내려받을 때 만들어진다.
     */
    public void generateOnSubmit(Contract c, LocalDateTime now) {
        byte[] bytes;
        String key;
        try {
            bytes = renderer.render(template.render(c), c.getContractNumber());
            key = storage.putGenerated(c.getId(), bytes);
        } catch (RuntimeException e) {
            log.warn("Contract draft PDF generation on submit failed; admin download will retry. contractId={}",
                    c.getId(), e);
            return;
        }
        store(c, key, bytes.length, ContractActorType.SYSTEM, null, null, now);
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
