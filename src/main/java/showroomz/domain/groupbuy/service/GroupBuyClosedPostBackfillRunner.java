package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 마감 게시물 1회 백필(공구 게시물 설계 4-1) — {@code groupbuy.backfill.closed-posts.enabled=true}일 때만 뜬다.
 * 투영식이 바뀌기 전에 종료 즉시 DRAFT가 된 게시물 중 종료 3일 이내인 것을 되살린다. 배포 후 한 번 돌리고 설정을 다시 끈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "groupbuy.backfill.closed-posts", name = "enabled", havingValue = "true")
public class GroupBuyClosedPostBackfillRunner implements ApplicationRunner {

    private final GroupBuyBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;
        int failed = 0;
        for (Long groupBuyId : backfillService.findClosedPostTargets(now)) {
            try {
                if (backfillService.resyncClosedPost(groupBuyId, now)) {
                    changed++;
                }
            } catch (Exception e) {
                failed++;
                log.error("마감 게시물 백필 실패 - groupBuyId: {}", groupBuyId, e);
            }
        }
        log.info("마감 게시물 백필 완료 - 재노출 {}건 · 실패 {}건", changed, failed);
    }
}
