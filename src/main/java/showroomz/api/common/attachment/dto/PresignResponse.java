package showroomz.api.common.attachment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class PresignResponse {

    @Schema(description = "첨부 ID — complete 통지, 메시지 전송 시 attachmentIds에 사용", example = "501")
    private final Long attachmentId;

    @Schema(description = "S3 presigned PUT URL — 이 URL로 파일 바이트를 직접 PUT한다(서버 경유 없음)",
            example = "https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/message/55/uuid.mp4?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=900&...")
    private final String uploadUrl;

    @Schema(description = "PUT 요청 시 반드시 동일하게 실어야 하는 Content-Type(서명에 포함된 값과 다르면 SignatureDoesNotMatch)",
            example = "video/mp4")
    private final String requiredContentType;

    @Schema(description = "presigned URL 만료까지 남은 시간(초)", example = "900")
    private final long expiresInSeconds;

    public PresignResponse(Long attachmentId, String uploadUrl, String requiredContentType, long expiresInSeconds) {
        this.attachmentId = attachmentId;
        this.uploadUrl = uploadUrl;
        this.requiredContentType = requiredContentType;
        this.expiresInSeconds = expiresInSeconds;
    }
}
