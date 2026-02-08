package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "상품 문의 등록 응답")
public class ProductInquiryRegisterResponse {

    @Schema(description = "생성된 상품 문의 ID", example = "1")
    private Long inquiryId;
}
