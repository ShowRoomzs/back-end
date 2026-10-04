package showroomz.global.payment.portone;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트·로컬 무자격 실행용 게이트웨이({@code portone.enabled=false}, 결제 계획서 6-1).
 *
 * <p>없는 것을 대신하는 빈 구현이 아니라 <b>시나리오를 지정하는 테스트 더블</b>이다. 기본 동작은 「사전 등록된 결제는 조회하면
 * 등록 금액으로 PAID」라 자격 없이도 주문 → 결제창 없이 complete → 주문 완료가 돈다. 통합 테스트는 결제마다 시나리오를 바꿔
 * 금액 불일치·FAILED·404·타임아웃·취소 거절을 만들어 낸다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "portone", name = "enabled", havingValue = "false")
public class FakePaymentGateway implements PortOnePaymentGateway {

    public static final String STORE_ID = "store-fake";
    public static final String CHANNEL_KEY = "channel-key-fake";

    public enum Failure { TIMEOUT, REJECTED }

    private final Map<String, Long> preRegistered = new ConcurrentHashMap<>();
    private final Map<String, Optional<PortOnePayment>> lookups = new ConcurrentHashMap<>();
    private final Map<String, Failure> lookupFailures = new ConcurrentHashMap<>();
    private final Map<String, Failure> preRegisterFailures = new ConcurrentHashMap<>();
    private final Map<String, Object> cancelBehaviours = new ConcurrentHashMap<>();
    private final List<PortOnePayment> listed = new CopyOnWriteArrayList<>();
    private final List<String> cancelCalls = new CopyOnWriteArrayList<>();
    private final List<String> preRegisterCalls = new CopyOnWriteArrayList<>();
    private final AtomicInteger lookupCalls = new AtomicInteger();
    private final java.util.concurrent.atomic.AtomicBoolean failNextPreRegister = new java.util.concurrent.atomic.AtomicBoolean();

    // ------------------------------------------------------------------ 시나리오

    public void reset() {
        preRegistered.clear();
        lookups.clear();
        lookupFailures.clear();
        preRegisterFailures.clear();
        cancelBehaviours.clear();
        listed.clear();
        cancelCalls.clear();
        preRegisterCalls.clear();
        lookupCalls.set(0);
        failNextPreRegister.set(false);
    }

    /** 조회 결과를 고정한다. */
    public void willReturn(String paymentId, PortOnePayment payment) {
        lookups.put(paymentId, Optional.ofNullable(payment));
        lookupFailures.remove(paymentId);
    }

    public void willReturnPaid(String paymentId, long amount) {
        willReturn(paymentId, PortOnePayment.of(paymentId, PortOneStatus.PAID, STORE_ID, amount));
    }

    public void willReturnStatus(String paymentId, PortOneStatus status, long amount) {
        willReturn(paymentId, PortOnePayment.of(paymentId, status, STORE_ID, amount));
    }

    public void willReturnNotFound(String paymentId) {
        lookups.put(paymentId, Optional.empty());
        lookupFailures.remove(paymentId);
    }

    /** 조회 통신 실패(결과 모름). */
    public void willFailLookup(String paymentId) {
        lookupFailures.put(paymentId, Failure.TIMEOUT);
    }

    public void willFailPreRegister(String paymentId, Failure failure) {
        preRegisterFailures.put(paymentId, failure);
    }

    /** 다음 사전 등록 호출 한 번을 타임아웃시킨다 — paymentId 를 미리 알 수 없는 주문 생성 테스트용. */
    public void willFailPreRegisterNext() {
        failNextPreRegister.set(true);
    }

    public void willFailCancel(String paymentId, Failure failure) {
        cancelBehaviours.put(paymentId, failure);
    }

    public void willAnswerCancel(String paymentId, PortOneCancelResult.Outcome outcome) {
        cancelBehaviours.put(paymentId, outcome);
    }

