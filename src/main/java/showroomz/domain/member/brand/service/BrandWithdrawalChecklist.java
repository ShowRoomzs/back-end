package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.type.BrandSuspensionReason;
import showroomz.domain.member.brand.type.BrandWithdrawalAction;
import showroomz.domain.member.brand.type.BrandWithdrawalBreakdownGroup;
import showroomz.domain.member.brand.type.BrandWithdrawalCondition;
import showroomz.domain.member.brand.type.BrandWithdrawalTarget;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader;
import showroomz.domain.settlement.adjustment.type.AdjustmentTurn;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 파트너센터 계정 탭 탈퇴 조건 행의 <b>내역</b>(51 설계서 4절 · 시안 4-G · 4-G2) — 표시 전용이다. 탈퇴 가능 여부는
 * {@link BrandWithdrawalEligibility#evaluate} 하나가 정하고 이 클래스는 판정하지 않는다. 스튜디오 {@code CreatorWithdrawalChecklist}와
 * 같은 모양에 브랜드 화면이 요구하는 갈래({@link BrandWithdrawalBreakdownGroup}) · 이동 대상 · 행위를 더했다.
 *
 * <p>불변식: 조건마다 내역 합계 = 판정식 건수. 정산 내역은 판정식과 같은 WHERE(브랜드 몫이 끝나지 않은 정산)를 정산 상태 · 브랜드 행 상태 ·
 * 보류 사유로 나눠 센다 — 쿼리 둘로 나누지 않아 확정 전 정산이 이중으로 잡히지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandWithdrawalChecklist {

    private static final List<GroupBuyStatus> GROUP_BUY_ORDER = List.of(GroupBuyStatus.PREPARING, GroupBuyStatus.READY,
            GroupBuyStatus.IN_PROGRESS, GroupBuyStatus.SUSPENSION_SCHEDULED);
    private static final Set<String> ACCOUNT_CODES = Set.of(PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name(),
            PayoutBlockReason.ACCOUNT_MISSING.name(), PayoutBlockReason.ACCOUNT_INVALID.name(),
            PayoutBlockReason.UNSUPPORTED_BANK.name());

    private final GroupBuyRepository groupBuyRepository;
    private final ContractRepository contractRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementClawbackRepository clawbackRepository;
    private final SettlementAdjustmentReader adjustmentReader;
    private final BrandSanctionReader sanctionReader;

    /**
     * 내역 한 행 — 선택 필드는 해당하는 행에만 값이 있다.
     *
     * @param expectedDate 기다리면 풀리는 행의 예상일 — 확인 중은 가장 이른 확인 기한 · 지급 예정은 가장 이른 지급 예정일
     * @param items        조정 협의 행만 — 정산별 이슈 스레드 · 합의 기한 · 차례
     */
    public record Breakdown(String key, String label, long count, BrandWithdrawalBreakdownGroup group,
                            BrandWithdrawalAction action, LocalDate expectedDate, String reasonCode, String reasonLabel,
                            String dueNote, BrandWithdrawalTarget target, List<AdjustmentItem> items) {

        static Breakdown of(String key, String label, long count, BrandWithdrawalBreakdownGroup group) {
            return new Breakdown(key, label, count, group, null, null, null, null, null, null, List.of());
        }
    }

    public record AdjustmentItem(Long settlementId, String settlementNumber, Long threadId, LocalDateTime deadlineAt,
                                 AdjustmentTurn turn) {
    }

    public record Condition(BrandWithdrawalCondition condition, long count, BrandWithdrawalTarget target,
                            List<Breakdown> breakdown) {

        public boolean satisfied() {
            return count == 0;
        }
    }

    /** @param draftContractCount 작성 중인 계약 — 승인 때 함께 삭제된다(4-G 하단 · M5 「작성 중인 계약 N건은 함께 삭제됩니다」) */
    public record Checklist(List<Condition> conditions, long draftContractCount) {
    }

    public Checklist read(Long marketId) {
        Map<ContractStatus, Long> contracts = toMap(contractRepository.countByStatus(marketId), ContractStatus.class);
        List<Condition> conditions = List.of(groupBuys(marketId), contracts(contracts), orders(marketId),
                settlements(marketId));
        return new Checklist(conditions, contracts.getOrDefault(ContractStatus.DRAFT, 0L));
    }

    // ------------------------------------------------------------------ ① 진행 중 · 준비 중 공구(4-1)

    private Condition groupBuys(Long marketId) {
        Map<GroupBuyStatus, Long> byStatus = toMap(groupBuyRepository.countByStatus(marketId), GroupBuyStatus.class);
        List<Breakdown> rows = new ArrayList<>();
        for (GroupBuyStatus status : GROUP_BUY_ORDER) {
            addIfPositive(rows, Breakdown.of(status.name(), status.getLabel(), byStatus.getOrDefault(status, 0L),
                    BrandWithdrawalBreakdownGroup.WAIT));
        }
        return condition(BrandWithdrawalCondition.ACTIVE_GROUP_BUYS, BrandWithdrawalTarget.GROUP_BUYS, rows);
    }

    // ------------------------------------------------------------------ ② 체결 전 계약(4-2)

    private Condition contracts(Map<ContractStatus, Long> byStatus) {
        List<Breakdown> rows = new ArrayList<>();
        addContract(rows, byStatus, ContractStatus.REVIEW_PENDING, BrandWithdrawalBreakdownGroup.MY_ACTION,
                BrandWithdrawalAction.CANCEL_REVIEW);
        addContract(rows, byStatus, ContractStatus.REVIEW_REJECTED, BrandWithdrawalBreakdownGroup.MY_ACTION,
                BrandWithdrawalAction.DELETE);
        addContract(rows, byStatus, ContractStatus.SIGNING, BrandWithdrawalBreakdownGroup.OPERATOR,
                BrandWithdrawalAction.CONTACT_OPERATOR);
        addContract(rows, byStatus, ContractStatus.CONCLUSION_PENDING, BrandWithdrawalBreakdownGroup.OPERATOR,
                BrandWithdrawalAction.CONTACT_OPERATOR);
        return condition(BrandWithdrawalCondition.CONTRACTS_BEFORE_CONCLUSION, BrandWithdrawalTarget.CONTRACTS, rows);
    }

    private static void addContract(List<Breakdown> rows, Map<ContractStatus, Long> byStatus, ContractStatus status,
                                    BrandWithdrawalBreakdownGroup group, BrandWithdrawalAction action) {
        long count = byStatus.getOrDefault(status, 0L);
        addIfPositive(rows, new Breakdown(status.name(), status.getLabel(), count, group, action, null, null, null,
                null, null, List.of()));
    }

    // ------------------------------------------------------------------ ③ 미종결 주문(4-3) — 시안이 한 줄이라 나누지 않는다

    private Condition orders(Long marketId) {
        List<Breakdown> rows = new ArrayList<>();
        addIfPositive(rows, Breakdown.of("UNCLOSED", BrandWithdrawalCondition.UNCLOSED_ORDERS.getLabel(),
                deliveryGroupRepository.countUnclosedByMarket(marketId), BrandWithdrawalBreakdownGroup.MY_ACTION));
        return condition(BrandWithdrawalCondition.UNCLOSED_ORDERS, BrandWithdrawalTarget.ORDERS, rows);
    }

    // ------------------------------------------------------------------ ④ 진행 중 정산(4-4 · 브랜드 몫 기준)

    private Condition settlements(Long marketId) {
        long reviewing = 0;
        LocalDateTime reviewDue = null;
        long adjusting = 0;
        long scheduled = 0;
        LocalDate scheduledDue = null;
        long failed = 0;
        long otherWaiting = 0;
        Map<String, Long> blockedByCode = new LinkedHashMap<>();
        for (Object[] row : payoutRepository.countBrandOpenByStatus(marketId)) {
            SettlementStatus settlementStatus = (SettlementStatus) row[0];
            PayoutStatus payoutStatus = (PayoutStatus) row[1];
            String failCode = (String) row[2];
            long count = ((Number) row[3]).longValue();
            if (settlementStatus == SettlementStatus.REVIEWING) {
                reviewing += count;
                reviewDue = earlier(reviewDue, (LocalDateTime) row[5]);
            } else if (settlementStatus == SettlementStatus.ADJUSTING) {
                adjusting += count;
            } else if (payoutStatus == PayoutStatus.SCHEDULED || payoutStatus == PayoutStatus.REQUESTED) {
                scheduled += count;
                scheduledDue = earlier(scheduledDue, (LocalDate) row[4]);
            } else if (payoutStatus == PayoutStatus.BLOCKED) {
                blockedByCode.merge(failCode == null ? "" : failCode, count, Long::sum);
            } else if (payoutStatus == PayoutStatus.FAILED) {
                failed += count;
            } else {
                // 확정 뒤인데 브랜드 행이 아직 확인 대기 · 보류 — 정상 경로에는 없다. 판정과 합계를 맞추려고 「기다리면 풀림」으로 센다.
                otherWaiting += count;
            }
        }

        List<Breakdown> rows = new ArrayList<>();
        addIfPositive(rows, new Breakdown(SettlementStatus.REVIEWING.name(), SettlementStatus.REVIEWING.getLabel(),
                reviewing, BrandWithdrawalBreakdownGroup.WAIT, null, reviewDue == null ? null : reviewDue.toLocalDate(),
                null, null, null, null, List.of()));
        if (adjusting > 0) {
            rows.add(new Breakdown(SettlementStatus.ADJUSTING.name(), SettlementStatus.ADJUSTING.getLabel(), adjusting,
                    BrandWithdrawalBreakdownGroup.MY_ACTION, null, null, null, null, null, null,
                    adjustmentItems(marketId)));
        }
        addIfPositive(rows, new Breakdown(SettlementStatus.PAYOUT_SCHEDULED.name(),
                SettlementStatus.PAYOUT_SCHEDULED.getLabel(), scheduled + otherWaiting, BrandWithdrawalBreakdownGroup.WAIT,
                null, scheduledDue, null, null, null, null, List.of()));
        Optional<BrandSanction> sanction = blockedByCode.containsKey(PayoutBlockReason.BRAND_SUSPENDED.name())
                ? sanctionReader.openSanction(marketId) : Optional.empty();
        blockedByCode.forEach((code, count) -> rows.add(blocked(code, count, sanction)));
        addIfPositive(rows, Breakdown.of(PayoutStatus.FAILED.name(), PayoutStatus.FAILED.getLabel(), failed,
                BrandWithdrawalBreakdownGroup.OPERATOR));
        addIfPositive(rows, Breakdown.of("CLAWBACK_PENDING", "차감 대기", clawbackRepository.countPendingForMarket(marketId),
                BrandWithdrawalBreakdownGroup.OPERATOR));
        return condition(BrandWithdrawalCondition.UNSETTLED, BrandWithdrawalTarget.SETTLEMENTS, rows);
    }

    private List<AdjustmentItem> adjustmentItems(Long marketId) {
        List<Long> ids = settlementRepository.findIdsByMarketAndStatus(marketId, SettlementStatus.ADJUSTING);
        Map<Long, SettlementAdjustmentReader.AdjustmentSummary> summaries = adjustmentReader.findBySettlementIds(ids);
        List<AdjustmentItem> items = new ArrayList<>();
        for (Long id : ids) {
            SettlementAdjustmentReader.AdjustmentSummary summary = summaries.get(id);
            items.add(summary == null
                    ? new AdjustmentItem(id, null, null, null, null)
                    : new AdjustmentItem(id, summary.settlementNumber(), summary.threadId(), summary.deadlineAt(),
                            SettlementAdjustmentReader.turnFor(summary, SettlementParty.SELLER)));
        }
        return items;
    }

    /**
     * 지급 보류 행(4-G2 포함) — 사유별 갈래 · 이동 대상. 정지 사유가 사업자 정보 허위 기재면 사업자 정보로, 그 밖의 지급 정지(정산 정보 불일치)는
     * 정산 정보로 보낸다.
     */
    private static Breakdown blocked(String code, long count, Optional<BrandSanction> sanction) {
        Optional<PayoutBlockReason> reason = PayoutBlockReason.fromCode(code.isEmpty() ? null : code);
        String reasonLabel = reason.map(PayoutBlockReason::getLabel).orElse(null);
        String dueNote = reason.map(PayoutBlockReason::getDueNoteFormat).filter(note -> !note.contains("%")).orElse(null);
        BrandWithdrawalBreakdownGroup group = BrandWithdrawalBreakdownGroup.OPERATOR;
        BrandWithdrawalTarget target = null;
        if (PayoutBlockReason.BRAND_SUSPENDED.name().equals(code)) {
            group = BrandWithdrawalBreakdownGroup.MY_ACTION;
            BrandSuspensionReason suspension = sanction.map(BrandSanction::getReason).orElse(null);
            target = suspension == BrandSuspensionReason.BUSINESS_INFO_FALSIFIED
                    ? BrandWithdrawalTarget.BUSINESS_INFO : BrandWithdrawalTarget.SETTLEMENT_ACCOUNT;
            if (suspension != null) {
                reasonLabel = reasonLabel + "(" + suspension.getLabel() + ")";
            }
        } else if (ACCOUNT_CODES.contains(code)) {
            group = BrandWithdrawalBreakdownGroup.MY_ACTION;
            target = BrandWithdrawalTarget.SETTLEMENT_ACCOUNT;
        } else if (PayoutBlockReason.COMPANY_NOT_IN_BUSINESS.name().equals(code)) {
            group = BrandWithdrawalBreakdownGroup.MY_ACTION;
            target = BrandWithdrawalTarget.BUSINESS_INFO;
        } else if (PayoutBlockReason.PAYEE_ACTION_CODES.contains(code)) {
            group = BrandWithdrawalBreakdownGroup.MY_ACTION;
        }
        return new Breakdown(PayoutStatus.BLOCKED.name(), PayoutStatus.BLOCKED.getLabel(), count, group, null, null,
                code.isEmpty() ? null : code, reasonLabel, dueNote, target, List.of());
    }

    // ------------------------------------------------------------------ 공통

    private static Condition condition(BrandWithdrawalCondition condition, BrandWithdrawalTarget target,
                                       List<Breakdown> rows) {
        long total = rows.stream().mapToLong(Breakdown::count).sum();
        return new Condition(condition, total, target, List.copyOf(rows));
    }

    private static void addIfPositive(List<Breakdown> rows, Breakdown row) {
        if (row.count() > 0) {
            rows.add(row);
        }
    }

    private static LocalDateTime earlier(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private static LocalDate earlier(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private static <E extends Enum<E>> Map<E, Long> toMap(List<Object[]> rows, Class<E> type) {
        Map<E, Long> map = new EnumMap<>(type);
        for (Object[] row : rows) {
            map.merge(type.cast(row[0]), ((Number) row[1]).longValue(), Long::sum);
        }
        return map;
    }
}
