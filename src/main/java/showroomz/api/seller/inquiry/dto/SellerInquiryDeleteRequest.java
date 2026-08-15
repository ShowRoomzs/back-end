package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.ProductInquiryDeleteReason;

/**
 * 문의 삭제 요청 (§23-5) — 브랜드는 요청까지, 집행은 운영자가 한다.
 * 요청해도 문의는 즉시 삭제되지 않고 검토 중에도 계속 게시된다. 요청 후 취소는 불가하다.
 */
@Getter
@NoArgsConstructor
@Schema(description = "문의 삭제 요청")
public class SellerInquiryDeleteRequest {

    @NotNull(message = "삭제 요청 사유를 선택해주세요.")
    @Schema(description = "요청 사유", requiredMode = Schema.RequiredMode.REQUIRED, example = "BRAND_COMPARISON",
            allowableValues = {"ABUSE", "PRIVACY_EXPOSURE", "ADVERTISEMENT", "BRAND_COMPARISON", "ETC"})
    private ProductInquiryDeleteReason reason;

    @Size(max = 500, message = "상세 설명은 최대 500자까지 입력 가능합니다.")
    @Schema(description = "상세 설명 — 선택. 사유가 `ETC`(기타 직접 입력)면 필수", maxLength = 500)
    private String detail;
}
