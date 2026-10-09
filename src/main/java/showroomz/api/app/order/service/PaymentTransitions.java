package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.cart.repository.CartRepository;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.entity.PaymentCancel;
import showroomz.domain.payment.repository.PaymentCancelRepository;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.type.CancelRequester;
import showroomz.domain.payment.type.MismatchReason;
import showroomz.domain.payment.type.PaymentCancelStatus;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.payment.portone.PortOnePayment;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 결제·주문 전이의 <b>짧은 트랜잭션들</b>(결제 계획서 4-5 · 4-7 T4~T16). 외부 HTTP 는 여기 없다 — 호출자가 트랜잭션 밖에서
 * 포트원을 부르고 결과를 여기로 가져온다. 모든 메서드는 조건부 UPDATE 의 결과(0행/1행)로 분기하며 예외로 경합을 알리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentTransitions {

    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final OrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;
    private final CartRepository cartRepository;
    private final StockReleaser stockReleaser;
    private final OrderFulfillmentService fulfillmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderProperties orderProperties;
    private final PaymentAlerts alerts;
    private final OrderRefundTaskRepository refundTaskRepository;

    public enum PaidOutcome { PAID_NOW, ALREADY_MINE, NOT_MINE_ORDER_CLOSED, NOT_MINE_OTHER_PAYMENT }

    public enum CancelCompletion { CANCELLED, CANCELLED_MISMATCH, NOOP }

    // ------------------------------------------------------------------ 확정(T4)

    /**
     * T4 — 주문 PAYMENT_PENDING → PAID(paid_payment_id = 이 결제) → 결제 PAID → 상품 PAID · 장바구니 정리 · 이벤트 등록.
     * n=0 이면 주문을 다시 읽어 「이미 내가 완료한 것」과 「내 결제가 아님」을 가른다(4-5 ④a).
     */
    @Transactional
    public PaidOutcome applyPaid(Payment payment, PortOnePayment remote, LocalDateTime now) {
        Long orderId = payment.getOrderId();
        String paymentId = payment.getPaymentId();
        LocalDateTime paidAt = remote.paidAt() != null ? remote.paidAt() : now;

        if (orderRepository.markPaid(orderId, paymentId, paidAt) == 1) {
            int m = paymentRepository.markPaid(paymentId, PaymentStatus.PAYABLE, paidAt, remote.pgProvider(),
                    remote.transactionId(), remote.methodJson());
            if (m != 1) {
                // 불변식 위반 — 주문은 이 결제로 PAID 인데 결제 행이 전이 불가 상태. 롤백하고 사람을 부른다.
                alerts.error("결제 확정 불변식 위반 - orderId: " + orderId + ", paymentId: " + paymentId);
                throw new IllegalStateException("결제 행 전이 실패: " + paymentId);
            }
            orderProductRepository.transitionByOrder(orderId, EnumSet.of(OrderProductStatus.PENDING), OrderProductStatus.PAID);
            // 하위주문의 탄생(34 설계서 5-1) — 같은 트랜잭션에서 NEW 전이 · 하위주문번호 · 발송기한 스냅샷.
            fulfillmentService.activateOnPaid(orderId, paidAt);
            List<Long> cartIds = orderProductRepository.findByOrderIdWithVariant(orderId).stream()
                    .map(OrderProduct::getCartId).filter(Objects::nonNull).distinct().toList();
            if (!cartIds.isEmpty()) {
                cartRepository.deleteAllByIdInBatch(cartIds);
            }
            Order order = orderRepository.findById(orderId).orElseThrow();
            eventPublisher.publishEvent(new OrderPaidEvent(orderId, order.getOrderNumber(), paymentId,
                    order.getUser().getId(), paidAt));
            return PaidOutcome.PAID_NOW;
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
        if (order.isPaidWith(paymentId)) {
            return PaidOutcome.ALREADY_MINE;
        }
        return order.getStatus() == OrderStatus.PAID
                ? PaidOutcome.NOT_MINE_OTHER_PAYMENT
                : PaidOutcome.NOT_MINE_ORDER_CLOSED;
    }

    /** T5 — 원문 저장. 상태 전이와 무관한 별도 트랜잭션(4-5 ⑧). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRawResponse(String paymentId, String raw) {
        if (raw != null) {
            paymentRepository.saveRawResponse(paymentId, raw);
        }
    }

    /** 4-5 ⑥ — 포트원이 명시적으로 FAILED 라고 한 경우뿐이다. */
    @Transactional
    public boolean markFailed(String paymentId, String failCode, String failMessage, LocalDateTime now) {
        String message = failMessage != null && failMessage.length() > 500 ? failMessage.substring(0, 500) : failMessage;
        return paymentRepository.markFailed(paymentId, now, failCode, message) == 1;
    }

    // ------------------------------------------------------------------ 취소 선점 · 수렴

    /** T6 — 불일치 자동 취소 선점(4-5 ④b). 통과한 호출만 포트원을 부른다. */
    @Transactional
    public boolean claimAutoCancel(String paymentId, MismatchReason reason, LocalDateTime now) {
        LocalDateTime retryAt = now.plusMinutes(orderProperties.getCancelRetryBaseMinutes());
        if (paymentRepository.claimCancel(paymentId, PaymentStatus.AUTO_CANCEL_CLAIMABLE, reason, now, retryAt) != 1) {
            return false;
        }
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        paymentCancelRepository.save(PaymentCancel.requested(payment, payment.getAmount(),
                "자동 취소 - " + reason.name(), CancelRequester.SYSTEM, now));
        return true;
    }

    /**
     * T7 / T9 / 4-5 ⑤ — 취소 확인. 사용자 취소는 CANCELLED, 자동 취소는 CANCELLED_MISMATCH. 이 결제로 PAID 인 주문이면
     * 주문도 CANCELLED 로 내리고 재고를 돌려놓는다(1회 규칙). 주문이 PAYMENT_PENDING 이면 결제만 닫힌다(재시도 가능).
     */
    @Transactional
    public CancelCompletion completeCancel(String paymentId, String pgCancellationId, String raw, LocalDateTime now) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            return CancelCompletion.NOOP;
        }
        // 부분 환불(환불 큐 집행)이 있었던 결제 — 포트원 CANCELLED 는 부분 취소 누적이 결제액에 닿은 것이다. 주문 전체 취소로
        // 수렴시키면 이미 각 경로가 닫은 항목의 재고가 한 번 더 돌아간다. 환불 큐 쪽이 결제를 닫는다(1009 기획 수정본 2-3).
        if (payment.getStatus() == PaymentStatus.PAID && (payment.getCancelledAmount() > 0
                || refundTaskRepository.existsByPaymentIdAndStatus(paymentId, RefundTaskStatus.EXECUTING))) {
            paymentRepository.closeIfFullyRefunded(paymentId);
            return CancelCompletion.NOOP;
        }
        EnumSet<PaymentStatus> from = EnumSet.of(PaymentStatus.PAID, PaymentStatus.CANCEL_REQUESTED);
        boolean mismatch = payment.getMismatchReason() != null;
        int n = mismatch
                ? paymentRepository.markCancelledByMismatch(paymentId, from)
                : paymentRepository.markCancelledByRequest(paymentId, from);
        if (n != 1) {
            return CancelCompletion.NOOP;
        }
        String reason = paymentCancelRepository.findByPayment_PaymentIdAndStatus(paymentId, PaymentCancelStatus.REQUESTED).stream()
                .map(PaymentCancel::getReason).filter(Objects::nonNull).findFirst()
                .orElse(mismatch ? "자동 취소 - " + payment.getMismatchReason() : "결제 취소");
        if (paymentCancelRepository.finishRequested(paymentId, PaymentCancelStatus.SUCCEEDED, pgCancellationId, now, raw) == 0) {
            // 선점 없이 취소된 결제(PG 콘솔 취소 등) — 기록은 남긴다.
            Payment attached = paymentRepository.findById(paymentId).orElseThrow();
            PaymentCancel record = PaymentCancel.requested(attached, attached.getAmount(), "PG 측 취소 수렴",
                    CancelRequester.SYSTEM, now);
            paymentCancelRepository.save(record);
            paymentCancelRepository.finishRequested(paymentId, PaymentCancelStatus.SUCCEEDED, pgCancellationId, now, raw);
        }
        Long orderId = payment.getOrderId();
        if (orderRepository.cancelPaid(orderId, paymentId, now, reason) == 1) {
            stockReleaser.release(orderId, now);
            // 하위주문에도 취소의 사실을 남긴다(34 설계서 5-2) — 그룹 CANCELLED(CONSUMER) + 항목 취소 메타 + 이력.
            fulfillmentService.applyConsumerCancel(orderId, now);
        }
        if (mismatch) {
            alerts.error("결제 자동 취소 - paymentId: " + paymentId + ", 사유: " + payment.getMismatchReason());
            return CancelCompletion.CANCELLED_MISMATCH;
        }
        return CancelCompletion.CANCELLED;
    }

    /** T9' / T15 — 포트원이 명시적으로 거절. 사용자 취소는 PAID 복귀(주문 그대로), 자동 취소는 CANCEL_FAILED(운영자 처리). */
    @Transactional
    public void rejectCancel(String paymentId, String error, LocalDateTime now) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            return;
        }
        if (payment.getMismatchReason() == null) {
            paymentRepository.revertCancelToPaid(paymentId);
            paymentCancelRepository.finishRequested(paymentId, PaymentCancelStatus.FAILED, null, now, error);
            alerts.warning("사용자 취소 거절 - paymentId: " + paymentId + " - " + error);
            return;
        }
        markCancelFailedTx(paymentId, error, now);
    }

    /** 취소 호출 결과를 모른 채 끝났다 — 다음 재조회를 지수로 미룬다. 상한 도달 시 CANCEL_FAILED(4-4 둘째 단계). */
    @Transactional
    public void bumpCancelRetry(String paymentId, LocalDateTime now) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.CANCEL_REQUESTED) {
            return;
        }
        int attempts = payment.getCancelAttempts() + 1;
        if (attempts >= orderProperties.getCancelRetryMaxAttempts()) {
            markCancelFailedTx(paymentId, "취소 수렴 재시도 상한(" + attempts + "회) 도달", now);
            return;
        }
        long minutes = (long) orderProperties.getCancelRetryBaseMinutes() * (1L << (attempts - 1));
        paymentRepository.scheduleCancelRetry(paymentId, attempts, now.plusMinutes(minutes));
    }

    private void markCancelFailedTx(String paymentId, String error, LocalDateTime now) {
        if (paymentRepository.markCancelFailed(paymentId) == 1) {
            paymentCancelRepository.finishRequested(paymentId, PaymentCancelStatus.FAILED, null, now, error);
            alerts.error("결제 취소 실패(운영자 처리 필요) - paymentId: " + paymentId + " - " + error);
        }
    }

    // ------------------------------------------------------------------ 만료(T10)

    /** T10 — PAYMENT_PENDING → EXPIRED(만료 시각 경과) → 재고 복원(1회) → 살아 있는 결제 EXPIRED. */
    @Transactional
    public boolean expire(Long orderId, LocalDateTime now) {
        if (orderRepository.markExpired(orderId, now) != 1) {
            return false;
        }
        stockReleaser.release(orderId, now);
        paymentRepository.closeReadyOfOrder(orderId, PaymentStatus.EXPIRED);
        return true;
    }

    @Transactional
    public void deferExpiry(Long orderId, LocalDateTime newExpiresAt) {
        orderRepository.deferExpiry(orderId, newExpiresAt);
    }

    @Transactional
    public void recordExpiryCheckFailure(Long orderId) {
        orderRepository.recordExpiryCheckFailure(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<ExpiryView> readForExpiry(Long orderId) {
        return orderRepository.findById(orderId).map(order -> new ExpiryView(
                order.getStatus(), order.getExpiresAt(), order.getExpiryDeferrals(), order.getExpiryCheckFailures(),
                paymentRepository.findFirstByOrder_IdAndStatus(orderId, PaymentStatus.READY)
                        .map(Payment::getPaymentId).orElse(null)));
    }

    public record ExpiryView(OrderStatus status, LocalDateTime expiresAt, int deferrals, int checkFailures,
                             String livePaymentId) {
    }
}
