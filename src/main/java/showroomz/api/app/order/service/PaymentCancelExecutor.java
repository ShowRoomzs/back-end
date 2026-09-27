package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOneCancelResult;
import showroomz.global.payment.portone.PortOnePaymentGateway;

import java.time.LocalDateTime;

/**
 * 포트원 취소 호출 한 번 — 선점(CANCEL_REQUESTED)이 끝난 뒤 <b>트랜잭션 밖</b>에서 부른다(결제 계획서 4-7 ③④ · 5-6).
 * 사용자 취소 · 자동 취소 · 수렴 재호출이 같은 코드를 탄다. 결과 셋 — 확인됨 · 명시적 거절 · 모름.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCancelExecutor {

    public enum Outcome { CANCELLED, REJECTED, UNKNOWN }

    private final PortOnePaymentGateway gateway;
    private final PaymentTransitions transitions;

    public Outcome execute(String paymentId, long amount, String reason, LocalDateTime now) {
        try {
            PortOneCancelResult result = gateway.cancel(paymentId, amount, reason);
            if (result.isCancelled()) {
                transitions.completeCancel(paymentId, result.pgCancellationId(), result.rawJson(), now);
                return Outcome.CANCELLED;
            }
            // PG 가 접수만 했다 — 웹훅(Transaction.Cancelled)이나 수렴 재조회가 닫는다.
            log.info("결제 취소 접수 대기 - paymentId: {}", paymentId);
            return Outcome.UNKNOWN;
        } catch (PaymentGatewayRejectedException e) {
            transitions.rejectCancel(paymentId, e.getMessage(), now);
            return Outcome.REJECTED;
        } catch (PaymentGatewayException e) {
            // 타임아웃은 실패가 아니다 — 취소됐는데 PAID 로 되돌리면 환불된 주문이 배송된다(5-6).
            log.warn("결제 취소 결과 미확인 - paymentId: {} - {}", paymentId, e.getMessage());
            return Outcome.UNKNOWN;
        }
    }
}
