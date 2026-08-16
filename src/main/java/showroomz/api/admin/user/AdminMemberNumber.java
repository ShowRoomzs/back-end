package showroomz.api.admin.user;

/**
 * 회원번호 {@code CST-88231} (§25-3).
 *
 * <p>별도 컬럼을 만들지 않고 {@code USERS.USER_ID}를 그대로 포맷한다. 새 채번 규칙을 두면
 * 기존 회원에 소급 부여할 근거가 없고, 두 개의 식별자가 생겨 CS가 어느 쪽을 물어야 하는지
 * 흐려진다. 회원번호가 곧 가입 순서라는 성질도 그대로 남는다(정렬 `회원번호순`).
 *
 * <p>목록 검색의 축 판별에도 이 클래스를 쓴다 — 운영자가 CS에서 받은 번호를 접두사째
 * 붙여넣는 것이 자연스러우므로 {@code CST-} 로 시작하는 검색어만 회원번호로 본다.
 */
public final class AdminMemberNumber {

    private static final String PREFIX = "CST-";

    private AdminMemberNumber() {
    }

    /** 1024 → {@code CST-1024}. id가 없으면 null */
    public static String format(Long userId) {
        return userId == null ? null : PREFIX + userId;
    }

    /** 검색어가 회원번호 축인지 — {@code CST-} 로 시작하면 그렇다(대소문자 무시) */
    public static boolean looksLikeMemberNumber(String keyword) {
        return keyword != null && keyword.trim().regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    /**
     * {@code CST-88231} → 88231. 접두사 뒤가 숫자가 아니면 null이다.
     *
     * <p>null을 "조건 없음"으로 해석하면 오타 하나에 전체 목록이 돌아온다. 호출부는 null을
     * <b>일치하는 회원이 없다</b>로 다뤄야 한다.
     */
    public static Long parseOrNull(String keyword) {
        if (!looksLikeMemberNumber(keyword)) {
            return null;
        }
        String digits = keyword.trim().substring(PREFIX.length()).trim();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
