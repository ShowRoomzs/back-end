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
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 지급 배치의 서비스(44 어드민 설계서 3-3) — 트랜잭션 밖에서 게이트웨이를 부르고 짧은 트랜잭션들({@link SettlementPayoutTransitions})로
 * 상태를 옮긴다.
 *
 * <ul>
 *   <li><b>묶음 지급</b>(시뮬레이터 · 이니시스 · 60_KG이니시스_지급대행_전환_계획서.md D-1 · D-2 · 6절) — 지시 = 영업일 X 10:00, 대상 = 지급일이
 *   X + 리드 영업일(D)이 되는 행. 선점이 행을 묶음에 붙이고 {@link SettlementPayoutGroupService} 가 합계를 올린다. 영업일 14:00 마감 전 틱
 *   ({@link #cutoffTick})이 늦게 풀린 행 지시 · 수렴 · 잔액 재검사를 한다. 결과는 폴링이 1차다.</li>
 *   <li><b>정산별 지급</b>(토스 · 삭제 전까지) — 지시가 REQUESTED 로 남고 웹훅 · {@link #pollResults} 가 닫는다(토스 설계서 5절).</li>
 * </ul>
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
    private final CreatorAccountVerificationService accountVerification;
    private final PayoutGroupPlanner groupPlanner;
    private final SettlementPayoutGroupService groupService;

    @Transactional(readOnly = true)
    public List<Long> findSettlementIdsDue(LocalDate today, int limit) {
        return payoutRepository.findSettlementIdsDue(today, PageRequest.of(0, limit));
    }

    /**
     * 지급 배치 본체(토스 설계서 5-1) — 예정일이 된 정산을 모아 잔액을 먼저 보고(①), 들어가는 만큼만 지시한다. 잔액을 모르는 모드
     * ({@code SIMULATED})는 전부 지시한다.
     *
     * @return 지시한 정산 수
     */
    public int distributeDue(LocalDate today, LocalDateTime now, int limit) {
        if (groupPlanner.enabled()) {
            return distributeDueInGroups(now, limit, true);
        }
        List<Long> due = findSettlementIdsDue(today, limit);
        if (due.isEmpty()) {
            return 0;
        }
        List<Long> allowed = due;
        Optional<Long> balance = Optional.empty();
        try {
            balance = gateway.availableBalance();
        } catch (RuntimeException e) {
            log.error("정산 지급 잔액 조회 실패 — 선검사 없이 지시한다(PG 가 거절하면 연기된다)", e);
        }
        if (balance.isPresent()) {
            allowed = fitToBalance(due, today, now, balance.get());
        }
        int distributed = 0;
        for (Long settlementId : allowed) {
            try {
                distribute(settlementId, today, now);
                distributed++;
            } catch (Exception e) {
                log.error("정산 지급 실패 - settlementId: {}", settlementId, e);
            }
        }
        return distributed;
    }

    /** 수취자 행 합계 내림차순으로 잔액 안에 들어가는 정산만 — 나머지는 다음 영업일로 연기 · 운영자 알림. */
    private List<Long> fitToBalance(List<Long> due, LocalDate today, LocalDateTime now, long available) {
        Map<Long, Long> totals = sumDueBySettlement(due, today);
        long required = totals.values().stream().mapToLong(Long::longValue).sum();
        if (required <= available) {
            return due;
        }
        List<Long> allowed = new ArrayList<>();
        long remaining = available;
        int deferred = 0;
        LocalDate next = businessCalendar.addBusinessDays(today, 1);
        List<Long> bySize = totals.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).toList();
        for (Long settlementId : bySize) {
            long total = totals.get(settlementId);
            if (total <= remaining) {
                remaining -= total;
                allowed.add(settlementId);
            } else {
                try {
                    deferred += transitions.deferForBalance(settlementId, today, next, now);
                } catch (Exception e) {
                    log.error("정산 지급 연기 실패 - settlementId: {}", settlementId, e);
                }
            }
        }
        // 금액이 0인(플랫폼 몫만 남은) 정산은 합계 표에 없다 — 그대로 지시한다.
        for (Long settlementId : due) {
            if (!totals.containsKey(settlementId)) {
                allowed.add(settlementId);
            }
        }
        log.warn("정산 지급 잔액 부족 - date: {}, available: {}, required: {}, deferredRows: {}", today, available, required, deferred);
        notifier.payoutBalanceInsufficient(today, available, required, deferred);
        return allowed;
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> sumDueBySettlement(List<Long> settlementIds, LocalDate today) {
        Map<Long, Long> totals = new LinkedHashMap<>();
        for (Object[] row : payoutRepository.sumDueBySettlement(settlementIds, today)) {
            totals.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return totals;
    }

    /**
     * 묶음 지급 배치(D-2) — 마감이 지난 묶음을 먼저 정리하고, 지급일 D(= 가장 이른 지급일)가 되는 정산을 선점해 묶음에 붙인 뒤 이번에 붙은 묶음을
     * 올린다. 마감 뒤 · 비영업일이면 아무것도 하지 않는다(다음 틱 · 배치). 잔액은 알림만(10:00 · D-9).
     *
     * @return 선점한 정산 수
     */
    private int distributeDueInGroups(LocalDateTime now, int limit, boolean warnBalance) {
        if (groupPlanner.hasCutoff()) {
            groupService.settleMissedCutoffs(now);
            if (!groupPlanner.isUploadWindow(now)) {
                return 0;
            }
        }
        LocalDate payDate = groupPlanner.earliestPayDate(now);
        List<Long> due = findSettlementIdsDue(payDate, limit);
        if (due.isEmpty()) {
            return 0;
        }
        if (warnBalance) {
            groupService.warnIfShort(payDate, now);
        }
        try {
            partnerSync.refreshAmlForSettlements(due);
        } catch (RuntimeException e) {
            log.error("AML 상태 재조회 실패 — 저장값으로 판정한다", e);
        }
        Set<Long> groupIds = new LinkedHashSet<>();
        int claimed = 0;
        for (Long settlementId : due) {
            try {
                SettlementPayoutTransitions.GroupClaim claim = transitions.claimForGroups(settlementId, payDate, now);
                if (claim != null) {
                    groupIds.addAll(claim.groupIds());
                    claimed++;
                }
            } catch (Exception e) {
                log.error("정산 지급 선점 실패 - settlementId: {}", settlementId, e);
            }
        }
        groupService.submitAll(groupIds, now);
        return claimed;
    }

    /**
     * 마감 전 틱(영업일 {@code settlement.payout-cutoff-hour}시 · D-2′) — ① 10:00 뒤 보류가 풀린 행 · 대기 중인 재분배 행 지시(「해제 다음 영업일
     * 지급」 약속) ② 수렴(확정 합계 ≠ 현재 합계인 묶음을 다시 올린다 · D-1 ④) ③ 잔액 재검사(아직 모자라면 큰 묶음부터 남기고 정리 · D-9).
     * 마감이 없는 모드(시뮬레이터 · 토스)는 하지 않는다.
     */
    /**
     * 마감 전 수렴(업로드 창 안 10분마다) — 확정 합계 ≠ 현재 합계(보류 뒤 재전송 실패 · 결과 모름 · 일시 오류)인 묶음만 다시 올린다. 맞는 묶음은 PG 를
     * 부르지 않는다. 14:00 틱 하나만 믿으면 14시 이후 보류의 재전송이 실패했을 때 마감까지 고칠 기회가 없다(그대로 마감되면 빠진 행이 이전 합계로
     * 나간다). 선점 · 잔액 재검사는 하지 않는다(14:00 틱의 일). 업로드 창 밖 · 마감 없는 모드는 아무것도 하지 않는다.
     *
     * @return 전송을 시도한 묶음 수
     */
    public int convergeBeforeCutoff(LocalDateTime now) {
        if (!groupPlanner.enabled() || !groupPlanner.hasCutoff() || !groupPlanner.isUploadWindow(now)) {
            return 0;
        }
        return groupService.converge(now);
    }

    public void cutoffTick(LocalDateTime now, int limit) {
        if (!groupPlanner.enabled() || !groupPlanner.hasCutoff()) {
            return;
        }
        int claimed = distributeDueInGroups(now, limit, false);
        if (claimed > 0) {
            log.info("정산 지급 마감 전 틱 — 늦게 풀린 행 지시 {}건", claimed);
        }
        int converged = groupService.converge(now);
        if (converged > 0) {
            log.info("정산 지급 마감 전 틱 — 수렴 전송 {}건", converged);
        }
        if (gateway.trimOnShortfall() && groupPlanner.isUploadWindow(now)) {
            groupService.trimToBalance(groupPlanner.earliestPayDate(now), now);
        }
    }

    /**
     * 정산 1건의 예정일 도래 행 지급(재분배 즉시 지시 · 테스트). 묶음 경로는 영업일 마감 전에만 즉시 묶음에 넣어 올리고, 그 밖에는
     * {@code SCHEDULED} 로 둔다(다음 틱 · 배치가 집는다 · D-2′). 정산별 경로의 게이트웨이 예외(결과 모름)는 REQUESTED 로 둔다 — 웹훅 · 폴링이 닫는다.
     */
    public void distribute(Long settlementId, LocalDate today, LocalDateTime now) {
        if (groupPlanner.enabled()) {
            if (!groupPlanner.isUploadWindow(now)) {
                return;
            }
            SettlementPayoutTransitions.GroupClaim claim = transitions.claimForGroups(settlementId,
                    groupPlanner.earliestPayDate(now), now);
            if (claim != null) {
                groupService.submitAll(claim.groupIds(), now);
            }
            return;
        }
        PayoutCommand command = transitions.claim(settlementId, today, now);
        if (command == null) {
            return;
        }
        PayoutResult result = command.lines().isEmpty() ? new PayoutResult(List.of()) : call(command);
        transitions.applyResults(settlementId, command, result, now);
    }

    /**
     * 보류 재판정 — 지급 배치 앞단. ⓪ 정산 계좌 「확인 중」으로 남은 인플루언서를 다시 확인한다(50 설계서 6-4 — 확인이 끝나면 확인 훅이
     * 보류를 푼다) ① PG 셀러 사유로 보류된 행(수취자 무관)은 셀러 등록 · 상태 조회를 다시 시도한다(토스 설계서 4-3)
     * ② 그 밖의 인플루언서 보류 행은 주민등록번호 · 세금계산서 재판정(5-4).
     */
    public int releaseBlocked(LocalDate today) {
        try {
            accountVerification.retryVerifying();
        } catch (Exception e) {
            log.error("정산 계좌 확인 재시도 실패", e);
        }
        int released = 0;
        for (SettlementPayout payout : findPartnerBlocked()) {
            try {
                if (PayoutBlockReason.KYC_REQUIRED.name().equals(payout.getFailCode()) && partnerSync.isAmlAware()) {
                    // 고객확인(AML) 보류는 AML 상태가 「이행 완료 · 유효」일 때만 푼다 — 등록 성공으로 풀면 다음 지시에서 다시 보류돼 매일 해제 ·
                    // 보류 · 알림이 반복된다(60 전환 계획서 D-6 ②).
                    if (partnerSync.isAmlClear(payout.getSettlementId(), payout.getPayee())
                            && transitions.releasePartnerBlocked(payout.getSettlementId(), payout.getId(), payout.getPayee(), today)) {
                        released++;
                    }
                    continue;
                }
                PartnerResult result = partnerSync.ensure(payout.getSettlementId(), payout.getPayee());
                if (result.isApproved()) {
                    if (transitions.releasePartnerBlocked(payout.getSettlementId(), payout.getId(), payout.getPayee(), today)) {
                        released++;
                    }
                } else if (result.failCode() != null && !result.failCode().equals(payout.getFailCode())) {
                    transitions.updateBlockReason(payout.getId(), result.failCode(), result.failReason());
                }
            } catch (Exception e) {
                log.error("PG 셀러 보류 재판정 실패 - payoutId: {}", payout.getId(), e);
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

    // ------------------------------------------------------------------ 결과 조회(토스 설계서 5-4 · 백스톱)

    /**
     * REQUESTED 행의 결과를 PG 에 조회해 닫는다. 묶음 경로는 지급일 ≤ 오늘인 묶음을 날짜 단위로(D-8), 정산별 경로는 정산 단위로 묶어 게이트웨이
     * 1회. PG 처리 지연은 하루 1회(지급 시각 틱)만 알린다.
     */
    public void pollResults(LocalDateTime now) {
        if (groupPlanner.enabled()) {
            groupService.pollResults(now);
            return;
        }
        List<SettlementPayout> requested = findRequested();
        if (requested.isEmpty()) {
            return;
        }
        Map<Long, List<SettlementPayout>> bySettlement = new LinkedHashMap<>();
        for (SettlementPayout payout : requested) {
            bySettlement.computeIfAbsent(payout.getSettlementId(), id -> new ArrayList<>()).add(payout);
        }
        boolean staleTick = KstDates.toKst(now).getHour() == properties.getPayoutHour() && now.getMinute() < 30;
        LocalDate today = KstDates.today(now);
        for (Map.Entry<Long, List<SettlementPayout>> entry : bySettlement.entrySet()) {
            Long settlementId = entry.getKey();
            try {
                String number = settlementRepository.findById(settlementId).map(Settlement::getSettlementNumber).orElse(null);
                if (number == null) {
                    continue;
                }
                List<PayoutLookup> lookups = entry.getValue().stream()
                        .map(p -> new PayoutLookup(p.getId(), settlementId, number, p.getPayee(), p.getAmount(),
                                p.getAttempt(), p.getPgTransferId(), p.getPgPayoutId(), p.getRequestedAt()))
                        .toList();
                List<LineResult> results = gateway.lookup(lookups);
                if (!results.isEmpty()) {
                    transitions.applyLookupResults(settlementId, results, now);
                }
                if (staleTick) {
                    for (SettlementPayout payout : entry.getValue()) {
                        boolean closed = results.stream().anyMatch(r -> r.payoutId().equals(payout.getId())
                                && r.outcome() != Outcome.REQUESTED);
                        if (!closed && payout.getRequestedAt() != null
                                && !businessCalendar.addBusinessDays(KstDates.toKstDate(payout.getRequestedAt()),
                                properties.getPayout().getResultStaleBusinessDays()).isAfter(today)) {
                            transitions.markStale(settlementId, payout.getPayee(), now);
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

    /**
     * 지시 끝(토스 설계서 5-3) — 오늘 올린 건수 · 합계를 운영자에게 요약한다. 묶음 경로는 우리 묶음만 센다 — 이니시스는 업로드된 미송금 데이터를 조회하는
     * API 가 없어 PG 대조는 송금 다음 날 결과 폴링으로 한다(D-8).
     */
    public void reconcileRequested(LocalDate today) {
        if (groupPlanner.enabled()) {
            if (!gateway.isExternal()) {
                return;
            }
            LocalDate payDate = groupPlanner.earliestPayDate(KstDates.at(today, java.time.LocalTime.of(properties.getPayoutHour(), 0)));
            long[] summary = groupService.openSummary(payDate);
            if (summary[0] > 0) {
                notifier.payoutRequestedSummary(payDate, (int) summary[0], summary[1], null, null);
            }
            return;
        }
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
            log.error("정산 지급 대사 실패 - date: {}", today, e);
        }
        notifier.payoutRequestedSummary(today, ourCount, ourAmount, pg.map(Reconciliation::count).orElse(null),
                pg.map(Reconciliation::amount).orElse(null));
        if (pg.isPresent() && (pg.get().count() != ourCount || pg.get().amount() != ourAmount)) {
            log.warn("정산 지급 대사 불일치 - date: {}, ours: {}건/{}원, pg: {}건/{}원", today, ourCount, ourAmount,
                    pg.get().count(), pg.get().amount());
        }
    }

    /** PG 지급일 = 오늘인 행(REQUESTED · PAID · FAILED · 지급 id 있음) — PG 목록 조회와 같은 기준으로 센다. */
    @Transactional(readOnly = true)
    public Object[] countRequested(LocalDate today) {
        List<Object[]> rows = payoutRepository.countByPgPayoutDate(today);
        return rows.isEmpty() ? new Object[]{0L, 0L} : rows.get(0);
    }

    private PayoutResult call(PayoutCommand command) {
        try {
            return gateway.distribute(command);
        } catch (RuntimeException e) {
            log.error("정산 지급 지시 결과 미확인 — REQUESTED 로 둔다 - settlementId: {}", command.settlementId(), e);
            // 플랫폼 몫은 PG 에 가지 않는다(내부 계정) — 결과 모름이어도 바로 지급 완료. 결과 조회가 닫을 수 없는 행을 남기지 않는다.
            return new PayoutResult(command.lines().stream()
                    .map(line -> line.payee() == SettlementPayee.PLATFORM
                            ? new LineResult(line.payoutId(), Outcome.PAID, "PLATFORM-" + command.settlementNumber(), null, null)
                            : new LineResult(line.payoutId(), Outcome.REQUESTED, null, null, null))
                    .toList());
        }
    }
}
