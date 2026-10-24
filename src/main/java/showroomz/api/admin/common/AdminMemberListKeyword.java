package showroomz.api.admin.common;

import showroomz.domain.member.common.type.MemberType;

/**
 * 회원 목록 검색어 축 — 회원번호 접두사(BRD- · INF-)로 시작하면 회원번호 정확 일치(접두사 뒤가 숫자가 아니면 0건 — 오타 하나에 전체 목록이
 * 돌아오면 안 된다), 그 밖은 목록마다 정한 텍스트 축(브랜드명 · 쇼룸명 + 업무용 이메일)으로 찾는다.
 *
 * @param memberId 회원번호 검색이면 id
 * @param text     텍스트 검색이면 다듬은 검색어
 * @param noMatch  결과가 반드시 0건(회원번호 형식 오류)
 */
public record AdminMemberListKeyword(Long memberId, String text, boolean noMatch) {

    public static AdminMemberListKeyword of(MemberType type, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new AdminMemberListKeyword(null, null, false);
        }
        if (MemberNumber.looksLike(type, keyword)) {
            Long id = MemberNumber.parseOrNull(type, keyword);
            return new AdminMemberListKeyword(id, null, id == null);
        }
        return new AdminMemberListKeyword(null, keyword.trim(), false);
    }
}
