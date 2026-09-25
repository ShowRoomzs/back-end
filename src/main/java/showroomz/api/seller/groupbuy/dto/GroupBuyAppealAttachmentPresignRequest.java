package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 소명 증빙 업로드 URL 발급(C9) — PNG · JPG · PDF · 10MB 이하. */
@Schema(description = "소명 증빙 presign 요청")
public record GroupBuyAppealAttachmentPresignRequest(

        @Schema(example = "시험성적서.pdf") @NotBlank @Size(max = 255)
        String fileName,

        @Schema(description = "image/png · image/jpeg · application/pdf", example = "application/pdf") @NotBlank
        String contentType,

        @Schema(example = "812345") @NotNull @Positive
        Long sizeBytes
) {
}
