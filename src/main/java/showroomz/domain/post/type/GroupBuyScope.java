package showroomz.domain.post.type;

/**
 * 소비자 목록이 공구 게시물을 어디까지 싣는가(공구 게시물 설계 6절). 모든 목록은 {@code post.status = PUBLISHED}에
 * 이 조건 하나를 더한다.
 *
 * <p><b>진행 중 공구</b> = {@code GROUP_BUY ∧ group_buy.status ∈ SELLING ∧ end_at > now}, <b>마감 게시물</b> = 그 밖의
 * {@code GROUP_BUY}. {@code PUBLISHED}가 숨김·미승인·종료 3일 경과를 이미 걸렀으므로(4-1) 공구 상태만 보면 된다.
 *
 * <p>화면 시안이 바뀌면(예: C1에 마감 카드 — 미결 ⑧) 호출하는 쪽의 값만 바꾼다.
 */
public enum GroupBuyScope {

    /** 일반 + 진행 중 공구 — C1 팔로잉·추천·전체 피드. 살 수 없는 공구는 발견이 아니다 */
    GENERAL_AND_ONGOING,

    /** 일반 + 마감 게시물 — C4 아래 피드. 진행 중은 고정 섹션이 따로 그려 두 번 뜨지 않게 뺀다 */
    GENERAL_AND_CLOSED,

    /** 진행 중 공구만 — C4 고정 섹션 */
    ONGOING_ONLY
}
