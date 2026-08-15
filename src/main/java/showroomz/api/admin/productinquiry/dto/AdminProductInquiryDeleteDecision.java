package showroomz.api.admin.productinquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브랜드의 삭제 요청에 대한 운영자 판단 (§23-5).
 * 삭제 사유와 반려 사유는 성격이 다르다 — 반려 사유는 요청 브랜드에게 전달되고,
 * 삭제 사유는 운영자 내부 기록이라 브랜드·작성자 어느 쪽에도 공개되지 않는다.
 */
public class AdminProductInquiryDeleteDecision {

    @Getter
    @NoArgsConstructor
    @Schema(description = "삭제 집행 요청")
    public static class ExecuteRequest {

        @Size(max = 500, message = "삭제 사유는 최대 500자까지 입력 가능합니다.")
        @Schema(description = "삭제 사유 — 운영자 내부 기록. 브랜드·작성자에게 공개되지 않습니다", maxLength = 500)
        private String reason;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "삭제 요청 반려")
    public static class RejectRequest {

        @NotBlank(message = "반려 사유를 입력해주세요.")
        @Size(max = 500, message = "반려 사유는 최대 500자까지 입력 가능합니다.")
        @Schema(description = "반려 사유 — 요청 브랜드에게 전달됩니다",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500,
                example = "단순 비교 질문으로 판단되며 비방·허위 사실에 해당하지 않습니다.")
        private String reason;
    }
}
