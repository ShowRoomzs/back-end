package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentPort;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;

/**
 * 조정 협의 → 정산 포트 구현(44 어드민 설계서 4-2). 금액은 정산이 소유한다 — 상한 · 미리보기 · 재계산은 {@link SettlementCalculator},
 * 확정은 {@link SettlementConfirmService#confirm}(자동 확정과 같은 메서드)다.
 *
 * <p>세 쓰기 메서드는 호출자 트랜잭션에 합류(MANDATORY) · 멱등 · <b>DB 상태 전이만</b> 한다 — 분배 호출은 지급 배치다(이슈 스레드 설계서
 * 10-3 #1).
 */
@Component
@RequiredArgsConstructor
public class SettlementAdjustmentPortAdapter implements SettlementAdjustmentPort {

    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementAdjustmentRepository adjustmentRepository;
    private final SettlementCalculator calculator;
    private final SettlementConfirmService confirmService;
    private final SettlementHistoryRecorder historyRecorder;

    /** {@code inReviewWindow} = 정산 확인 중 ∧ 마감 전 ∧ 협의 없음(3-1 · 조회 · 자동 확정과 같은 식). */
    @Override
    @Transactional(readOnly = true)
    public Optional<AdjustableSettlement> find(Long settlementId) {
        LocalDateTime now = LocalDateTime.now();
        return settlementRepository.findDetailById(settlementId).map(s -> new AdjustableSettlement(s.getId(),
                s.getSettlementNumber(), s.getGroupBuyId(), s.getContract().getTitle(), s.getContractId(),
                s.getContract().getContractNumber(), s.getMarketId(), s.getCreatorId(),
                s.isInReviewWindow(now) && adjustmentRepository.findBySettlementId(settlementId).isEmpty(),
                s.getReviewDueAt(), s.getRewardAmount(), calculator.maxRewardAmount(s), s.getBrandPayoutAmount()));
    }

    @Override
    @Transactional(readOnly = true)
    public AmountPreview preview(Long settlementId, long rewardAmount) {
        Settlement s = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        SettlementAmounts a = calculator.preview(s, rewardAmount);
        return new AmountPreview(a.rewardAmount(), a.rewardVatAmount(), a.withholdingAmount(), a.creatorPayoutAmount(),
                a.brandPayoutAmount());
    }

    /**
     * 정산 확인 중 → 조정 협의(전액 보류). 마감을 UPDATE 조건으로 다시 본다 — 자동 확정이 먼저였으면 0행 · WINDOW_CLOSED 로 요청 전체가
     * 롤백된다. 차액 선지급은 없다(§41-5).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void holdForAdjustment(Long settlementId, Long adjustmentId, LocalDateTime now) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (settlement.getStatus() == SettlementStatus.ADJUSTING) {
            return; // 멱등 — 이미 보류됐다
        }
        if (settlementRepository.holdIfInReviewWindow(settlementId, now) != 1) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_WINDOW_CLOSED);
        }
        settlement.applyAdjusting();
        payoutRepository.updateAllStatusWhere(settlementId, EnumSet.of(PayoutStatus.WAITING), PayoutStatus.HELD);
        SettlementParty requester = adjustmentRepository.findById(adjustmentId)
                .map(SettlementAdjustment::getRequesterType).orElse(null);
        historyRecorder.record(settlementId, SettlementEventType.ADJUSTMENT_REQUESTED, actorOf(requester),
                requester == null ? null : adjustmentRepository.findById(adjustmentId)
                        .map(a -> a.partyIdOf(requester)).orElse(null),
                "조정 요청 접수 · 전액 보류", now);
    }

    /** 합의 확정(4-3) — 리워드 이하 행만 다시 계산하고 확정한다. 확정 시각 = 동의 시각 · 지급 예정일 = 확정일 + N영업일. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmByAgreement(Long settlementId, Long adjustmentId, long agreedRewardAmount, LocalDateTime now) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (settlement.getStatus() != SettlementStatus.ADJUSTING) {
            if (settlement.getStatus().isConfirmed() && settlement.getRewardAmount() == agreedRewardAmount) {
                return; // 멱등 — 이미 합의 금액으로 확정됐다
            }
            throw new BusinessException(ErrorCode.SETTLEMENT_STATE_CHANGED);
        }
        long original = settlement.getRewardAmount();
        SettlementAmounts amounts = calculator.recalculateForReward(settlement, agreedRewardAmount);
        settlement.applyAmounts(amounts);
        // 수취자 행 UPDATE 가 정산 엔티티의 변경을 먼저 flush 한다.
        payoutRepository.updateAmountBeforeConfirm(settlementId, SettlementPayee.BRAND, amounts.brandPayoutAmount());
        payoutRepository.updateAmountBeforeConfirm(settlementId, SettlementPayee.CREATOR, amounts.creatorPayoutAmount());
        payoutRepository.updateAmountBeforeConfirm(settlementId, SettlementPayee.PLATFORM, amounts.platformShareAmount());
        historyRecorder.recordBySystem(settlementId, SettlementEventType.ADJUSTMENT_ACCEPTED,
                "합의 · 금액 변경 %,d → %,d".formatted(original, agreedRewardAmount), now);
        confirmService.confirm(settlementId, SettlementConfirmReason.AGREED, now);
    }

    /** 만료 확정 — 원래 금액 그대로 · 확정 시각 = 합의 기한 다음 날 00:00(규칙값 · 0-10). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmByExpiry(Long settlementId, Long adjustmentId, LocalDateTime now) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (settlement.getStatus() != SettlementStatus.ADJUSTING) {
            if (settlement.getStatus().isConfirmed()) {
                return; // 멱등
            }
            throw new BusinessException(ErrorCode.SETTLEMENT_STATE_CHANGED);
        }
        SettlementAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_NOT_FOUND));
        LocalDateTime confirmedAt = adjustment.getDeadlineAt().toLocalDate().plusDays(1).atStartOfDay();
        historyRecorder.recordBySystem(settlementId, SettlementEventType.ADJUSTMENT_EXPIRED,
                "합의 기한 경과 · 원래 금액 확정", now);
        confirmService.confirm(settlementId, SettlementConfirmReason.EXPIRED, confirmedAt);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEvent(Long settlementId, SettlementEventType type, SettlementParty actor, Long actorId,
                            String detail) {
        historyRecorder.record(settlementId, type, actorOf(actor), actor == null ? null : actorId, detail,
                LocalDateTime.now());
    }

    private static SettlementActorType actorOf(SettlementParty party) {
        return party == null ? SettlementActorType.SYSTEM : party.actorType();
    }
}
