package showroomz.domain.groupbuy.service.port;

import org.springframework.stereotype.Component;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Optional;

/**
 * 정산 모듈이 생기기 전의 빈 구현 — 단계는 항상 empty이고 확인은 거절한다(32 설계 8-2 착수 게이트).
 * 정산 관리가 {@link GroupBuySettlementGateway}를 구현하면 이 클래스를 지운다.
 */
@Component
public class EmptyGroupBuySettlementGateway implements GroupBuySettlementGateway {

    @Override
    public Optional<SettlementStage> readStage(Long groupBuyId) {
        return Optional.empty();
    }

    @Override
    public void confirm(Long groupBuyId, Long operatorId) {
        throw new BusinessException(ErrorCode.GROUP_BUY_SETTLEMENT_NOT_READY);
    }
}
