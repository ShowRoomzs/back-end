package showroomz.domain.faq.type;

import lombok.Getter;

/**
 * FAQ 카테고리 (고정 목록)
 * - ALL(전체)는 목록/필터용이며, FAQ 저장 시에는 사용하지 않음
 */
@Getter
public enum FaqCategory {

    ALL("전체"),
    DELIVERY("배송"),
    CANCEL_EXCHANGE_REFUND("취소/교환/반품"),
    PRODUCT_AS("상품/AS문의"),
    ORDER_PAYMENT("주문/결제"),
    SERVICE("서비스"),
    USAGE_GUIDE("이용 안내"),
    MEMBER_INFO("회원 정보");

    private final String displayName;

    FaqCategory(String displayName) {
        this.displayName = displayName;
    }

    /** API 요청용: enum 이름(DELIVERY 등) 또는 "전체" → null(전체), 그 외 한글명도 허용 */
    public static FaqCategory fromRequestParam(String value) {
        if (value == null || value.isBlank() || "전체".equals(value.trim())) {
            return null;
        }
        String v = value.trim();
        for (FaqCategory c : values()) {
            if (c.name().equalsIgnoreCase(v) || c.displayName.equals(v)) {
                return c;
            }
        }
        return null;
    }

    /** FAQ 저장 시 사용 가능한 카테고리만 (ALL 제외) */
    public boolean isPersistable() {
        return this != ALL;
    }
}
