package showroomz.domain.settlement.service.port;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.service.port.SettlementClawbackHook;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.domain.settlement.service.SettlementClawbackService;

import java.time.LocalDateTime;

/**
 * 환불 집행 → 정산 차감(44 어드민 설계서 6-1 · 8-4) — 주문 모듈의 훅 구현.
 *
 * <p>환불 완료 트랜잭션에서는 큐 행 id 만 집고, 차감 등록은 <b>커밋 뒤 별도 트랜잭션</b>으로 한다 — PG 환불은 이미 끝났으므로 차감 등록
 * 실패가 환불 완료 기록을 되돌리면 안 된다. 실패는 로그로 남기고(같은 큐 행으로 다시 부르면 한 번만 만든다) 환불은 그대로 끝난다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementClawbackHookAdapter implements SettlementClawbackHook {

    private final SettlementClawbackService clawbackService;

    @Override
    public void onRefundExecuted(OrderRefundTask task) {
        if (!SettlementClawbackService.isClawbackSource(task.getSource())) {
            return;
        }
        Long taskId = task.getId();
        AfterCommit.run(() -> {
            try {
                clawbackService.register(taskId, LocalDateTime.now());
            } catch (Exception e) {
                log.error("정산 차감 등록 실패 — 환불은 완료됐다 · 운영 확인 필요 - refundTaskId: {}", taskId, e);
            }
        });
    }
}
