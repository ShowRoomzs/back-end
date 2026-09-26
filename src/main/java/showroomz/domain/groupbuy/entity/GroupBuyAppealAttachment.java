package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.groupbuy.type.GroupBuyAttachmentStatus;

import java.time.LocalDateTime;

/**
 * 소명 증빙(C9) — PNG · JPG · PDF · 10MB 이하.
 *
 * <p>presign 발급 시 PENDING 행을 먼저 만들고, 소명 제출 시 HeadObject로 실재·크기를 재검증해 UPLOADED로
 * 확정한다. 연결·소통 첨부({@code message_attachment})와 같은 흐름이다 — 새 방식을 만들지 않는다.
 */
@Entity
@Table(name = "group_buy_appeal_attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GroupBuyAppealAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_suspension_id", nullable = false)
    private GroupBuyAdminSuspension adminSuspension;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GroupBuyAttachmentStatus status;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static GroupBuyAppealAttachment pending(GroupBuyAdminSuspension suspension, Long uploaderId,
                                                   String s3Key, String originalName, String contentType,
                                                   long sizeBytes, LocalDateTime now) {
        return GroupBuyAppealAttachment.builder()
                .adminSuspension(suspension)
                .uploaderId(uploaderId)
                .s3Key(s3Key)
                .originalName(originalName)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .status(GroupBuyAttachmentStatus.PENDING)
                .createdAt(now)
                .build();
    }

    public boolean isRejected() {
        return status == GroupBuyAttachmentStatus.REJECTED;
    }

    public void markUploaded(long actualSize, LocalDateTime now) {
        this.status = GroupBuyAttachmentStatus.UPLOADED;
        this.sizeBytes = actualSize;
        this.uploadedAt = now;
    }

    public void markRejected() {
        this.status = GroupBuyAttachmentStatus.REJECTED;
    }
}
