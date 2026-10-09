package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.service.SettlementGenerationService;

import java.time.LocalDateTime;

/**
 * 정산 생성 배치(44 어드민 설계서 2-1) — 종료(ENDED)됐고 정산이 없는 공구를 돌며 주문 전부 종결이면 정산을 만든다.
 *
 * <p>10분 주기 — 최대 10분 지연은 「3영업일 확인 기간」 앞에서 의미가 없다. 단일 인스턴스 · 분산 락 없음 — 동시 실행은
 * {@code uk_settlement_group_buy}가 떨어뜨리고 그 예외는 「이미 생겼다」로 무시한다. 행마다 별도 트랜잭션이고 한 건 실패는
 * 로그(Sentry) 후 다음 건이다 — 계약 항목 없는 상품처럼 고쳐야 생기는 건은 다음 회차가 다시 시도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "settlement", name = "generation-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class SettlementGenerationScheduler {

    private static final int MAX_PER_RUN = 200;

    private final SettlementGenerationService generationService;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int generated = 0;
        for (Long groupBuyId : generationService.findGroupBuyIdsToGenerate(MAX_PER_RUN)) {
            try {
                if (generationService.generate(groupBuyId, now).isPresent()) {
                    generated++;
                }
            } catch (DataIntegrityViolationException e) {
                log.info("정산 생성 건너뜀 — 이미 생성됨 - groupBuyId: {}", groupBuyId);
            } catch (Exception e) {
                log.error("정산 생성 실패 - groupBuyId: {}", groupBuyId, e);
            }
        }
        if (generated > 0) {
            log.info("정산 생성 완료 - {}건", generated);
        }
    }
}
