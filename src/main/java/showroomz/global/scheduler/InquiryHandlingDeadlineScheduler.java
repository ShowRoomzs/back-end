package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.inquiry.service.InquiryHandlingDeadlineService;

import java.time.LocalDateTime;

/** 1:1 문의 처리 경과 통지 배치(수정요청 C) — 매시 정각. 접수 + 3영업일 미답변이면 경과 · 결과 예정일을 알린다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "admin", name = "inquiry-deadline-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class InquiryHandlingDeadlineScheduler {

    private static final int MAX_PER_RUN = 500;

    private final InquiryHandlingDeadlineService deadlineService;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int noticed = 0;
        for (Long id : deadlineService.findCandidateIds(now, MAX_PER_RUN)) {
            try {
                if (deadlineService.noticeIfDue(id, now)) {
                    noticed++;
                }
            } catch (Exception e) {
                log.error("1:1 문의 처리 경과 통지 실패 - inquiryId: {}", id, e);
            }
        }
        if (noticed > 0) {
            log.info("1:1 문의 처리 경과 통지 - {}건", noticed);
        }
    }
}
