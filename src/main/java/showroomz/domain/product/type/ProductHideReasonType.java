package showroomz.domain.product.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductHideReasonType {
    PRODUCT_NOTICE_ERROR("상품 정보 제공 고시 오류"),
    AD_DISPLAY_VIOLATION("표시/광고 위반 의심"),
    BRAND_REQUEST("브랜드 요청"),
    OTHER("기타");

    private final String description;

    /** 브랜드 수정 시 재검토 대기로 전환되는 사유인지 */
    public boolean triggersPendingReviewOnBrandEdit() {
        return this != BRAND_REQUEST;
    }
}
