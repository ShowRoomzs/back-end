package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * 정산 확정(44 어드민 설계서 3-2) — 자동(확인 기간 경과) · 합의 · 기한 만료가 <b>같은 메서드</b>를 부른다. 확정 주체는 시스템뿐이다
 * (운영자 「정산 확정」 없음 · 12절 #1).
 *
 * <p>{@code confirmed_at}은 배치 실행 시각이 아니라 <b>규칙값</b>이다(0-10) — 자동 확정 = 마감 다음 날 00:00 · 합의 = 동의 시각 ·
 * 만료 = 합의 기한 다음 날 00:00. 지급 예정일 = 확정일 + N영업일이라 배치가 늦어도 밀리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementConfirmService {

    private static final EnumSet<SettlementStatus> CONFIRMABLE =
            EnumSet.of(SettlementStatus.REVIEWING, SettlementStatus.ADJUSTING);

    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementHistoryRecorder historyRecorder;
    private final PayoutBlockPolicy blockPolicy;
    private final SettlementSchedule schedule;
    private final SettlementTaxDocumentHook taxDocumentHook;
    private final SettlementNotifier notifier;

    @Transactional(readOnly = true)
    public List<Long> findIdsToAutoConfirm(LocalDateTime now, int limit) {
        return settlementRepository.findIdsToAutoConfirm(now, PageRequest.of(0, limit));
    }

    /**
     * 자동 확정(3-2) — 행마다 새 트랜잭션. 마감이 지났고 아직 정산 확인 중일 때만 · 확정 시각은 마감 다음 날 00:00.
     *
     * @return 확정했으면 true — 조정 요청이 먼저였거나 이미 확정됐으면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean autoConfirm(Long settlementId, LocalDateTime now) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId).orElse(null);
        if (settlement == null || settlement.getStatus() != SettlementStatus.REVIEWING
                || !settlement.getReviewDueAt().isBefore(now)) {
            return false;
        }
        confirm(settlementId, SettlementConfirmReason.AUTO, settlement.autoConfirmAt());
        return true;
    }

    /**
     * 확정 공통(3-2) — 호출자 트랜잭션에 합류한다(자동 확정 배치 · 조정 포트의 합의 · 만료).
     *
     * <ol>
     *   <li>조건부 UPDATE {@code REVIEWING · ADJUSTING → PAYOUT_SCHEDULED} — 0행이면 409</li>
     *   <li>수취자 3행 — 브랜드 · 플랫폼 {@code SCHEDULED(예정일)} · 인플루언서는 보류 사유가 있으면 {@code BLOCKED}, 0원이면
     *       {@code NOT_APPLICABLE}. 확정 전 상태(WAITING · HELD)에서만 옮긴다</li>
     *   <li>증빙 행(세금계산서 발행 대기 · 입력 대기) — 증빙 모듈 훅</li>
     *   <li>이력 — 「자동 확정 10.06 + 3영업일 · 10.09 한글날 제외」(파트너 「정산 근거」의 원천)</li>
     * </ol>
     */
    @Transactional
    public void confirm(Long settlementId, SettlementConfirmReason reason, LocalDateTime confirmedAt) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (settlementRepository.transition(settlementId, CONFIRMABLE, SettlementStatus.PAYOUT_SCHEDULED) != 1) {
            throw new BusinessException(ErrorCode.SETTLEMENT_STATE_CHANGED);
        }
        LocalDate confirmedDate = confirmedAt.toLocalDate();
        LocalDate payoutDueDate = schedule.payoutDueDate(confirmedDate);
        settlement.applyConfirmed(reason, confirmedAt, payoutDueDate);
        boolean creatorBlocked = blockPolicy.isBlocked(settlement);
        String basis = schedule.payoutBasis(reason, confirmedDate, payoutDueDate);

        // 수취자 행 UPDATE 는 영속성 컨텍스트를 비운다 — 정산 엔티티의 변경은 그 전에 자동 flush 된다.
        for (SettlementPayout payout : payoutRepository.findBySettlementId(settlementId)) {
            PayoutStatus to;
            LocalDate dueDate = null;
            if (payout.getAmount() <= 0) {
                to = PayoutStatus.NOT_APPLICABLE;
            } else if (payout.getPayee() == SettlementPayee.CREATOR && creatorBlocked) {
                to = PayoutStatus.BLOCKED;
            } else {
                to = PayoutStatus.SCHEDULED;
                dueDate = payoutDueDate;
            }
            if (payoutRepository.updateStatusWhere(payout.getId(), PayoutStatus.BEFORE_CONFIRM, to, dueDate) != 1) {
                throw new BusinessException(ErrorCode.SETTLEMENT_STATE_CHANGED);
            }
        }
        taxDocumentHook.onConfirmed(settlement, confirmedAt);
        historyRecorder.recordBySystem(settlementId, eventOf(reason), basis, confirmedAt);
        afterCommit(() -> notifier.settlementConfirmed(settlementId));
        log.info("정산 확정 - settlementId: {}, reason: {}, payoutDueDate: {}", settlementId, reason, payoutDueDate);
    }

    private static SettlementEventType eventOf(SettlementConfirmReason reason) {
        return switch (reason) {
            case AUTO -> SettlementEventType.AUTO_CONFIRMED;
            case AGREED -> SettlementEventType.CONFIRMED_BY_AGREEMENT;
            case EXPIRED -> SettlementEventType.CONFIRMED_BY_EXPIRY;
        };
    }

    static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
