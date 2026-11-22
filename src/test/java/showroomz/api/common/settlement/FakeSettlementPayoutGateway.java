package showroomz.api.common.settlement;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.type.SettlementPayee;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 테스트용 지급 게이트웨이(44 구현 계획서 1-2 · {@code FakePaymentGateway} 관례) — 통합 테스트 컨텍스트 공용 빈이라 테스트마다 {@link #reset()}한다.
 * 지급은 전부 묶음 경로다. 두 모드가 있다.
 *
 * <ul>
 *   <li><b>시뮬레이터(기본)</b> — 시뮬레이터처럼 올리는 즉시 지급 완료(60 이니시스 전환 계획서 D-1 ⑥ — 시뮬레이터로 도는 정산 테스트 전부가 묶음
 *   경로의 회귀 시험이다). 참조번호 {@code FAKE-{정산번호}-{수취자}}. 수취자별 실패({@link #failFor}) · 결과 모름({@link #unresponsive})을 심는다.
 *   올린 묶음은 {@link #groupCalls()}.</li>
 *   <li><b>이니시스</b>({@link #inicisMode()}) — 리드 1영업일 · 마감 15:00 · {@code NEXT_DAY}. 올리면 접수(REQUESTED)만 되고, 같은 키로 다시 올리면
 *   마지막 합계로 덮어쓴다({@link #uploaded()}). 결과는 {@link #completeFor} · {@link #failLookupFor} · {@link #remitAmountFor} 로 다음
 *   {@link #lookupGroups} 가 돌려준다. 잔액은 {@link #balance}(테스트 중간에 바꿀 수 있다 — 입금).</li>
 * </ul>
 */
@Primary
@Component
public class FakeSettlementPayoutGateway implements SettlementPayoutGateway {

    public static final String NEXT_DAY = "NEXT_DAY";

    public enum Mode { SIMULATED, INICIS }

    /** 심어 둔 결과 — 결말 · 사유 코드 · 사유. */
    private record Script(Outcome outcome, String failCode, String failReason) {
    }

    private final Map<SettlementPayee, Script> scripted = new EnumMap<>(SettlementPayee.class);
    private final Set<SettlementPayee> deferred = EnumSet.noneOf(SettlementPayee.class);
    private final Map<SettlementPayee, Script> lookupScripts = new EnumMap<>(SettlementPayee.class);
    private final Map<SettlementPayee, Long> remitAmounts = new EnumMap<>(SettlementPayee.class);
    private final Map<SettlementPayee, String> retryCodes = new EnumMap<>(SettlementPayee.class);
    private final Map<SettlementPayee, RemittedAccount> remitAccounts = new EnumMap<>(SettlementPayee.class);
    private final List<GroupCommand> groupCalls = new ArrayList<>();
    private final List<List<GroupLookup>> groupLookups = new ArrayList<>();
    /** 이니시스 흉내 — PG 에 올라가 있는 합계 {@code idMall|dtPay → total}(같은 키로 다시 올리면 덮어쓴다 · DEL 이면 사라진다). */
    private final Map<String, Long> uploaded = new LinkedHashMap<>();
    private CountDownLatch holdEntered;
    private CountDownLatch holdRelease;
    private Mode mode = Mode.SIMULATED;
    private boolean unresponsive;
    private boolean lookupUnresponsive;
    private Long balance;
    private int requestSeq;

    // ------------------------------------------------------------------ 모드

    @Override
    public synchronized String provider() {
        return mode == Mode.INICIS ? PROVIDER_INICIS : PROVIDER_SIMULATED;
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

    // ------------------------------------------------------------------ 묶음 지급

    /**
     * 다음 묶음 전송 1회를 PG 호출 안에서 붙든다 — {@code entered} 를 내리고 {@code release} 가 내려갈 때까지 기다린다(가짜의 모니터 밖에서 —
     * 다른 스레드의 전송이 이 가짜에 닿는지 볼 수 있게). 같은 수취자 전송 직렬화 시험용.
     */
    public synchronized void holdNextSubmit(CountDownLatch entered, CountDownLatch release) {
        this.holdEntered = entered;
        this.holdRelease = release;
    }

    @Override
    public GroupSubmitResult submitGroup(GroupCommand command) {
        CountDownLatch entered;
        CountDownLatch release;
        synchronized (this) {
            entered = holdEntered;
            release = holdRelease;
            holdEntered = null;
            holdRelease = null;
        }
        if (entered != null) {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return submitGroupNow(command);
    }

    private synchronized GroupSubmitResult submitGroupNow(GroupCommand command) {
        groupCalls.add(command);
        if (unresponsive) {
            throw new IllegalStateException("FAKE PG 지급대행 응답 없음(타임아웃)");
        }
        Script script = scripted.get(command.payee());
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
            Script script = lookupScripts.get(lookup.payee());
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

    @Override
    public synchronized Optional<Long> availableBalance() {
        return Optional.ofNullable(balance);
    }

    private static String key(String idMall, LocalDate payDate) {
        return idMall + "|" + payDate;
    }

    // ------------------------------------------------------------------ 시나리오

    /** 이 수취자의 묶음 전송을 PG 가 거절한다 — 다음 {@link #reset()} · {@link #succeedFor} 전까지 계속. */
    public synchronized void failFor(SettlementPayee payee, String code, String reason) {
        scripted.put(payee, new Script(Outcome.FAILED, code, reason));
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
        deferred.remove(payee);
    }

    /** 동기 잔액 거절 — 묶음 전송이 DEFERRED 로 돌아온다. */
    public synchronized void deferFor(SettlementPayee... payees) {
        deferred.addAll(List.of(payees));
    }

    /** 잔액 선검사 값 — null 이면 선검사 없음(SIMULATED 처럼). 이니시스 흉내에선 테스트 중간에 바꿔 입금을 흉내 낸다. */
    public synchronized void balance(Long available) {
        this.balance = available;
    }

    /** 다음 결과 조회에서 이 수취자 묶음이 지급 완료로 닫힌다. */
    public synchronized void completeFor(SettlementPayee payee) {
        lookupScripts.put(payee, new Script(Outcome.PAID, null, null));
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

    /** 다음 결과 조회에서 이 수취자 묶음이 분배 실패로 닫힌다. */
    public synchronized void failLookupFor(SettlementPayee payee, String code, String reason) {
        lookupScripts.put(payee, new Script(Outcome.FAILED, code, reason));
    }

    public synchronized void lookupUnresponsive() {
        lookupUnresponsive = true;
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
        deferred.clear();
        lookupScripts.clear();
        remitAmounts.clear();
        retryCodes.clear();
        remitAccounts.clear();
        holdEntered = null;
        holdRelease = null;
        groupCalls.clear();
        groupLookups.clear();
        uploaded.clear();
        mode = Mode.SIMULATED;
        unresponsive = false;
        lookupUnresponsive = false;
        balance = null;
        requestSeq = 0;
    }
}
