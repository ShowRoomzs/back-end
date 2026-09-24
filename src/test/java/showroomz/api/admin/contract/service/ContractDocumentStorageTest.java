package showroomz.api.admin.contract.service;

import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.global.config.properties.S3Properties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import java.net.URL;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContractDocumentStorageTest {
    S3Client s3;
    ContractDocumentStorage storage;
    @BeforeEach void setup() {
        s3 = mock(S3Client.class);
        S3Properties properties = mock(S3Properties.class);
        when(properties.getBucket()).thenReturn("contracts-test");
        storage = new ContractDocumentStorage(s3, mock(S3Presigner.class), properties);
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void sealsIntoPrivateKeyAndDeletesStagingOnlyAfterCommit() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentType("application/pdf").contentLength(100L).build());
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "%PDF-".getBytes()));
        String key = storage.sealUpload("contracts/1/uploads/2/SIGNED_PDF/abc.pdf", 1L, 100L);
        assertThat(key).startsWith("contracts/1/documents/").doesNotContain("uploads");
        verify(s3).copyObject(argThat((CopyObjectRequest r) -> r.key().equals(key)));
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(s3).deleteObject(argThat((DeleteObjectRequest r) -> r.key().contains("uploads")));
    }

    @Test void rejectsSpoofedPdfAndCleansCopyOnRollback() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentType("application/pdf").contentLength(100L).build());
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "<html".getBytes()));
        assertThatThrownBy(() -> storage.sealUpload("contracts/1/uploads/abc.pdf", 1L, 100L)).isInstanceOf(BusinessException.class);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(s3).deleteObject(argThat((DeleteObjectRequest r) -> r.key().startsWith("contracts/1/documents/")));
        verify(s3, never()).deleteObject(argThat((DeleteObjectRequest r) -> r.key().contains("uploads")));
    }

    @Test void rejectsMismatchedSizeBeforeReadingFile() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentType("application/pdf").contentLength(99L).build());
        assertThatThrownBy(() -> storage.sealUpload("contracts/1/uploads/abc.pdf", 1L, 100L)).isInstanceOf(BusinessException.class);
        verify(s3, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test void rejectsNonPdfContentType() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentType("image/png").contentLength(100L).build());
        assertThatThrownBy(() -> storage.sealUpload("contracts/1/uploads/abc.pdf", 1L, 100L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_DOCUMENT_INVALID));
    }

    @Test void missingUploadIsInvalidRequestNotServerError() {
        when(s3.copyObject(any(CopyObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThatThrownBy(() -> storage.sealUpload("contracts/1/uploads/never-put.pdf", 1L, 100L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_DOCUMENT_INVALID));
    }

    @Test void issuesFifteenMinutePdfUploadUrlUnderGivenPrefix() throws Exception {
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://upload.test/x"));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);
        storage = new ContractDocumentStorage(s3, presigner, bucket());

        var response = storage.presign("contracts/1/uploads/2/SIGNED_PDF/");

        assertThat(response.s3Key()).startsWith("contracts/1/uploads/2/SIGNED_PDF/").endsWith(".pdf");
        assertThat(response.expiresInSeconds()).isEqualTo(900);
        assertThat(response.contentType()).isEqualTo("application/pdf");
        verify(presigner).presignPutObject(argThat((PutObjectPresignRequest r) -> r.signatureDuration().equals(Duration.ofMinutes(15))
                && r.putObjectRequest().contentType().equals("application/pdf") && r.putObjectRequest().key().equals(response.s3Key())));
    }

    @Test void issuesFiveMinuteDownloadUrlWithStoredFileName() throws Exception {
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://download.test/x"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
        storage = new ContractDocumentStorage(s3, presigner, bucket());
        ContractDocument document = ContractDocument.builder().documentType(ContractDocumentType.SIGNED_PDF)
                .s3Key("contracts/1/documents/abc.pdf").originalName("CTR-20260813-034_서명완료.pdf").sizeBytes(100L).build();

        var response = storage.download(document);

        assertThat(response.expiresInSeconds()).isEqualTo(300);
        assertThat(response.fileName()).isEqualTo("CTR-20260813-034_서명완료.pdf");
        verify(presigner).presignGetObject(argThat((GetObjectPresignRequest r) -> r.signatureDuration().equals(Duration.ofMinutes(5))
                && r.getObjectRequest().key().equals("contracts/1/documents/abc.pdf")
                && r.getObjectRequest().responseContentDisposition().startsWith("attachment")
                && r.getObjectRequest().responseContentDisposition().contains("CTR-20260813-034")));
    }

    @Test void cleanupFailureAfterCommitIsLoggedNotThrown() {
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(500).build());
        storage.deleteAfterCommit("contracts/1/documents/old.pdf");
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit))
                .doesNotThrowAnyException();
        verify(s3).deleteObject(argThat((DeleteObjectRequest r) -> r.key().equals("contracts/1/documents/old.pdf")));
    }

    private S3Properties bucket() {
        S3Properties properties = mock(S3Properties.class);
        when(properties.getBucket()).thenReturn("contracts-test");
        return properties;
    }
}
