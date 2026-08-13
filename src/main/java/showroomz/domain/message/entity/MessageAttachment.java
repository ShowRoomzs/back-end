package showroomz.domain.message.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.message.type.AttachmentStatus;
import showroomz.domain.message.type.AttachmentType;
import showroomz.domain.message.type.ParticipantType;

/**
 * §4 — S3 Presigned URL 직접 업로드. presign 발급 시점엔 아직 메시지가 없으므로 MESSAGE는 NULL로
 * 선(先)생성되고(§4-1 ①), 메시지 전송 시 조건부 UPDATE로만 연결된다(§4-5).
 */
@Entity
@Table(name = "message_attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MessageAttachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private Message message;

    /** 소유·권한 검증용(메시지 연결 전에도 필요) — 전송 시 요청 threadId와 일치 검증(§4-5). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private MessageThread thread;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploader_type", nullable = false, length = 20)
    private ParticipantType uploaderType;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttachmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false, length = 20)
    private AttachmentType attachmentType;

    /** HeadObject 조회·삭제에 필요 — URL에서 역파싱하지 않고 별도 보관. */
    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Column(name = "file_url", nullable = false, length = 1024)
    private String fileUrl;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "extension", nullable = false, length = 16)
    private String extension;

    @Column(name = "content_type", length = 128)
    private String contentType;

    /** presign 시점엔 클라이언트 선언값, complete 시점에 HeadObject 실측값으로 덮어쓴다. */
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "sort_order")
    private Integer sortOrder;

    public static MessageAttachment pending(MessageThread thread, ParticipantType uploaderType, Long uploaderId,
                                             AttachmentType attachmentType, String s3Key, String fileUrl,
                                             String originalName, String extension, String contentType,
                                             long declaredSizeBytes) {
        return MessageAttachment.builder()
                .thread(thread)
                .uploaderType(uploaderType)
                .uploaderId(uploaderId)
                .status(AttachmentStatus.PENDING)
                .attachmentType(attachmentType)
                .s3Key(s3Key)
                .fileUrl(fileUrl)
                .originalName(originalName)
                .extension(extension)
                .contentType(contentType)
                .sizeBytes(declaredSizeBytes)
                .build();
    }

    /** §4-1 ③ 완료 통지 — HeadObject 실측값으로 갱신 후 UPLOADED로 전환. */
    public void markUploaded(long actualSizeBytes, String actualContentType, Integer durationSeconds) {
        this.status = AttachmentStatus.UPLOADED;
        this.sizeBytes = actualSizeBytes;
        this.contentType = actualContentType;
        this.durationSeconds = durationSeconds;
    }

    /** §4-2 — 선언값과 실측이 어긋난 경우. S3 객체 삭제는 서비스 레이어 책임(엔티티는 상태만 갖는다). */
    public void markRejected() {
        this.status = AttachmentStatus.REJECTED;
    }

    public boolean isPending() {
        return this.status == AttachmentStatus.PENDING;
    }

    public boolean isUploaded() {
        return this.status == AttachmentStatus.UPLOADED;
    }

    /** 메시지에 연결되기 전(§4-5)에는 아직 상대방에게 보이지 않은 첨부다 — 열람 권한 판정에 쓴다. */
    public boolean isSent() {
        return this.message != null;
    }

    public boolean isUploadedBy(ParticipantType type, Long id) {
        return this.uploaderType == type && this.uploaderId.equals(id);
    }
}
