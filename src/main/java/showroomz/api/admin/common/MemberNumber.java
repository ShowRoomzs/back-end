package showroomz.api.admin.common;

import showroomz.domain.member.common.type.MemberType;

/**
 * 회원번호 — 소비자 {@code CST-88231} · 브랜드 {@code BRD-1042} · 인플루언서 {@code INF-3018}(§25-3 · 52 설계서 0-1 · 53 설계서 0-12 ·
 * 정합 계획서 R1-2). 별도 컬럼 없이 회원 유형의 id 를 그대로 포맷한다 — 회원 관리 · 소통 스레드가 같은 번호를 보여야 CS 가 대조할 수 있다.
 *
 * <p>목록 검색의 축 판별에도 쓴다 — 운영자가 CS 에서 받은 번호를 접두사째 붙여넣는 것이 자연스러우므로 그 유형의 접두사로 시작하는 검색어만
 * 회원번호로 본다.
 */
public final class MemberNumber {

    private MemberNumber() {
    }

    /** 1024 → {@code BRD-1024}. id 가 없으면 null. */
    public static String format(MemberType type, Long memberId) {
        return memberId == null ? null : type.getMemberNoPrefix() + memberId;
    }

    /** 검색어가 그 유형의 회원번호 축인지 — 접두사로 시작하면 그렇다(대소문자 무시). */
    public static boolean looksLike(MemberType type, String keyword) {
        String prefix = type.getMemberNoPrefix();
        return keyword != null && keyword.trim().regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * {@code BRD-1042} → 1042. 접두사 뒤가 숫자가 아니면 null 이다.
     *
     * <p>null 을 「조건 없음」으로 해석하면 오타 하나에 전체 목록이 돌아온다. 호출부는 null 을 <b>일치하는 회원이 없다</b>로 다뤄야 한다.
     */
    public static Long parseOrNull(MemberType type, String keyword) {
        if (!looksLike(type, keyword)) {
            return null;
        }
        String digits = keyword.trim().substring(type.getMemberNoPrefix().length()).trim();
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
