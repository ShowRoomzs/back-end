package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway.LineResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.Outcome;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutCommand;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutLookup;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.Reconciliation;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 지급 배치의 서비스(44 어드민 설계서 3-3) — 트랜잭션 밖에서 게이트웨이를 부르고 짧은 트랜잭션들({@link SettlementPayoutTransitions})로
 * 상태를 옮긴다. 포트원 모드에서는 지시가 REQUESTED 로 남고 {@link #pollResults} 가 닫는다(포트원 설계서 5절).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementPayoutService {

    private static final int RELEASE_BATCH = 200;
    private static final int POLL_BATCH = 500;

    private final SettlementPayoutRepository payoutRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementPayoutTransitions transitions;
    private final SettlementPayoutGateway gateway;
    private final SettlementPartnerSyncService partnerSync;
    private final SettlementProperties properties;
    private final BusinessCalendar businessCalendar;
    private final SettlementNotifier notifier;

    @Transactional(readOnly = true)
    public List<Long> findSettlementIdsDue(LocalDate today, int limit) {
        return payoutRepository.findSettlementIdsDue(today, PageRequest.of(0, limit));
    }

    /** 정산 1건의 예정일 도래 행 지급. 게이트웨이 예외(결과 모름)는 REQUESTED 로 둔다 — PG 결과 조회가 닫는다. */
    public void distribute(Long settlementId, LocalDate today, LocalDateTime now) {
        PayoutCommand command = transitions.claim(settlementId, today, now);
        if (command == null) {
            return;
        }
        PayoutResult result = command.lines().isEmpty() ? new PayoutResult(List.of()) : call(command);
        transitions.applyResults(settlementId, command, result, now);
    }

    /**
     * 보류 재판정 — 지급 배치 앞단. ① 파트너 사유로 보류된 행(수취자 무관)은 파트너 등록을 다시 시도한다(포트원 설계서 4-3)
     * ② 그 밖의 인플루언서 보류 행은 주민등록번호 · 세금계산서 재판정(5-4).
     */
    public int releaseBlocked(LocalDate today) {
        int released = 0;
        for (SettlementPayout payout : findPartnerBlocked()) {
            try {
                PartnerResult result = partnerSync.ensure(payout.getSettlementId(), payout.getPayee());
                if (result.isApproved()) {
                    if (transitions.releasePartnerBlocked(payout.getSettlementId(), payout.getId(), payout.getPayee(), today)) {
                        released++;
                    }
                } else if (result.failCode() != null && !result.failCode().equals(payout.getFailCode())) {
                    transitions.updateBlockReason(payout.getId(), result.failCode(), result.failReason());
                }
            } catch (Exception e) {
                log.error("PG 파트너 보류 재판정 실패 - payoutId: {}", payout.getId(), e);
            }
        }
        for (SettlementPayout payout : findBlocked()) {
            if (payout.getFailCode() != null && PayoutBlockReason.PARTNER_CODES.contains(payout.getFailCode())) {
                continue;
            }
            try {
                if (transitions.releaseIfUnblocked(payout.getSettlementId(), payout.getId(), today)) {
                    released++;
                }
            } catch (Exception e) {
                log.error("지급 보류 재판정 실패 - payoutId: {}", payout.getId(), e);
            }
        }
        return released;
    }

    @Transactional(readOnly = true)
    public List<SettlementPayout> findBlocked() {
        return payoutRepository.findBlockedCreatorPayouts(PageRequest.of(0, RELEASE_BATCH));
    }

    @Transactional(readOnly = true)
    public List<SettlementPayout> findPartnerBlocked() {
        return payoutRepository.findBlockedWithFailCodes(PayoutBlockReason.PARTNER_CODES, PageRequest.of(0, RELEASE_BATCH));
    }

    // ------------------------------------------------------------------ 결과 조회(포트원 설계서 5-4 · 9-2)

    /** REQUESTED 행의 결과를 PG 에 조회해 닫는다 — 정산 단위로 묶어 게이트웨이 1회. 지급 미실행은 하루 1회(지급 시각 틱)만 알린다. */
    public void pollResults(LocalDateTime now) {
        List<SettlementPayout> requested = findRequested();
        if (requested.isEmpty()) {
            return;
        }
        Map<Long, List<SettlementPayout>> bySettlement = new LinkedHashMap<>();
        for (SettlementPayout payout : requested) {
            bySettlement.computeIfAbsent(payout.getSettlementId(), id -> new ArrayList<>()).add(payout);
        }
        boolean overdueTick = now.getHour() == properties.getPayoutHour() && now.getMinute() < 30;
        LocalDate overdueBefore = now.toLocalDate();
        for (Map.Entry<Long, List<SettlementPayout>> entry : bySettlement.entrySet()) {
            Long settlementId = entry.getKey();
            try {
                String number = settlementRepository.findById(settlementId).map(Settlement::getSettlementNumber).orElse(null);
                if (number == null) {
                    continue;
                }
                List<PayoutLookup> lookups = entry.getValue().stream()
                        .map(p -> new PayoutLookup(p.getId(), settlementId, number, p.getPayee(), p.getAmount(),
                                p.getAttempt(), p.getPgTransferId(), p.getPgPayoutId()))
                        .toList();
                List<LineResult> results = gateway.lookup(lookups);
                if (!results.isEmpty()) {
                    transitions.applyLookupResults(settlementId, results, now);
                }
                if (overdueTick) {
                    for (SettlementPayout payout : entry.getValue()) {
                        boolean closed = results.stream().anyMatch(r -> r.payoutId().equals(payout.getId())
                                && r.outcome() != Outcome.REQUESTED);
                        if (!closed && payout.getPgPayoutId() == null && payout.getRequestedAt() != null
                                && !businessCalendar.addBusinessDays(payout.getRequestedAt().toLocalDate(),
                                properties.getPayout().getResultStaleBusinessDays()).isAfter(overdueBefore)) {
                            transitions.markExecutionOverdue(settlementId, payout.getPayee(), now);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("정산 지급 결과 조회 실패 - settlementId: {}", settlementId, e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<SettlementPayout> findRequested() {
        return payoutRepository.findRequested(PageRequest.of(0, POLL_BATCH));
    }

    /** 지시 끝(포트원 설계서 5-3) — 오늘 올린 건수 · 합계를 PG 와 대조해 운영자에게 「콘솔에서 일괄 지급 실행」을 알린다. */
    public void notifyReadyForExecution(LocalDate today) {
        Object[] counts = countRequested(today);
        int ourCount = ((Number) counts[0]).intValue();
        if (ourCount == 0) {
            return;
        }
        long ourAmount = ((Number) counts[1]).longValue();
        Optional<Reconciliation> pg = Optional.empty();
        try {
            pg = gateway.reconcile(today);
        } catch (RuntimeException e) {
            log.error("정산 지급 대조 실패 - date: {}", today, e);
        }
        notifier.payoutReadyForExecution(today, ourCount, ourAmount, pg.map(Reconciliation::count).orElse(null),
                pg.map(Reconciliation::amount).orElse(null));
        if (pg.isPresent() && (pg.get().count() != ourCount || pg.get().amount() != ourAmount)) {
            log.warn("정산 지급 대조 불일치 - date: {}, ours: {}건/{}원, pg: {}건/{}원", today, ourCount, ourAmount,
                    pg.get().count(), pg.get().amount());
        }
    }

    @Transactional(readOnly = true)
    public Object[] countRequested(LocalDate today) {
        List<Object[]> rows = payoutRepository.countRequestedBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        return rows.isEmpty() ? new Object[]{0L, 0L} : rows.get(0);
    }

    private PayoutResult call(PayoutCommand command) {
        try {
            return gateway.distribute(command);
        } catch (RuntimeException e) {
            log.error("정산 지급 지시 결과 미확인 — REQUESTED 로 둔다 - settlementId: {}", command.settlementId(), e);
            return new PayoutResult(command.lines().stream()
                    .map(line -> new LineResult(line.payoutId(), Outcome.REQUESTED, null, null, null)).toList());
        }
    }
}
