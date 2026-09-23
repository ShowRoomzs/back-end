package showroomz.api.admin.contract.service;

import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import showroomz.global.config.properties.S3Properties;
import showroomz.global.error.exception.BusinessException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
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
}
