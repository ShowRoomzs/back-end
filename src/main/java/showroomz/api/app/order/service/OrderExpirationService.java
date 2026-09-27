package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.order.service.PaymentTransitions.ExpiryView;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOnePayment;
import showroomz.global.payment.portone.PortOnePaymentGateway;
import showroomz.global.payment.portone.PortOneStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 시간이 여는 전이(결제 계획서 4-4) — ① 미결제 만료 ② 취소 수렴. 스케줄러가 {@code now}를 넘겨 부르고, 통합 테스트는
 * 스케줄러를 끄고 시각을 정해 직접 부른다(선행 수정 계획서 C8). 외부 HTTP 는 트랜잭션 밖이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransitions transitions;
    private final PaymentConfirmService confirmService;
    private final PaymentCancelExecutor cancelExecutor;
    private final PortOnePaymentGateway gateway;
    private final OrderProperties properties;

    @Transactional(readOnly = true)
    public List<Long> findIdsToExpire(LocalDateTime now, int limit) {
        return orderRepository.findIdsToExpire(now, Pageable.ofSize(limit));
    }

    @Transactional(readOnly = true)
    public List<String> findPaymentIdsToConvergeCancel(LocalDateTime now, int limit) {
        return paymentRepository.findIdsToConvergeCancel(now, Pageable.ofSize(limit));
    }

    /**
     * 만료 한 건 — 살아 있는 결제가 있으면 포트원을 1회 조회해 「잃어버린 결제」를 구제하고, 승인 진행 중이면 미루고,
     * 그 밖(404 · READY · FAILED)은 즉시 만료한다. 통신 실패는 상한까지 이번 회차를 건너뛴다.
     *
     * @return 만료했으면 true
     */
    public boolean expire(Long orderId, LocalDateTime now) {
        ExpiryView view = transitions.readForExpiry(orderId).orElse(null);
        if (view == null || view.status() != OrderStatus.PAYMENT_PENDING
                || view.expiresAt() == null || view.expiresAt().isAfter(now)) {
            return false;
        }
        if (view.livePaymentId() != null) {
            Optional<PortOnePayment> remote;
            try {
                remote = gateway.getPayment(view.livePaymentId());
            } catch (PaymentGatewayException | PaymentGatewayRejectedException e) {
                transitions.recordExpiryCheckFailure(orderId);
                if (view.checkFailures() + 1 < properties.getExpiryMaxCheckFailures()) {
                    log.warn("만료 전 포트원 조회 실패({}회) - orderId: {}", view.checkFailures() + 1, orderId);
                    return false;
                }
                log.warn("만료 전 포트원 조회 실패 상한 도달 - orderId: {} - 조회 없이 만료", orderId);
                remote = Optional.empty();
            }
            if (remote.isPresent()) {
                PortOneStatus status = remote.get().status();
                if (status == PortOneStatus.PAID) {
                    // 잃어버린 결제 구제 — complete 도 웹훅도 안 온 결제를 만료·자동 취소하는 대신 확정한다.
                    try {
                        confirmService.confirm(view.livePaymentId(), PaymentConfirmService.Trigger.EXPIRY_CHECK);
                    } catch (BusinessException e) {
                        log.warn("만료 전 확정 실패 - orderId: {} - {}", orderId, e.getMessage());
                    }
                    return false;
                }
                if (status.isInProgress() && view.deferrals() < properties.getExpiryMaxDeferrals()) {
                    // 29분 50초에 결제창을 열어 카드사 인증 중인 소비자를 30분 정각에 만료시키지 않는다.
                    transitions.deferExpiry(orderId, now.plusMinutes(properties.getExpiryDeferralMinutes()));
                    return false;
                }
            }
        }
        return transitions.expire(orderId, now);
    }

    /**
     * 취소 수렴 — CANCEL_REQUESTED 인 채 결과를 모르는 결제를 포트원에 다시 묻는다. CANCELLED 면 수렴, PAID 면 취소 재호출,
     * 그 밖·통신 실패면 재시도 시각을 지수로 미룬다(상한 도달 시 CANCEL_FAILED).
     */
    public void convergeCancel(String paymentId, LocalDateTime now) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.CANCEL_REQUESTED) {
            return;
        }
        Optional<PortOnePayment> remote;
        try {
            remote = gateway.getPayment(paymentId);
        } catch (PaymentGatewayException | PaymentGatewayRejectedException e) {
            log.warn("취소 수렴 - 포트원 조회 실패 - paymentId: {} - {}", paymentId, e.getMessage());
            transitions.bumpCancelRetry(paymentId, now);
            return;
        }
        PortOneStatus status = remote.map(PortOnePayment::status).orElse(PortOneStatus.UNKNOWN);
        if (status == PortOneStatus.CANCELLED) {
            transitions.completeCancel(paymentId, null, remote.get().rawJson(), now);
            return;
        }
        if (status == PortOneStatus.PAID) {
            String reason = payment.getMismatchReason() != null ? "자동 취소 - " + payment.getMismatchReason() : "주문 취소";
            if (cancelExecutor.execute(paymentId, payment.getAmount(), reason, now) == PaymentCancelExecutor.Outcome.UNKNOWN) {
                transitions.bumpCancelRetry(paymentId, now);
            }
            return;
        }
        log.warn("취소 수렴 - 포트원 상태가 예상 밖 - paymentId: {}, status: {}", paymentId, status);
        transitions.bumpCancelRetry(paymentId, now);
    }
}
