package showroomz.domain.settlement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.entity.SettlementPayoutGroup;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway.GroupCommand;
import showroomz.domain.settlement.port.SettlementPayoutGateway.GroupLookup;
import showroomz.domain.settlement.port.SettlementPayoutGateway.GroupResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.GroupSubmitResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.Outcome;
import showroomz.domain.settlement.repository.SettlementPayoutGroupRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 지급 묶음의 <b>유일한 진입점</b>(60_KG이니시스_지급대행_전환_계획서.md D-1 · 6절) — 배치 · 마감 전 틱 · 재분배 · 보류가 전부 이것을 부른다.
 * 합계 계산 · 잠금 · {@code intended/confirmed} 기록은 여기에만 있다(합류가 D-1 의 핵심 함정이다 — 같은 키에 이미 올라간 묶음이 있으면 반드시
 * 「기존 + 이번」 합계로 덮는다).
 *
 * <p>PG 호출은 트랜잭션 밖이고, 앞뒤 기록은 묶음 행을 잠근 짧은 트랜잭션이다 — ① 잠금 · 합계 · {@code intended} 커밋 ② 전송 ③ 잠금 · 결과 반영.
 * 전송 예외는 결과 모름이다 — {@code confirmed} 를 바꾸지 않고 마감 전 틱이 같은 합계로 다시 올린다(덮어쓰기라 안전 · Q3).
 */
@Slf4j
@Service
public class SettlementPayoutGroupService {

    /** 결과 모름 표시 — 마감이 지나도 행을 옮기지 않는다(PG 에 있을 수 있다 · 송금 결과가 가른다). */
    static final String OUTCOME_UNKNOWN = "OUTCOME_UNKNOWN";
    static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    static final String OUT_OF_SYNC = "OUT_OF_SYNC";
    static final String CUTOFF_MISSED = "CUTOFF_MISSED";
    /** 송금 결과의 계좌(은행 · 끝 4자리)가 행의 계좌 스냅샷과 다르다 — 지급 완료 묶음에 남는 확인 표시. */
    static final String ACCOUNT_MISMATCH = "ACCOUNT_MISMATCH";
    private static final int POLL_BATCH = 500;
    /** 수렴 · 마감 경과 판정이 보는 지급일 범위(오늘 기준 앞뒤 일수). */
    private static final int SCAN_DAYS = 45;

    private final SettlementPayoutGroupRepository groupRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementPayoutGateway gateway;
    private final PayoutGroupPlanner planner;
    private final SettlementPayoutTransitions transitions;
    private final SettlementPartnerSyncService partnerSync;
    private final SettlementHistoryRecorder historyRecorder;
    private final SettlementNotifier notifier;
    private final SettlementTaxDocumentHook taxDocumentHook;
    private final BusinessCalendar businessCalendar;
    private final SettlementProperties properties;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public SettlementPayoutGroupService(SettlementPayoutGroupRepository groupRepository,
                                        SettlementPayoutRepository payoutRepository, SettlementRepository settlementRepository,
                                        SettlementPayoutGateway gateway, PayoutGroupPlanner planner,
                                        SettlementPayoutTransitions transitions, SettlementPartnerSyncService partnerSync,
                                        SettlementHistoryRecorder historyRecorder, SettlementNotifier notifier,
                                        SettlementTaxDocumentHook taxDocumentHook, BusinessCalendar businessCalendar,
                                        SettlementProperties properties, PlatformTransactionManager transactionManager) {
        this.groupRepository = groupRepository;
        this.payoutRepository = payoutRepository;
        this.settlementRepository = settlementRepository;
        this.gateway = gateway;
        this.planner = planner;
        this.transitions = transitions;
        this.partnerSync = partnerSync;
        this.historyRecorder = historyRecorder;
        this.notifier = notifier;
        this.taxDocumentHook = taxDocumentHook;
        this.businessCalendar = businessCalendar;
        this.properties = properties;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction.setReadOnly(true);
    }

    /** 전송 1회의 결말. */
    public enum SubmitOutcome {
        /** 묶음이 없거나 닫혔다 · 보낼 것이 없다. */
        SKIPPED,
        /** 확정 합계 = 현재 합계 — 호출하지 않았다. */
        IN_SYNC,
        /** PG 가 접수했다. */
        SENT,
        /** 결과 모름(통신 실패) · 계좌 정렬 보류 — 마감 전 틱이 다시. */
        UNKNOWN,
        PAID,
        FAILED,
        DEFERRED,
        /** PG 일시 오류(접수 안 됨) — 행 그대로 · 마감 전 틱이 다시 · 마감이 지나면 다음 지급일로. */
        RETRY
    }

