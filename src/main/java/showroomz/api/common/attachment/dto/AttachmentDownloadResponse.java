package showroomz.api.common.attachment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class AttachmentDownloadResponse {

    @Schema(description = "첨부 ID", example = "501")
    private final Long attachmentId;

    @Schema(description = "다운로드용 presigned GET URL — 원본 파일명으로 저장되도록 Content-Disposition이 " +
            "미리 서명돼 있다. 만료가 짧으므로 받아온 즉시 사용해야 한다",
            example = "https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/message/55/uuid.mp4?X-Amz-Algorithm=...")
    private final String downloadUrl;

    @Schema(description = "브라우저에 저장될 원본 파일명", example = "촬영본.mp4")
    private final String originalName;

    @Schema(description = "파일 용량(byte)", example = "31457280")
    private final Long sizeBytes;

    @Schema(description = "downloadUrl 유효 시간(초)", example = "300")
    private final long expiresInSeconds;

    public AttachmentDownloadResponse(Long attachmentId, String downloadUrl, String originalName,
                                       Long sizeBytes, long expiresInSeconds) {
        this.attachmentId = attachmentId;
        this.downloadUrl = downloadUrl;
        this.originalName = originalName;
        this.sizeBytes = sizeBytes;
        this.expiresInSeconds = expiresInSeconds;
    }
}
