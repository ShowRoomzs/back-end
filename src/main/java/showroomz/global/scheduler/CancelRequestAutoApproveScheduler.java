package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.order.service.OrderCancelRequestService;

import java.time.LocalDateTime;

/**
 * 취소 요청 자동 승인 배치(1009 기획 수정본 3-2 · 거래 관리 결정 8 · 15) — 10분마다. 브랜드가 요청 + 1영업일 안에 승인 · 거부하지
 * 않으면 시스템이 승인한다. 승인 경로는 브랜드 승인과 같다 — 요청 항목 취소 · 재고 원복 · PG 즉시 자동 환불.
 * 어드민 예외 관리의 처리 지연 탭에 「취소 요청 미응답」이 없는 이유가 이것이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order", name = "cancel-request-auto-approve-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class CancelRequestAutoApproveScheduler {

    private static final int MAX_PER_RUN = 200;

    private final OrderCancelRequestService cancelRequestService;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int approved = 0;
        for (Long id : cancelRequestService.findOverdueIds(now, MAX_PER_RUN)) {
            try {
                if (cancelRequestService.autoApproveIfOverdue(id, now)) {
                    approved++;
                }
            } catch (Exception e) {
                log.error("취소 요청 자동 승인 실패 - cancelRequestId: {}", id, e);
            }
        }
        if (approved > 0) {
            log.info("취소 요청 자동 승인 - {}건", approved);
        }
    }
}
