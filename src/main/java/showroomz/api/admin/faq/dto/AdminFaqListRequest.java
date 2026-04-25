package showroomz.api.admin.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import showroomz.domain.faq.type.FaqCategory;

@Getter
@Setter
public class AdminFaqListRequest {

    @Schema(description = "카테고리 필터 (미입력 시 전체 조회)", example = "DELIVERY",
            allowableValues = {"ALL", "DELIVERY", "CANCEL_EXCHANGE_REFUND", "PRODUCT_AS", "ORDER_PAYMENT", "SERVICE", "USAGE_GUIDE", "MEMBER_INFO"})
    private FaqCategory category;

    @Schema(description = "질문 또는 답변 키워드 검색", example = "배송")
    private String keyword;
}
