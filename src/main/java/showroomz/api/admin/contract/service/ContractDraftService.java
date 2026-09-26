package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.contract.dto.AdminContractDto.DownloadResponse;
import showroomz.domain.contract.entity.*;
import showroomz.domain.contract.repository.ContractDocumentRepository;
import showroomz.domain.contract.type.*;
import showroomz.global.error.exception.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContractDraftService {
    private final AdminContractAccess access;
    private final ContractDocumentRepository documents;
    private final ContractDraftGenerator generator;
    private final ContractDocumentStorage storage;

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public DownloadResponse download(Long id, Long operator) {
        String actor = access.operatorName(operator);
        // Lock also serializes cache misses with withdrawal, resubmission and approval.
        Contract c = access.lock(id);
        var existing = documents.findByContractIdAndDocumentType(id, ContractDocumentType.GENERATED_DRAFT);
        if (existing.isPresent() && Objects.equals(existing.get().getSourceReviewRequestedAt(), c.getReviewRequestedAt())) {
            return storage.download(existing.get());
        }
        // Normally generated on review request; this is the fallback when that attempt failed.
        // After sending, generating a different source document would invalidate the evidence chain.
        if (c.getStatus() != ContractStatus.REVIEW_PENDING) throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        return storage.download(generator.generate(c, operator, actor, LocalDateTime.now()));
    }
}
