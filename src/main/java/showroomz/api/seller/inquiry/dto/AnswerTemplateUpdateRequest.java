package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;

@Getter
@NoArgsConstructor
@Schema(description = "답변 템플릿 수정 요청")
public class AnswerTemplateUpdateRequest {

    @NotBlank(message = "템플릿 제목을 입력해주세요.")
    @Size(max = 30, message = "템플릿 제목은 최대 30자까지 입력 가능합니다.")
    @Schema(description = "템플릿 제목 (최대 30자)", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 30, example = "재입고 안내 - 수정본")
    private String title;

    @NotNull(message = "카테고리를 선택해주세요.")
    @Schema(
            description = "카테고리",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "STOCK",
            allowableValues = {"PRODUCT", "SIZE", "STOCK", "DELIVERY", "ORDER_PAYMENT", "CANCEL_REFUND_EXCHANGE", "DEFECT_AS"}
    )
    private MarketInquiryFilterType category;

    @NotBlank(message = "답변 내용을 입력해주세요.")
    @Size(max = 1000, message = "답변 내용은 최대 1000자까지 입력 가능합니다.")
    @Schema(description = "답변 내용 (최대 1000자)", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 1000, example = "안녕하세요, 해당 상품은 이번 주 내로 재입고 예정입니다.")
    private String content;

    @NotNull(message = "사용 여부를 입력해주세요.")
    @Schema(description = "사용 여부", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean isActive;
}
