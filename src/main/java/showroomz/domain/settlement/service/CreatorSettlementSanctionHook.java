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
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 인플루언서 계정 정지 ↔ 리워드 지급(§51-3 · 53 설계서 4-3). 브랜드의 {@link SettlementSanctionHook}과 같은 틀이다 — 새 상태 · 새 버튼을
 * 만들지 않고 수취자 행의 {@code BLOCKED} + {@link PayoutBlockReason#CREATOR_SUSPENDED}로 표현한다.
 *
 * <p>인플루언서 몫은 다른 보류 사유(사업자 세금계산서 확인 전 · 비사업자 주민등록번호 미등록)가 겹칠 수 있다 — 해제는
 * {@link PayoutBlockPolicy}로 <b>남은 사유를 다시 본다</b>. 남으면 정지 사유만 지우고 보류를 유지한다(기존 재판정 · 운영자 M4 가 푼다).
 * 확정 때 정책 보류로 사유 없이 BLOCKED 가 된 행(사업자 · 세금계산서 확인 완료)은 기존 배치가 풀지 않으므로 여기서 함께 푼다.
 *
 * <p>지시가 나간 행(REQUESTED)은 돌이킬 수 없어 건드리지 않는다. 브랜드 · 플랫폼 몫도 손대지 않는다. 호출자(정지 · 해제) 트랜잭션에 합류한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorSettlementSanctionHook {

    private static final String CODE = PayoutBlockReason.CREATOR_SUSPENDED.name();
    private static final String REASON = PayoutBlockReason.CREATOR_SUSPENDED.getLabel();

    private final SettlementPayoutRepository payoutRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementHistoryRecorder historyRecorder;
    private final PayoutBlockPolicy blockPolicy;
    private final BusinessCalendar businessCalendar;

    /** 정지(지급 정지 사유) — 그 인플루언서 몫 SCHEDULED → BLOCKED(CREATOR_SUSPENDED). 확정 전 행은 확정이 판정한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int holdCreatorPayouts(Long creatorId, LocalDateTime now) {
        int held = 0;
        for (SettlementPayout payout : payoutRepository.findScheduledForCreator(creatorId)) {
            if (payoutRepository.holdScheduled(payout.getId(), CODE, REASON) == 1) {
                historyRecorder.recordBySystem(payout.getSettlementId(), SettlementEventType.PAYOUT_BLOCKED,
                        "인플루언서 몫 · " + REASON, now);
                held++;
            }
        }
        log.info("인플루언서 정지 · 지급 보류 - creatorId: {}, rows: {}", creatorId, held);
        return held;
    }

    /**
     * 해제 — 정지 사유 · 사유 없는 보류 행을 다시 판정한다. 다른 보류 사유가 없으면 SCHEDULED(예정일 = 해제일의 다음 영업일), 남으면
     * 정지 사유만 지운다. 호출 전에 정지 행이 이미 닫혀 있어야 한다(정책이 정지를 보지 않게).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int releaseCreatorPayouts(Long creatorId, LocalDate today, LocalDateTime now) {
        LocalDate due = businessCalendar.addBusinessDays(today, 1);
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
            if (payoutRepository.releaseCreatorSuspension(payout.getId(), CODE, due) == 1) {
                historyRecorder.recordBySystem(payout.getSettlementId(), SettlementEventType.PAYOUT_HOLD_RELEASED,
                        "인플루언서 몫 · 정지 해제 · " + due + " 지급 예정", now);
                released++;
            }
        }
        log.info("인플루언서 정지 해제 · 지급 보류 해제 - creatorId: {}, rows: {}, due: {}", creatorId, released, due);
        return released;
    }
}
