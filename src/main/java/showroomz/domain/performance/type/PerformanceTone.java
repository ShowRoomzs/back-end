package showroomz.domain.performance.type;

/**
 * 성과 상태 배지 톤(§47-1 상태색) — 중립 · 경고 · 성공 셋뿐이다. 성과 화면은 조치 화면이 아니라 위험색 · 정보색을 쓰지 않는다
 * (정산 13 의 「정산 확인 중」이 정보색이어도 여기서는 중립이다 · 48 파트너 설계서 0-2).
 */
public enum PerformanceTone {
    NEUTRAL,
    WARNING,
    SUCCESS
}
