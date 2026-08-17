package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.global.config.properties.PostProperties;

import java.time.LocalDateTime;

/**
 * 노출 원천 로그 보관 정리 (§24-8 ⓔ).
 *
 * <p><b>보관 일수가 정해지기 전에는 아무것도 지우지 않는다</b>({@code post.impression-retention-days=0}).
 * 이 로그는 연령·성별을 사용자와 조인해 얻는 표본이라 개인정보 보관 기간과 얽혀 있고, 동시에
 * 인사이트를 소급 계산할 유일한 원천이다. 짧게 잡았다가 늘리는 것이 불가능한 쪽이라 기본값을
 * "지우지 않음"으로 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostImpressionRetentionScheduler {

    private final PostImpressionRepository postImpressionRepository;
    private final PostProperties postProperties;

    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeOldImpressions() {
        int retentionDays = postProperties.getImpressionRetentionDays();
        if (retentionDays <= 0) {
            return;
        }

        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
            int deleted = postImpressionRepository.deleteOlderThan(threshold);
            if (deleted > 0) {
                log.info("노출 로그 보관 정리 완료 - {}건 (기준 {}일)", deleted, retentionDays);
            }
        } catch (Exception e) {
            log.error("노출 로그 보관 정리 실패", e);
        }
    }
}