    // ================================================================== 전송(D-1 ① ② ③)

    /** 여러 묶음을 차례로 — 하나의 실패가 다른 묶음을 막지 않는다. */
    public void submitAll(Collection<Long> groupIds, LocalDateTime now) {
        for (Long groupId : new TreeSet<>(groupIds)) {
            try {
                submit(groupId, now);
            } catch (RuntimeException e) {
                log.error("지급 묶음 전송 실패 - groupId: {}", groupId, e);
            }
        }
    }

    /** 묶음의 현재 합계로 올린다(합계 0 이면 삭제). 확정 합계와 같으면 부르지 않는다. */
    public SubmitOutcome submit(Long groupId, LocalDateTime now) {
        Prepared prepared = writeTransaction.execute(tx -> prepare(groupId, now));
        if (prepared == null) {
            return SubmitOutcome.SKIPPED;
        }
        if (prepared.done() != null) {
            return prepared.done();
        }
        if (gateway.isExternal() && prepared.command().total() > 0 && !partnerSync.alignAccountForGroup(groupId)) {
            // 서브몰 등록 계좌가 묶음 계좌와 다르고 지금은 바꿀 수 없다(다른 계좌의 미송금 묶음 · D-1 ⑤) — 다음 틱이 다시.
            log.warn("지급 묶음 — 서브몰 계좌 정렬 보류 · 전송 미룸 - groupId: {}", groupId);
            return SubmitOutcome.UNKNOWN;
        }
        GroupSubmitResult result;
        try {
            result = gateway.submitGroup(prepared.command());
        } catch (RuntimeException e) {
            log.error("지급 묶음 전송 결과 미확인 — 확정 합계 유지 · 마감 전 틱이 다시 - groupId: {}, total: {}", groupId,
                    prepared.command().total(), e);
            writeTransaction.executeWithoutResult(tx -> groupRepository.findForUpdate(groupId)
                    .ifPresent(g -> {
                        g.recordAttempt(null, OUTCOME_UNKNOWN, now);
                        groupRepository.save(g);
                    }));
            return SubmitOutcome.UNKNOWN;
        }
        return writeTransaction.execute(tx -> apply(groupId, prepared, result, now));
    }

    private record Prepared(GroupCommand command, List<Long> rowIds, SubmitOutcome done) {

        static Prepared done(SubmitOutcome outcome) {
            return new Prepared(null, List.of(), outcome);
        }
    }

    private Prepared prepare(Long groupId, LocalDateTime now) {
        SettlementPayoutGroup group = groupRepository.findForUpdate(groupId).orElse(null);
        if (group == null || !group.getStatus().isOpen()) {
            return null;
        }
        List<SettlementPayout> rows = requestedRows(groupId);
        long total = rows.stream().mapToLong(SettlementPayout::getAmount).sum();
        if (!group.differsFrom(total) && !OUTCOME_UNKNOWN.equals(group.getResultCode())) {
            return Prepared.done(SubmitOutcome.IN_SYNC);
        }
        if (total == 0 && !group.isUploaded() && !OUTCOME_UNKNOWN.equals(group.getResultCode())) {
            // PG 에 아무것도 없다 — 지울 것도 없다.
            group.markDeleted(null, now);
            groupRepository.save(group);
            return Prepared.done(SubmitOutcome.SKIPPED);
        }
        if (gateway.isExternal() && group.getPgPartnerId() == null) {
            // 서브몰 미연결 — 확정 때 보장했어야 한다(D-3). 행을 분배 실패로 돌려 재분배(M3)로.
            group.markFailed(PayoutBlockReason.PARTNER_NOT_READY.name(), now);
            groupRepository.saveAndFlush(group);
            failRows(rows, PayoutBlockReason.PARTNER_NOT_READY.name(), "PG 서브몰 미연결 — 확정 때 등록되지 않았다", false, now);
            return Prepared.done(SubmitOutcome.FAILED);
        }
        group.intend(total, now);
        groupRepository.save(group);
        GroupCommand command = new GroupCommand(group.getId(), group.getPayee(), group.getPayeeRefId(), group.getPgPartnerId(),
                group.getPayDate(), group.getScheduleType(), total);
        return new Prepared(command, rows.stream().map(SettlementPayout::getId).toList(), null);
    }

