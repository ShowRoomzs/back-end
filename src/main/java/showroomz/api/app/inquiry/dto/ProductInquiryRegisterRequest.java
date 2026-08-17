package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.ProductInquiryType;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "상품 문의 등록 요청 (C7-1) — 답변은 브랜드가 직접 등록한다")
public class ProductInquiryRegisterRequest {

    @NotNull(message = "문의 유형을 선택해주세요.")
    @Schema(description = "문의 유형 (필수) — OPTION: 옵션, INGREDIENT_USAGE: 성분·사용법, RESTOCK: 재입고, DELIVERY: 배송, ETC: 기타",
            example = "INGREDIENT_USAGE", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"OPTION", "INGREDIENT_USAGE", "RESTOCK", "DELIVERY", "ETC"})
    private ProductInquiryType type;

    @NotBlank(message = "문의 내용을 입력해주세요.")
    @Size(max = 250, message = "문의 내용은 250자 이내로 입력해주세요.")
    @Schema(description = "문의 내용 (최대 250자)", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 250)
    private String content;

    @Schema(description = "비밀글 여부 — 작성자와 브랜드만 볼 수 있습니다. 답변해도 공개로 전환되지 않습니다", example = "false")
    private boolean secret;

    @Size(max = 3, message = "사진은 최대 3장까지 첨부할 수 있습니다.")
    @Schema(description = "첨부 사진 URL 리스트 (선택 · 최대 3장)")
    private List<String> imageUrls;
}
