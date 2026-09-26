package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기동 시 1회 백필(설계서 2-3) — {@code groupbuy.backfill.enabled=true}일 때만 뜬다.
 * 계약 모듈이 먼저 배포되어 쌓인 「체결완료인데 공구가 없는 계약」을 정리한 뒤 설정을 다시 끈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "groupbuy.backfill", name = "enabled", havingValue = "true")
public class GroupBuyBackfillRunner implements ApplicationRunner {

    private final GroupBuyBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        int created = 0;
        int failed = 0;
        for (Long contractId : backfillService.findTargets()) {
            try {
                if (backfillService.backfill(contractId)) {
                    created++;
                }
            } catch (Exception e) {
                failed++;
                log.error("공구 백필 실패 - contractId: {}", contractId, e);
            }
        }
        log.info("공구 백필 완료 - 생성 {}건 · 실패 {}건", created, failed);
    }
}
