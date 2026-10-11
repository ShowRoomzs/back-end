package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import showroomz.domain.settlement.port.SettlementTaxDocumentStorage;
import showroomz.global.config.properties.S3Properties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

/** 증빙 파일의 S3 입출력 — 계약서 문서({@code ContractDocumentStorage})와 같은 방식(private · 롤백 시 정리 · 서버 스트림). */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3SettlementTaxDocumentStorage implements SettlementTaxDocumentStorage {

    private final S3Client s3;
    private final S3Properties properties;

    @Override
    public String put(Long settlementId, Long documentId, byte[] bytes) {
        String key = "settlements/" + settlementId + "/tax-document/" + documentId + "-" + UUID.randomUUID() + ".pdf";
        s3.putObject(PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType(PDF).build(),
                RequestBody.fromBytes(bytes));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        deleteSafely(key);
                    }
                }
            });
        }
        return key;
    }

    @Override
    public byte[] read(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build())
                .asByteArray();
    }

    private void deleteSafely(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
        } catch (RuntimeException e) {
            log.warn("정산 증빙 파일 정리 실패: {}", key, e);
        }
    }
}
