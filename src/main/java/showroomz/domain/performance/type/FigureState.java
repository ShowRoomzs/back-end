package showroomz.domain.performance.type;

/**
 * 지표 값 하나의 상태(48 파트너 설계서 0-5 · 5절) — 어느 값이 어느 상태인지는 서버가 정하고 FE 는 태그 · 회색 · 「—」만 그린다.
 * 「0원 · 0%」와 「아직 없음」은 다르다 — 없음은 {@code value = null}.
 */
public enum FigureState {
    /** 확정 — 더 바뀌지 않는다. */
    FINAL,
    /** 예정 — 미처리 반품 · 회수 결과에 따라 줄어들 수 있다(집계 중). */
    PROJECTED,
    /** 정산 후 확정 — 숫자를 내지 않는다(집계 중의 돈 지표). */
    PENDING_SETTLEMENT,
    /** 협의 중 — 리워드 조정 협의 결과에 따라 바뀐다 · {@code original}에 원래 값. */
    NEGOTIATING,
    /** 측정 불가 — 원천 로그가 아직 없다(48 설계서 12절 집계 대기). */
    NOT_AVAILABLE
}