    private SubmitOutcome apply(Long groupId, Prepared prepared, GroupSubmitResult result, LocalDateTime now) {
        SettlementPayoutGroup group = groupRepository.findForUpdate(groupId).orElse(null);
        if (group == null || !group.getStatus().isOpen()) {
            return SubmitOutcome.SKIPPED;
        }
        long total = prepared.command().total();
        List<SettlementPayout> sent = payoutRepository.findAllById(prepared.rowIds()).stream()
                .filter(p -> p.getStatus() == PayoutStatus.REQUESTED && groupId.equals(p.getPayoutGroupId()))
                .sorted(Comparator.comparing(SettlementPayout::getId)).toList();
        boolean firstUpload = !group.isUploaded();
        if (OUTCOME_UNKNOWN.equals(group.getResultCode()) && result.outcome() != Outcome.REQUESTED
                && result.outcome() != Outcome.PAID) {
            // 앞선 전송의 결과를 모른다 — PG 에 그 합계가 올라가 있을 수 있다. 이번 요청의 거절은 앞선 요청이 없었다는 뜻이 아니다.
            // 여기서 행을 실패 · 연기로 돌리면 PG 가 앞선 합계로 송금하고 운영자 재분배가 한 번 더 보낸다(이중 지급). 결과 모름을 유지하고
            // 마감 전 틱이 다시 올린다 — 접수되면 그 합계로 확정, 끝내 안 되면 송금 결과(폴링)가 가르고 없으면 처리 지연 알림이 사람을 부른다.
            group.recordAttempt(result.requestId(), OUTCOME_UNKNOWN, now);
            groupRepository.saveAndFlush(group);
            log.warn("지급 묶음 — 앞선 전송 결과 모름 · 이번 거절({} {})로 행을 닫지 않는다 - groupId: {}, total: {}", result.outcome(),
                    result.failCode(), groupId, total);
            return SubmitOutcome.UNKNOWN;
        }
        switch (result.outcome()) {
            case REQUESTED -> {
                group.confirm(total, result.requestId(), now);
                groupRepository.saveAndFlush(group);
                List<Long> unmarked = sent.stream().filter(p -> p.getPgTransferId() == null).map(SettlementPayout::getId).toList();
                if (!unmarked.isEmpty()) {
                    payoutRepository.markConfirmedInGroup(groupId, unmarked, group.transferKey());
                }
                log.info("지급 묶음 접수 - groupId: {}, payDate: {}, total: {}, rows: {}", groupId, group.getPayDate(), total,
                        sent.size());
                if (SettlementPayoutGateway.NOTICE_AML_REQUIRED.equals(result.failCode())) {
                    // 접수는 됐지만 고객확인 대상 — 수취자 · 운영자에게 안내(D-6 · 이니시스 SB05). 보류는 AML 상태 조회가 정한다.
                    SettlementPayee payee = group.getPayee();
                    sent.stream().map(SettlementPayout::getSettlementId).distinct().sorted()
                            .forEach(id -> notifier.payoutAmlThresholdNotice(id, payee));
                }
                return total == 0 ? SubmitOutcome.SKIPPED : SubmitOutcome.SENT;
            }
            case PAID -> {
                // 시뮬레이터 — 올리는 즉시 완료. 참조번호 = {접두}-{정산번호}-{수취자}(시뮬레이터 SIM).
                group.confirm(total, null, now);
                group.markPaid(KstDates.today(now), now);
                groupRepository.saveAndFlush(group);
                String prefix = result.requestId() == null ? "SIM" : result.requestId();
                payRows(sent, row -> prefix + "-" + settlementNumber(row.getSettlementId()) + "-" + row.getPayee().name(), now, now);
                return SubmitOutcome.PAID;
            }
            case FAILED -> {
                String code = result.failCode() == null ? "REJECTED_BY_PG" : result.failCode();
                if (firstUpload) {
                    group.markFailed(code, now);
                    groupRepository.saveAndFlush(group);
                    failRows(sent, code, result.failReason(), false, now);
                } else {
                    // 이미 올라간 묶음에 합류가 거절됐다 — 앞서 올라간 행은 PG 에 이전 합계로 살아 있다. 새로 붙은 행만 뺀다(5절).
                    group.recordAttempt(result.requestId(), code, now);
                    groupRepository.saveAndFlush(group);
                    failRows(sent.stream().filter(p -> p.getPgTransferId() == null).toList(), code, result.failReason(), true, now);
                }
                return SubmitOutcome.FAILED;
            }
            case DEFERRED -> {
                LocalDate next = laterOf(planner.nextPayDate(group.getPayDate()), planner.earliestPayDate(now));
                if (firstUpload) {
                    group.markDeleted(result.failCode() == null ? "INSUFFICIENT_BALANCE" : result.failCode(), now);
                    groupRepository.saveAndFlush(group);
                    deferRows(sent, next, "잔액 부족", result.failCode(), now);
                } else {
                    group.recordAttempt(result.requestId(), result.failCode(), now);
                    groupRepository.saveAndFlush(group);
                    deferRows(sent.stream().filter(p -> p.getPgTransferId() == null).toList(), next, "잔액 부족",
                            result.failCode(), now);
                }
                return SubmitOutcome.DEFERRED;
            }
            case RETRY -> {
                // PG 일시 오류 — 접수 안 됨이 확실하다. 확정 합계 · 행 그대로(실패로 돌리지 않는다 · attempt 불변). 마감 전 틱의 수렴이 같은 합계로
                // 다시 올리고, 마감이 지나면 받은 적 없는 행은 마감 경과 정리가 다음 지급일로 넘긴다.
                group.recordAttempt(result.requestId(), retryCode(result.failCode()), now);
                groupRepository.saveAndFlush(group);
                log.warn("지급 묶음 PG 일시 오류 — 다음 틱 재전송 - groupId: {}, code: {}, total: {}", groupId, result.failCode(), total);
                return SubmitOutcome.RETRY;
            }
        }
        return SubmitOutcome.SKIPPED;
    }

