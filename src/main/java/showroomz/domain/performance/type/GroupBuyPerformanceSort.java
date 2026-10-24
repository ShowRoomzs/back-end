package showroomz.domain.performance.type;

/**
 * 성과 목록의 공구 정렬(48 파트너 설계서 3-2) — 공용 성과 쿼리({@code PerformanceGroupBuyQueryRepository})가 받는 값이라 도메인에 둔다.
 * 스튜디오 정렬(리워드 · 전환율)은 값이 정산 후에만 있어 스튜디오 API 레이어가 메모리에서 든다(정합 계획서 R4-3).
 */
public enum GroupBuyPerformanceSort {
    /** 종료일 최신순(기본) — {@code COALESCE(ended_at, end_at) DESC}. */
    ENDED_DESC,
    /** 거래액 높은순 — 정산이 있는 공구의 확정 거래액 · 집계 중 공구는 맨 뒤. */
    SALES_DESC
}
