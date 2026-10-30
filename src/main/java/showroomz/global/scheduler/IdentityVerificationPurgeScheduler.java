package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.user.entity.IdentityVerification;
import showroomz.domain.member.user.repository.IdentityVerificationRepository;
import showroomz.domain.member.user.type.IdentityVerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 본인인증 행의 개인정보 파기 배치(57 설계서 1-2) — 매일 04:00. 소비되지 않은 VERIFIED(만료 포함) 행에 남은
 * {@code verified_*} · 암호문을 30일 뒤 비운다. 행 자체(id · 상태 · 사유 · 시각)는 조사 자료로 남는다. CONSUMED · FAILED 행은
 * 전이 때 이미 비웠다. 보존 기간은 법무 확인 전 가정값(13절 #9).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "portone.identity", name = "purge-scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class IdentityVerificationPurgeScheduler {

    static final int RETENTION_DAYS = 30;
    private static final int MAX_PER_RUN = 500;

    private final IdentityVerificationRepository repository;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void tick() {
        int purged = purge(LocalDateTime.now());
        if (purged > 0) {
            log.info("본인인증 개인정보 파기 - {}건", purged);
        }
    }

    @Transactional
    public int purge(LocalDateTime now) {
        List<IdentityVerification> stale = repository.findStaleWithPersonalData(
                List.of(IdentityVerificationStatus.VERIFIED), now.minusDays(RETENTION_DAYS), MAX_PER_RUN);
        stale.forEach(IdentityVerification::purgePersonalData);
        return stale.size();
    }
}
