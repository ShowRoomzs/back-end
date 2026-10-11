package showroomz.global.payment.settlement;

import lombok.extern.slf4j.Slf4j;
import showroomz.domain.settlement.port.SettlementPartnerGateway;

/** {@code SIMULATED} 모드의 파트너 포트 — 등록할 곳이 없으니 항상 승인. 통합 테스트는 {@code FakeSettlementPartnerGateway}가 대신한다. */
@Slf4j
public class NoopSettlementPartnerGateway implements SettlementPartnerGateway {

    @Override
    public boolean isExternal() {
        return false;
    }

    @Override
    public PartnerResult ensure(PartnerProfile profile) {
        log.debug("[settlement-partner:noop] partnerId={}", profile.partnerId());
        return PartnerResult.approved();
    }
}
