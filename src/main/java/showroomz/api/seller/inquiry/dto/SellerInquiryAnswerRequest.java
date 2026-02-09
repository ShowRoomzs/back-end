package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "상품 문의 답변 등록 요청")
public class SellerInquiryAnswerRequest {

    @NotBlank(message = "답변 내용을 입력해주세요.")
    @Schema(description = "답변 내용", requiredMode = Schema.RequiredMode.REQUIRED)
    private String answerContent;
}
