package showroomz.api.common.settlement;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.type.SettlementPayee;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 테스트용 지급 게이트웨이(44 구현 계획서 1-2 · {@code FakePaymentGateway} 관례) — 기본은 시뮬레이터처럼 전부 즉시 지급 완료이고,
 * 수취자별 결과(실패 · 결과 미룸)를 미리 심을 수 있다. 통합 테스트 컨텍스트 공용 빈이라 테스트마다 {@link #reset()}한다.
 */
@Primary
@Component
public class FakeSettlementPayoutGateway implements SettlementPayoutGateway {

    private final Map<SettlementPayee, LineResult> scripted = new EnumMap<>(SettlementPayee.class);
    private final List<PayoutCommand> calls = new ArrayList<>();
    private boolean unresponsive;

    @Override
    public synchronized PayoutResult distribute(PayoutCommand command) {
        calls.add(command);
        if (unresponsive) {
            throw new IllegalStateException("FAKE PG 지급대행 응답 없음(타임아웃)");
        }
        return new PayoutResult(command.lines().stream().map(line -> {
            LineResult script = scripted.get(line.payee());
            if (script == null) {
                return new LineResult(line.payoutId(), Outcome.PAID,
                        "FAKE-%s-%s".formatted(command.settlementNumber(), line.payee().name()), null, null);
            }
            return new LineResult(line.payoutId(), script.outcome(), script.pgReference(), script.failCode(),
                    script.failReason());
        }).toList());
    }

    /** 이 수취자의 지급을 PG 가 거절한다 — 다음 {@link #reset()} 전까지 계속. */
    public synchronized void failFor(SettlementPayee payee, String code, String reason) {
        scripted.put(payee, new LineResult(null, Outcome.FAILED, "FAKE-FAIL-" + payee.name(), code, reason));
    }

    /** 지시를 받은 뒤 응답 없이 끊긴다(결과 모름) — 다음 {@link #reset()} 전까지 계속. */
    public synchronized void unresponsive() {
        unresponsive = true;
    }

    public synchronized void succeedFor(SettlementPayee payee) {
        scripted.remove(payee);
    }

    public synchronized List<PayoutCommand> calls() {
        return List.copyOf(calls);
    }

    public synchronized void reset() {
        scripted.clear();
        calls.clear();
        unresponsive = false;
    }
}
