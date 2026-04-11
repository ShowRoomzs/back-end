package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "상품 문의 답변 등록 요청")
public class SellerInquiryAnswerRequest {

    @NotBlank(message = "답변 내용을 입력해주세요.")
    @Size(max = 500, message = "답변 내용은 최대 500자까지 입력 가능합니다.")
    @Schema(description = "답변 내용 (최대 500자)", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500)
    private String answerContent;
}
