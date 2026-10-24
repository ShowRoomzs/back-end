package showroomz.domain.post.type;

/** 어드민 일반 게시물 목록 정렬 (§54-2) */
public enum PostMonitoringSort {
    /**
     * 조치 필요 우선(기본) — 심사 중 · 신고 접수(오래 기다린 순) → 노출 중지(기한 급한 순)
     * → 게시중(최신 게시순) → 영구 삭제(최신 삭제순).
     */
    ACTION_FIRST,
    /** 최신 등록순 — 게시 시각 */
    LATEST,
    /** 신고 많은 순 — 목록의 「신고」 열과 같은 수(대기 + 조치로 이어진 신고) */
    MOST_REPORTED
}
