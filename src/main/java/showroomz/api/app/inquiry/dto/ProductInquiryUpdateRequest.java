package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.ProductInquiryType;

@Getter
@NoArgsConstructor
@Schema(description = "상품 문의 수정 요청")
public class ProductInquiryUpdateRequest {

    @NotNull(message = "문의 타입을 선택해주세요.")
    @Schema(description = "문의 타입 (PRODUCT_INQUIRY, SIZE_INQUIRY, STOCK_INQUIRY)", example = "SIZE_INQUIRY", requiredMode = Schema.RequiredMode.REQUIRED)
    private ProductInquiryType type;

    @NotBlank(message = "문의 내용을 입력해주세요.")
    @Schema(description = "문의 내용", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
