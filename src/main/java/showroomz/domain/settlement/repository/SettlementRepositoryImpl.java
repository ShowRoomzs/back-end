package showroomz.domain.settlement.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import showroomz.domain.settlement.adjustment.entity.QSettlementAdjustment;
import showroomz.domain.settlement.entity.QSettlementPayout;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementAdminSort;
import showroomz.domain.settlement.type.SettlementPartySort;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static showroomz.domain.contract.entity.QContract.contract;
import static showroomz.domain.groupbuy.entity.QGroupBuy.groupBuy;
import static showroomz.domain.market.entity.QMarket.market;
import static showroomz.domain.member.creator.entity.QCreator.creator;
import static showroomz.domain.settlement.entity.QSettlement.settlement;

@Repository
@RequiredArgsConstructor
public class SettlementRepositoryImpl implements SettlementRepositoryCustom {

    private static final String SETTLEMENT_NUMBER_PREFIX = "STL-";

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Settlement> searchForMarket(Long marketId, Collection<SettlementStatus> statuses, String keyword,
                                            SettlementPartySort sort, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder(settlement.market.id.eq(marketId)).and(settlement.status.in(statuses));
        String pattern = pattern(keyword);
        if (pattern != null) {
            where.and(contract.title.like(pattern).or(creator.showroomName.like(pattern)));
        }
        return page(where, order(sort, settlement.brandPayoutAmount), pageable);
    }

    @Override
    public Page<Settlement> searchForCreator(Long creatorId, Collection<SettlementStatus> statuses, String keyword,
                                             SettlementPartySort sort, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder(settlement.creator.id.eq(creatorId)).and(settlement.status.in(statuses));
        String pattern = pattern(keyword);
        if (pattern != null) {
            where.and(contract.title.like(pattern).or(market.marketName.like(pattern)));
        }
        return page(where, order(sort, settlement.creatorPayoutAmount), pageable);
    }

    @Override
    public Page<Settlement> searchForAdmin(Collection<SettlementStatus> statuses, String keyword,
                                           SettlementAdminSort sort, Pageable pageable) {
        return page(adminWhere(statuses, keyword), adminOrder(sort), pageable);
    }

    @Override
    public SettlementTotals totalsForAdmin(Collection<SettlementStatus> statuses, String keyword) {
        Tuple row = queryFactory.select(settlement.count(),
                        settlement.confirmedSalesAmount.sum().coalesce(0L),
                        settlement.brandPayoutAmount.sum().coalesce(0L),
                        settlement.pgFeeAmount.sum().coalesce(0L),
                        settlement.creatorPayoutAmount.sum().coalesce(0L),
                        settlement.withholdingAmount.sum().coalesce(0L),
                        settlement.creatorVatAmount.sum().coalesce(0L),
                        settlement.rewardVatAmount.sum().coalesce(0L),
                        settlement.platformFeeAmount.sum().coalesce(0L))
                .from(settlement)
                .join(settlement.contract, contract)
                .join(settlement.market, market)
                .join(settlement.creator, creator)
                .where(adminWhere(statuses, keyword))
                .fetchOne();
        if (row == null) {
            return SettlementTotals.EMPTY;
        }
        return new SettlementTotals(longOf(row.get(0, Long.class)), longOf(row.get(1, Long.class)),
                longOf(row.get(2, Long.class)), longOf(row.get(3, Long.class)), longOf(row.get(4, Long.class)),
                longOf(row.get(5, Long.class)), longOf(row.get(6, Long.class)), longOf(row.get(7, Long.class)),
                longOf(row.get(8, Long.class)));
    }

    private static BooleanBuilder adminWhere(Collection<SettlementStatus> statuses, String keyword) {
        BooleanBuilder where = new BooleanBuilder(settlement.status.in(statuses));
        if (keyword == null || keyword.isBlank()) {
            return where;
        }
        String trimmed = keyword.trim();
        if (trimmed.regionMatches(true, 0, SETTLEMENT_NUMBER_PREFIX, 0, SETTLEMENT_NUMBER_PREFIX.length())) {
            return where.and(settlement.settlementNumber.upper().startsWith(trimmed.toUpperCase()));
        }
        String pattern = "%" + trimmed + "%";
        return where.and(contract.title.like(pattern).or(market.marketName.like(pattern))
                .or(creator.showroomName.like(pattern)));
    }

