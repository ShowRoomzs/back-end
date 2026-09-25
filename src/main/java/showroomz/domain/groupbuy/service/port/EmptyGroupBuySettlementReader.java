package showroomz.domain.groupbuy.service.port;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 정산 모듈이 생기기 전의 빈 구현 — 항상 empty다. 0을 돌려주지 않는다(30 설계 0-6).
 * 정산 관리가 {@link GroupBuySettlementReader}를 구현하면 이 클래스를 지운다.
 */
@Component
public class EmptyGroupBuySettlementReader implements GroupBuySettlementReader {

    @Override
    public Optional<Long> readConfirmedReward(Long groupBuyId) {
        return Optional.empty();
    }
}
