package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.ProductInquiryType;

import java.util.List;

/** 상품 문의 수정 요청 — 공개↔비밀글 전환은 지원하지 않는다(작성 시점 값을 유지한다). */
@Getter
@NoArgsConstructor
@Schema(description = "상품 문의 수정 요청")
public class ProductInquiryUpdateRequest {

    @NotNull(message = "문의 유형을 선택해주세요.")
    @Schema(description = "문의 유형 (OPTION, INGREDIENT_USAGE, RESTOCK, DELIVERY, ETC)",
            example = "INGREDIENT_USAGE", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"OPTION", "INGREDIENT_USAGE", "RESTOCK", "DELIVERY", "ETC"})
    private ProductInquiryType type;

    @NotBlank(message = "문의 내용을 입력해주세요.")
    @Size(max = 250, message = "문의 내용은 250자 이내로 입력해주세요.")
    @Schema(description = "문의 내용 (최대 250자)", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 250)
    private String content;

    @Size(max = 3, message = "사진은 최대 3장까지 첨부할 수 있습니다.")
    @Schema(description = "첨부 사진 URL 리스트 (선택 · 최대 3장)")
    private List<String> imageUrls;
}
