package showroomz.global.payment.settlement;

import lombok.extern.slf4j.Slf4j;
import showroomz.domain.settlement.port.SettlementPayoutGateway;

/**
 * 지급 시뮬레이터(44 어드민 설계서 0-5 · {@code settlement.payout.mode = SIMULATED}) — 모든 수취자 행을 즉시 지급 완료로 돌려준다.
 * 참조번호는 {@code SIM-{정산번호}-{수취자}}다. 개발 · QA · 통합 테스트가 「생성 → 확정 → 지급 완료 → 공구 SETTLED → 영수증」을
 * 끝까지 돌리게 하려고 둔다 — <b>운영 프로필에서는 기동하지 않는다</b>({@code SettlementPayoutGatewayConfig}).
 */
@Slf4j
public class SimulatedSettlementPayoutGateway implements SettlementPayoutGateway {

    @Override
    public PayoutResult distribute(PayoutCommand command) {
        log.info("[settlement-payout:simulated] settlementId={} number={} lines={}", command.settlementId(),
                command.settlementNumber(), command.lines().size());
        return new PayoutResult(command.lines().stream()
                .map(line -> new LineResult(line.payoutId(), Outcome.PAID,
                        "SIM-%s-%s".formatted(command.settlementNumber(), line.payee().name()), null, null))
                .toList());
    }
}
