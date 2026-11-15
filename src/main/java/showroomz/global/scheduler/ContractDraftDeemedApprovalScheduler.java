package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.contract.service.ContractDraftReviewService;

import java.time.LocalDateTime;

/** 사전 검수 승인 간주 배치(수정요청 B-13) — 매시 20분. 초안 수령 + 3영업일 무회신이면 승인 간주를 기록한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "contract", name = "draft-deemed-approval-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class ContractDraftDeemedApprovalScheduler {

    private static final int MAX_PER_RUN = 200;

    private final ContractDraftReviewService reviewService;

    @Scheduled(cron = "0 20 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int deemed = 0;
        for (Long id : reviewService.findIdsDueForDeemedApproval(now, MAX_PER_RUN)) {
            try {
                if (reviewService.deemApproveIfDue(id, now)) {
                    deemed++;
                }
            } catch (Exception e) {
                log.error("사전 검수 승인 간주 실패 - submissionId: {}", id, e);
            }
        }
        if (deemed > 0) {
            log.info("사전 검수 승인 간주 - {}건", deemed);
        }
    }
}
