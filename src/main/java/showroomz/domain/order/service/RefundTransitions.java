package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.entity.PaymentCancel;
import showroomz.domain.payment.repository.PaymentCancelRepository;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.type.CancelRequester;
import showroomz.domain.payment.type.PaymentStatus;

import java.time.LocalDateTime;

/**
 * 환불 큐 집행의 짧은 트랜잭션들(1009 기획 수정본 2-4) — PG 호출은 여기 없다. {@code RefundExecutor}가 트랜잭션 밖에서 포트원을
 * 부르고 결과를 가져온다. 집행은 커밋 뒤 이벤트 리스너에서 시작되므로 전부 {@code REQUIRES_NEW}다(원 트랜잭션에 묻히지 않게).
 *
 * <p>직렬화 — 한 결제의 부분 취소는 하나씩만 보낸다. 선점(claim)이 결제 행을 잠그고 같은 결제의 집행 중 행이 없을 때만
 * EXECUTING 으로 올린다. 포트원에는 지금 취소 가능한 잔액을 함께 보내 PG 쪽에서도 끼어들기를 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundTransitions {

    private final OrderRefundTaskRepository refundTaskRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final OrderFulfillmentService fulfillmentService;
    private final OrderClaimService claimService;

    /**
     * @param zeroAmount 환불액 0 — PG 를 부르지 않고 바로 완료로 닫는다(차감이 상품 금액을 다 덮은 경우)
     */
    public record Claim(Long taskId, String paymentId, int amount, int cancellableAmount, String reason,
                        boolean zeroAmount) {
    }

    /**
     * 집행 선점 — 집행할 수 없으면 null(대기 그대로 · 다음 회차). 실패로 굳혀야 하는 경우(결제 없음이 아닌 결제 상태 이상 ·
     * 잔액 부족)는 FAILED 로 닫고 null 을 돌려준다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(Long taskId) {
        OrderRefundTask task = refundTaskRepository.findForUpdate(taskId).orElse(null);
        if (task == null || !task.isExecutable()) {
            return null;
        }
        if (task.getRefundAmount() <= 0) {
            task.startExecution();
            return new Claim(taskId, task.getPaymentId(), 0, 0, null, true);
        }
        if (task.getPaymentId() == null) {
            // 결제가 없는 주문(시드 · 결제 밖 주문) — PG 로 돌려줄 길이 없다. 대기로 두고 운영자가 수동 기록한다.
            log.warn("환불 집행 보류 — 결제 없음 - refundTaskId: {}", taskId);
            return null;
        }
        Payment payment = paymentRepository.findForUpdate(task.getPaymentId()).orElse(null);
        if (payment == null) {
            log.warn("환불 집행 보류 — 결제 행 없음 - refundTaskId: {}, paymentId: {}", taskId, task.getPaymentId());
            return null;
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            // 전액 취소 수렴 중(CANCEL_REQUESTED)이면 기다린다. 그 밖의 상태는 사람이 봐야 한다.
            if (payment.getStatus() != PaymentStatus.CANCEL_REQUESTED) {
                task.markFailed("결제 상태가 " + payment.getStatus().name() + " 라 부분 취소할 수 없습니다.");
                appendHistory(task, FulfillmentEventType.REFUND_FAILED, task.getLastError());
            }
            return null;
        }
        if (refundTaskRepository.existsByPaymentIdAndStatus(task.getPaymentId(), RefundTaskStatus.EXECUTING)) {
            return null; // 같은 결제의 다른 환불이 PG 를 기다린다 — 끝난 뒤 다음 회차가 집는다.
        }
        if (task.getRefundAmount() > payment.cancellableAmount()) {
            task.markFailed("환불액(" + task.getRefundAmount() + ")이 취소 가능 잔액(" + payment.cancellableAmount() + ")보다 큽니다.");
            appendHistory(task, FulfillmentEventType.REFUND_FAILED, task.getLastError());
            return null;
        }
        task.startExecution();
        return new Claim(taskId, task.getPaymentId(), task.getRefundAmount(), payment.cancellableAmount(),
                reasonOf(task), false);
    }

    /**
     * PG 취소 확인 — 큐 DONE · 결제 누적 취소액 · 부분 취소 기록 · 경로별 후속(반품이면 클레임 종결). 누적이 결제액에 닿으면 결제를
     * CANCELLED 로 닫는다(주문 전체 취소 연쇄 없음).
     *
     * @param operatorId 운영자 집행이면 그 운영자 — PG 자동은 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(Long taskId, String pgCancellationId, String raw, Long operatorId, LocalDateTime now) {
        OrderRefundTask task = refundTaskRepository.findForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus() != RefundTaskStatus.EXECUTING) {
            return false;
        }
        int amount = task.getRefundAmount();
        Long paymentCancelId = null;
        String paymentId = task.getPaymentId();
        if (amount > 0 && paymentId != null) {
            Payment payment = paymentRepository.findForUpdate(paymentId).orElseThrow();
            PaymentCancel record = paymentCancelRepository.save(PaymentCancel.partialSucceeded(payment, amount,
                    reasonOf(task), operatorId == null ? CancelRequester.SYSTEM : CancelRequester.ADMIN,
                    now, pgCancellationId, raw, taskId, now));
            paymentCancelId = record.getId();
        }
        task.markDone(paymentCancelId, operatorId, now);
        Long deliveryGroupId = task.getDeliveryGroup().getId();
        RefundTaskSource source = task.getSource();
        Long sourceId = task.getSourceId();
        FulfillmentActorType actor = operatorId == null ? FulfillmentActorType.SYSTEM : FulfillmentActorType.ADMIN;
        fulfillmentService.appendHistory(deliveryGroupId, FulfillmentEventType.REFUND_EXECUTED, actor, operatorId,
                String.format("%s · %,d원 · %s", task.refundNo(), amount, task.getOrigin().getLabel()), now);
        // 아래 조건부 UPDATE 가 영속성 컨텍스트를 비운다 — 엔티티 변경을 다 적은 뒤에 부른다.
        if (amount > 0 && paymentId != null) {
            paymentRepository.addCancelledAmount(paymentId, amount);
            paymentRepository.closeIfFullyRefunded(paymentId);
        }
        if (source == RefundTaskSource.CLAIM_RETURN_PASSED && sourceId != null) {
            claimService.applyRefundExecuted(sourceId, amount, actor, operatorId, now);
        }
        return true;
    }

    /** PG 가 명시적으로 거절했다 — FAILED. 자동 재시도 상한 뒤에는 운영자 [재시도]다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long taskId, String error) {
        OrderRefundTask task = refundTaskRepository.findForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus() != RefundTaskStatus.EXECUTING) {
            return;
        }
        task.markFailed(error);
        appendHistory(task, FulfillmentEventType.REFUND_FAILED, task.getLastError());
    }

    /**
     * 결과를 모른 채 머문 집행 중 건의 결론 — 포트원의 누적 취소액이 우리 누적 + 이 건 이상이면 성공으로, 아니면 FAILED(재시도
     * 대기)로 닫는다. 재시도는 지금 잔액을 다시 보내므로 이중 환불이 되지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isSettledRemotely(Long taskId, Long remoteCancelledAmount) {
        OrderRefundTask task = refundTaskRepository.findForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus() != RefundTaskStatus.EXECUTING || task.getPaymentId() == null
                || remoteCancelledAmount == null) {
            return false;
        }
        Payment payment = paymentRepository.findById(task.getPaymentId()).orElse(null);
        return payment != null && remoteCancelledAmount >= (long) payment.getCancelledAmount() + task.getRefundAmount();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundTaskOrigin originOf(Long taskId) {
        return refundTaskRepository.findById(taskId).map(OrderRefundTask::getOrigin).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String paymentIdOf(Long taskId) {
        return refundTaskRepository.findById(taskId).map(OrderRefundTask::getPaymentId).orElse(null);
    }

    private void appendHistory(OrderRefundTask task, FulfillmentEventType eventType, String detail) {
        // 환불번호 접두 — 어드민 환불 상세가 이 큐 행의 이력만 고르는 키다(39 설계서 7-2 #5 · 컬럼 추가 없음).
        fulfillmentService.appendHistory(task.getDeliveryGroup().getId(), eventType, FulfillmentActorType.SYSTEM, null,
                task.refundNo() + " · " + detail, LocalDateTime.now());
    }

    private static String reasonOf(OrderRefundTask task) {
        if (task.getReasonCode() != null) {
            return task.getReasonCode().getLabel();
        }
        return switch (task.getSource()) {
            case CANCEL_REQUEST_APPROVED -> "취소 요청 승인";
            case SELLER_DIRECT_CANCEL -> "브랜드 직권 취소";
            case RETURN_COMPLETED -> "반송 완료";
            case CLAIM_RETURN_PASSED -> "반품 검수 통과";
            case OPERATOR_REASON -> "운영자 사유 환불";
            case USER_CANCEL_BEFORE_PREPARE -> "결제완료 소비자 취소";
            case CLAIM_PAYMENT_CANCELLED -> "교환 재발송비 환불";
        };
    }
}
