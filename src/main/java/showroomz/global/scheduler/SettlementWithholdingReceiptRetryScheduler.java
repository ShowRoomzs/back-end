package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.service.WithholdingReceiptGenerator;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;

/**
 * 원천징수영수증 재생성(44 어드민 설계서 3-5) — 매시 40분. 지급 완료 직후의 비동기 생성이 실패해 {@code PENDING_ISSUE}로 남은 행을
 * 다시 만든다. 행마다 따로 — 한 건 실패는 로그 후 다음 건.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "settlement", name = "receipt-retry-scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class SettlementWithholdingReceiptRetryScheduler {

    private static final int MAX_PER_RUN = 200;

    private final SettlementTaxDocumentRepository documentRepository;
    private final WithholdingReceiptGenerator generator;

    @Scheduled(cron = "0 40 * * * *", zone = "Asia/Seoul")
    public void tick() {
        int generated = 0;
        for (Long documentId : documentRepository.findIdsByTypeAndStatus(TaxDocumentType.WITHHOLDING_RECEIPT,
                TaxDocumentStatus.PENDING_ISSUE, PageRequest.of(0, MAX_PER_RUN))) {
            if (generator.generate(documentId)) {
                generated++;
            }
        }
        if (generated > 0) {
            log.info("원천징수영수증 재생성 완료 - {}건", generated);
        }
    }
}