    public void willList(PortOnePayment payment) {
        listed.add(payment);
    }

    public List<String> cancelCalls() {
        return List.copyOf(cancelCalls);
    }

    public List<String> preRegisterCalls() {
        return List.copyOf(preRegisterCalls);
    }

    public int lookupCalls() {
        return lookupCalls.get();
    }

    public boolean isPreRegistered(String paymentId) {
        return preRegistered.containsKey(paymentId);
    }

    // ------------------------------------------------------------------ 게이트웨이

    @Override
    public String storeId() {
        return STORE_ID;
    }

    @Override
    public void preRegister(String paymentId, long totalAmount, String currency) {
        preRegisterCalls.add(paymentId);
        Failure failure = preRegisterFailures.remove(paymentId);
        if (failure == null && failNextPreRegister.compareAndSet(true, false)) {
            failure = Failure.TIMEOUT;
        }
        if (failure == Failure.TIMEOUT) {
            throw new PaymentGatewayException("fake: 사전 등록 타임아웃");
        }
        if (failure == Failure.REJECTED) {
            throw new PaymentGatewayRejectedException(400, "INVALID_REQUEST", "fake: 사전 등록 거절");
        }
        preRegistered.put(paymentId, totalAmount);
    }

    @Override
    public Optional<PortOnePayment> getPayment(String paymentId) {
        lookupCalls.incrementAndGet();
        if (lookupFailures.containsKey(paymentId)) {
            throw new PaymentGatewayException("fake: 조회 타임아웃");
        }
        Optional<PortOnePayment> fixed = lookups.get(paymentId);
        if (fixed != null) {
            return fixed;
        }
        Long amount = preRegistered.get(paymentId);
        if (amount == null) {
            return Optional.empty();
        }
        // 기본 시나리오 — 등록 금액으로 결제됐다(로컬 무자격 실행에서 complete 경로가 끝까지 돈다).
        return Optional.of(PortOnePayment.of(paymentId, PortOneStatus.PAID, STORE_ID, amount));
    }

    @Override
    public PortOneCancelResult cancel(String paymentId, long amount, String reason) {
        cancelCalls.add(paymentId);
        Object behaviour = cancelBehaviours.get(paymentId);
        if (behaviour == Failure.TIMEOUT) {
            throw new PaymentGatewayException("fake: 취소 타임아웃");
        }
        if (behaviour == Failure.REJECTED) {
            throw new PaymentGatewayRejectedException(409, "PG_PROVIDER", "fake: 취소 거절");
        }
        PortOneCancelResult.Outcome outcome = behaviour instanceof PortOneCancelResult.Outcome o
                ? o : PortOneCancelResult.Outcome.SUCCEEDED;
        if (outcome != PortOneCancelResult.Outcome.PENDING) {
            // 취소된 결제는 이후 조회에서 CANCELLED 로 보인다 — 수렴 경로가 실 PG 와 같은 답을 받는다.
            Long registered = preRegistered.get(paymentId);
            long total = registered != null ? registered : amount;
            lookups.put(paymentId, Optional.of(PortOnePayment.of(paymentId, PortOneStatus.CANCELLED, STORE_ID, total)));
        }
        return new PortOneCancelResult(outcome, "fake-cancel-" + paymentId, "{\"fake\":true}");
    }

    @Override
    public List<PortOnePayment> listPayments(LocalDateTime from, LocalDateTime until, List<PortOneStatus> statuses) {
        List<PortOnePayment> result = new ArrayList<>();
        for (PortOnePayment payment : listed) {
            if (statuses.contains(payment.status())) {
                result.add(payment);
            }
        }
        return result;
    }

    @Override
    public Optional<String> channelKeyFor(PaymentMethod method, EasyPayProvider easyPayProvider) {
        if (method == PaymentMethod.EASY_PAY && easyPayProvider == null) {
            return Optional.empty();
        }
        return Optional.of(CHANNEL_KEY);
    }
}
