package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import showroomz.domain.order.event.RefundTaskEnqueuedEvent;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOneCancelResult;
import showroomz.global.payment.portone.PortOnePayment;
import showroomz.global.payment.portone.PortOnePaymentGateway;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 환불 집행기(1009 기획 수정본 2-4) — <b>PG 즉시 자동 환불</b>. 결제완료 취소 · 취소 요청 승인(자동 승인 포함) · 브랜드 직권 취소 ·
 * 반품 검수 통과 · 반송 완료는 큐에 쌓이는 순간(커밋 직후) 여기서 포트원 부분 취소로 돌려준다. 운영자 사유 환불은 어드민 환불
 * 관리의 [집행]이 같은 메서드를 부른다.
 *
 * <pre>
 *  선점(결제 행 잠금 · EXECUTING) → 포트원 부분 취소(트랜잭션 밖) ┬ 성공          → 완료(DONE · 누적 취소액 · 후속)
 *                                                                 ├ 명시적 거절   → FAILED(자동 재시도 → 운영자)
 *                                                                 └ 결과 모름     → EXECUTING 유지 → 정리 배치가 포트원 조회로 결론
 * </pre>
 *
 * <p>예외는 밖으로 내보내지 않는다 — 커밋 뒤 리스너라 던져도 원 요청(승인 · 검수)은 이미 끝났다. 남은 일은 재시도 배치가 줍는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundExecutor {

    public enum Outcome { DONE, FAILED, UNKNOWN, SKIPPED }

    private final RefundTransitions transitions;
    private final PortOnePaymentGateway gateway;

    /** 큐 적재의 커밋 뒤 — PG 자동만 바로 집행한다. 운영자 사유 환불은 재확인 뒤 운영자가 누른다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnqueued(RefundTaskEnqueuedEvent event) {
        if (event.origin() == RefundTaskOrigin.PG_AUTO) {
            execute(event.refundTaskId(), null);
        }
    }

    /**
     * 집행 한 번 — 자동 · 재시도 배치 · 운영자 집행이 같은 길을 탄다.
     *
     * @param operatorId 운영자 집행이면 그 운영자, 자동이면 null
     */
    public Outcome execute(Long taskId, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        RefundTransitions.Claim claim;
        try {
            claim = transitions.claim(taskId);
        } catch (RuntimeException e) {
            log.error("환불 집행 선점 실패 - refundTaskId: {}", taskId, e);
            return Outcome.SKIPPED;
        }
        if (claim == null) {
            return Outcome.SKIPPED;
        }
        if (claim.failed()) {
            return Outcome.FAILED;
        }
        if (claim.zeroAmount()) {
            return complete(taskId, null, null, operatorId, now);
        }
        try {
            PortOneCancelResult result = gateway.cancelPartial(claim.paymentId(), claim.amount(),
                    claim.cancellableAmount(), claim.reason());
            if (result.outcome() == PortOneCancelResult.Outcome.SUCCEEDED) {
                return complete(taskId, result.pgCancellationId(), result.rawJson(), operatorId, now);
            }
            if (result.outcome() == PortOneCancelResult.Outcome.ALREADY_CANCELLED) {
                transitions.fail(taskId, "이미 전액 취소된 결제입니다 — 결제 상태를 확인해 주세요.", "ALREADY_CANCELLED");
                return Outcome.FAILED;
            }
            log.info("환불 PG 접수 대기 - refundTaskId: {}", taskId);
            return Outcome.UNKNOWN;
        } catch (PaymentGatewayRejectedException e) {
            log.warn("환불 PG 거절 - refundTaskId: {} - {}", taskId, e.getMessage());
            transitions.fail(taskId, "PG 거절 · " + e.getMessage(), e.getType());
            return Outcome.FAILED;
        } catch (PaymentGatewayException e) {
            // 결과 모름 — 실패로 굳히지 않는다. 돌려줬는데 FAILED 로 두면 재시도가 이중 환불을 시도한다(PG 가 막지만 기록이 틀린다).
            log.warn("환불 PG 결과 미확인 - refundTaskId: {} - {}", taskId, e.getMessage());
            return Outcome.UNKNOWN;
        } catch (RuntimeException e) {
            log.error("환불 집행 중 오류 - refundTaskId: {}", taskId, e);
            return Outcome.UNKNOWN;
        }
    }

    /**
     * 결과를 모른 채 머문 집행 중 건 — 포트원 누적 취소액으로 결론을 낸다. 돌려준 것이 확인되면 완료, 아니면 FAILED(재시도 대기).
     */
    public Outcome resolveStale(Long taskId) {
        String paymentId = transitions.paymentIdOf(taskId);
        if (paymentId == null) {
            transitions.fail(taskId, "결과 미확인 — 결제 정보 없음");
            return Outcome.FAILED;
        }
        Optional<PortOnePayment> remote;
        try {
            remote = gateway.getPayment(paymentId);
        } catch (RuntimeException e) {
            log.warn("환불 결과 확인 조회 실패 - refundTaskId: {} - {}", taskId, e.getMessage());
            return Outcome.UNKNOWN;
        }
        if (remote.isPresent() && transitions.isSettledRemotely(taskId, remote.get().cancelledAmount())) {
            return complete(taskId, null, remote.get().rawJson(), null, LocalDateTime.now());
        }
        transitions.fail(taskId, "결과 미확인 — 포트원에 취소가 없어 재시도 대기");
        return Outcome.FAILED;
    }

    /** 편입 철회 — 전이만(PG 없음). */
    public boolean voidTask(Long taskId, String reason, Long operatorId, LocalDateTime now) {
        return transitions.voidTask(taskId, reason, operatorId, now);
    }

    /** 수동 완료 기록 — PG 없이 완료 후속만. 기록 실패는 예외로 올린다(돈이 나간 것이 아니라 기록이므로 되돌려도 된다). */
    public boolean recordManual(Long taskId, String pgCancellationId, String note, Long operatorId, LocalDateTime now) {
        return transitions.recordManual(taskId, pgCancellationId, note, operatorId, now);
    }

    private Outcome complete(Long taskId, String pgCancellationId, String raw, Long operatorId, LocalDateTime now) {
        try {
            return transitions.complete(taskId, pgCancellationId, raw, operatorId, now) ? Outcome.DONE : Outcome.SKIPPED;
        } catch (RuntimeException e) {
            // 돈은 나갔는데 기록이 실패했다 — EXECUTING 으로 남고 정리 배치가 포트원 누적액으로 다시 닫는다.
            log.error("환불 완료 기록 실패(정리 배치가 수렴) - refundTaskId: {}", taskId, e);
            return Outcome.UNKNOWN;
        }
    }
}
