package showroomz.api.common.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.type.SettlementPayee;

import java.io.IOException;
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
 * <p>토스 모드 흉내(토스 설계서 5절) — {@link #requestedFor} 로 지시가 REQUESTED(+ 참조 id · PG 지급 id)로 남게 하고, {@link #completeFor} ·
 * {@link #failLookupFor} 로 다음 {@link #lookup} 이 닫게 한다. {@link #balance} 로 잔액 선검사를, {@link #deferFor} 로 동기 잔액 거절을
 * 심는다. {@link #fromWebhook} 은 토스 {@code Payout} 객체의 {@code status} 를 그대로 매핑한다.
 */
@Primary
@Component
public class FakeSettlementPayoutGateway implements SettlementPayoutGateway {

    public static final String EXPRESS = "EXPRESS";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<SettlementPayee, LineResult> scripted = new EnumMap<>(SettlementPayee.class);
    private final Set<SettlementPayee> requested = EnumSet.noneOf(SettlementPayee.class);
    private final Set<SettlementPayee> deferred = EnumSet.noneOf(SettlementPayee.class);
    private final Map<SettlementPayee, LineResult> lookupScripts = new EnumMap<>(SettlementPayee.class);
    private final List<PayoutCommand> calls = new ArrayList<>();
    private final List<List<PayoutLookup>> lookups = new ArrayList<>();
    private boolean unresponsive;
    private boolean lookupUnresponsive;
    private Reconciliation reconciliation;
    private Long balance;

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
                        script.failReason(), payoutRef(command.settlementNumber(), line.payee(), line.attempt()), null, null);
            }
            if (deferred.contains(line.payee())) {
                return new LineResult(line.payoutId(), Outcome.DEFERRED, null, "INSUFFICIENT_BALANCE", "잔액 부족",
                        payoutRef(command.settlementNumber(), line.payee(), line.attempt()), null, null);
            }
            if (requested.contains(line.payee())) {
                String ref = payoutRef(command.settlementNumber(), line.payee(), line.attempt());
                return new LineResult(line.payoutId(), Outcome.REQUESTED, payoutId(ref), null, null, ref, EXPRESS,
                        command.requestedAt() == null ? LocalDate.now() : command.requestedAt().toLocalDate());
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
            if (lookup.payee() == SettlementPayee.PLATFORM) {
                return new LineResult(lookup.payoutId(), Outcome.PAID, "PLATFORM-" + lookup.settlementNumber(), null, null);
            }
            String ref = lookup.pgTransferId() != null ? lookup.pgTransferId()
                    : payoutRef(lookup.settlementNumber(), lookup.payee(), lookup.attempt());
            String id = lookup.pgPayoutId() != null ? lookup.pgPayoutId() : payoutId(ref);
            LineResult script = lookupScripts.get(lookup.payee());
            if (script == null) {
                return new LineResult(lookup.payoutId(), Outcome.REQUESTED, id, null, null, ref, EXPRESS, null);
            }
            return new LineResult(lookup.payoutId(), script.outcome(), script.pgReference() == null ? id : script.pgReference(),
                    script.failCode(), script.failReason(), ref, EXPRESS, null);
        }).toList();
    }

    /** 토스 {@code Payout.status} 매핑의 축소판 — COMPLETED → PAID · FAILED/REJECTED/CANCELED → FAILED · 그 밖 REQUESTED. */
    @Override
    public synchronized Optional<LineResult> fromWebhook(String payoutJson, PayoutLookup lookup) {
        try {
            JsonNode node = objectMapper.readTree(payoutJson);
            String status = node.path("status").asText("");
            String id = node.path("id").asText(lookup.pgPayoutId());
            String ref = lookup.pgTransferId() != null ? lookup.pgTransferId() : node.path("refPayoutId").asText(null);
            return Optional.of(switch (status) {
                case "COMPLETED" -> new LineResult(lookup.payoutId(), Outcome.PAID, id, null, null, ref, EXPRESS, null);
                case "FAILED", "REJECTED" -> new LineResult(lookup.payoutId(), Outcome.FAILED, id, "BANK_REJECTED",
                        node.path("error").path("code").asText("FAILED"), ref, EXPRESS, null);
                case "CANCELED" -> new LineResult(lookup.payoutId(), Outcome.FAILED, id, "CANCELLED_AT_PG", "PG 지급 취소", ref,
                        EXPRESS, null);
                default -> new LineResult(lookup.payoutId(), Outcome.REQUESTED, id, null, null, ref, EXPRESS, null);
            });
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<Reconciliation> reconcile(LocalDate payoutDate) {
        return Optional.ofNullable(reconciliation);
    }

    @Override
    public synchronized Optional<Long> availableBalance() {
        return Optional.ofNullable(balance);
    }

    public static String payoutRef(String settlementNumber, SettlementPayee payee, int attempt) {
        return "%s-%s-%d".formatted(settlementNumber, payee.name(), attempt);
    }

    /** 가짜 PG 발급 지급 id — 참조 id 에서 결정적으로. */
    public static String payoutId(String payoutRef) {
        return "FAKE-PO-" + payoutRef;
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
        deferred.remove(payee);
    }

    // ------------------------------------------------------------------ 토스 모드 흉내

    /** 토스처럼 — 지시는 요청만 받아들여지고 REQUESTED(+ 참조 id · 지급 id)로 남는다(결과는 {@link #lookup} · 웹훅). */
    public synchronized void requestedFor(SettlementPayee... payees) {
        requested.addAll(List.of(payees));
    }

    /** 동기 잔액 거절({@code EXCEEDED_PAYOUT_BALANCE_AMOUNT}) — 지시가 DEFERRED 로 돌아온다. */
    public synchronized void deferFor(SettlementPayee... payees) {
        deferred.addAll(List.of(payees));
    }

    /** 잔액 선검사 값 — null 이면 선검사 없음(SIMULATED 처럼). */
    public synchronized void balance(Long available) {
        this.balance = available;
    }

    /** 다음 결과 조회에서 이 수취자 행이 지급 완료로 닫힌다. {@code pgPayoutId} 가 null 이면 지시 때 발급된 id 그대로. */
    public synchronized void completeFor(SettlementPayee payee, String pgPayoutId) {
        lookupScripts.put(payee, new LineResult(null, Outcome.PAID, pgPayoutId, null, null));
    }

    public synchronized void completeFor(SettlementPayee payee) {
        completeFor(payee, null);
    }

    /** 다음 결과 조회에서 이 수취자 행이 분배 실패로 닫힌다. */
    public synchronized void failLookupFor(SettlementPayee payee, String code, String reason) {
        lookupScripts.put(payee, new LineResult(null, Outcome.FAILED, null, code, reason));
    }

    /** 다음 결과 조회에서 REQUESTED 로 남되 사유가 붙는다(사람을 부른다). */
    public synchronized void flagLookupFor(SettlementPayee payee, String code, String reason) {
        lookupScripts.put(payee, new LineResult(null, Outcome.REQUESTED, null, code, reason));
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
        deferred.clear();
        lookupScripts.clear();
        calls.clear();
        lookups.clear();
        unresponsive = false;
        lookupUnresponsive = false;
        reconciliation = null;
        balance = null;
    }
}
