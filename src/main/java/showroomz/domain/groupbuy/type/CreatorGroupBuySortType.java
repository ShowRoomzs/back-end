package showroomz.domain.groupbuy.type;

/**
 * 스튜디오 목록 정렬(31 설계 1-2).
 *
 * <p>「시작일 빠른순」은 단순 오름차순이 아니다 — 순수 {@code start_at ASC}면 한 달 전에 끝난 중단 건이 맨 위에 와서
 * 오늘 할 일을 밀어낸다. 비종결을 먼저(시작일 오름차순), 종결은 그 뒤(시작일 내림차순)로 둔다.
 */
public enum CreatorGroupBuySortType {
    /** ① 비종결 먼저 ② 비종결은 start_at ASC ③ 종결은 start_at DESC ④ id DESC */
    START_AT_ASC,
    /** ⓪ 내 조치 필요 먼저 → 이후 START_AT_ASC와 같다. 조치 판정은 배지·목록 헤더와 같은 식이다. */
    ACTION_REQUIRED_FIRST
}
