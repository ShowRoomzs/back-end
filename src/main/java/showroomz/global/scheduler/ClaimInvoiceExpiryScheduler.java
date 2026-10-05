package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.order.service.OrderClaimService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 회수 송장 미등록 자동 취소 배치(앱 클레임 설계서 6절) — 등록 기한(접수 + 7일)이 지났는데 아직 회수 대기인 요청을
 * 닫는다. 닫히는 순간 그 하위주문의 구매확정 보류가 풀린다 — 소비자가 보내지 않은 요청이 정산을 묶는 상한이 7일이다.
 *
 * <p>매시 주기면 최대 1시간 늦게 닫힌다 — 그사이 들어온 송장 등록은 기한 검사에서 걸린다. 송장은 넣었는데 물건이
 * 오지 않는 회수 중 방치는 여기서 닫지 않는다(운영자 직권).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order.claim", name = "invoice-expiry-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class ClaimInvoiceExpiryScheduler {

    private static final int MAX_PER_RUN = 500;

    private final OrderClaimService claimService;

    @Scheduled(cron = "0 20 * * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> collectionIds = claimService.findCollectionIdsWithExpiredInvoice(now, MAX_PER_RUN);
        int closed = 0;
        for (Long collectionId : collectionIds) {
            try {
                closed += claimService.expireInvoice(collectionId, now);
            } catch (Exception e) {
                // 한 건 실패가 나머지를 막지 않는다 — 다음 회차가 다시 시도한다.
                log.error("회수 송장 미등록 자동 취소 실패 - collectionId: {}", collectionId, e);
            }
        }
        if (closed > 0) {
            log.info("회수 송장 미등록 자동 취소 완료 - {}건", closed);
        }
    }
}
