package showroomz.api.admin.brand;

/**
 * 브랜드 회원번호 {@code BRD-1042}(52 설계서 0-1) — 소비자 {@code CST-}와 같은 방식이다. 컬럼을 만들지 않고 {@code market_id}를 그대로 포맷한다.
 * 정산 · 연결 · 계약 · 공구가 전부 {@code market_id}로 브랜드를 가리킨다.
 */
public final class AdminBrandNumber {

    private static final String PREFIX = "BRD-";

    private AdminBrandNumber() {
    }

    public static String format(Long marketId) {
        return marketId == null ? null : PREFIX + marketId;
    }

    public static boolean looksLikeBrandNumber(String keyword) {
        return keyword != null && keyword.trim().regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    /** {@code BRD-1042} → 1042 · 접두사 뒤가 숫자가 아니면 null — 호출부는 null 을 「일치 없음」으로 다룬다. */
    public static Long parseOrNull(String keyword) {
        if (!looksLikeBrandNumber(keyword)) {
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
