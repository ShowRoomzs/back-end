package showroomz.api.common.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.type.SettlementPayee;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 테스트용 지급 게이트웨이(44 구현 계획서 1-2 · {@code FakePaymentGateway} 관례) — 통합 테스트 컨텍스트 공용 빈이라 테스트마다 {@link #reset()}한다.
 * 세 모드가 있다.
 *
 * <ul>
 *   <li><b>시뮬레이터(기본)</b> — 시뮬레이터처럼 <b>묶음 지급 경로</b>를 타고 올리는 즉시 지급 완료(60 이니시스 전환 계획서 D-1 ⑥ — 시뮬레이터로 도는
 *   정산 테스트 전부가 묶음 경로의 회귀 시험이다). 참조번호 {@code FAKE-{정산번호}-{수취자}}. 수취자별 실패({@link #failFor}) · 결과 모름
 *   ({@link #unresponsive})을 심는다. 올린 묶음은 {@link #groupCalls()}.</li>
 *   <li><b>이니시스</b>({@link #inicisMode()}) — 리드 1영업일 · 마감 15:00 · {@code NEXT_DAY}. 올리면 접수(REQUESTED)만 되고, 같은 키로 다시 올리면
 *   마지막 합계로 덮어쓴다({@link #uploaded()}). 결과는 {@link #completeFor} · {@link #failLookupFor} · {@link #remitAmountFor} 로 다음
 *   {@link #lookupGroups} 가 돌려준다. 잔액은 {@link #balance}(테스트 중간에 바꿀 수 있다 — 입금).</li>
 *   <li><b>토스</b>({@link #tossMode()}) — 정산별 경로. {@link #requestedFor} 로 지시가 REQUESTED(+ 참조 id · PG 지급 id)로 남게 하고,
 *   {@link #completeFor} · {@link #failLookupFor} 로 다음 {@link #lookup} 이 닫게 한다. {@link #fromWebhook} 은 토스 {@code Payout} 의 {@code status}
 *   를 그대로 매핑한다.</li>
 * </ul>
 */
@Primary
@Component
public class FakeSettlementPayoutGateway implements SettlementPayoutGateway {

    public static final String EXPRESS = "EXPRESS";
    public static final String NEXT_DAY = "NEXT_DAY";

    public enum Mode { SIMULATED, INICIS, TOSS }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<SettlementPayee, LineResult> scripted = new EnumMap<>(SettlementPayee.class);
    private final Set<SettlementPayee> requested = EnumSet.noneOf(SettlementPayee.class);
    private final Set<SettlementPayee> deferred = EnumSet.noneOf(SettlementPayee.class);
    private final Map<SettlementPayee, LineResult> lookupScripts = new EnumMap<>(SettlementPayee.class);
    private final Map<SettlementPayee, Long> remitAmounts = new EnumMap<>(SettlementPayee.class);
    private final Map<SettlementPayee, String> retryCodes = new EnumMap<>(SettlementPayee.class);
    private final Map<SettlementPayee, RemittedAccount> remitAccounts = new EnumMap<>(SettlementPayee.class);
    private final List<PayoutCommand> calls = new ArrayList<>();
    private final List<List<PayoutLookup>> lookups = new ArrayList<>();
    private final List<GroupCommand> groupCalls = new ArrayList<>();
    private final List<List<GroupLookup>> groupLookups = new ArrayList<>();
    /** 이니시스 흉내 — PG 에 올라가 있는 합계 {@code idMall|dtPay → total}(같은 키로 다시 올리면 덮어쓴다 · DEL 이면 사라진다). */
    private final Map<String, Long> uploaded = new LinkedHashMap<>();
    private Mode mode = Mode.SIMULATED;
    private boolean unresponsive;
    private boolean lookupUnresponsive;
    private Reconciliation reconciliation;
    private Long balance;
    private int requestSeq;

    // ------------------------------------------------------------------ 모드

    @Override
    public synchronized String provider() {
        return switch (mode) {
            case SIMULATED -> PROVIDER_SIMULATED;
            case INICIS -> PROVIDER_INICIS;
            case TOSS -> PROVIDER_TOSS;
        };
    }

    @Override
    public synchronized boolean groupsByPayee() {
        return mode != Mode.TOSS;
    }

    @Override
    public synchronized int leadBusinessDays() {
        return mode == Mode.INICIS ? 1 : 0;
    }

    @Override
    public synchronized Optional<LocalTime> uploadCutoff() {
        return mode == Mode.INICIS ? Optional.of(LocalTime.of(15, 0)) : Optional.empty();
    }

    @Override
    public synchronized String scheduleType() {
        return mode == Mode.INICIS ? NEXT_DAY : "IMMEDIATE";
    }

    /** 이니시스 흉내로 — 리드 1 · 마감 15:00 · 접수만 · 결과는 {@link #lookupGroups}. */
    public synchronized void inicisMode() {
        mode = Mode.INICIS;
    }

    /** 토스 흉내로 — 정산별 경로(삭제 전까지). */
    public synchronized void tossMode() {
        mode = Mode.TOSS;
    }

    // ------------------------------------------------------------------ 묶음 지급

    @Override
    public synchronized GroupSubmitResult submitGroup(GroupCommand command) {
        groupCalls.add(command);
        if (unresponsive) {
            throw new IllegalStateException("FAKE PG 지급대행 응답 없음(타임아웃)");
        }
        LineResult script = scripted.get(command.payee());
        if (script != null && command.total() > 0) {
            return new GroupSubmitResult(Outcome.FAILED, script.failCode(), script.failReason(), null);
        }
        String retryCode = retryCodes.get(command.payee());
        if (retryCode != null) {
            return new GroupSubmitResult(Outcome.RETRY, retryCode, retryCode + " · FAKE 일시 오류", null);
        }
        if (deferred.contains(command.payee()) && command.total() > 0) {
            return new GroupSubmitResult(Outcome.DEFERRED, "INSUFFICIENT_BALANCE", "잔액 부족", null);
        }
        if (mode == Mode.SIMULATED) {
            return new GroupSubmitResult(Outcome.PAID, null, null, "FAKE");
        }
        String key = key(command.partnerExternalId(), command.payDate());
        if (command.total() > 0) {
            uploaded.put(key, command.total());
        } else {
            uploaded.remove(key);
        }
        return GroupSubmitResult.requested("FAKE-REQ-" + (++requestSeq));
    }

    @Override
    public synchronized List<GroupResult> lookupGroups(List<GroupLookup> lookups) {
        groupLookups.add(List.copyOf(lookups));
        if (lookupUnresponsive) {
            throw new IllegalStateException("FAKE PG 결과 조회 응답 없음");
        }
        List<GroupResult> results = new ArrayList<>();
        for (GroupLookup lookup : lookups) {
            LineResult script = lookupScripts.get(lookup.payee());
            if (script == null) {
                continue;
            }
            Long onPg = uploaded.get(key(lookup.partnerExternalId(), lookup.payDate()));
            long amount = remitAmounts.containsKey(lookup.payee()) ? remitAmounts.get(lookup.payee())
                    : onPg != null ? onPg : lookup.confirmedAmount();
            results.add(new GroupResult(lookup.groupId(), script.outcome(), lookup.payDate(), script.failCode(),
                    script.failReason(), amount, remitAccounts.get(lookup.payee())));
        }
        return results;
    }

    private static String key(String idMall, LocalDate payDate) {
        return idMall + "|" + payDate;
    }

    // ------------------------------------------------------------------ 정산별 지급(토스 흉내)

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

    // ------------------------------------------------------------------ 시나리오

    /** 이 수취자의 지급을 PG 가 거절한다(묶음 · 정산별 모두) — 다음 {@link #reset()} · {@link #succeedFor} 전까지 계속. */
    public synchronized void failFor(SettlementPayee payee, String code, String reason) {
        scripted.put(payee, new LineResult(null, Outcome.FAILED, "FAKE-FAIL-" + payee.name(), code, reason));
    }

    /** 지시를 받은 뒤 응답 없이 끊긴다(결과 모름) — 다음 {@link #reset()} · {@link #responsive()} 전까지 계속. */
    public synchronized void unresponsive() {
        unresponsive = true;
    }

    public synchronized void responsive() {
        unresponsive = false;
    }

    public synchronized void succeedFor(SettlementPayee payee) {
        scripted.remove(payee);
        requested.remove(payee);
        deferred.remove(payee);
    }

    /** 토스처럼 — 지시는 요청만 받아들여지고 REQUESTED(+ 참조 id · 지급 id)로 남는다(결과는 {@link #lookup} · 웹훅). */
    public synchronized void requestedFor(SettlementPayee... payees) {
        requested.addAll(List.of(payees));
    }

    /** 동기 잔액 거절 — 지시가 DEFERRED 로 돌아온다. */
    public synchronized void deferFor(SettlementPayee... payees) {
        deferred.addAll(List.of(payees));
    }

    /** 잔액 선검사 값 — null 이면 선검사 없음(SIMULATED 처럼). 이니시스 흉내에선 테스트 중간에 바꿔 입금을 흉내 낸다. */
    public synchronized void balance(Long available) {
        this.balance = available;
    }

    /** 다음 결과 조회에서 이 수취자 행(묶음)이 지급 완료로 닫힌다. {@code pgPayoutId} 가 null 이면 지시 때 발급된 id 그대로(토스). */
    public synchronized void completeFor(SettlementPayee payee, String pgPayoutId) {
        lookupScripts.put(payee, new LineResult(null, Outcome.PAID, pgPayoutId, null, null));
    }

    public synchronized void completeFor(SettlementPayee payee) {
        completeFor(payee, null);
    }

    /** 이니시스 흉내 — 송금 결과의 금액을 이 값으로(확정 합계와 다르게 해 금액 불일치를 만든다). */
    public synchronized void remitAmountFor(SettlementPayee payee, long amount) {
        remitAmounts.put(payee, amount);
    }

    /** 이 수취자의 묶음 전송이 PG 일시 오류(접수 안 됨 · RETRY)로 돌아온다 — 다음 {@link #reset()} · {@link #recoverFor} 전까지 계속. */
    public synchronized void retryFor(SettlementPayee payee, String pgCode) {
        retryCodes.put(payee, pgCode);
    }

    public synchronized void recoverFor(SettlementPayee payee) {
        retryCodes.remove(payee);
    }

    /** 이니시스 흉내 — 송금 결과에 실린 계좌(은행 코드 · 계좌번호). 계좌 스냅샷과 다르게 해 송금 계좌 불일치를 만든다. */
    public synchronized void remitAccountFor(SettlementPayee payee, String bankCode, String accountNumber) {
        remitAccounts.put(payee, RemittedAccount.of(bankCode, accountNumber));
    }

    /** 다음 결과 조회에서 이 수취자 행(묶음)이 분배 실패로 닫힌다. */
    public synchronized void failLookupFor(SettlementPayee payee, String code, String reason) {
        lookupScripts.put(payee, new LineResult(null, Outcome.FAILED, null, code, reason));
    }

    /** 다음 결과 조회에서 REQUESTED 로 남되 사유가 붙는다(사람을 부른다 · 토스). */
    public synchronized void flagLookupFor(SettlementPayee payee, String code, String reason) {
        lookupScripts.put(payee, new LineResult(null, Outcome.REQUESTED, null, code, reason));
    }

    public synchronized void lookupUnresponsive() {
        lookupUnresponsive = true;
    }

    public synchronized void reconcileWith(int count, long amount) {
        reconciliation = new Reconciliation(count, amount);
    }

    /** 정산별 지시(토스 흉내). */
    public synchronized List<PayoutCommand> calls() {
        return List.copyOf(calls);
    }

    public synchronized List<List<PayoutLookup>> lookups() {
        return List.copyOf(lookups);
    }

    /** 묶음 전송(시뮬레이터 · 이니시스 흉내). */
    public synchronized List<GroupCommand> groupCalls() {
        return List.copyOf(groupCalls);
    }

    public synchronized List<GroupCommand> groupCalls(SettlementPayee payee) {
        return groupCalls.stream().filter(c -> c.payee() == payee).toList();
    }

    public synchronized List<List<GroupLookup>> groupLookups() {
        return List.copyOf(groupLookups);
    }

    /** 이니시스 흉내 — PG 에 올라가 있는 합계({@code idMall|dtPay → total}). */
    public synchronized Map<String, Long> uploaded() {
        return Map.copyOf(uploaded);
    }

    public synchronized void reset() {
        scripted.clear();
        requested.clear();
        deferred.clear();
        lookupScripts.clear();
        remitAmounts.clear();
        retryCodes.clear();
        remitAccounts.clear();
        calls.clear();
        lookups.clear();
        groupCalls.clear();
        groupLookups.clear();
        uploaded.clear();
        mode = Mode.SIMULATED;
        unresponsive = false;
        lookupUnresponsive = false;
        reconciliation = null;
        balance = null;
        requestSeq = 0;
    }
}
