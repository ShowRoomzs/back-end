package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.SettlementEventType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 인플루언서 정산 계좌 변경 ↔ 리워드 지급(50 설계서 1-3 · 6-6) — {@link CreatorSettlementSanctionHook}과 같은 틀이다. 새 상태 · 새 버튼 없이
 * 수취자 행의 {@code BLOCKED} + {@link PayoutBlockReason#CREATOR_ACCOUNT_UNVERIFIED}로 표현한다.
 *
 * <p>지시가 나간 행(REQUESTED)은 이전 계좌로 끝난다 — 건드리지 않는다. 확정 · 지시 앞단은 {@link PayoutBlockPolicy}가 같은 사유로 막으므로
 * 여기서는 변경 순간 이미 예정(SCHEDULED)인 행만 옮기고, 확인이 끝나면 다시 판정해 푼다. 지급 배치 앞단의 재판정은 사업자 건을 풀지 않으므로
 * (운영자 M4 몫) 해제는 배치에 맡기지 않는다. 호출자 트랜잭션에 합류한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorAccountPayoutHook {

    private static final String CODE = PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED.name();
    private static final String REASON = PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED.getLabel();

    private final SettlementPayoutRepository payoutRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementHistoryRecorder historyRecorder;
    private final PayoutBlockPolicy blockPolicy;

    /** 계좌 변경 — 그 인플루언서 몫 SCHEDULED → BLOCKED(정산 계좌 확인 중). 확정 전 행은 확정이 판정한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int hold(Long creatorId, LocalDateTime now) {
        int held = 0;
        for (SettlementPayout payout : payoutRepository.findScheduledForCreator(creatorId)) {
            if (payoutRepository.holdScheduled(payout.getId(), CODE, REASON) == 1) {
                historyRecorder.recordBySystem(payout.getSettlementId(), SettlementEventType.PAYOUT_BLOCKED,
                        "인플루언서 몫 · " + REASON, now);
                held++;
            }
        }
        log.info("인플루언서 계좌 변경 · 지급 보류 - creatorId: {}, rows: {}", creatorId, held);
        return held;
    }

    /**
     * 확인 완료 — 이 사유 · 사유 없는 보류 행을 다시 판정한다. 남은 사유가 없으면 SCHEDULED(예정일 = 원래 예정일과 오늘 중 늦은 날 · 지급 배치
     * 앞단 재판정과 같은 규칙), 남으면(세금계산서 확인 전 · 정지 등) 이 사유 코드만 지우고 보류를 유지한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int release(Long creatorId, LocalDate today, LocalDateTime now) {
        int released = 0;
        for (SettlementPayout payout : payoutRepository.findCreatorRowsForSuspensionRelease(creatorId, CODE)) {
            Settlement settlement = settlementRepository.findById(payout.getSettlementId()).orElse(null);
            if (settlement == null) {
                continue;
            }
            if (blockPolicy.isBlocked(settlement)) {
                payoutRepository.clearCreatorSuspensionCode(payout.getId(), CODE);
                continue;
            }
            LocalDate due = settlement.getPayoutDueDate() == null || settlement.getPayoutDueDate().isBefore(today)
                    ? today : settlement.getPayoutDueDate();
            if (payoutRepository.releaseCreatorSuspension(payout.getId(), CODE, due) == 1) {
                historyRecorder.recordBySystem(payout.getSettlementId(), SettlementEventType.PAYOUT_HOLD_RELEASED,
                        "인플루언서 몫 · 정산 계좌 확인 완료 · " + due + " 지급 예정", now);
                released++;
            }
        }
        log.info("인플루언서 계좌 확인 · 지급 보류 해제 - creatorId: {}, rows: {}", creatorId, released);
        return released;
    }
}
