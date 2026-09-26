package showroomz.domain.groupbuy.type;

/**
 * 게시물 리비전 종류(31 설계 2-6). 기록 대상은 <b>운영자가 본 글</b>(제출)과 <b>소비자가 본 글</b>(승인 후 수정)뿐이다 —
 * 임시저장은 공개된 적도 심사받은 적도 없어 남기지 않는다.
 */
public enum GroupBuyPostRevisionKind {
    /** 제출 · 재제출 */
    SUBMITTED,
    /** 승인 후 수정 */
    EDITED
}
