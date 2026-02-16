package showroomz.api.admin.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.faq.type.FaqCategory;

@Getter
@NoArgsConstructor
public class AdminFaqRegisterRequest {

    @Schema(description = "카테고리 (ALL 제외)", example = "DELIVERY", allowableValues = {"DELIVERY", "CANCEL_EXCHANGE_REFUND", "PRODUCT_AS", "ORDER_PAYMENT", "SERVICE", "USAGE_GUIDE", "MEMBER_INFO"})
    @NotNull(message = "카테고리를 선택해주세요.")
    private FaqCategory category;

    @Schema(description = "질문", example = "배송은 얼마나 걸리나요?")
    @NotBlank(message = "질문 내용을 입력해주세요.")
    private String question;

    @Schema(description = "답변", example = "평균 2~3일 소요됩니다.")
    @NotBlank(message = "답변 내용을 입력해주세요.")
    private String answer;

    @Schema(description = "노출 여부", example = "true", nullable = true)
    private Boolean isVisible = true; // 기본값 true
}

