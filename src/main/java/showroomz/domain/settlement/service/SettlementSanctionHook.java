package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 계정 정지 ↔ 브랜드 몫 지급(§51-3 · 52 설계서 6절). 새 상태 · 새 버튼을 만들지 않는다 — 수취자 행의 {@code BLOCKED} +
 * {@link PayoutBlockReason#BRAND_SUSPENDED} 다. 어드민 07b · 파트너 13 은 수취자 행의 상태 · 사유 라벨을 이미 내린다(표시만).
 *
 * <p>정산은 생성 · 확인 · 확정까지 그대로 간다. 인플루언서 · 플랫폼 몫은 손대지 않는다. 지시가 나간 행(REQUESTED)은 돌이킬 수 없어
 * 건드리지 않는다. 호출자(정지 · 해제) 트랜잭션에 합류한다 — 정지가 롤백되면 보류도 같이 롤백된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementSanctionHook {

    private static final String CODE = PayoutBlockReason.BRAND_SUSPENDED.name();
    private static final String REASON = PayoutBlockReason.BRAND_SUSPENDED.getLabel();

    private final SettlementPayoutRepository payoutRepository;
    private final SettlementHistoryRecorder historyRecorder;
    private final BusinessCalendar businessCalendar;

    /** 정지(지급 정지 사유) — 그 마켓의 브랜드 몫 SCHEDULED → BLOCKED(BRAND_SUSPENDED). 확정 전 행은 확정이 판정한다(6-3). */
    @Transactional(propagation = Propagation.MANDATORY)
    public int holdBrandPayouts(Long marketId, LocalDateTime now) {
        int held = 0;
        for (SettlementPayout payout : payoutRepository.findScheduledForMarket(marketId)) {
            if (payoutRepository.holdScheduled(payout.getId(), CODE, REASON) == 1) {
                historyRecorder.recordBySystem(payout.getSettlementId(), SettlementEventType.PAYOUT_BLOCKED,
                        "브랜드 몫 · " + REASON, now);
                held++;
            }
        }
        log.info("브랜드 정지 · 지급 보류 - marketId: {}, rows: {}", marketId, held);
        return held;
    }

    /** 해제 — 그 사유로 보류된 브랜드 몫만 SCHEDULED(예정일 = 해제일의 다음 영업일). 다른 보류 사유(PG 셀러 등)는 그대로다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int releaseBrandPayouts(Long marketId, LocalDate today, LocalDateTime now) {
        LocalDate due = businessCalendar.addBusinessDays(today, 1);
        List<SettlementPayout> blocked = payoutRepository.findBlockedForMarket(marketId, Set.of(CODE));
        int released = 0;
        for (SettlementPayout payout : blocked) {
            if (payoutRepository.releaseHold(payout.getId(), CODE, due) == 1) {
                historyRecorder.recordBySystem(payout.getSettlementId(), SettlementEventType.PAYOUT_HOLD_RELEASED,
                        "브랜드 몫 · 정지 해제 · " + due + " 지급 예정", now);
                released++;
            }
        }
        log.info("브랜드 정지 해제 · 지급 보류 해제 - marketId: {}, rows: {}, due: {}", marketId, released, due);
        return released;
    }
}
