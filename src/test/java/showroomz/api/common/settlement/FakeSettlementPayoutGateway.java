package showroomz.api.common.settlement;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 테스트용 지급 게이트웨이(44 구현 계획서 1-2 · {@code FakePaymentGateway} 관례) — 기본은 시뮬레이터처럼 전부 즉시 지급 완료이고,
 * 수취자별 결과(실패 · 결과 미룸)를 미리 심을 수 있다. 통합 테스트 컨텍스트 공용 빈이라 테스트마다 {@link #reset()}한다.
 *
 * <p>포트원 모드 흉내(포트원 설계서 5절) — {@link #requestedFor} 로 지시가 REQUESTED(+ 정산건 id)로 남게 하고, {@link #completeFor} 로
 * 다음 {@link #lookup} 이 PAID · FAILED · 사유 붙은 REQUESTED 를 돌려주게 한다.
 */
@Primary
@Component
public class FakeSettlementPayoutGateway implements SettlementPayoutGateway {

    private final Map<SettlementPayee, LineResult> scripted = new EnumMap<>(SettlementPayee.class);
    private final Set<SettlementPayee> requested = EnumSet.noneOf(SettlementPayee.class);
    private final Map<SettlementPayee, LineResult> lookupScripts = new EnumMap<>(SettlementPayee.class);
    private final List<PayoutCommand> calls = new ArrayList<>();
    private final List<List<PayoutLookup>> lookups = new ArrayList<>();
    private boolean unresponsive;
    private boolean lookupUnresponsive;
    private Reconciliation reconciliation;

    @Override
    public synchronized PayoutResult distribute(PayoutCommand command) {
        calls.add(command);
        if (unresponsive) {
            throw new IllegalStateException("FAKE PG 지급대행 응답 없음(타임아웃)");
        }
        return new PayoutResult(command.lines().stream().map(line -> {
            LineResult script = scripted.get(line.payee());
            if (script != null) {
                return new LineResult(line.payoutId(), script.outcome(), script.pgReference(), script.failCode(),
                        script.failReason());
            }
            if (requested.contains(line.payee())) {
                return new LineResult(line.payoutId(), Outcome.REQUESTED, null, null, null,
                        transferId(command.settlementNumber(), line.payee(), line.attempt()));
            }
            return new LineResult(line.payoutId(), Outcome.PAID,
                    "FAKE-%s-%s".formatted(command.settlementNumber(), line.payee().name()), null, null);
        }).toList());
    }

    @Override
    public synchronized List<LineResult> lookup(List<PayoutLookup> lookups) {
        this.lookups.add(List.copyOf(lookups));
        if (lookupUnresponsive) {
            throw new IllegalStateException("FAKE PG 결과 조회 응답 없음");
        }
        return lookups.stream().map(lookup -> {
            String transferId = lookup.pgTransferId() != null ? lookup.pgTransferId()
                    : transferId(lookup.settlementNumber(), lookup.payee(), lookup.attempt());
            LineResult script = lookupScripts.get(lookup.payee());
            if (script == null) {
                return new LineResult(lookup.payoutId(), Outcome.REQUESTED, null, null, null, transferId);
            }
            return new LineResult(lookup.payoutId(), script.outcome(), script.pgReference(), script.failCode(),
                    script.failReason(), transferId);
        }).toList();
    }

    @Override
    public synchronized Optional<Reconciliation> reconcile(LocalDate settlementDate) {
        return Optional.ofNullable(reconciliation);
    }

    public static String transferId(String settlementNumber, SettlementPayee payee, int attempt) {
        return "FAKE-TRF-%s-%s-%d".formatted(settlementNumber, payee.name(), attempt);
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
        requested.remove(payee);
    }

    // ------------------------------------------------------------------ 포트원 모드 흉내

    /** 포트원처럼 — 지시는 정산건만 올리고 REQUESTED 로 남는다(결과는 {@link #lookup}). */
    public synchronized void requestedFor(SettlementPayee... payees) {
        requested.addAll(List.of(payees));
    }

    /** 다음 결과 조회에서 이 수취자 행이 지급 완료로 닫힌다. */
    public synchronized void completeFor(SettlementPayee payee, String pgPayoutId) {
        lookupScripts.put(payee, new LineResult(null, Outcome.PAID, pgPayoutId, null, null));
    }

    /** 다음 결과 조회에서 이 수취자 행이 분배 실패로 닫힌다. */
    public synchronized void failLookupFor(SettlementPayee payee, String code, String reason) {
        lookupScripts.put(payee, new LineResult(null, Outcome.FAILED, "FAKE-PAYOUT-FAIL", code, reason));
    }

    /** 다음 결과 조회에서 REQUESTED 로 남되 사유가 붙는다(지급액 불일치 등 — 사람을 부른다). */
    public synchronized void flagLookupFor(SettlementPayee payee, String code, String reason) {
        lookupScripts.put(payee, new LineResult(null, Outcome.REQUESTED, "FAKE-PAYOUT-FLAG", code, reason));
    }

    public synchronized void lookupUnresponsive() {
        lookupUnresponsive = true;
    }

    public synchronized void reconcileWith(int count, long amount) {
        reconciliation = new Reconciliation(count, amount);
    }

    public synchronized List<PayoutCommand> calls() {
        return List.copyOf(calls);
    }

    public synchronized List<List<PayoutLookup>> lookups() {
        return List.copyOf(lookups);
    }

    public synchronized void reset() {
        scripted.clear();
        requested.clear();
        lookupScripts.clear();
        calls.clear();
        lookups.clear();
        unresponsive = false;
        lookupUnresponsive = false;
        reconciliation = null;
    }
}
