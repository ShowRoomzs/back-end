package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import showroomz.domain.contract.event.ContractReviewRequestedEvent;
import showroomz.global.config.ContractPdfAsyncConfig;

/**
 * 검토 요청이 커밋되면 제출본 PDF를 만든다.
 *
 * <p><b>커밋 이후</b> — 검토 요청이 롤백되면 만들 이유가 없다. <b>비동기</b> — 렌더링·업로드가 10초 안팎이라
 * 검토 요청 응답이 그만큼 붙잡혀 있었다. 예외는 삼킨다 — 이미 커밋된 제출을 되돌릴 수 없고,
 * 생성본은 어드민이 처음 내려받을 때 만들어진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractSubmitDraftListener {

    private final ContractDraftGenerator generator;

    @Async(ContractPdfAsyncConfig.CONTRACT_PDF_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewRequested(ContractReviewRequestedEvent event) {
        try {
            generator.generateOnSubmit(event.contractId());
        } catch (RuntimeException e) {
            log.warn("Contract draft PDF generation on submit failed; admin download will retry. contractId={}",
                    event.contractId(), e);
        }
    }
}
