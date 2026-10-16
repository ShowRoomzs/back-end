package showroomz.domain.performance.type;

/** 파트너 성과 목록 정렬(48 파트너 설계서 3-2). 스튜디오 정렬은 스튜디오 API 레이어가 따로 든다. */
public enum PerformanceSort {
    /** 종료일 최신순(기본) — {@code COALESCE(ended_at, end_at) DESC}. */
    ENDED_DESC,
    /** 거래액 높은순 — 정산이 있는 공구의 확정 거래액 · 집계 중 공구는 맨 뒤. */
    SALES_DESC
}
