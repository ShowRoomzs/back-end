package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.entity.SettlementHistory;
import showroomz.domain.settlement.repository.SettlementHistoryRepository;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementEventType;

import java.time.LocalDateTime;

/** 정산 이력 append(44 어드민 설계서 1-8) — 전이와 같은 트랜잭션에서 쓴다. 이벤트 리스너로 떼지 않는다. */
@Component
@RequiredArgsConstructor
public class SettlementHistoryRecorder {

    private final SettlementHistoryRepository historyRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long settlementId, SettlementEventType eventType, SettlementActorType actorType, Long actorId,
                       String detail, LocalDateTime occurredAt) {
        historyRepository.save(SettlementHistory.of(settlementId, eventType, actorType, actorId, detail, occurredAt));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordBySystem(Long settlementId, SettlementEventType eventType, String detail,
                               LocalDateTime occurredAt) {
        record(settlementId, eventType, SettlementActorType.SYSTEM, null, detail, occurredAt);
    }
}
