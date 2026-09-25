package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "소명 증빙 presign 응답 — uploadUrl로 PUT한 뒤 소명 제출에 attachmentId를 싣는다")
public record GroupBuyAppealAttachmentPresignResponse(
        Long attachmentId,
        @Schema(description = "S3 PUT URL — 요청과 같은 Content-Type으로 올려야 한다") String uploadUrl,
        String contentType,
        LocalDateTime expiresAt
) {
}