    /** 일시 오류 표시값 — {@code RETRY_{PG 코드}}(결과 코드 칸 32자 안). */
    static String retryCode(String pgCode) {
        String code = "RETRY_" + (pgCode == null || pgCode.isBlank() ? "UNKNOWN" : pgCode.trim());
        return code.length() > 32 ? code.substring(0, 32) : code;
    }

    // ================================================================== 수렴 · 마감 경과(D-1 ④ · D-2′)

    /**
     * 마감 전 틱의 수렴 — 아직 고칠 수 있는 열린 묶음마다 확정 합계 ≠ 현재 합계면 합계로 다시 올린다(0원이면 삭제). 즉시 전송은 최적화이고 정합의
     * 보증은 이 틱이다. @return 전송을 시도한 묶음 수
     */
    public int converge(LocalDateTime now) {
        if (!planner.hasCutoff()) {
            return 0;
        }
        int tried = 0;
        for (SettlementPayoutGroup group : openAround(now)) {
            if (!planner.isEditable(group.getPayDate(), now)) {
                continue;
            }
            SubmitOutcome outcome = submit(group.getId(), now);
            if (outcome != SubmitOutcome.IN_SYNC && outcome != SubmitOutcome.SKIPPED) {
                tried++;
            }
        }
        return tried;
    }

    /**
     * 마감이 지난 열린 묶음 정리 — ① PG 가 받은 적 없는 행(표시 없음)은 다음 지급일 예정으로 되돌린다(지난 지급일로 올릴 수 없다) ② PG 합계 ≠ 남은 행
     * 합계면(마감 직전 빼기 · 재전송 실패) 사람을 부른다. 결과 모름 묶음은 건드리지 않는다 — PG 에 있을 수 있어 송금 결과가 가른다.
     */
    public void settleMissedCutoffs(LocalDateTime now) {
        if (!planner.hasCutoff()) {
            return;
        }
        for (SettlementPayoutGroup candidate : openAround(now)) {
            if (planner.isEditable(candidate.getPayDate(), now)) {
                continue;
            }
            try {
                writeTransaction.executeWithoutResult(tx -> settleMissed(candidate.getId(), now));
            } catch (RuntimeException e) {
                log.error("지급 묶음 마감 경과 정리 실패 - groupId: {}", candidate.getId(), e);
            }
        }
    }