    /**
     * 「일정」 열 = 상태별 다음 날짜(7-1) — 확인 마감 · 합의 기한 · 지급 예정일 · 지급 완료 · 분배 실패 시각. 같은 식으로 정렬한다.
     */
    private static OrderSpecifier<?>[] adminOrder(SettlementAdminSort sort) {
        QSettlementAdjustment adjustment = new QSettlementAdjustment("orderAdjustment");
        QSettlementPayout failed = new QSettlementPayout("orderFailedPayout");
        Expression<LocalDateTime> agreementDue = JPAExpressions.select(adjustment.deadlineAt).from(adjustment)
                .where(adjustment.settlementId.eq(settlement.id));
        Expression<LocalDateTime> failedAt = JPAExpressions.select(failed.failedAt.max()).from(failed)
                .where(failed.settlementId.eq(settlement.id), failed.status.eq(PayoutStatus.FAILED));
        return switch (sort) {
            case SALES_DESC -> new OrderSpecifier<?>[]{settlement.confirmedSalesAmount.desc(), settlement.id.desc()};
            case REVIEW_DUE_ASC -> new OrderSpecifier<?>[]{settlement.reviewDueAt.asc(), settlement.id.asc()};
            case AGREEMENT_DUE_ASC -> new OrderSpecifier<?>[]{new OrderSpecifier<>(Order.ASC, agreementDue),
                    settlement.id.asc()};
            case FAILED_AT_ASC -> new OrderSpecifier<?>[]{new OrderSpecifier<>(Order.ASC, failedAt), settlement.id.asc()};
            case SCHEDULE_DESC -> {
                Expression<LocalDateTime> payoutDue = Expressions.dateTimeTemplate(LocalDateTime.class,
                        "cast({0} as LocalDateTime)", settlement.payoutDueDate);
                Expression<LocalDateTime> schedule = new CaseBuilder()
                        .when(settlement.status.eq(SettlementStatus.REVIEWING))
                        .then((Expression<LocalDateTime>) settlement.reviewDueAt)
                        .when(settlement.status.eq(SettlementStatus.ADJUSTING)).then(agreementDue)
                        .when(settlement.status.eq(SettlementStatus.PAYOUT_SCHEDULED)).then(payoutDue)
                        .when(settlement.status.eq(SettlementStatus.PAID)).then(settlement.paidAt)
                        .otherwise(failedAt);
                yield new OrderSpecifier<?>[]{
                        new OrderSpecifier<>(Order.DESC, schedule, OrderSpecifier.NullHandling.NullsLast),
                        settlement.id.desc()};
            }
        };
    }

    private static long longOf(Long value) {
        return value == null ? 0 : value;
    }

    private Page<Settlement> page(BooleanBuilder where, OrderSpecifier<?>[] order, Pageable pageable) {
        List<Settlement> content = queryFactory.selectFrom(settlement)
                .join(settlement.groupBuy, groupBuy).fetchJoin()
                .join(settlement.contract, contract).fetchJoin()
                .join(settlement.market, market).fetchJoin()
                .join(settlement.creator, creator).fetchJoin()
                .where(where)
                .orderBy(order)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        return PageableExecutionUtils.getPage(content, pageable, () -> {
            Long total = queryFactory.select(settlement.count()).from(settlement)
                    .join(settlement.contract, contract)
                    .join(settlement.market, market)
                    .join(settlement.creator, creator)
                    .where(where)
                    .fetchOne();
            return total == null ? 0 : total;
        });
    }

    private static OrderSpecifier<?>[] order(SettlementPartySort sort, NumberPath<Long> payout) {
        if (sort == SettlementPartySort.PAYOUT_DESC) {
            return new OrderSpecifier<?>[]{payout.desc(), settlement.createdAt.desc(), settlement.id.desc()};
        }
        return new OrderSpecifier<?>[]{settlement.createdAt.desc(), settlement.id.desc()};
    }

    private static String pattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim() + "%";
    }
}
