package showroomz.api.app.showroom.type;

/**
 * 팔로잉 쇼룸 목록 정렬 기준 (C2 팔로잉 화면의 정렬 바텀시트).
 */
public enum FollowingShowroomSort {

    /** 기본 — 최근에 게시물을 올린 쇼룸 순서 (게시물이 없는 쇼룸은 뒤로) */
    DEFAULT,

    /** 팔로우한 날짜: 최신순 */
    FOLLOW_LATEST,

    /** 팔로우한 날짜: 오래된순 */
    FOLLOW_OLDEST
}