    private void settleMissed(Long groupId, LocalDateTime now) {
        SettlementPayoutGroup group = groupRepository.findForUpdate(groupId).orElse(null);
        if (group == null || !group.getStatus().isOpen() || OUTCOME_UNKNOWN.equals(group.getResultCode())) {
            return;
        }
        List<SettlementPayout> rows = requestedRows(groupId);
        List<SettlementPayout> unconfirmed = rows.stream().filter(p -> p.getPgTransferId() == null).toList();
        long confirmedRows = rows.stream().filter(p -> p.getPgTransferId() != null).mapToLong(SettlementPayout::getAmount).sum();
        if (unconfirmed.isEmpty() && (group.getConfirmedAmount() == null || group.getConfirmedAmount() == confirmedRows)) {
            return;
        }
        if (!unconfirmed.isEmpty()) {
            LocalDate next = laterOf(planner.nextPayDate(group.getPayDate()), planner.earliestPayDate(now));
            if (!group.isUploaded() && unconfirmed.size() == rows.size()) {
                group.markDeleted(CUTOFF_MISSED, now);
            }
            groupRepository.saveAndFlush(group);
            deferRows(unconfirmed, next, "PG 수정 마감 경과", CUTOFF_MISSED, now);
        }
        if (group.isUploaded() && group.getConfirmedAmount() != confirmedRows && !OUT_OF_SYNC.equals(group.getResultCode())) {
            SettlementPayoutGroup fresh = groupRepository.findForUpdate(groupId).orElseThrow();
            fresh.flag(OUT_OF_SYNC, now);
            groupRepository.save(fresh);
            log.warn("지급 묶음 마감 경과 · PG 합계 ≠ 행 합계 - groupId: {}, confirmed: {}, rows: {}", groupId,
                    fresh.getConfirmedAmount(), confirmedRows);
            notifier.payoutGroupOutOfSync(groupId, fresh.getPayDate(), fresh.getConfirmedAmount(), confirmedRows);
        }
    }

    // ================================================================== 잔액(D-9)

    /** 10:00 선검사 — 모자라면 알림만(전부 지시 · 14:00 재검사 전 입금). 잔액을 모르면 조용히. */
    public void warnIfShort(LocalDate payDate, LocalDateTime now) {
        Long available = availableQuietly();
        if (available == null) {
            return;
        }
        long required = readTransaction.execute(tx -> payoutRepository.sumScheduledDue(payDate)
                + openTotal(payDate));
        if (required > available) {
            log.warn("정산 지급 잔액 부족(10:00) — 전부 지시 · 14:00 재검사 - payDate: {}, available: {}, required: {}", payDate,
                    available, required);
            notifier.payoutBalanceShortfall(payDate, available, required, LocalTime.of(properties.getPayoutCutoffHour(), 0));
        }
    }

