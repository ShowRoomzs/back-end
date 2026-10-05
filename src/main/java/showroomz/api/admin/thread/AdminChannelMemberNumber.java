package showroomz.api.admin.thread;

import showroomz.api.admin.thread.type.AdminChannelTab;

/**
 * 브랜드 · 인플루언서 회원번호 {@code BRD-1017} · {@code INF-3021}(36 설계 3-1 · 11-2 #1 — <b>잠정</b>).
 *
 * <p>소비자의 {@code CST-}와 같은 방식이다 — 별도 채번 없이 id를 그대로 포맷한다(브랜드 = 마켓 id · 인플루언서 = 크리에이터 id).
 * 회원 관리(브랜드 · 인플루언서)가 다른 규칙을 정하면 이 클래스만 고친다.
 */
public final class AdminChannelMemberNumber {

    private AdminChannelMemberNumber() {
    }

    public static String format(AdminChannelTab tab, Long memberId) {
        return memberId == null ? null : prefix(tab) + memberId;
    }

    /** 검색어가 회원번호 축인지 — 그 탭의 접두사로 시작하면 그렇다(대소문자 무시). */
    public static boolean looksLikeMemberNumber(AdminChannelTab tab, String keyword) {
        String prefix = prefix(tab);
        return keyword != null && keyword.trim().regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * 접두사 뒤가 숫자가 아니면 null이다. 호출부는 null을 <b>일치하는 회원이 없다</b>로 다뤄야 한다 —
     * 「조건 없음」으로 풀면 오타 하나에 전체 목록이 돌아온다.
     */
    public static Long parseOrNull(AdminChannelTab tab, String keyword) {
        if (!looksLikeMemberNumber(tab, keyword)) {
            return null;
        }
        String digits = keyword.trim().substring(prefix(tab).length()).trim();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String prefix(AdminChannelTab tab) {
        return tab == AdminChannelTab.BRAND ? "BRD-" : "INF-";
    }
}
