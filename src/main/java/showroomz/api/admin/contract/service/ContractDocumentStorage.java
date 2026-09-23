package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.global.config.properties.S3Properties;
import showroomz.global.error.exception.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractDocumentStorage {
    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3Properties properties;

    public PresignResponse presign(String prefix) {
        String key = prefix + UUID.randomUUID() + ".pdf";
        var request = PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType("application/pdf").build();
        String url = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15)).putObjectRequest(request).build()).url().toString();
        return new PresignResponse(key, url, "application/pdf", 900);
    }

    /** Copy into a key for which no PUT URL was issued, so an old upload URL cannot alter concluded evidence. */
    public String sealUpload(String source, Long contractId, long expectedSize) {
        String key = finalKey(contractId);
        try {
            s3.copyObject(CopyObjectRequest.builder().bucket(properties.getBucket()).key(key)
                    .copySource(properties.getBucket() + "/" + source).build());
            cleanupOnRollback(key);
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
            if (!Long.valueOf(expectedSize).equals(head.contentLength()) || !"application/pdf".equals(head.contentType())) invalid();
            byte[] header = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.getBucket()).key(key)
                    .range("bytes=0-4").build()).asByteArray();
            if (!"%PDF-".equals(new String(header, StandardCharsets.US_ASCII))) invalid();
            deleteAfterCommit(source);
            return key;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) invalid();
            throw e;
        }
    }

    public String putGenerated(Long contractId, byte[] bytes) {
        String key = finalKey(contractId);
        s3.putObject(PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType("application/pdf").build(),
                RequestBody.fromBytes(bytes));
        cleanupOnRollback(key);
        return key;
    }

    public DownloadResponse download(ContractDocument document) {
        var request = GetObjectRequest.builder().bucket(properties.getBucket()).key(document.getS3Key())
                .responseContentDisposition(ContentDisposition.attachment().filename(document.getOriginalName(), StandardCharsets.UTF_8).build().toString()).build();
        String url = presigner.presignGetObject(GetObjectPresignRequest.builder().getObjectRequest(request)
                .signatureDuration(Duration.ofMinutes(5)).build()).url().toString();
        return new DownloadResponse(url, document.getOriginalName(), document.getSizeBytes(), 300, document.getSourceReviewRequestedAt());
    }

    public void deleteAfterCommit(String key) {
        if (key == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteSafely(key); }
        });
    }

    private void cleanupOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) deleteSafely(key);
            }
        });
    }
    private void deleteSafely(String key) {
        try { s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build()); }
        catch (RuntimeException e) { log.warn("Contract object cleanup failed: {}", key, e); }
    }
    private String finalKey(Long id) { return "contracts/" + id + "/documents/" + UUID.randomUUID() + ".pdf"; }
    private static void invalid() { throw new BusinessException(ErrorCode.CONTRACT_DOCUMENT_INVALID); }
}
