package showroomz.api.admin.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import showroomz.domain.cs.type.CsCategory;

@Getter
@Setter
public class AdminFaqListRequest {

    @Schema(description = "카테고리 탭 (미입력/ALL 시 전체 조회)", example = "DELIVERY",
            allowableValues = {"ALL", "DELIVERY", "CANCEL_EXCHANGE_RETURN", "ORDER_PAYMENT", "SERVICE", "ACCOUNT"})
    private String category;

    @Schema(description = "질문 키워드 검색", example = "배송")
    private String keyword;

    /** 전체 탭(ALL·미입력)은 필터 없음(null)으로 환산한다 */
    public CsCategory toCategory() {
        return CsCategory.fromFilterParam(category);
    }
}
