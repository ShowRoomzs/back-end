package showroomz.api.common.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.thread.dto.ThreadAdjustmentBadge;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader.AdjustmentSummary;
import showroomz.domain.settlement.adjustment.type.AdjustmentTurn;
import showroomz.domain.settlement.adjustment.type.SettlementParty;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 파트너센터 · 스튜디오 스레드 응답의 정산 조정 협의 확장(44 이슈 스레드 설계서 3-6) — 목록 줄 배지 · 카드 버튼(지금 답할 수 있는 제안).
 * 두 서피스가 뷰어(당사자)만 바꿔 같은 판정을 쓴다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThreadAdjustmentViews {

    private final SettlementAdjustmentReader reader;

    /** 스레드 id → 배지. 조정 스레드가 아니면 맵에 없다. */
    public Map<Long, ThreadAdjustmentBadge> badges(List<MessageThread> threads, SettlementParty viewer) {
        List<Long> ids = threads.stream().filter(t -> t.getKind() == ThreadKind.SETTLEMENT_ADJUSTMENT)
                .map(MessageThread::getId).toList();
        Map<Long, ThreadAdjustmentBadge> badges = new HashMap<>();
        if (ids.isEmpty()) {
            return badges;
        }
        reader.findByThreadIds(ids).forEach((threadId, summary) -> badges.put(threadId, badge(summary, viewer)));
        return badges;
    }

    /**
     * 이 스레드에서 뷰어가 지금 답할 수 있는 제안 — 최신 제안이고 동의 · 반대 · 다른 금액 제안 중 하나라도 열려 있으면 그 id, 아니면 null.
     * 카드의 {@code respondable}이 이 값과 카드의 제안 id 를 비교한다.
     */
    public Long respondableProposalId(MessageThread thread, SettlementParty viewer) {
        if (thread.getKind() != ThreadKind.SETTLEMENT_ADJUSTMENT) {
            return null;
        }
        return reader.findByThreadId(thread.getId())
                .filter(summary -> summary.latestProposal() != null)
                .filter(summary -> SettlementAdjustmentReader.permissions(summary, viewer, LocalDateTime.now())
                        .canRespond())
                .map(summary -> summary.latestProposal().proposalId())
                .orElse(null);
    }

    private static ThreadAdjustmentBadge badge(AdjustmentSummary summary, SettlementParty viewer) {
        AdjustmentTurn turn = SettlementAdjustmentReader.turnFor(summary, viewer);
        return new ThreadAdjustmentBadge(summary.adjustmentId(), summary.status().name(), summary.status().getLabel(),
                turn.name(), turn.getLabel(), turn.getTone().name(), summary.deadlineAt(),
                summary.remainingBusinessDays());
    }
}
