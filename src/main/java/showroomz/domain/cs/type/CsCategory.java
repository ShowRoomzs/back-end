package showroomz.domain.cs.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * CS 분류 — FAQ 카테고리(§19-1)와 1:1 문의 유형(§17-2-1)이 **공유하는 단일 소스**.
 * <p>
 * 소비자는 FAQ에서 먼저 찾고 못 찾으면 1:1로 넘어오므로 두 화면의 분류가 같아야
 * "어떤 유형의 문의가 많은가 → 어떤 FAQ를 보강해야 하는가"로 연결된다.
 * 값을 이중 관리하지 않기 위해 enum 하나를 두 도메인이 참조한다.
 * <p>
 * - 5종 고정이며 {@code 기타}는 두지 않는다 (있으면 거기로 몰려 유형별 집계가 무의미해진다)<br>
 * - 폐기 값: {@code 환불 · 주문 · 기타 · 상품확인 · 상품/AS문의 · 이용 안내 · 회원 정보} — 재사용하지 않는다<br>
 * - 선언 순서 = 어드민 탭 · 소비자 칩 노출 순서<br>
 * - "전체"는 분류 값이 아니라 <b>필터가 없는 상태(null)</b>로 표현한다
 */
@Getter
@AllArgsConstructor
public enum CsCategory {

    DELIVERY("배송"),
    CANCEL_EXCHANGE_RETURN("취소/교환/반품"),
    ORDER_PAYMENT("주문·결제"),
    SERVICE("서비스"),
    ACCOUNT("계정");

    /** 목록 필터·탭에서 쓰는 "전체" 의사값 — 저장되는 분류가 아니다 */
    public static final String ALL_CODE = "ALL";
    public static final String ALL_LABEL = "전체";

    private final String description;

    /**
     * 필터 파라미터 → 분류.
     * null·빈값·{@code ALL}·{@code 전체}면 null(= 전체, 필터 없음)을 반환하고,
     * enum 이름과 한글 표시명을 모두 허용한다.
     */
    public static CsCategory fromFilterParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (ALL_CODE.equalsIgnoreCase(trimmed) || ALL_LABEL.equals(trimmed)) {
            return null;
        }
        for (CsCategory category : values()) {
            if (category.name().equalsIgnoreCase(trimmed) || category.description.equals(trimmed)) {
                return category;
            }
        }
        return null;
    }
}
