package showroomz.api.admin.user.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 소비자 목록 정렬 (§25-3 툴바 셀렉트 3종). */
@Getter
@AllArgsConstructor
public enum AdminUserSort {

    /** 기본값 — 가입일 최신순 */
    RECENT_JOINED("최근 가입순"),

    /** 누적 주문 많은 순 — 0건 회원이 뒤로 밀린다 */
    ORDER_COUNT_DESC("누적 주문 많은 순"),

    /** 회원번호 오름차순 — CST 번호는 회원 ID라 가입 순서와 같다 */
    MEMBER_NO("회원번호순");

    private final String description;
}