    /**
     * 14:00 재검사 — 그래도 모자라면 합계 큰 묶음부터 들어가는 만큼 남기고 나머지 묶음의 행을 다음 지급일로 미룬다(attempt 불변 · 묶음은 0원으로
     * 지운다). @return 미룬 행 수
     */
    public int trimToBalance(LocalDate payDate, LocalDateTime now) {
        Long available = availableQuietly();
        if (available == null) {
            return 0;
        }
        Map<Long, Long> totals = readTransaction.execute(tx -> {
            Map<Long, Long> map = new LinkedHashMap<>();
            for (SettlementPayoutGroup g : groupRepository.findOpenBetween(gateway.provider(), payDate, payDate)) {
                if (planner.isEditable(g.getPayDate(), now)) {
                    map.put(g.getId(), payoutRepository.sumRequestedInGroup(g.getId()));
                }
            }
            return map;
        });
        long required = totals.values().stream().mapToLong(Long::longValue).sum();
        if (required <= available) {
            return 0;
        }
        List<Long> bySize = totals.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).toList();
        long remaining = available;
        int deferred = 0;
        LocalDate next = planner.nextPayDate(payDate);
        for (Long groupId : bySize) {
            long total = totals.get(groupId);
            if (total <= remaining) {
                remaining -= total;
                continue;
            }
            Integer moved = writeTransaction.execute(tx -> {
                // 묶음을 잠그고 옮긴다 — 같은 묶음에 행을 붙이는 선점 · 재분배와 겹치지 않게(D-1 ③).
                if (groupRepository.findForUpdate(groupId).filter(g -> g.getStatus().isOpen()).isEmpty()) {
                    return 0;
                }
                List<SettlementPayout> rows = requestedRows(groupId);
                deferRows(rows, next, "잔액 부족", "INSUFFICIENT_BALANCE", now);
                return rows.size();
            });
            deferred += moved == null ? 0 : moved;
            submit(groupId, now);   // 남은 합계 0 — 올라간 묶음이면 삭제(DEL), 아니면 그냥 닫는다
        }
        log.warn("정산 지급 잔액 부족(14:00) — 큰 묶음부터 남기고 정리 - payDate: {}, available: {}, required: {}, deferredRows: {}",
                payDate, available, required, deferred);
        notifier.payoutBalanceInsufficient(payDate, available, required, deferred);
        return deferred;
    }

    private Long availableQuietly() {
        try {
            return gateway.availableBalance().orElse(null);
        } catch (RuntimeException e) {
            log.error("정산 지급 잔액 조회 실패 — 선검사 · 재검사를 건너뛴다", e);
            return null;
        }
    }

    private long openTotal(LocalDate payDate) {
        long sum = 0;
        for (SettlementPayoutGroup g : groupRepository.findOpenBetween(gateway.provider(), payDate, payDate)) {
            sum += payoutRepository.sumRequestedInGroup(g.getId());
        }
        return sum;
    }

    // ================================================================== 결과(D-8)

    /**
     * 송금 결과 폴링(폴링이 1차) — 지급일 ≤ 오늘인 열린 묶음만 모아 PG 에 묻는다(어댑터가 날짜별 1회로 묶는다). 처리 지연 판정은 <b>지급일 기준</b> —
     * 지급일 + N영업일에도 열려 있으면 하루 1회(지급 시각 틱) 알린다(지시 시각 기준이면 D-1 지시 때문에 결과 전에 매일 거짓 알림이 난다).
     */
    public void pollResults(LocalDateTime now) {
        LocalDate today = KstDates.today(now);
        List<SettlementPayoutGroup> due = readTransaction.execute(tx ->
                groupRepository.findOpenDue(gateway.provider(), today, PageRequest.of(0, POLL_BATCH)));
        if (due == null || due.isEmpty()) {
            return;
        }
        // 결과 모름 묶음은 합계 0(삭제 시도)이어도 묻는다 — 앞선 전송이 PG 에 올라가 송금됐을 수 있다(금액 불일치로 사람을 부른다).
        List<GroupLookup> lookups = due.stream()
                .filter(g -> g.getConfirmedAmount() != null || g.getIntendedAmount() > 0 || OUTCOME_UNKNOWN.equals(g.getResultCode()))
                .map(g -> new GroupLookup(g.getId(), g.getPayee(), g.getPgPartnerId(), g.getPayDate(), g.getScheduleType(),
                        expected(g)))
                .toList();
        List<GroupResult> results = List.of();
        if (!lookups.isEmpty()) {
            try {
                results = gateway.lookupGroups(lookups);
            } catch (RuntimeException e) {
                log.error("지급 묶음 송금 결과 조회 실패 — 다음 폴링이 다시", e);
            }
        }
        Set<Long> closed = new TreeSet<>();
        for (GroupResult result : results) {
            if (result.outcome() == Outcome.REQUESTED) {
                continue;
            }
            try {
                Boolean done = writeTransaction.execute(tx -> applyResult(result, now));
                if (Boolean.TRUE.equals(done)) {
                    closed.add(result.groupId());
                }
            } catch (RuntimeException e) {
                log.error("지급 묶음 송금 결과 반영 실패 - groupId: {}", result.groupId(), e);
            }
        }
        boolean staleTick = KstDates.toKst(now).getHour() == properties.getPayoutHour() && KstDates.toKst(now).getMinute() < 30;
        if (!staleTick) {
            return;
        }
        int staleDays = properties.getPayout().getResultStaleBusinessDays();
        for (SettlementPayoutGroup group : due) {
            if (closed.contains(group.getId())
                    || businessCalendar.addBusinessDays(group.getPayDate(), staleDays).isAfter(today)) {
                continue;
            }
            List<SettlementPayout> rows = readTransaction.execute(tx -> requestedRows(group.getId()));
            for (Long settlementId : rows.stream().map(SettlementPayout::getSettlementId).distinct().sorted().toList()) {
                try {
                    transitions.markStale(settlementId, group.getPayee(), now);
                } catch (RuntimeException e) {
                    log.error("PG 처리 지연 기록 실패 - settlementId: {}", settlementId, e);
                }
            }
        }
    }

    /** 송금 결과 1건 반영 — 닫았으면 true. 금액이 확정 합계와 다르면 닫지 않고 사람을 부른다. */
    private Boolean applyResult(GroupResult result, LocalDateTime now) {
        SettlementPayoutGroup group = groupRepository.findForUpdate(result.groupId()).orElse(null);
        if (group == null || !group.getStatus().isOpen()) {
            return false;
        }
        List<SettlementPayout> rows = requestedRows(group.getId());
        if (result.outcome() == Outcome.FAILED) {
            String code = result.failCode() == null ? "REJECTED_BY_PG" : result.failCode();
            group.markFailed(code, now);
            groupRepository.saveAndFlush(group);
            failRows(rows, code, result.failReason() == null ? "PG 송금 실패 " + code : result.failReason(), false, now);
            return true;
        }
        if (result.outcome() != Outcome.PAID) {
            return false;
        }
        long all = rows.stream().mapToLong(SettlementPayout::getAmount).sum();
        List<SettlementPayout> confirmed = rows.stream().filter(p -> p.getPgTransferId() != null).toList();
        long confirmedSum = confirmed.stream().mapToLong(SettlementPayout::getAmount).sum();
        List<SettlementPayout> toPay;
        List<SettlementPayout> toDefer = List.of();
        if (result.pgAmount() == all && all > 0) {
            toPay = rows;
        } else if (result.pgAmount() == confirmedSum && confirmedSum > 0) {
            // PG 는 이전 합계로 보냈다 — 마지막 합류분은 올라가지 않았다. 그 행은 다음 지급일로.
            toPay = confirmed;
            toDefer = rows.stream().filter(p -> p.getPgTransferId() == null).toList();
        } else {
            if (!AMOUNT_MISMATCH.equals(group.getResultCode())) {
                group.flag(AMOUNT_MISMATCH, now);
                groupRepository.save(group);
                log.warn("지급 묶음 송금 금액 불일치 — 닫지 않는다 - groupId: {}, expected: {}, rows: {}, pg: {}", group.getId(),
                        expected(group), all, result.pgAmount());
                notifier.payoutAmountMismatch(group.getId(), group.getPayDate(), expected(group), result.pgAmount());
            }
            return false;
        }
        LocalDate remittedOn = result.remittedOn() == null ? KstDates.today(now) : result.remittedOn();
        // PG 가 실제로 보낸 계좌가 행의 계좌 스냅샷과 다른가 — 돈은 이미 나갔으니 지급은 닫되 사람을 부른다(Q3 — 송금 시점 계좌 · 이니시스 화면에서 바뀐 계좌).
        String accountMismatch = partnerSync.remittedAccountMismatch(toPay, result.remittedTo());
        group.markPaid(remittedOn, now);
        if (accountMismatch != null) {
            group.flag(ACCOUNT_MISMATCH, now);
        }
        groupRepository.saveAndFlush(group);
        if (accountMismatch != null) {
            log.warn("지급 묶음 송금 계좌 ≠ 행 계좌 스냅샷 — 지급은 닫고 확인 요청 - groupId: {}, {}", group.getId(), accountMismatch);
            notifier.payoutAccountMismatch(group.getId(), group.getPayDate(), accountMismatch);
            SettlementPayee payee = group.getPayee();
            toPay.stream().map(SettlementPayout::getSettlementId).distinct().sorted().forEach(id ->
                    historyRecorder.recordBySystem(id, SettlementEventType.PAYOUT_CHECK_REQUIRED,
                            payee.getLabel() + " · PG 송금 계좌가 지급 당시 등록 계좌와 다릅니다 — 운영자 확인", now));
        }
        LocalDateTime paidAt = remittedOn.equals(KstDates.today(now)) ? now : KstDates.at(remittedOn, LocalTime.NOON);
        String key = group.transferKey();
        payRows(toPay, row -> row.getPgReference() == null ? key : row.getPgReference(), paidAt, now);
        if (!toDefer.isEmpty()) {
            deferRows(toDefer, laterOf(planner.nextPayDate(group.getPayDate()), planner.earliestPayDate(now)),
                    "PG 미반영 합류분", CUTOFF_MISSED, now);
        }
        return true;
    }

    // ================================================================== 조회

    /** 그 지급일의 열린 묶음 요약 — 지시 요약 알림(PG 업로드 대사는 불가 · D-8). [건수, 확정 합계]. */
    public long[] openSummary(LocalDate payDate) {
        return readTransaction.execute(tx -> {
            List<SettlementPayoutGroup> groups = groupRepository.findOpenBetween(gateway.provider(), payDate, payDate);
            long amount = groups.stream().mapToLong(g -> g.getConfirmedAmount() == null ? 0 : g.getConfirmedAmount()).sum();
            return new long[]{groups.size(), amount};
        });
    }

    // ================================================================== 행 전이(호출자 트랜잭션 안)

    private List<SettlementPayout> requestedRows(Long groupId) {
        return payoutRepository.findByPayoutGroupId(groupId).stream()
                .filter(p -> p.getStatus() == PayoutStatus.REQUESTED).toList();
    }

    private interface Reference {
        String of(SettlementPayout row);
    }

    private void payRows(List<SettlementPayout> rows, Reference reference, LocalDateTime paidAt, LocalDateTime now) {
        Map<Long, Boolean> settlements = new LinkedHashMap<>();
        for (SettlementPayout row : rows) {
            String ref = reference.of(row);
            if (payoutRepository.markPaid(row.getId(), ref, paidAt) == 1) {
                historyRecorder.record(row.getSettlementId(), SettlementEventType.PAYOUT_PAID, SettlementActorType.PG, null,
                        "%s · %,d원 · %s".formatted(row.getPayee().getLabel(), row.getAmount(), ref), now);
                notifier.settlementPaid(row.getSettlementId(), row.getPayee());
                settlements.merge(row.getSettlementId(), row.getPayee() == SettlementPayee.CREATOR, Boolean::logicalOr);
            }
        }
        settlements.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            if (e.getValue()) {
                taxDocumentHook.onCreatorPaid(e.getKey(), now);
            }
            transitions.derive(e.getKey(), now);
        });
    }

    private void failRows(List<SettlementPayout> rows, String code, String reason, boolean detach, LocalDateTime now) {
        Set<Long> settlements = new TreeSet<>();
        for (SettlementPayout row : rows) {
            int updated = detach ? payoutRepository.detachAndFail(row.getId(), code, reason, now)
                    : payoutRepository.markFailed(row.getId(), null, code, reason, now);
            if (updated == 1) {
                historyRecorder.record(row.getSettlementId(), SettlementEventType.PAYOUT_FAILED, SettlementActorType.PG, null,
                        "%s · %s".formatted(row.getPayee().getLabel(), reason == null ? "분배 실패 " + code : reason), now);
                notifier.payoutFailed(row.getSettlementId(), row.getPayee());
                settlements.add(row.getSettlementId());
            }
        }
        settlements.forEach(id -> transitions.derive(id, now));
    }

    private void deferRows(List<SettlementPayout> rows, LocalDate next, String why, String code, LocalDateTime now) {
        Set<Long> settlements = new TreeSet<>();
        for (SettlementPayout row : rows) {
            if (payoutRepository.detachAndDefer(row.getId(), next) == 1) {
                historyRecorder.recordBySystem(row.getSettlementId(), SettlementEventType.PAYOUT_DEFERRED,
                        "%s · %s · %s 로 연기".formatted(row.getPayee().getLabel(), why, next), now);
                notifier.payoutCheckRequired(row.getSettlementId(), row.getPayee(), code == null ? "INSUFFICIENT_BALANCE" : code);
                settlements.add(row.getSettlementId());
            }
        }
        settlements.forEach(id -> transitions.derive(id, now));
    }

    private List<SettlementPayoutGroup> openAround(LocalDateTime now) {
        LocalDate today = KstDates.today(now);
        List<SettlementPayoutGroup> groups = readTransaction.execute(tx ->
                groupRepository.findOpenBetween(gateway.provider(), today.minusDays(SCAN_DAYS), today.plusDays(SCAN_DAYS)));
        return groups == null ? List.of() : new ArrayList<>(groups);
    }

    private String settlementNumber(Long settlementId) {
        return settlementRepository.findById(settlementId).map(Settlement::getSettlementNumber)
                .orElse(String.valueOf(settlementId));
    }

    private static long expected(SettlementPayoutGroup group) {
        return group.getConfirmedAmount() != null ? group.getConfirmedAmount() : group.getIntendedAmount();
    }

    private static LocalDate laterOf(LocalDate a, LocalDate b) {
        return Objects.requireNonNull(a).isAfter(b) ? a : b;
    }
}
