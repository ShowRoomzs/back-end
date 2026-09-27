package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "소명 증빙 presign 응답 — uploadUrl로 PUT한 뒤 소명 제출에 attachmentId를 싣는다")
public record GroupBuyAppealAttachmentPresignResponse(
        @Schema(description = "첨부 id — 소명 제출의 attachmentIds에 싣는다", example = "31") Long attachmentId,
        @Schema(description = "S3 presigned PUT URL — 요청과 같은 Content-Type 헤더로 파일 본문을 PUT한다") String uploadUrl,
        @Schema(description = "발급 요청의 MIME 타입 — PUT 요청의 Content-Type 값", example = "application/pdf") String contentType,
        @Schema(description = "uploadUrl 만료 시각 — 발급 후 15분") LocalDateTime expiresAt
) {
}
