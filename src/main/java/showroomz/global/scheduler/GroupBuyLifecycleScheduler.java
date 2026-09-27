package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.groupbuy.service.GroupBuyLifecycleService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 공구 수명주기 배치 — 오픈 · 종료 · 무응답 자동 이행(설계서 3-3) · 마감 게시물 내리기(공구 게시물 설계 4-2).
 *
 * <p><b>1분 주기인 이유</b> — 공구는 「08.14 10:00 시작」을 소비자에게 공지한 시각이 있다. 1시간 주기면
 * 10:59에 열리는 공구가 생긴다. 반대로 스케줄러 사이 최대 1분 동안 종료 시각이 지났는데 IN_PROGRESS인 공구가
 * 있으므로, 읽는 쪽(상세 timeline)은 서버 {@code now} 기준으로 계산한다.
 *
 * <p>기존 스케줄러와 같은 전제 — 단일 인스턴스, 분산 락 없음. 모든 전이가 조건부 UPDATE라 중복 실행돼도
 * 이중 전이는 없다(두 번째는 0행). 행마다 별도 트랜잭션이고 예외는 로그 후 삼킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "groupbuy", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class GroupBuyLifecycleScheduler {

    /** 1회 실행 상한 — 남은 건 다음 회차가 처리한다. */
    private static final int MAX_PER_RUN = 200;

    private final GroupBuyLifecycleService lifecycleService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        run("오픈", lifecycleService.findIdsToOpen(now, MAX_PER_RUN), now,
                (id, at) -> lifecycleService.open(id, at) ? 1 : 0);
        run("종료", lifecycleService.findIdsToEnd(now, MAX_PER_RUN), now,
                (id, at) -> lifecycleService.end(id, at) ? 1 : 0);
        run("자동 이행", lifecycleService.findIdsToAutoConfirm(now, MAX_PER_RUN), now,
                lifecycleService::autoConfirmFulfillment);
        // 종료 후 3일이 지난 공구 게시물을 소비자에게서 내린다 — post.status는 저장된 값이라 스스로 바뀌지 않는다.
        // 최대 1분 지연은 보정하지 않는다(72시간 중 1분).
        run("마감 게시물 내리기", lifecycleService.findIdsToRetirePost(now, MAX_PER_RUN), now,
                lifecycleService::retirePost);
    }

    private void run(String job, List<Long> ids, LocalDateTime now, BiFunction<Long, LocalDateTime, Integer> step) {
        int processed = 0;
        for (Long id : ids) {
            try {
                processed += step.apply(id, now);
            } catch (Exception e) {
                // 한 건 실패가 나머지를 막지 않는다 — 다음 회차가 다시 시도한다.
                log.error("공구 {} 처리 실패 - groupBuyId: {}", job, id, e);
            }
        }
        if (processed > 0) {
            log.info("공구 {} 처리 완료 - {}건", job, processed);
        }
    }
}
