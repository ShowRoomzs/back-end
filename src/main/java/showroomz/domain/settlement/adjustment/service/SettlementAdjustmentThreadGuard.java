package showroomz.domain.settlement.adjustment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 종결 이슈 스레드는 읽기 전용(44 이슈 스레드 설계서 3-7) — 파트너 · 스튜디오의 메시지 전송 · 첨부 presign 앞에서 부른다. 판정은 이 한 곳.
 * {@code MessageThreadService.sendMessage} 자체는 건드리지 않는다 — 카드 등록(시스템)은 종결 트랜잭션 안에서 종결 직후에도 들어가야 한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementAdjustmentThreadGuard {

    private final SettlementAdjustmentRepository adjustmentRepository;

    public void requireWritable(MessageThread thread) {
        if (thread.getKind() != ThreadKind.SETTLEMENT_ADJUSTMENT) {
            return;
        }
        adjustmentRepository.findByThreadId(thread.getId())
                .filter(adjustment -> adjustment.getStatus().isClosed())
                .ifPresent(closed -> {
                    throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_CLOSED);
                });
    }
}
