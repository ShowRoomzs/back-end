package showroomz.domain.settlement.service.port;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementReader;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.SettlementStatus;

import java.util.Optional;

/** 공구 → 정산 리워드 포트 구현(44 어드민 설계서 8-1) — 지급 완료 정산의 리워드를 그대로 준다. 공구는 계산하지 않는다. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementGroupBuyReader implements GroupBuySettlementReader {

    private final SettlementRepository settlementRepository;

    /** 지급 완료 정산의 리워드 — 합의 후 · 차감 전(31 설계 4-5 「공제 전」). 정산 전 · 지급 전은 empty. */
    @Override
    public Optional<Long> readConfirmedReward(Long groupBuyId) {
        return settlementRepository.findByGroupBuyId(groupBuyId)
                .filter(settlement -> settlement.getStatus() == SettlementStatus.PAID)
                .map(Settlement::getRewardAmount);
    }
}
