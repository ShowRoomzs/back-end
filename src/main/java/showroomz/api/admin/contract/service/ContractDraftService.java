package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.contract.dto.AdminContractDto.DownloadResponse;
import showroomz.domain.contract.entity.*;
import showroomz.domain.contract.repository.ContractDocumentRepository;
import showroomz.domain.contract.service.ContractHistoryRecorder;
import showroomz.domain.contract.type.*;
import showroomz.global.error.exception.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContractDraftService {
    private final AdminContractAccess access;
    private final ContractDocumentRepository documents;
    private final ContractPdfTemplate template;
    private final ContractPdfRenderer renderer;
    private final ContractDocumentStorage storage;
    private final ContractHistoryRecorder history;

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public DownloadResponse download(Long id, Long operator) {
        String actor = access.operatorName(operator);
        // Lock also serializes cache misses with withdrawal, resubmission and approval.
        Contract c = access.lock(id);
        var existing = documents.findByContractIdAndDocumentType(id, ContractDocumentType.GENERATED_DRAFT);
        if (existing.isPresent() && Objects.equals(existing.get().getSourceReviewRequestedAt(), c.getReviewRequestedAt())) {
            return storage.download(existing.get());
        }
        // After sending, generating a different source document would invalidate the evidence chain.
        if (c.getStatus() != ContractStatus.REVIEW_PENDING) throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        byte[] bytes = renderer.render(template.render(c), c.getContractNumber());
        String key = storage.putGenerated(id, bytes);
        ContractDocument document = existing.orElseGet(() -> ContractDocument.builder().contract(c).documentType(ContractDocumentType.GENERATED_DRAFT).build());
        String oldKey = document.getS3Key();
        LocalDateTime now = LocalDateTime.now();
        String title = c.getTitle().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        document.replace(key, "계약서_" + title + "_" + c.getContractNumber() + ".pdf", bytes.length, operator, now, c.getReviewRequestedAt());
        documents.save(document);
        storage.deleteAfterCommit(oldKey);
        history.record(c, ContractEventType.CONTRACT_PDF_GENERATED, ContractActorType.ADMIN, operator, actor,
                "제출본 기준 " + c.getReviewRequestedAt(), now);
        return storage.download(document);
    }
}
