package showroomz.api.common.attachment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.message.type.AttachmentStatus;
import showroomz.domain.message.type.AttachmentType;

@Getter
public class AttachmentSummary {

    @Schema(description = "첨부 ID")
    private final Long attachmentId;

    @Schema(description = "업로드 완료 검증 결과 — UPLOADED만 정상, REJECTED면 FE가 목록에서 제거하고 안내해야 함")
    private final AttachmentStatus status;

    @Schema(description = "첨부 종류")
    private final AttachmentType attachmentType;

    @Schema(description = "CloudFront URL")
    private final String fileUrl;

    @Schema(description = "원본 파일명")
    private final String originalName;

    @Schema(description = "확장자 — FE 아이콘 매핑 키(§13-8)")
    private final String extension;

    @Schema(description = "실제 용량(byte) — HeadObject 실측값")
    private final Long sizeBytes;

    @Schema(description = "영상 재생시간(초) — 표시용 참고값, 영상이 아니면 null")
    private final Integer durationSeconds;

    @Schema(description = "그리드/목록 표시 순서")
    private final Integer sortOrder;

    public AttachmentSummary(Long attachmentId, AttachmentStatus status, AttachmentType attachmentType,
                              String fileUrl, String originalName, String extension,
                              Long sizeBytes, Integer durationSeconds, Integer sortOrder) {
        this.attachmentId = attachmentId;
        this.status = status;
        this.attachmentType = attachmentType;
        this.fileUrl = fileUrl;
        this.originalName = originalName;
        this.extension = extension;
        this.sizeBytes = sizeBytes;
        this.durationSeconds = durationSeconds;
        this.sortOrder = sortOrder;
    }
}
