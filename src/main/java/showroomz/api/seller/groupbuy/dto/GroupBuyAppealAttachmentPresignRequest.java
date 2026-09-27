package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 소명 증빙 업로드 URL 발급(C9) — PNG · JPG · PDF · 10MB 이하. */
@Schema(description = "소명 증빙 업로드 URL 발급 요청 — PNG · JPG · PDF · 10MB 이하. 발급 횟수 제한은 없다(개수 상한 5개는 소명 제출 때만 본다)")
public record GroupBuyAppealAttachmentPresignRequest(

        @Schema(description = "원본 파일명 — 필수 · 255자. 운영자가 내려받을 때 이 이름으로 받는다", example = "시험성적서.pdf")
        @NotBlank @Size(max = 255)
        String fileName,

        @Schema(description = "MIME 타입 — image/png · image/jpeg · application/pdf 중 하나. S3 PUT 때 같은 Content-Type을 보내야 한다",
                example = "application/pdf", allowableValues = {"image/png", "image/jpeg", "application/pdf"})
        @NotBlank
        String contentType,

        @Schema(description = "파일 크기(바이트) — 10,485,760(10MB) 이하. 제출 시 실제 업로드된 크기로 다시 검증한다", example = "812345")
        @NotNull @Positive
        Long sizeBytes
) {
}
