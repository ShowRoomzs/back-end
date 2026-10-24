package showroomz.api.admin.common;

/**
 * 회원 목록 정렬 — 가입 최신순(기본) · 회원번호순. 전체 탭에서는 이 정렬 앞에 「탈퇴 승인 대기 먼저」가 붙는다(시안 「승인 대기 상단 고정」 · 고정된
 * 건끼리는 이 정렬을 따른다 · D13-5).
 */
public enum AdminMemberListSort {
    RECENT_JOINED,
    MEMBER_NO,
    /** @deprecated 브랜드 목록의 옛 값 — {@link #MEMBER_NO}와 같다. 이미 보내는 클라이언트를 위해 받는다(D13-4). */
    @Deprecated
    BRAND_NO;

    public boolean byMemberNo() {
        return this == MEMBER_NO || this == BRAND_NO;
    }
}
