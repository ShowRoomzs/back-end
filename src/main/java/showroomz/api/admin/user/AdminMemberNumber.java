package showroomz.api.admin.user;

import showroomz.api.admin.common.MemberNumber;
import showroomz.domain.member.common.type.MemberType;

/**
 * 소비자 회원번호 {@code CST-88231}(§25-3) — 규칙은 {@link MemberNumber} 한 곳이다(정합 계획서 R1-2). 소비자 목록 · 상세의 기존 호출부를
 * 위해 남겨 둔 얇은 위임이다.
 *
 * <p>별도 컬럼을 만들지 않고 {@code USERS.USER_ID}를 그대로 포맷한다 — 회원번호가 곧 가입 순서라는 성질도 그대로 남는다(정렬 `회원번호순`).
 */
public final class AdminMemberNumber {

    private AdminMemberNumber() {
    }

    /** 1024 → {@code CST-1024}. id가 없으면 null */
    public static String format(Long userId) {
        return MemberNumber.format(MemberType.CONSUMER, userId);
    }

    /** 검색어가 회원번호 축인지 — {@code CST-} 로 시작하면 그렇다(대소문자 무시) */
    public static boolean looksLikeMemberNumber(String keyword) {
        return MemberNumber.looksLike(MemberType.CONSUMER, keyword);
    }

    /** {@code CST-88231} → 88231. 접두사 뒤가 숫자가 아니면 null — 호출부는 null 을 「일치하는 회원이 없다」로 다룬다. */
    public static Long parseOrNull(String keyword) {
        return MemberNumber.parseOrNull(MemberType.CONSUMER, keyword);
    }
}
