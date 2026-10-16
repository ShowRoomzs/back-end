package showroomz.api.creator.performance.type;

/**
 * 스튜디오 성과 목록 정렬(§49-1) — 보기 옵션이라 필터와 따로 받는다. 리워드 · 전환율 정렬에서 값이 없는 행(집계 중 · 조정 협의)은
 * 맨 뒤이고, 같은 값끼리는 종료일 최신순이다.
 */
public enum CreatorPerformanceSort {
    ENDED_DESC,
    REWARD_DESC,
    CONVERSION_DESC
}
