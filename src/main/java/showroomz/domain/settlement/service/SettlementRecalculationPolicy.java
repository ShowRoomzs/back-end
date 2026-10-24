package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.global.config.properties.SettlementProperties;

/**
 * 지급 전 환불의 재계산 대상(1021 결정 노트 기-13 【잠정】 · 설계서 6-3 · 14절 Q18) — 설정된 상태(기본 확인 기간 {@code REVIEWING})이고
 * 다른 회차의 차감을 반영하지 않은 정산은 클로백 대신 금액을 다시 계산한다. 협의 중(ADJUSTING) · 확정 뒤는 클로백이다.
 *
 * <p>다른 회차 차감을 반영한 정산은 재계산하면 반영분을 다시 나눠야 해(이월 · 수정세금계산서) 클로백으로 둔다.
 */
@Component
@RequiredArgsConstructor
public class SettlementRecalculationPolicy {

    private final SettlementProperties properties;

    public boolean isRecalculable(Settlement settlement) {
        return settlement != null
                && properties.getClawback().getRecalculateStatuses().contains(settlement.getStatus())
                && settlement.getBrandClawbackAmount() == 0 && settlement.getRewardClawbackAmount() == 0;
    }
}
