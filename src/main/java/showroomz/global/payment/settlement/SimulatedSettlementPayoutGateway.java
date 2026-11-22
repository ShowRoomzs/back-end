package showroomz.global.payment.settlement;

import lombok.extern.slf4j.Slf4j;
import showroomz.domain.settlement.port.SettlementPayoutGateway;

/**
 * 지급 시뮬레이터(44 어드민 설계서 0-5 · {@code settlement.payout.mode = SIMULATED}) — 묶음 지급 경로를 탄다(60_KG이니시스_지급대행_전환_계획서.md
 * D-1 ⑥ — 시뮬레이터로 도는 정산 테스트 전부가 묶음 경로의 회귀 시험이 된다). 묶음을 올리는 즉시 지급 완료로 돌려준다 — 리드 0 · 마감 없음이라
 * 「예정일 당일 지급」 그대로다. 참조번호는 {@code SIM-{정산번호}-{수취자}}다. 개발 · QA · 통합 테스트가 「생성 → 확정 → 지급 완료 → 공구 SETTLED →
 * 영수증」을 끝까지 돌리게 하려고 둔다 — <b>운영 프로필에서는 기동하지 않는다</b>({@code SettlementPayoutGatewayConfig}).
 */
@Slf4j
public class SimulatedSettlementPayoutGateway implements SettlementPayoutGateway {

    /** 행 참조번호 접두 — 도메인이 {@code {접두}-{정산번호}-{수취자}} 로 붙인다. */
    static final String REFERENCE_PREFIX = "SIM";

    @Override
    public boolean groupsByPayee() {
        return true;
    }

    @Override
    public GroupSubmitResult submitGroup(GroupCommand command) {
        log.info("[settlement-payout:simulated] groupId={} payee={} refId={} payDate={} total={}", command.groupId(),
                command.payee(), command.payeeRefId(), command.payDate(), command.total());
        return new GroupSubmitResult(Outcome.PAID, null, null, REFERENCE_PREFIX);
    }

    /** 정산별 경로(토스 시절 호출자 호환) — 묶음 경로에서는 부르지 않는다. */
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
