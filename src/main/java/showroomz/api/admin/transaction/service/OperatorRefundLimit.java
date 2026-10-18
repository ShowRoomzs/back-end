package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 운영자 사유 환불의 상한(06a B5) — 결제의 취소 가능 잔액 − 아직 나가지 않은 환불(대기 · 집행 중 · 실패). 편입 API의 409 와
 * 상세의 버튼({@code canEnqueueRefund}) · 다이얼로그 상한({@code operatorRefundableAmount})이 이 한 식을 쓴다(46 수정계획 1절) —
 * 둘이 다른 식을 쓰면 버튼이 보이는데 누르면 409 가 난다.
 */
@Component
@RequiredArgsConstructor
public class OperatorRefundLimit {

    private final PaymentRepository paymentRepository;
    private final OrderRefundTaskRepository refundTaskRepository;

    /** 결제가 없는 주문은 null — 상한이 없다(운영자가 수동 기록한다). 음수는 0으로 깎는다. */
    public Integer available(Order order) {
        String paymentId = order.getPaidPaymentId();
        if (paymentId == null) {
            return null;
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        return available(payment);
    }

    public int available(Payment payment) {
        long outstanding = refundTaskRepository.sumOutstandingByPayment(payment.getPaymentId());
        return (int) Math.max(0, payment.cancellableAmount() - outstanding);
    }
}
