package showroomz.api.admin.transaction;

/**
 * 환불번호 {@code RFD-918} (39 설계서 1-4).
 *
 * <p>별도 컬럼을 만들지 않고 {@code order_refund_task.refund_task_id}를 그대로 포맷한다 — 회원번호 {@code CST-}와 같은 규칙.
 * 0 채움은 없다(시안의 {@code RFD-0918}은 예시값).
 *
 * <p>검색어 판별에도 쓴다. {@code RFD-} 접두가 붙었거나 숫자만 온 검색어를 환불번호로 본다. 접두가 붙었는데 뒤가 숫자가
 * 아니면({@code RFD-abc}) 「일치하는 환불이 없다」이지 「조건 없음」이 아니다 — 오타 하나에 전체 목록이 돌아오면 안 된다.
 */
public final class AdminRefundNumber {

    private static final String PREFIX = "RFD-";

    private AdminRefundNumber() {
    }

    /** 918 → {@code RFD-918}. id 가 없으면 null. */
    public static String format(Long refundTaskId) {
        return refundTaskId == null ? null : PREFIX + refundTaskId;
    }

    /** 검색어가 {@code RFD-} 로 시작하는가(대소문자 무시). */
    public static boolean hasPrefix(String keyword) {
        return keyword != null && keyword.trim().regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    /**
     * {@code RFD-918} · {@code 918} → 918. 그 밖은 null — 접두가 있는데 null 이면 호출부는 <b>결과 0건</b>으로 다룬다
     * ({@link #hasPrefix}로 가른다).
     */
    public static Long parseOrNull(String keyword) {
        if (keyword == null) {
            return null;
        }
        String digits = hasPrefix(keyword) ? keyword.trim().substring(PREFIX.length()).trim() : keyword.trim();
        if (digits.isEmpty() || digits.length() > 18 || !digits.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Long.parseLong(digits);
    }
}
