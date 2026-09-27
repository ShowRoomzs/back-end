package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import showroomz.api.app.order.dto.OrderDto;
import showroomz.api.app.order.service.CheckoutService.CreatedOrder;
import showroomz.api.app.order.service.CheckoutService.ExistingOrder;
import showroomz.api.app.order.service.CheckoutService.UserCancelClaim;
import showroomz.api.app.order.service.PaymentCancelExecutor.Outcome;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOnePayment;
import showroomz.global.payment.portone.PortOnePaymentGateway;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 주문 생성·결제 재시도·취소의 <b>파사드</b> — 트랜잭션(T2·T8·T13)은 {@link CheckoutService}에 있고, 여기는 커밋 뒤의
 * 외부 호출(사전 등록 · 포트원 취소)과 멱등키 경합 복구를 맡는다(결제 계획서 4-7). {@code @Transactional}이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final CheckoutService checkoutService;
    private final PortOnePaymentGateway gateway;
    private final PaymentCancelExecutor cancelExecutor;

    /**
     * 주문 생성(5-3). 같은 {@code (user, idempotencyKey)}의 주문이 있으면 새로 만들지 않고 그 주문으로 응답을 다시 조립한다.
     * 동시 요청 둘이 모두 INSERT 에 들어가면 둘째의 유니크 위반을 잡아 같은 경로로 돌려보낸다.
     */
    public OrderDto.CreateOrderResponse createOrder(Long userId, OrderDto.CreateOrderRequest request) {
        Optional<ExistingOrder> existing = checkoutService.findExisting(userId, request.getIdempotencyKey());
        if (existing.isPresent()) {
            return resume(existing.get());
        }
        CreatedOrder created;
        try {
            created = checkoutService.createOrderTx(userId, request, LocalDateTime.now());
        } catch (DataIntegrityViolationException e) {
            // 멱등키 유니크 위반은 예외가 아니라 신호다(4-7). 500 으로 올리면 앱은 「주문 실패」로 그리는데 실제로는 주문이 있다.
            ExistingOrder raced = checkoutService.findExisting(userId, request.getIdempotencyKey())
                    .orElseThrow(() -> e);
            return resume(raced);
        }
        preRegister(created.paymentId(), created.amount());
        return checkoutService.buildCreateResponse(created.orderId(), created.paymentId());
    }

    /** 결제 재시도(5-1) — 새 paymentId 를 발급한다. 이전 결제창이 뒤늦게 결제되면 자동 취소된다(4-5 ④). */
    public OrderDto.CreateOrderResponse retryPayment(Long userId, Long orderId, OrderDto.PaymentSelection selection) {
        CreatedOrder created;
        try {
            created = checkoutService.retryPaymentTx(userId, orderId, selection, LocalDateTime.now());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_IN_PROGRESS);
        }
        preRegister(created.paymentId(), created.amount());
        return checkoutService.buildCreateResponse(created.orderId(), created.paymentId());
    }

    /**
     * 주문 취소(5-6). 결제 전이면 즉시. 결제 후면 선점(T8) → 포트원 취소 → 결과에 따라 T9 / T9' / 202.
     */
    public CancelResult cancelOrder(Long userId, Long orderId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        if (checkoutService.cancelPendingTx(userId, orderId, reason, now)) {
            return new CancelResult(OrderStatus.CANCELLED, PaymentStatus.CANCELLED, false);
        }
        UserCancelClaim claim = checkoutService.claimUserCancel(userId, orderId, reason, now);
        Outcome outcome = cancelExecutor.execute(claim.paymentId(), claim.amount(), reason, now);
        return switch (outcome) {
            case CANCELLED -> new CancelResult(OrderStatus.CANCELLED, PaymentStatus.CANCELLED, false);
            case REJECTED -> throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
            case UNKNOWN -> new CancelResult(OrderStatus.PAID, PaymentStatus.CANCEL_REQUESTED, true);
        };
    }

    // ------------------------------------------------------------------ 내부

    private OrderDto.CreateOrderResponse resume(ExistingOrder existing) {
        if (existing.status().isClosed() || (existing.status() == OrderStatus.PAYMENT_PENDING && existing.expired())) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CLOSED);
        }
        if (existing.paymentId() == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (existing.status() == OrderStatus.PAYMENT_PENDING && !existing.preRegistered()
                && existing.paymentStatus() == PaymentStatus.READY) {
            preRegister(existing.paymentId(), existing.amount());
        }
        return checkoutService.buildCreateResponse(existing.orderId(), existing.paymentId());
    }

    /**
     * 사전 등록(2-1) — 커밋 뒤. 실패해도 주문은 남고 502 다. 재고는 잡혀 있고 만료 스케줄러가 정리하거나, 앱이 같은 키로
     * 재요청하면 다시 시도한다. 포트원이 「이미 등록된 paymentId」로 거절하면 등록 금액이 같을 때 성공으로 본다(5-3).
     */
    private void preRegister(String paymentId, long amount) {
        try {
            gateway.preRegister(paymentId, amount, Payment.CURRENCY_KRW);
        } catch (PaymentGatewayRejectedException e) {
            if (!alreadyRegisteredWithSameAmount(paymentId, amount)) {
                log.warn("사전 등록 거절 - paymentId: {} - {}", paymentId, e.getMessage());
                throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
            }
        } catch (PaymentGatewayException e) {
            log.warn("사전 등록 실패 - paymentId: {} - {}", paymentId, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
        checkoutService.markPreRegistered(paymentId, LocalDateTime.now());
    }

    private boolean alreadyRegisteredWithSameAmount(String paymentId, long amount) {
        try {
            Optional<PortOnePayment> remote = gateway.getPayment(paymentId);
            return remote.isPresent() && remote.get().totalAmount() != null && remote.get().totalAmount() == amount;
        } catch (PaymentGatewayException e) {
            return false;
        }
    }

    /** @param pending true 면 202 — 취소 처리 중(PG 응답 대기). 앱은 주문 상세를 다시 읽는다 */
    public record CancelResult(OrderStatus orderStatus, PaymentStatus paymentStatus, boolean pending) {
    }
}
