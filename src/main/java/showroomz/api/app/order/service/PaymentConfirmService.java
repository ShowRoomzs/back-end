package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.order.dto.PaymentDto;
import showroomz.api.app.order.service.PaymentTransitions.PaidOutcome;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.type.MismatchReason;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PortOnePayment;
import showroomz.global.payment.portone.PortOnePaymentGateway;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * 결제 확정 — {@code confirm(paymentId)} 하나를 complete·웹훅·만료 전 조회·대사가 함께 쓴다(결제 계획서 4-5).
 *
 * <p>순서 — ① 결제 행 ② 단락 ③ 포트원 GET(트랜잭션 밖) ④ PAID 검증·전이 ⑤ CANCELLED 수렴 ⑥ FAILED ⑦ 그 밖은 아무것도 안 함.
 * 앱이 보낸 결제창 결과는 힌트일 뿐이다 — 포트원 조회 결과만 믿는다(2-3 ②).
 *
 * <p>단락 집합은 트리거에 따라 다르다. 앱의 complete 는 PAID·FAILED 도 단락한다(완료 화면에서 반복 호출해도 포트원으로
 * 나가지 않는다). 웹훅·대사는 PAID 를 다시 읽는다 — PG 콘솔 취소({@code Transaction.Cancelled})를 받아야 하고,
 * FAILED 뒤 같은 paymentId 로 결제된 경우도 봐야 한다(5-7 「도착 순서는 믿지 않는다」).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    public enum Trigger {
        APP_COMPLETE(Set.of(PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.CANCELLED,
                PaymentStatus.CANCELLED_MISMATCH, PaymentStatus.CANCEL_FAILED)),
        WEBHOOK(PaymentStatus.CLOSED),
        EXPIRY_CHECK(PaymentStatus.CLOSED),
        RECONCILIATION(PaymentStatus.CLOSED),
        CANCEL_CONVERGENCE(PaymentStatus.CLOSED);

        private final Set<PaymentStatus> shortCircuit;

        Trigger(Set<PaymentStatus> shortCircuit) {
            this.shortCircuit = shortCircuit;
        }
    }

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PortOnePaymentGateway gateway;
    private final PaymentTransitions transitions;
    private final PaymentCancelExecutor cancelExecutor;
    private final PaymentAlerts alerts;

    /** 앱 콜백(5-4) — 본인 주문만. */
    public PaymentDto.CompleteResponse complete(Long userId, String paymentId, PaymentDto.ClientResult clientResult) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!ownedBy(payment, userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        if (clientResult != null && clientResult.getCode() != null) {
            log.info("결제창 결과(힌트) - paymentId: {}, code: {}, message: {}", paymentId, clientResult.getCode(),
                    clientResult.getMessage());
        }
        return confirm(paymentId, Trigger.APP_COMPLETE);
    }

    /**
     * 확정 — 멱등이다. 둘째 호출은 조회 결과가 같고 조건부 UPDATE 가 0행이라 현재 상태를 돌려준다.
     *
     * @throws BusinessException PAYMENT_NOT_FOUND · PAYMENT_GATEWAY_ERROR(조회 실패 — 상태 불변)
     */
    public PaymentDto.CompleteResponse confirm(String paymentId, Trigger trigger) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (trigger.shortCircuit.contains(payment.getStatus())) {
            return current(paymentId);
        }

        Optional<PortOnePayment> remote;
        try {
            remote = gateway.getPayment(paymentId);
        } catch (PaymentGatewayException e) {
            log.warn("포트원 조회 실패 - paymentId: {} - {}", paymentId, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
        if (remote.isEmpty()) {
            // 사전 등록만 되고 결제창을 연 적 없는 결제 — 「결제 안 됨」이다.
            return current(paymentId);
        }
        PortOnePayment portone = remote.get();
        LocalDateTime now = LocalDateTime.now();
        transitions.saveRawResponse(paymentId, portone.rawJson());

        switch (portone.status()) {
            case PAID -> handlePaid(payment, portone, now);
            case CANCELLED -> transitions.completeCancel(paymentId, null, portone.rawJson(), now);
            case FAILED -> transitions.markFailed(paymentId, portone.failCode(), portone.failMessage(), now);
            case PARTIAL_CANCELLED -> alerts.warning("부분 취소는 범위 밖 - paymentId: " + paymentId);
            case VIRTUAL_ACCOUNT_ISSUED -> alerts.warning("가상계좌 채널을 연 적이 없는데 발급됨 - paymentId: " + paymentId);
            default -> log.debug("결제 진행 중 - paymentId: {}, status: {}", paymentId, portone.status());
        }
        return current(paymentId);
    }

    /** ④ — 검증 통과면 T4, 아니면 자동 취소 선점(T6) → 포트원 취소(커밋 뒤). */
    private void handlePaid(Payment payment, PortOnePayment portone, LocalDateTime now) {
        boolean valid = gateway.storeId().equals(portone.storeId())
                && Payment.CURRENCY_KRW.equals(portone.currency())
                && portone.totalAmount() != null
                && portone.totalAmount() == payment.getAmount().longValue();
        if (!valid) {
            log.warn("결제 검증 실패 - paymentId: {}, storeId: {}, currency: {}, amount: {} (주문 {})",
                    payment.getPaymentId(), portone.storeId(), portone.currency(), portone.totalAmount(), payment.getAmount());
            autoCancel(payment, MismatchReason.AMOUNT, now);
            return;
        }
        PaidOutcome outcome = transitions.applyPaid(payment, portone, now);
        switch (outcome) {
            case PAID_NOW -> log.info("결제 확정 - paymentId: {}, orderId: {}", payment.getPaymentId(), payment.getOrderId());
            case ALREADY_MINE -> log.debug("이미 확정된 결제 - paymentId: {}", payment.getPaymentId());
            case NOT_MINE_ORDER_CLOSED -> autoCancel(payment, MismatchReason.ORDER_CLOSED, now);
            case NOT_MINE_OTHER_PAYMENT -> autoCancel(payment, MismatchReason.NOT_ORDER_PAYMENT, now);
        }
    }

    /** ④(b) — 선점한 호출만 포트원을 부른다. 동시에 둘이 불일치를 판정해도 외부 호출은 한 번이다(4-7). */
    private void autoCancel(Payment payment, MismatchReason reason, LocalDateTime now) {
        if (!transitions.claimAutoCancel(payment.getPaymentId(), reason, now)) {
            return;
        }
        cancelExecutor.execute(payment.getPaymentId(), payment.getAmount(), "자동 취소 - " + reason.name(), now);
    }

    @Transactional(readOnly = true)
    public PaymentDto.CompleteResponse current(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return PaymentDto.CompleteResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus())
                .paymentStatus(payment.getStatus())
                .failReason(failReason(payment, order))
                .build();
    }

    private static String failReason(Payment payment, Order order) {
        return switch (payment.getStatus()) {
            case FAILED -> payment.getFailMessage() != null ? payment.getFailMessage() : "결제에 실패했습니다.";
            case CANCELLED_MISMATCH, CANCEL_REQUESTED -> payment.getMismatchReason() == null ? null : switch (payment.getMismatchReason()) {
                case AMOUNT -> ErrorCode.PAYMENT_AMOUNT_MISMATCH.getMessage();
                case ORDER_CLOSED -> ErrorCode.ORDER_ALREADY_CLOSED.getMessage();
                case NOT_ORDER_PAYMENT -> "이미 다른 결제로 완료된 주문이라 이 결제는 취소되었습니다.";
            };
            case EXPIRED -> order.getStatus() == OrderStatus.EXPIRED ? ErrorCode.ORDER_ALREADY_CLOSED.getMessage() : null;
            default -> null;
        };
    }

    private boolean ownedBy(Payment payment, Long userId) {
        return orderRepository.existsByIdAndUser_Id(payment.getOrderId(), userId);
    }
}
