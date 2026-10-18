package showroomz.api.admin.thread;

import showroomz.api.admin.common.MemberNumber;
import showroomz.api.admin.thread.type.AdminChannelTab;
import showroomz.domain.member.common.type.MemberType;

/**
 * 운영팀 채널 탭의 회원번호 — 브랜드 탭 {@code BRD-1017} · 인플루언서 탭 {@code INF-3021}(36 설계 3-1). 규칙은 {@link MemberNumber} 한
 * 곳이다(정합 계획서 R1-2) — 회원 관리 상세와 같은 번호가 나가야 CS 가 대조할 수 있다. 여기서는 탭을 회원 유형으로 바꾸기만 한다.
 */
public final class AdminChannelMemberNumber {

    private AdminChannelMemberNumber() {
    }

    public static String format(AdminChannelTab tab, Long memberId) {
        return MemberNumber.format(typeOf(tab), memberId);
    }

    /** 검색어가 회원번호 축인지 — 그 탭의 접두사로 시작하면 그렇다(대소문자 무시). */
    public static boolean looksLikeMemberNumber(AdminChannelTab tab, String keyword) {
        return MemberNumber.looksLike(typeOf(tab), keyword);
    }

    /** 접두사 뒤가 숫자가 아니면 null — 호출부는 null 을 「일치하는 회원이 없다」로 다룬다. */
    public static Long parseOrNull(AdminChannelTab tab, String keyword) {
        return MemberNumber.parseOrNull(typeOf(tab), keyword);
    }

    private static MemberType typeOf(AdminChannelTab tab) {
        return tab == AdminChannelTab.BRAND ? MemberType.BRAND : MemberType.CREATOR;
    }
}
