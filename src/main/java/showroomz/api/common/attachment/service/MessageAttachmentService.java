package showroomz.api.common.attachment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.type.AttachmentStatus;
import showroomz.domain.message.type.AttachmentType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.global.config.properties.S3Properties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.AllowedAttachmentExtensions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * §4 — S3 Presigned URL 직접 업로드. 파트너센터·쇼룸 스튜디오 양쪽이 공유한다(스레드 종류와 무관하게
 * 첨부 로직은 동일, §14-5). 각 role의 Thread 서비스가 "이 스레드가 내 것인지"를 먼저 검증한 뒤
 * 이 서비스에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageAttachmentService {

    private static final Duration PRESIGN_EXPIRY = Duration.ofMinutes(15);

    private final MessageAttachmentRepository attachmentRepository;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    /** §4-1 ① — 확장자·개별 파일 크기를 먼저 검증하고 PENDING 행을 선(先)생성한다. */
    @Transactional
    public PresignResponse createPresignedUpload(MessageThread thread, ParticipantType uploaderType,
                                                  Long uploaderId, PresignRequest request) {
        AttachmentType type = AllowedAttachmentExtensions.resolve(request.getFileName());
        if (type == null) {
            throw new BusinessException(ErrorCode.ATTACHMENT_EXTENSION_NOT_ALLOWED);
        }
        // 개별 파일이 이미 500MB를 넘으면 §4-5의 합계 검증을 통과할 수 없으므로 여기서 즉시 거부한다.
        if (request.getSizeBytes() > AllowedAttachmentExtensions.MAX_TOTAL_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.ATTACHMENT_SIZE_EXCEEDED);
        }

        String extension = AllowedAttachmentExtensions.extensionOf(request.getFileName());
        String s3Key = "uploads/message/" + thread.getId() + "/" + UUID.randomUUID() + "." + extension;
        String fileUrl = buildFileUrl(s3Key);

        MessageAttachment attachment = attachmentRepository.save(MessageAttachment.pending(
                thread, uploaderType, uploaderId, type, s3Key, fileUrl,
                request.getFileName(), extension, request.getContentType(), request.getSizeBytes()));

        String uploadUrl = presign(s3Key, request.getContentType());
        return new PresignResponse(attachment.getId(), uploadUrl, request.getContentType(), PRESIGN_EXPIRY.toSeconds());
    }

    /**
     * §4-1 ③ — HeadObject로 실제 업로드 크기·Content-Type을 재확인한다. 검증에 실패해도 예외를 던지지
     * 않고 REJECTED 상태로 응답한다 — 여기서 예외를 던지면 같은 트랜잭션의 markRejected()가 롤백돼
     * 버려야 할 PENDING 행이 그대로 남기 때문이다(REJECTED로 남겨야 감사·정리 대상이 된다, §4-6).
     */
    @Transactional
    public AttachmentSummary completeUpload(ParticipantType uploaderType, Long uploaderId,
                                             Long attachmentId, Integer durationSeconds) {
        MessageAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_ACCESS_DENIED));

        // 업로더 본인 소유인지만 확인하면 충분하다 — 첨부는 애초에 자신이 참가자인 스레드에서만
        // presign 발급이 가능했으므로(createPresignedUpload), 소유자 일치가 곧 스레드 접근 권한이다.
        if (attachment.getUploaderType() != uploaderType || !attachment.getUploaderId().equals(uploaderId)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_ACCESS_DENIED);
        }
        if (!attachment.isPending()) {
            return toSummary(attachment);
        }

        HeadObjectResponse head;
        try {
            head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(attachment.getS3Key())
                    .build());
        } catch (NoSuchKeyException e) {
            // 업로드 자체가 안 된 상태 — 아직 아무 것도 확정된 게 없으므로 예외로 롤백해도 무방하다.
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_UPLOADED);
        }

        boolean sizeOk = head.contentLength() != null && head.contentLength() <= AllowedAttachmentExtensions.MAX_TOTAL_SIZE_BYTES;
        boolean typeOk = AllowedAttachmentExtensions.isContentTypeConsistent(attachment.getAttachmentType(), head.contentType());

        if (!sizeOk || !typeOk) {
            deleteObjectSafely(attachment.getS3Key());
            attachment.markRejected();
            return toSummary(attachment);
        }

        attachment.markUploaded(head.contentLength(), head.contentType(), durationSeconds);
        return toSummary(attachment);
    }

    public AttachmentSummary toSummary(MessageAttachment attachment) {
        return new AttachmentSummary(
                attachment.getId(), attachment.getStatus(), attachment.getAttachmentType(),
                attachment.getFileUrl(), attachment.getOriginalName(), attachment.getExtension(),
                attachment.getSizeBytes(), attachment.getDurationSeconds(), attachment.getSortOrder());
    }

    private String presign(String key, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_EXPIRY)
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return presigned.url().toString();
    }

    /**
     * §4-6 고아 정리 — 한 청크를 처리하고 다음 커서를 돌려준다. 스케줄러가 이 메서드를 반복 호출한다.
     *
     * <p><b>S3 객체를 먼저 지우고 성공했을 때만 행을 지운다.</b> 순서를 뒤집거나 실패해도 행을 지우면
     * S3 고아가 영구히 남아, 정확히 이 스케줄러가 존재하는 이유가 사라진다. 실패한 행은 그대로 두고
     * 다음 회차에 다시 시도한다.
     *
     * <p>청크마다 트랜잭션을 끊기 위해 스케줄러가 아니라 이 빈에 둔다 — 스케줄러 안에서 자기 자신을
     * 호출하면 프록시를 타지 않아 @Transactional이 걸리지 않는다.
     */
    @Transactional
    public PurgeChunk purgeOrphanChunk(AttachmentStatus status, LocalDateTime threshold,
                                        Long afterId, int chunkSize) {
        List<MessageAttachment> candidates = attachmentRepository.findOrphanCandidates(
                status, threshold, afterId, Pageable.ofSize(chunkSize));
        if (candidates.isEmpty()) {
            return new PurgeChunk(0, 0, afterId);
        }

        int deleted = 0;
        for (MessageAttachment attachment : candidates) {
            if (!deleteObject(attachment.getS3Key())) {
                continue;
            }
            attachmentRepository.delete(attachment);
            deleted++;
        }
        Long lastId = candidates.get(candidates.size() - 1).getId();
        return new PurgeChunk(candidates.size(), deleted, lastId);
    }

    /** scanned가 chunkSize보다 작으면 마지막 청크다. lastId는 다음 호출의 커서. */
    public record PurgeChunk(int scanned, int deleted, Long lastId) {
    }

    private void deleteObjectSafely(String key) {
        // 삭제 실패해도 응답 자체를 막지 않는다 — 고아 정리 스케줄러(§4-6)가 최종적으로 정리한다.
        deleteObject(key);
    }

    /** 삭제 성공 여부를 반환한다 — 정리 스케줄러는 실패 시 행을 남겨야 하므로 결과를 알아야 한다. */
    private boolean deleteObject(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("첨부 객체 삭제 실패 - key: {}", key, e);
            return false;
        }
    }

    private String buildFileUrl(String key) {
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20")
                .replaceAll("%2F", "/");
        if (s3Properties.getCloudFrontDomain() != null && !s3Properties.getCloudFrontDomain().isEmpty()) {
            return "https://" + s3Properties.getCloudFrontDomain() + "/" + encodedKey;
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                s3Properties.getBucket(), s3Properties.getRegion(), encodedKey);
    }
}
