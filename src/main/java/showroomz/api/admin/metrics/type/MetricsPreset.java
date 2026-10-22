package showroomz.api.admin.metrics.type;

/** 운영 지표 기간 프리셋(1017 설계서 7-1) — 기간 규칙은 서버가 확정해 응답에 적는다. */
public enum MetricsPreset {
    /** 오늘 00:00 ~ 지금 · 시간별 · 비교 = 어제 같은 시간대. */
    TODAY,
    /** 7일 전 ~ 어제 · 일별. */
    D7,
    /** 이번 달 1일 ~ 어제 · 일별 · 비교 = 전월 1일 ~ 같은 일. */
    M1,
    /** 6개월 · 월별. */
    M6,
    /** from ~ to(최대 1년) · 90일 초과면 월별. */
    CUSTOM
}
