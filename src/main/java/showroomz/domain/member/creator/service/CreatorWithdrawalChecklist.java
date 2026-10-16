package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.repository.CreatorMemberQueryRepository;
import showroomz.domain.member.creator.type.CreatorWithdrawalCondition;
import showroomz.domain.settlement.type.SettlementStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 스튜디오 계정 탭 탈퇴 조건 행의 <b>내역</b>(50 설계서 4-1 · S1 「진행중 1건 · 종료 후 주문 종결 대기 1건」) — 표시 전용이다. 탈퇴 가능
 * 여부는 {@link CreatorWithdrawalEligibility#evaluate} 하나가 정하고 이 클래스는 판정하지 않는다.
 *
 * <p>불변식: 판정이 통과 ⇔ 세 조건 모두 0건. ②에 「체결완료(연결 공구 미종결)」를 더해도 그 계약의 공구가 이미 ①에 잡혀 있으므로
 * 판정과 어긋나지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CreatorWithdrawalChecklist {

    public static final String AWAITING_ORDER_CLOSURE = "AWAITING_ORDER_CLOSURE";
    private static final String AWAITING_ORDER_CLOSURE_LABEL = "종료 후 주문 종결 대기";

    private static final List<GroupBuyStatus> GROUP_BUY_ORDER = List.of(GroupBuyStatus.PREPARING, GroupBuyStatus.READY,
            GroupBuyStatus.IN_PROGRESS, GroupBuyStatus.SUSPENSION_SCHEDULED);
    private static final List<ContractStatus> CONTRACT_ORDER = List.of(ContractStatus.SIGNING,
            ContractStatus.CONCLUSION_PENDING, ContractStatus.CONCLUDED);
    private static final List<SettlementStatus> SETTLEMENT_ORDER = List.of(SettlementStatus.REVIEWING,
            SettlementStatus.ADJUSTING, SettlementStatus.PAYOUT_SCHEDULED, SettlementStatus.PAYOUT_FAILED);

    private final CreatorMemberQueryRepository queries;
    private final CreatorWithdrawalEligibility eligibility;

    public record Breakdown(String key, String label, long count) {
    }

    public record Condition(CreatorWithdrawalCondition condition, long count, List<Breakdown> breakdown) {

        public boolean satisfied() {
            return count == 0;
        }
    }

    @Transactional(readOnly = true)
    public List<Condition> read(Long creatorId) {
        List<Long> awaitingIds = eligibility.awaitingOrderClosureGroupBuyIds(creatorId);
        return List.of(groupBuys(creatorId, awaitingIds), contracts(creatorId, awaitingIds), settlements(creatorId));
    }

    private Condition groupBuys(Long creatorId, List<Long> awaitingIds) {
        Map<GroupBuyStatus, Long> byStatus = toMap(queries.countActiveGroupBuysByStatus(creatorId), GroupBuyStatus.class);
        List<Breakdown> rows = new ArrayList<>();
        for (GroupBuyStatus status : GROUP_BUY_ORDER) {
            addIfPositive(rows, status.name(), status.getLabel(), byStatus.getOrDefault(status, 0L));
        }
        addIfPositive(rows, AWAITING_ORDER_CLOSURE, AWAITING_ORDER_CLOSURE_LABEL, awaitingIds.size());
        return condition(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS, rows);
    }

    private Condition contracts(Long creatorId, List<Long> awaitingIds) {
        Map<ContractStatus, Long> byStatus = toMap(queries.countOpenContractsByStatus(creatorId), ContractStatus.class);
        byStatus.put(ContractStatus.CONCLUDED, queries.countConcludedContractsOfUnfinishedGroupBuys(creatorId, awaitingIds));
        List<Breakdown> rows = new ArrayList<>();
        for (ContractStatus status : CONTRACT_ORDER) {
            addIfPositive(rows, status.name(), status.getLabel(), byStatus.getOrDefault(status, 0L));
        }
        return condition(CreatorWithdrawalCondition.UNFINISHED_CONTRACTS, rows);
    }

    private Condition settlements(Long creatorId) {
        Map<SettlementStatus, Long> byStatus = toMap(queries.countUnsettledByStatus(creatorId), SettlementStatus.class);
        List<Breakdown> rows = new ArrayList<>();
        for (SettlementStatus status : SETTLEMENT_ORDER) {
            addIfPositive(rows, status.name(), status.getLabel(), byStatus.getOrDefault(status, 0L));
        }
        return condition(CreatorWithdrawalCondition.UNSETTLED, rows);
    }

    private static Condition condition(CreatorWithdrawalCondition condition, List<Breakdown> rows) {
        long total = rows.stream().mapToLong(Breakdown::count).sum();
        return new Condition(condition, total, List.copyOf(rows));
    }

    private static void addIfPositive(List<Breakdown> rows, String key, String label, long count) {
        if (count > 0) {
            rows.add(new Breakdown(key, label, count));
        }
    }

    private static <E extends Enum<E>> Map<E, Long> toMap(List<Object[]> rows, Class<E> type) {
        Map<E, Long> map = new EnumMap<>(type);
        for (Object[] row : rows) {
            map.put(type.cast(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }
}
