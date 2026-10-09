package showroomz.domain.settlement.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementPartySort;
import showroomz.domain.settlement.type.SettlementStatus;

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
