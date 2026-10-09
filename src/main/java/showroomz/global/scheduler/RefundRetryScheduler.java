package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.service.RefundExecutor;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.global.config.properties.OrderProperties;

import java.time.LocalDateTime;

/**
 * 환불 재시도 배치(1009 기획 수정본 2-4) — 10분마다.
 * <ol>
 *   <li>결과 미확인으로 집행 중에 오래 머문 건 → 포트원 누적 취소액으로 결론(완료 / 재시도 대기)</li>
 *   <li>커밋 뒤 집행을 놓친 PG 자동 대기 건(2분 경과) · 자동 시도 여유가 남은 실패 건(10분 경과) → 다시 집행</li>
 * </ol>
 * 운영자 사유 환불은 다시 집행하지 않는다 — 돈이 나가는 순간은 운영자의 재확인뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order", name = "refund-retry-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class RefundRetryScheduler {

    private static final int MAX_PER_RUN = 200;

    private final OrderRefundTaskRepository refundTaskRepository;
    private final RefundExecutor refundExecutor;
    private final OrderProperties orderProperties;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (Long taskId : refundTaskRepository.findStaleExecutingIds(
                now.minusMinutes(orderProperties.getRefundStaleMinutes()), PageRequest.of(0, MAX_PER_RUN))) {
            try {
                refundExecutor.resolveStale(taskId);
            } catch (Exception e) {
                log.error("환불 결과 정리 실패 - refundTaskId: {}", taskId, e);
            }
        }
        int done = 0;
        for (Long taskId : refundTaskRepository.findIdsToRetry(RefundTaskOrigin.PG_AUTO, now.minusMinutes(2),
                now.minusMinutes(10), orderProperties.getRefundAutoMaxAttempts(), PageRequest.of(0, MAX_PER_RUN))) {
            try {
                if (refundExecutor.execute(taskId, null) == RefundExecutor.Outcome.DONE) {
                    done++;
                }
            } catch (Exception e) {
                log.error("환불 재시도 실패 - refundTaskId: {}", taskId, e);
            }
        }
        if (done > 0) {
            log.info("환불 재시도 완료 - {}건", done);
        }
    }
}
