package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.domain.contract.entity.*;
import showroomz.domain.contract.repository.ContractDocumentRepository;
import showroomz.domain.contract.service.ContractHistoryRecorder;
import showroomz.domain.contract.type.*;
import showroomz.global.error.exception.*;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContractDocumentService {
    private final AdminContractAccess access;
    private final ContractDocumentRepository documents;
    private final ContractDocumentStorage storage;
    private final ContractHistoryRecorder history;

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public PresignResponse presign(Long id, Long operator, PresignRequest request) {
        access.operatorName(operator);
        writable(access.lock(id), request.documentType());
        validateName(request.fileName());
        if (!"application/pdf".equals(request.contentType())) invalid();
        return storage.presign(uploadPrefix(id, operator, request.documentType()));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public DownloadResponse register(Long id, Long operator, RegisterDocumentRequest request) {
        String actor = access.operatorName(operator);
        Contract c = access.lock(id);
        writable(c, request.documentType());
        validateName(request.fileName());
        String prefix = uploadPrefix(id, operator, request.documentType());
        if (request.s3Key() == null || !request.s3Key().startsWith(prefix)
                || !request.s3Key().substring(prefix.length()).matches("[a-f0-9-]{36}\\.pdf")
                || request.sizeBytes() == null || request.sizeBytes() < 5) invalid();
        String key = storage.sealUpload(request.s3Key(), id, request.sizeBytes());
        ContractDocument document = documents.findByContractIdAndDocumentType(id, request.documentType())
                .orElseGet(() -> ContractDocument.builder().contract(c).documentType(request.documentType()).build());
        String old = document.getS3Key();
        LocalDateTime now = LocalDateTime.now();
        document.replace(key, c.getContractNumber() + "_" + request.fileName(), request.sizeBytes(), operator, now, null);
        documents.save(document);
        storage.deleteAfterCommit(old);
        history.record(c, ContractEventType.DOCUMENT_UPLOADED, ContractActorType.ADMIN, operator, actor,
                request.documentType() + " · " + (old == null ? "업로드" : "교체") + " · " + request.fileName(), now);
        return storage.download(document);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void delete(Long id, Long operator, ContractDocumentType type) {
        String actor = access.operatorName(operator);
        Contract c = access.lock(id);
        writable(c, type);
        ContractDocument d = find(id, type);
        documents.delete(d);
        storage.deleteAfterCommit(d.getS3Key());
        history.record(c, ContractEventType.DOCUMENT_DELETED, ContractActorType.ADMIN, operator, actor,
                type + " · " + d.getOriginalName(), LocalDateTime.now());
    }

    public DownloadResponse download(Long id, ContractDocumentType type) {
        Contract c = access.read(id);
        ContractDocument d = find(id, type);
        if (type == ContractDocumentType.GENERATED_DRAFT
                && !java.util.Objects.equals(d.getSourceReviewRequestedAt(), c.getReviewRequestedAt())) {
            throw new BusinessException(ErrorCode.CONTRACT_DOCUMENT_NOT_FOUND);
        }
        return storage.download(d);
    }

    private ContractDocument find(Long id, ContractDocumentType type) {
        return documents.findByContractIdAndDocumentType(id, type)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_DOCUMENT_NOT_FOUND));
    }
    private void writable(Contract c, ContractDocumentType type) {
        if (c.getStatus() == ContractStatus.CONCLUDED) throw new BusinessException(ErrorCode.CONTRACT_DOCUMENT_LOCKED);
        if (c.getStatus() != ContractStatus.CONCLUSION_PENDING) throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        if (type == null || type == ContractDocumentType.GENERATED_DRAFT) invalid();
    }
    private String uploadPrefix(Long id, Long operator, ContractDocumentType type) {
        return "contracts/" + id + "/uploads/" + operator + "/" + type + "/";
    }
    private void validateName(String name) {
        if (name == null || name.length() > 200 || !name.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl)) invalid();
    }
    private static void invalid() { throw new BusinessException(ErrorCode.CONTRACT_DOCUMENT_INVALID); }
}
