package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.InquiryType;

@Getter
@NoArgsConstructor
@Schema(description = "상품 문의 등록 요청")
public class ProductInquiryRegisterRequest {

    @NotNull(message = "문의 타입을 선택해주세요.")
    @Schema(description = "문의 타입 (대분류)", example = "DELIVERY", requiredMode = Schema.RequiredMode.REQUIRED)
    private InquiryType type;

    @NotBlank(message = "상세 유형을 입력해주세요.")
    @Size(max = 50, message = "문의 유형은 50자 이내로 입력해주세요.")
    @Schema(description = "문의 유형 (상세 사유 - 직접 입력)", example = "배송 지연", requiredMode = Schema.RequiredMode.REQUIRED)
    private String category;

    @NotBlank(message = "문의 내용을 입력해주세요.")
    @Schema(description = "문의 내용", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
