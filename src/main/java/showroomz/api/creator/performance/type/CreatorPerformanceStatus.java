package showroomz.api.creator.performance.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.performance.type.PerformanceTone;

/**
 * 스튜디오 성과 상태 3종(49 스튜디오 성과 설계서 0-3 · §49) — 공통 5종({@link PerformanceStatus})을 접은 것이다. 저장하지 않는다.
 *
 * <p>인플루언서 관점에서 돈이 들어오기 전은 전부 「집계 중」이다 — 주문 종결 대기 · 정산 확인 중 · 지급 예정을 한 이름으로 묶는다.
 * 조정 협의만 따로 두는 이유는 미확정의 이유가 달라서다(판매 지표는 확정 · 리워드만 합의 대기). 파트너는 같은 행을 5종 그대로 본다.
 */
@Getter
@RequiredArgsConstructor
public enum CreatorPerformanceStatus {
    COLLECTING("집계 중", PerformanceTone.NEUTRAL),
    ADJUSTING("조정 협의", PerformanceTone.WARNING),
    SETTLED("정산 완료", PerformanceTone.SUCCESS);

    private final String label;
    private final PerformanceTone tone;

    public static CreatorPerformanceStatus of(PerformanceStatus status) {
        return switch (status) {
            case AGGREGATING, REVIEWING, PAYOUT_SCHEDULED -> COLLECTING;
            case ADJUSTING -> ADJUSTING;
            case PAID -> SETTLED;
        };
    }
}
