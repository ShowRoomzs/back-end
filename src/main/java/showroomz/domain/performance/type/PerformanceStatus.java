package showroomz.domain.performance.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.settlement.type.SettlementStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 성과 상태 5종(48 파트너 설계서 0-2 · 1-1) = 「정산 상태 + 집계 중」. <b>저장하지 않고 파생한다</b> — 정산 행이 없으면
 * {@link #AGGREGATING}, 있으면 정산 상태(분배 실패는 지급 완료로 접는다 · 파트너 13 과 같은 규칙).
 *
 * <p>정산 상태 enum 을 그대로 쓰지 않는 이유는 톤이다 — 정산 13 의 「정산 확인 중」은 정보색인데 성과 화면은 정보 · 위험색을 쓰지
 * 않는다. 스튜디오 10(§49)은 이 5종을 3종(집계 중 · 조정 협의 · 정산 완료)으로 접어 쓴다 — 접는 규칙은 스튜디오 API 레이어가 든다.
 */
@Getter
@RequiredArgsConstructor
public enum PerformanceStatus {

    AGGREGATING("집계 중", PerformanceTone.NEUTRAL),
    REVIEWING("정산 확인 중", PerformanceTone.NEUTRAL),
    ADJUSTING("조정 협의", PerformanceTone.WARNING),
    PAYOUT_SCHEDULED("지급 예정", PerformanceTone.NEUTRAL),
    PAID("지급 완료", PerformanceTone.SUCCESS);

    /**
     * 파트너 성과 KPI 모집단(55 계획서 P1 · D3 · 시안 rev.7 「정산 2건 합산(지급 예정 1 · 지급 완료 1)」) — 금액이 확정된 정산만. 정산 확인 중 ·
     * 조정 협의는 조정 요청으로 값이 바뀔 수 있어 뺀다. 지급 완료에는 분배 실패가 들어 있다({@link #of}).
     */
    public static final Set<PerformanceStatus> KPI_POPULATION = Collections.unmodifiableSet(EnumSet.of(PAYOUT_SCHEDULED, PAID));

    private final String label;
    private final PerformanceTone tone;

    /** 정산 행이 없으면(null) 집계 중. */
    public static PerformanceStatus of(SettlementStatus settlementStatus) {
        if (settlementStatus == null) {
            return AGGREGATING;
        }
        return switch (settlementStatus) {
            case REVIEWING -> REVIEWING;
            case ADJUSTING -> ADJUSTING;
            case PAYOUT_SCHEDULED -> PAYOUT_SCHEDULED;
            case PAID, PAYOUT_FAILED -> PAID;
        };
    }

    public boolean isSettled() {
        return this != AGGREGATING;
    }

    /** 이 성과 상태에 해당하는 정산 상태 — 지급 완료는 분배 실패를 함께 고른다. 집계 중은 비어 있다(정산 행 없음). */
    public Set<SettlementStatus> settlementStatuses() {
        return switch (this) {
            case AGGREGATING -> EnumSet.noneOf(SettlementStatus.class);
            case REVIEWING -> EnumSet.of(SettlementStatus.REVIEWING);
            case ADJUSTING -> EnumSet.of(SettlementStatus.ADJUSTING);
            case PAYOUT_SCHEDULED -> EnumSet.of(SettlementStatus.PAYOUT_SCHEDULED);
            case PAID -> EnumSet.of(SettlementStatus.PAID, SettlementStatus.PAYOUT_FAILED);
        };
    }

    public static Set<SettlementStatus> settlementStatusesOf(Collection<PerformanceStatus> statuses) {
        Set<SettlementStatus> result = EnumSet.noneOf(SettlementStatus.class);
        statuses.forEach(status -> result.addAll(status.settlementStatuses()));
        return result;
    }
}
