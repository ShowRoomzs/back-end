package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 소명 자료 제출(C9) — 제출 후 수정할 수 없다. */
@Schema(description = "소명 자료 제출")
public record GroupBuyAppealSubmitRequest(

        @Schema(description = "소명 내용 — 필수 · 2,000자", example = "해당 표현은 시험성적서에 근거한 것으로…")
        @NotBlank @Size(max = 2000)
        String content,

        @Schema(description = "presign으로 올린 증빙 id — 선택", nullable = true)
        List<Long> attachmentIds
) {
}
