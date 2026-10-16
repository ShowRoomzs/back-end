package showroomz.domain.performance.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.entity.QOrder;
import showroomz.domain.order.entity.QOrderDeliveryGroup;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.performance.type.PerformanceSort;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static showroomz.domain.contract.entity.QContract.contract;
import static showroomz.domain.groupbuy.entity.QGroupBuy.groupBuy;
import static showroomz.domain.market.entity.QMarket.market;
import static showroomz.domain.member.creator.entity.QCreator.creator;
import static showroomz.domain.settlement.entity.QSettlement.settlement;

/**
 * 파트너 성과 목록(48 파트너 설계서 3-2 · 6-2) — 공구 기준 · 정산 LEFT JOIN 으로 성과 상태(0-2)를 쿼리 조건으로 건다.
 *
 * <p>모집단(0-3) = 내 마켓 ∧ {@code ENDED · SETTLED} ∧ 결제 완료 하위주문 ≥ 1. 진행 중 · 중단 · 판매 0건은 나오지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceGroupBuyQueryRepository {

    private static final Set<GroupBuyStatus> ENDED = EnumSet.of(GroupBuyStatus.ENDED, GroupBuyStatus.SETTLED);

    private final JPAQueryFactory queryFactory;

    /** 목록 한 페이지 — 계약 · 마켓 · 인플루언서를 함께 올린다. */
    public Page<GroupBuy> searchForMarket(Long marketId, Collection<PerformanceStatus> statuses, String keyword,
                                          PerformanceSort sort, Pageable pageable) {
        BooleanBuilder where = where(marketId, statuses, keyword);
        List<GroupBuy> content = queryFactory.selectFrom(groupBuy)
                .join(groupBuy.contract, contract).fetchJoin()
                .join(groupBuy.market, market).fetchJoin()
                .join(groupBuy.creator, creator).fetchJoin()
                .leftJoin(settlement).on(settlement.groupBuy.id.eq(groupBuy.id))
                .where(where)
                .orderBy(order(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        return PageableExecutionUtils.getPage(content, pageable, () -> {
            Long total = queryFactory.select(groupBuy.count()).from(groupBuy)
                    .join(groupBuy.contract, contract)
                    .join(groupBuy.creator, creator)
                    .leftJoin(settlement).on(settlement.groupBuy.id.eq(groupBuy.id))
                    .where(where)
                    .fetchOne();
            return total == null ? 0 : total;
        });
    }

    /** 조회기간 표기(1-2) — 필터를 통과한 공구들의 [MIN(start_at), MAX(COALESCE(ended_at, end_at))]. */
    public Tuple periodForMarket(Long marketId, Collection<PerformanceStatus> statuses, String keyword) {
        return queryFactory.select(groupBuy.startAt.min(), endOf().max()).from(groupBuy)
                .join(groupBuy.contract, contract)
                .join(groupBuy.creator, creator)
                .leftJoin(settlement).on(settlement.groupBuy.id.eq(groupBuy.id))
                .where(where(marketId, statuses, keyword))
                .fetchOne();
    }

    /** 모집단 전부(KPI · 상태 칩) — 검색어 · 상태 칩과 무관(KPI 는 필터를 따라가지 않는다). */
    public List<GroupBuy> findAllListedForMarket(Long marketId) {
        return queryFactory.selectFrom(groupBuy)
                .join(groupBuy.contract, contract).fetchJoin()
                .join(groupBuy.market, market).fetchJoin()
                .join(groupBuy.creator, creator).fetchJoin()
                .where(listed(marketId))
                .orderBy(endOf().desc(), groupBuy.id.desc())
                .fetch();
    }

    private static BooleanBuilder where(Long marketId, Collection<PerformanceStatus> statuses, String keyword) {
        BooleanBuilder where = new BooleanBuilder(listed(marketId));
        if (statuses != null && !statuses.isEmpty() && statuses.size() < PerformanceStatus.values().length) {
            BooleanBuilder status = new BooleanBuilder();
            if (statuses.contains(PerformanceStatus.AGGREGATING)) {
                status.or(settlement.id.isNull());
            }
            Set<SettlementStatus> settled = PerformanceStatus.settlementStatusesOf(statuses);
            if (!settled.isEmpty()) {
                status.or(settlement.status.in(settled));
            }
            where.and(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim() + "%";
            where.and(contract.title.like(pattern).or(creator.showroomName.like(pattern)));
        }
        return where;
    }

    private static BooleanExpression listed(Long marketId) {
        QOrderDeliveryGroup paid = new QOrderDeliveryGroup("performancePaidGroup");
        QOrder paidOrder = new QOrder("performancePaidOrder");
        return groupBuy.market.id.eq(marketId)
                .and(groupBuy.status.in(ENDED))
                .and(JPAExpressions.selectOne().from(paid).join(paid.order, paidOrder)
                        .where(paid.groupBuy.id.eq(groupBuy.id), paidOrder.paidAt.isNotNull(),
                                paid.fulfillmentStatus.ne(FulfillmentStatus.PENDING))
                        .exists());
    }

    private static DateTimeExpression<LocalDateTime> endOf() {
        return Expressions.dateTimeTemplate(LocalDateTime.class, "coalesce({0}, {1})", groupBuy.endedAt, groupBuy.endAt);
    }

    private static OrderSpecifier<?>[] order(PerformanceSort sort) {
        OrderSpecifier<LocalDateTime> ended = new OrderSpecifier<>(Order.DESC, endOf());
        if (sort == PerformanceSort.SALES_DESC) {
            return new OrderSpecifier<?>[]{
                    new OrderSpecifier<>(Order.DESC, settlement.confirmedSalesAmount, OrderSpecifier.NullHandling.NullsLast),
                    ended, groupBuy.id.desc()};
        }
        return new OrderSpecifier<?>[]{ended, groupBuy.id.desc()};
    }
}
