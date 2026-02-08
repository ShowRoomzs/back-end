package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "상품 문의 등록 요청")
public class ProductInquiryRegisterRequest {

    @NotBlank(message = "문의 내용을 입력해주세요.")
    @Schema(description = "문의 내용", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "비밀글 여부", example = "false")
    private boolean secret;
}
