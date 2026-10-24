package showroomz.api.admin.common;

/**
 * 브랜드 · 인플루언서 회원 목록 탭 5종(ui-admin-14 · 16 · 55 계획서 M1 · M2) — 기본 진입은 전체. 탭 건수는 서로 겹치지 않는다(전체 = 넷의
 * 합) — 탈퇴 승인 대기 행은 활성 · 정지 탭에서 빠진다({@link AdminMemberListStatus}).
 */
public enum AdminMemberListTab {
    ALL,
    ACTIVE,
    SUSPENDED,
    WITHDRAWAL_PENDING,
    WITHDRAWN
}
