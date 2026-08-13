package showroomz.domain.faq.type;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * FAQ 카테고리 (고정 목록, 기획 §19-1)
 * - 5종 고정: 배송 · 취소/교환/반품 · 주문·결제 · 서비스 · 계정 ("기타"는 두지 않는다)
 * - 선언 순서 = 어드민 탭 / 소비자 칩 노출 순서
 * - ALL(전체)는 목록/필터용이며, FAQ 저장 시에는 사용하지 않음
 */
@Getter
public enum FaqCategory {

    ALL("전체"),
    DELIVERY("배송"),
    CANCEL_EXCHANGE_REFUND("취소/교환/반품"),
    ORDER_PAYMENT("주문·결제"),
    SERVICE("서비스"),
    ACCOUNT("계정");

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

    /** 저장 가능한 5종을 노출 순서대로 반환 */
    public static List<FaqCategory> persistableValues() {
        return Arrays.stream(values())
                .filter(FaqCategory::isPersistable)
                .toList();
    }
}
