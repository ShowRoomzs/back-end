package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 소명 자료 제출(C9) — 제출 후 수정할 수 없다. */
@Schema(description = "소명 자료 제출 — 제출 후 수정할 수 없다")
public record GroupBuyAppealSubmitRequest(

        @Schema(description = "소명 내용 — 필수 · 2,000자. 운영자가 집행·철회 판단 근거로 읽는다", example = "해당 표현은 시험성적서에 근거한 것으로…")
        @NotBlank @Size(max = 2000)
        String content,

        @Schema(description = "증빙 첨부 id 목록 — 선택 · 최대 5개. 업로드 URL 발급 후 실제로 PUT을 마친 이 통지의 첨부만 허용한다(중복 id는 1개로 센다)",
                example = "[31, 32]", nullable = true)
        List<Long> attachmentIds
) {
}
