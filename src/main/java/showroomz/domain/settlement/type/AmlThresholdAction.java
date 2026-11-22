package showroomz.domain.settlement.type;

/**
 * 7일 누적 지급이 PG 의 고객확인(AML · KYC) 기준을 넘을 때 지시 앞단이 할 일(60_KG이니시스_지급대행_전환_계획서.md D-6).
 */
public enum AmlThresholdAction {
    /** 보지 않는다 — 시뮬레이터. */
    NONE,
    /** 지시는 하고 수취자 · 운영자에게 「고객확인 대상이 될 수 있음」 안내 — 이니시스 기본(우리가 막으면 고객확인이 시작되지 않을 수 있다). */
    ALERT,
    /** 먼저 보류({@code KYC_REQUIRED}) — 이니시스가 사전 보류를 요구하면(Q8). */
    BLOCK
}
