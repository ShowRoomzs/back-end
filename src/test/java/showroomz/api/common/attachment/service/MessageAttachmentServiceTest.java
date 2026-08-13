package showroomz.api.common.attachment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.common.attachment.dto.AttachmentDownloadResponse;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.type.AttachmentStatus;
import showroomz.domain.message.type.AttachmentType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadStatus;
import showroomz.global.config.properties.S3Properties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.AllowedAttachmentExtensions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessageAttachmentServiceTest {

    private static final long THREAD_ID = 1L;
    private static final long UPLOADER_ID = 7L;

    @Mock
    private MessageAttachmentRepository attachmentRepository;
    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private S3Client s3Client;
    @Mock
    private S3Properties s3Properties;

    @InjectMocks
    private MessageAttachmentService messageAttachmentService;

    private final MessageThread thread = MessageThread.builder()
            .id(THREAD_ID).status(ThreadStatus.OPEN).build();

    private static PresignRequest request(String fileName, String contentType, long sizeBytes) {
        PresignRequest request = new PresignRequest();
        request.setFileName(fileName);
        request.setContentType(contentType);
        request.setSizeBytes(sizeBytes);
        return request;
    }

    private MessageAttachment pendingAttachment(long id) {
        MessageAttachment attachment = MessageAttachment.pending(
                thread, ParticipantType.SELLER, UPLOADER_ID, AttachmentType.IMAGE,
                "uploads/message/1/uuid.jpg", "https://cdn.example/uuid.jpg",
                "shot.jpg", "jpg", "image/jpeg", 1024L);
        ReflectionTestUtils.setField(attachment, "id", id);
        return attachment;
    }

    private void givenBucket() {
        given(s3Properties.getBucket()).willReturn("showroomz-bucket");
    }

    private void givenPresignUrl() throws Exception {
        givenBucket();
        given(s3Properties.getRegion()).willReturn("ap-northeast-2");
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        given(presigned.url()).willReturn(URI.create("https://s3.example/upload").toURL());
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(presigned);
    }

    @Nested
    @DisplayName("Presign 발급 (§4-1 ①)")
    class CreatePresignedUpload {

        @Test
        @DisplayName("허용 확장자면 PENDING 행을 만들고 uploadUrl을 발급한다")
        void createsPendingAndReturnsUploadUrl() throws Exception {
            givenPresignUrl();
            given(attachmentRepository.save(any(MessageAttachment.class))).willAnswer(inv -> {
                MessageAttachment saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 501L);
                return saved;
            });

            PresignResponse response = messageAttachmentService.createPresignedUpload(
                    thread, ParticipantType.SELLER, UPLOADER_ID,
                    request("촬영본.jpg", "image/jpeg", 1024L));

            assertThat(response.getAttachmentId()).isEqualTo(501L);
            assertThat(response.getUploadUrl()).isEqualTo("https://s3.example/upload");
            assertThat(response.getExpiresInSeconds()).isEqualTo(15 * 60L);

            ArgumentCaptor<MessageAttachment> saved = ArgumentCaptor.forClass(MessageAttachment.class);
            verify(attachmentRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(AttachmentStatus.PENDING);
            assertThat(saved.getValue().getAttachmentType()).isEqualTo(AttachmentType.IMAGE);
            assertThat(saved.getValue().getS3Key()).startsWith("uploads/message/1/");
            assertThat(saved.getValue().getS3Key()).endsWith(".jpg");
        }

        @Test
        @DisplayName("실행·스크립트 확장자는 거부한다 (§2-1)")
        void blockedExtensionIsRejected() {
            assertThatThrownBy(() -> messageAttachmentService.createPresignedUpload(
                    thread, ParticipantType.SELLER, UPLOADER_ID,
                    request("malware.exe", "application/octet-stream", 100L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_EXTENSION_NOT_ALLOWED);
            verify(attachmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("개별 파일이 이미 500MB를 넘으면 즉시 거부한다")
        void oversizedFileIsRejected() {
            assertThatThrownBy(() -> messageAttachmentService.createPresignedUpload(
                    thread, ParticipantType.SELLER, UPLOADER_ID,
                    request("huge.mp4", "video/mp4", AllowedAttachmentExtensions.MAX_TOTAL_SIZE_BYTES + 1)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_SIZE_EXCEEDED);
            verify(attachmentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("업로드 완료 통지 (§4-1 ③)")
    class CompleteUpload {

        @Test
        @DisplayName("HeadObject 실측이 맞으면 UPLOADED로 전환하고 duration을 기록한다")
        void marksUploadedWhenHeadObjectMatches() {
            givenBucket();
            MessageAttachment attachment = pendingAttachment(501L);
            given(attachmentRepository.findById(501L)).willReturn(Optional.of(attachment));
            given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(
                    HeadObjectResponse.builder().contentLength(2048L).contentType("image/jpeg").build());

            AttachmentSummary summary = messageAttachmentService.completeUpload(
                    ParticipantType.SELLER, UPLOADER_ID, 501L, 58);

            assertThat(summary.getStatus()).isEqualTo(AttachmentStatus.UPLOADED);
            assertThat(summary.getSizeBytes()).isEqualTo(2048L);
            assertThat(summary.getDurationSeconds()).isEqualTo(58);
            assertThat(attachment.getStatus()).isEqualTo(AttachmentStatus.UPLOADED);
        }

        @Test
        @DisplayName("Content-Type이 확장자 분류와 어긋나면 REJECTED로 남기고 S3 객체를 지운다 (§4-2)")
        void mismatchedContentTypeIsRejectedWithoutThrowing() {
            givenBucket();
            MessageAttachment attachment = pendingAttachment(501L);
            given(attachmentRepository.findById(501L)).willReturn(Optional.of(attachment));
            given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(
                    HeadObjectResponse.builder().contentLength(100L).contentType("application/octet-stream").build());

            AttachmentSummary summary = messageAttachmentService.completeUpload(
                    ParticipantType.SELLER, UPLOADER_ID, 501L, null);

            assertThat(summary.getStatus()).isEqualTo(AttachmentStatus.REJECTED);
            assertThat(attachment.getStatus()).isEqualTo(AttachmentStatus.REJECTED);
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("실측 용량이 500MB를 넘으면 REJECTED다")
        void oversizedActualSizeIsRejected() {
            givenBucket();
            MessageAttachment attachment = pendingAttachment(501L);
            given(attachmentRepository.findById(501L)).willReturn(Optional.of(attachment));
            given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(
                    HeadObjectResponse.builder()
                            .contentLength(AllowedAttachmentExtensions.MAX_TOTAL_SIZE_BYTES + 1)
                            .contentType("image/jpeg")
                            .build());

            AttachmentSummary summary = messageAttachmentService.completeUpload(
                    ParticipantType.SELLER, UPLOADER_ID, 501L, null);

            assertThat(summary.getStatus()).isEqualTo(AttachmentStatus.REJECTED);
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("S3에 객체가 없으면 예외를 던진다 — 아직 확정할 게 없다")
        void missingObjectThrows() {
            givenBucket();
            MessageAttachment attachment = pendingAttachment(501L);
            given(attachmentRepository.findById(501L)).willReturn(Optional.of(attachment));
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willThrow(NoSuchKeyException.builder().message("missing").build());

            assertThatThrownBy(() -> messageAttachmentService.completeUpload(
                    ParticipantType.SELLER, UPLOADER_ID, 501L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_NOT_UPLOADED);
        }

        @Test
        @DisplayName("타인이 올린 첨부는 완료 통지할 수 없다")
        void otherUploaderIsDenied() {
            MessageAttachment attachment = pendingAttachment(501L);
            given(attachmentRepository.findById(501L)).willReturn(Optional.of(attachment));

            assertThatThrownBy(() -> messageAttachmentService.completeUpload(
                    ParticipantType.SELLER, 999L, 501L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_ACCESS_DENIED);
            verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
        }

        @Test
        @DisplayName("이미 처리된 첨부는 상태를 바꾸지 않고 그대로 응답한다")
        void alreadyProcessedIsIdempotent() {
            MessageAttachment attachment = pendingAttachment(501L);
            attachment.markUploaded(100L, "image/jpeg", null);
            given(attachmentRepository.findById(501L)).willReturn(Optional.of(attachment));

            AttachmentSummary summary = messageAttachmentService.completeUpload(
                    ParticipantType.SELLER, UPLOADER_ID, 501L, 99);

            assertThat(summary.getStatus()).isEqualTo(AttachmentStatus.UPLOADED);
            assertThat(summary.getDurationSeconds()).isNull();
            verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
        }
    }

    @Nested
    @DisplayName("첨부 다운로드 URL 발급 (§13-8)")
    class CreateDownloadUrl {

        /** 스레드 상대방 — 업로더(SELLER/7)와 다른 참가자. */
        private static final ParticipantType VIEWER_TYPE = ParticipantType.CREATOR;
        private static final long VIEWER_ID = 42L;

        private MessageAttachment sentAttachment(String originalName) {
            MessageAttachment attachment = uploadedAttachment(originalName);
            ReflectionTestUtils.setField(attachment, "message", mock(Message.class));
            return attachment;
        }

        private MessageAttachment uploadedAttachment(String originalName) {
            MessageAttachment attachment = MessageAttachment.pending(
                    thread, ParticipantType.SELLER, UPLOADER_ID, AttachmentType.VIDEO,
                    "uploads/message/1/uuid.mp4", "https://cdn.example/uuid.mp4",
                    originalName, "mp4", "video/mp4", 2048L);
            ReflectionTestUtils.setField(attachment, "id", 501L);
            attachment.markUploaded(31457280L, "video/mp4", 58);
            return attachment;
        }

        private void givenDownloadPresignUrl() throws Exception {
            givenBucket();
            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            given(presigned.url()).willReturn(URI.create("https://s3.example/download").toURL());
            given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(presigned);
        }

        @Test
        @DisplayName("전송된 첨부는 상대방도 받을 수 있고, 원본 파일명이 서명에 실린다")
        void counterpartCanDownloadSentAttachment() throws Exception {
            givenDownloadPresignUrl();

            AttachmentDownloadResponse response = messageAttachmentService.createDownloadUrl(
                    sentAttachment("촬영본.mp4"), VIEWER_TYPE, VIEWER_ID);

            assertThat(response.getAttachmentId()).isEqualTo(501L);
            assertThat(response.getDownloadUrl()).isEqualTo("https://s3.example/download");
            assertThat(response.getOriginalName()).isEqualTo("촬영본.mp4");
            assertThat(response.getSizeBytes()).isEqualTo(31457280L);
            assertThat(response.getExpiresInSeconds()).isEqualTo(5 * 60L);

            ArgumentCaptor<GetObjectPresignRequest> captor =
                    ArgumentCaptor.forClass(GetObjectPresignRequest.class);
            verify(s3Presigner).presignGetObject(captor.capture());
            GetObjectRequest signed = captor.getValue().getObjectRequest();
            assertThat(signed.key()).isEqualTo("uploads/message/1/uuid.mp4");
            // 미리보기가 아니라 "저장" — 한글 파일명은 RFC 5987 형식으로 인코딩돼야 한다.
            assertThat(signed.responseContentDisposition())
                    .startsWith("attachment;")
                    .contains("filename*=UTF-8''");
        }

        @Test
        @DisplayName("업로드가 끝나지 않은 첨부는 받을 수 없다")
        void notUploadedIsRejected() {
            MessageAttachment pending = pendingAttachment(501L);

            assertThatThrownBy(() -> messageAttachmentService.createDownloadUrl(
                    pending, ParticipantType.SELLER, UPLOADER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_NOT_UPLOADED);
            verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
        }

        @Test
        @DisplayName("아직 전송되지 않은 첨부는 같은 스레드 참가자라도 받을 수 없다")
        void unsentAttachmentIsHiddenFromCounterpart() {
            MessageAttachment unsent = uploadedAttachment("작업중.mp4");

            assertThatThrownBy(() -> messageAttachmentService.createDownloadUrl(
                    unsent, VIEWER_TYPE, VIEWER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_ACCESS_DENIED);
            verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
        }

        @Test
        @DisplayName("아직 전송되지 않은 첨부라도 올린 본인은 받을 수 있다")
        void uploaderCanDownloadOwnUnsentAttachment() throws Exception {
            givenDownloadPresignUrl();

            AttachmentDownloadResponse response = messageAttachmentService.createDownloadUrl(
                    uploadedAttachment("작업중.mp4"), ParticipantType.SELLER, UPLOADER_ID);

            assertThat(response.getDownloadUrl()).isEqualTo("https://s3.example/download");
        }
    }

    @Nested
    @DisplayName("고아 첨부 정리 (§4-6)")
    class PurgeOrphan {

        @Test
        @DisplayName("후보가 없으면 커서를 유지한 채 0을 반환한다")
        void emptyChunk() {
            LocalDateTime threshold = LocalDateTime.now().minusDays(1);
            given(attachmentRepository.findOrphanCandidates(
                    eq(AttachmentStatus.PENDING), eq(threshold), eq(0L), any(Pageable.class)))
                    .willReturn(List.of());

            MessageAttachmentService.PurgeChunk chunk = messageAttachmentService.purgeOrphanChunk(
                    AttachmentStatus.PENDING, threshold, 0L, 100);

            assertThat(chunk.scanned()).isZero();
            assertThat(chunk.deleted()).isZero();
            assertThat(chunk.lastId()).isEqualTo(0L);
        }

        @Test
        @DisplayName("S3 삭제에 성공한 행만 DB에서 지운다 — 실패하면 다음 회차에 재시도한다")
        void deletesOnlyWhenS3Succeeds() {
            givenBucket();
            LocalDateTime threshold = LocalDateTime.now().minusDays(1);
            MessageAttachment ok = pendingAttachment(10L);
            MessageAttachment fail = pendingAttachment(11L);
            given(attachmentRepository.findOrphanCandidates(
                    eq(AttachmentStatus.PENDING), eq(threshold), eq(0L), any(Pageable.class)))
                    .willReturn(List.of(ok, fail));

            AtomicInteger calls = new AtomicInteger();
            doAnswer(inv -> {
                if (calls.getAndIncrement() == 0) {
                    return null;
                }
                throw new RuntimeException("s3 down");
            }).when(s3Client).deleteObject(any(DeleteObjectRequest.class));

            MessageAttachmentService.PurgeChunk chunk = messageAttachmentService.purgeOrphanChunk(
                    AttachmentStatus.PENDING, threshold, 0L, 100);

            assertThat(chunk.scanned()).isEqualTo(2);
            assertThat(chunk.deleted()).isEqualTo(1);
            assertThat(chunk.lastId()).isEqualTo(11L);
            verify(attachmentRepository).delete(ok);
            verify(attachmentRepository, never()).delete(fail);
        }
    }
}
