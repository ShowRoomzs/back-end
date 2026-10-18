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
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회원 사유 지급 보류의 <b>전이 한 곳</b>(§51-3 · 52 설계서 6절 · 53 설계서 4-3 · 50 설계서 6-6 · 정합 계획서 R2) — 브랜드 정지 · 인플루언서
 * 정지 · 인플루언서 계좌 확인 중이 같은 틀을 쓴다. 새 상태 · 새 버튼 없이 수취자 행의 {@code BLOCKED} + 사유 코드({@code fail_code})로
 * 표현한다. 어드민 07b · 파트너 13 · 스튜디오 정산은 수취자 행의 상태 · 사유 라벨을 이미 내린다(표시만).
 *
 * <ul>
 *   <li><b>보류</b>({@link #hold}) — 그 회원 몫의 예정(SCHEDULED) 행 → BLOCKED(사유). 확정 전 행은 확정이 판정하고, 지시가 나간 행(REQUESTED)은
 *   돌이킬 수 없어 건드리지 않는다. 확정 · 지급 지시 앞단은 행 하나씩({@link #holdRow}) 같은 전이를 쓴다.</li>
 *   <li><b>해제</b>({@link #release}) — 그 사유로 보류된 행을 {@link PayoutBlockPolicy}로 <b>다시 판정</b>한다. 남은 사유가 없으면 예정으로
 *   올리고, 남으면(세금계산서 확인 전 · 다른 정지 등) 이 사유 코드만 지우고 보류를 유지한다. 인플루언서 몫은 확정 때 정책 보류로 사유 없이
 *   BLOCKED 가 된 행도 함께 본다 — 기존 배치가 풀지 않는 사업자 건을 여기서 푼다.</li>
 * </ul>
 *
 * <p>전이는 영속성 컨텍스트를 비우지 않는 쿼리({@code holdScheduled} · {@code releaseHold} · {@code clearHoldCode})만 쓴다 — 정지 · 확정
 * 트랜잭션이 붙들고 있는 마켓 · 인플루언서 · 정지 행이 준영속으로 떨어지면 그 뒤 변경이 저장되지 않는다(52 설계서 15 #9). 호출자 트랜잭션에
 * 합류한다 — 조치가 롤백되면 보류도 같이 롤백된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutHoldTransitions {

    /** 해제 뒤 예정일 — 정지 해제는 「다음 영업일」(§51-3), 계좌 확인 완료는 「원래 예정일과 오늘 중 늦은 날」(지급 배치 앞단 재판정과 같은 규칙). */
    public enum DueRule {
        NEXT_BUSINESS_DAY,
        ORIGINAL_OR_TODAY
    }

    private final SettlementPayoutRepository payoutRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementHistoryRecorder historyRecorder;
    private final PayoutBlockPolicy blockPolicy;
    private final BusinessCalendar businessCalendar;

    /** 그 회원 몫의 예정 행을 전부 보류한다. @return 옮긴 행 수 */
    @Transactional(propagation = Propagation.MANDATORY)
    public int hold(SettlementPayee payee, Long refId, PayoutBlockReason reason, LocalDateTime now) {
        int held = 0;
        for (SettlementPayout payout : payoutRepository.findScheduledFor(payee, refId)) {
            if (holdRow(payout, reason, now)) {
                held++;
            }
        }
        log.info("지급 보류 - payee: {}, refId: {}, reason: {}, rows: {}", payee, refId, reason, held);
        return held;
    }

    /** 예정 행 하나를 보류한다 — 확정 · 지급 지시 앞단이 쓴다. 이미 예정이 아니면(경합) false. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean holdRow(SettlementPayout payout, PayoutBlockReason reason, LocalDateTime now) {
        if (payoutRepository.holdScheduled(payout.getId(), reason.name(), reason.getLabel()) != 1) {
            return false;
        }
        historyRecorder.recordBySystem(payout.getSettlementId(), SettlementEventType.PAYOUT_BLOCKED,
                payout.getPayee().getLabel() + " 몫 · " + reason.getLabel(), now);
        return true;
    }

    /**
     * 그 사유로 보류된 행을 다시 판정해 푼다. 호출 전에 사유의 원인(정지 행 · 계좌 확인 상태)이 이미 풀려 있어야 한다 — 정책이 그 사유를 다시
     * 보지 않게. @return 예정으로 올린 행 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int release(SettlementPayee payee, Long refId, PayoutBlockReason reason, DueRule dueRule, LocalDate today,
                       LocalDateTime now) {
        boolean includeUncoded = payee == SettlementPayee.CREATOR;
        String code = reason.name();
        int released = 0;
        for (SettlementPayout payout : payoutRepository.findRowsForRelease(payee, refId, code, includeUncoded)) {
            Settlement settlement = settlementRepository.findById(payout.getSettlementId()).orElse(null);
            if (settlement == null) {
                continue;
            }
            if (stillBlocked(payee, settlement)) {
                payoutRepository.clearHoldCode(payout.getId(), code);
                continue;
            }
            LocalDate due = dueDate(dueRule, settlement, today);
            if (payoutRepository.releaseHold(payout.getId(), code, includeUncoded, due) == 1) {
                historyRecorder.recordBySystem(payout.getSettlementId(), SettlementEventType.PAYOUT_HOLD_RELEASED,
                        payee.getLabel() + " 몫 · " + releaseLabel(reason) + " · " + due + " 지급 예정", now);
                released++;
            }
        }
        log.info("지급 보류 해제 - payee: {}, refId: {}, reason: {}, rows: {}", payee, refId, reason, released);
        return released;
    }

    private boolean stillBlocked(SettlementPayee payee, Settlement settlement) {
        return switch (payee) {
            case BRAND -> blockPolicy.isBrandBlocked(settlement);
            case CREATOR -> blockPolicy.isBlocked(settlement);
            case PLATFORM -> false;
        };
    }

    private LocalDate dueDate(DueRule rule, Settlement settlement, LocalDate today) {
        return switch (rule) {
            case NEXT_BUSINESS_DAY -> businessCalendar.addBusinessDays(today, 1);
            case ORIGINAL_OR_TODAY -> settlement.getPayoutDueDate() == null || settlement.getPayoutDueDate().isBefore(today)
                    ? today : settlement.getPayoutDueDate();
        };
    }

    private static String releaseLabel(PayoutBlockReason reason) {
        return reason == PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED ? "정산 계좌 확인 완료" : "정지 해제";
    }
}
