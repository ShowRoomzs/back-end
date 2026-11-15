package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.contract.service.ContractSignatureExpiryService;

import java.time.LocalDateTime;

/**
 * 서명 기한 경과 불성립 배치(수정요청 B-11 · 2026-11-10) — 10분마다. 기한까지 양측 서명이 없으면 계약은 성립하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "contract", name = "signature-expiry-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class ContractSignatureExpiryScheduler {

    private static final int MAX_PER_RUN = 200;

    private final ContractSignatureExpiryService expiryService;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int expired = 0;
        for (Long id : expiryService.findIdsToExpire(now, MAX_PER_RUN)) {
            try {
                if (expiryService.expireIfOverdue(id, now)) {
                    expired++;
                }
            } catch (Exception e) {
                log.error("서명 기한 경과 불성립 처리 실패 - contractId: {}", id, e);
            }
        }
        if (expired > 0) {
            log.info("서명 기한 경과 불성립 - {}건", expired);
        }
    }
}
