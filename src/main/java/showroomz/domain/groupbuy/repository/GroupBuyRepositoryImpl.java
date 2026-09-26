package showroomz.domain.groupbuy.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.QGroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.QGroupBuyPost;
import showroomz.domain.groupbuy.type.AdminGroupBuyQueue;
import showroomz.domain.groupbuy.type.AdminGroupBuySortType;
import showroomz.domain.groupbuy.type.AdminGroupBuyTab;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.CreatorGroupBuySortType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static showroomz.domain.contract.entity.QContract.contract;
import static showroomz.domain.groupbuy.entity.QGroupBuy.groupBuy;
import static showroomz.domain.market.entity.QMarket.market;
import static showroomz.domain.member.creator.entity.QCreator.creator;

@Repository
@RequiredArgsConstructor
public class GroupBuyRepositoryImpl implements GroupBuyRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<GroupBuy> searchForCreator(Long creatorId, Collection<GroupBuyStatus> statuses, String pattern,
                                           CreatorGroupBuySortType sort, LocalDateTime now, Pageable pageable) {
        BooleanBuilder where = creatorFilter(creatorId, statuses, pattern);

        List<GroupBuy> content = queryFactory
                .selectFrom(groupBuy)
                .join(groupBuy.contract, contract).fetchJoin()
                .join(groupBuy.market, market).fetchJoin()
                .where(where)
                .orderBy(orderOf(sort, now))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(groupBuy.count())
                .from(groupBuy)
                .join(groupBuy.contract, contract)
                .join(groupBuy.market, market)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public List<Long> findOrderedIdsForCreator(Long creatorId, Collection<GroupBuyStatus> statuses, String pattern,
                                               CreatorGroupBuySortType sort, LocalDateTime now) {
        return queryFactory
                .select(groupBuy.id)
                .from(groupBuy)
                .join(groupBuy.contract, contract)
                .join(groupBuy.market, market)
                .where(creatorFilter(creatorId, statuses, pattern))
                .orderBy(orderOf(sort, now))
                .fetch();
    }

    @Override
    public long countActionRequiredForCreator(Long creatorId, LocalDateTime now) {
        Long count = queryFactory
                .select(groupBuy.count())
                .from(groupBuy)
                .where(groupBuy.creator.id.eq(creatorId), CreatorGroupBuyActionPredicate.of(groupBuy, now))
                .fetchOne();
        return count == null ? 0L : count;
    }

    @Override
    public Set<Long> findActionRequiredIdsForCreator(Long creatorId, Collection<Long> groupBuyIds,
                                                     LocalDateTime now) {
        if (groupBuyIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(queryFactory
                .select(groupBuy.id)
                .from(groupBuy)
                .where(groupBuy.creator.id.eq(creatorId),
                        groupBuy.id.in(groupBuyIds),
                        CreatorGroupBuyActionPredicate.of(groupBuy, now))
                .fetch());
    }

    private static BooleanBuilder creatorFilter(Long creatorId, Collection<GroupBuyStatus> statuses, String pattern) {
        BooleanBuilder where = new BooleanBuilder()
                .and(groupBuy.creator.id.eq(creatorId))
                .and(groupBuy.status.in(statuses));
        if (pattern != null) {
            where.and(contract.title.like(pattern)
                    .or(market.marketName.like(pattern))
                    .or(groupBuy.groupBuyNumber.like(pattern)));
        }
        return where;
    }

    /**
     * 31 설계 1-2의 복합 정렬. 비종결·종결을 먼저 가른 뒤, 비종결은 시작일 오름차순 열을, 종결은 시작일 내림차순 열을
     * 각각 쓴다 — 다른 쪽 그룹에서는 그 열이 전부 NULL이라 정렬에 영향이 없다.
     */
    private static OrderSpecifier<?>[] orderOf(CreatorGroupBuySortType sort, LocalDateTime now) {
        BooleanExpression terminal = groupBuy.status.in(GroupBuyStatus.TERMINAL);
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        if (sort == CreatorGroupBuySortType.ACTION_REQUIRED_FIRST) {
            orders.add(new CaseBuilder()
                    .when(CreatorGroupBuyActionPredicate.of(groupBuy, now)).then(0)
                    .otherwise(1).asc());
        }
        orders.add(new CaseBuilder().when(terminal).then(1).otherwise(0).asc());
        orders.add(new OrderSpecifier<>(Order.ASC, new CaseBuilder()
                .when(terminal).then(Expressions.nullExpression(LocalDateTime.class))
                .otherwise(groupBuy.startAt)));
        orders.add(new OrderSpecifier<>(Order.DESC, new CaseBuilder()
                .when(terminal).then(groupBuy.startAt)
                .otherwise(Expressions.nullExpression(LocalDateTime.class))));
        orders.add(groupBuy.id.desc());
        return orders.toArray(OrderSpecifier[]::new);
    }

    // ── 어드민(32 설계) ──────────────────────────────────────────────────────

    @Override
    public Page<GroupBuy> searchForAdmin(AdminGroupBuyTab tab, String pattern, AdminGroupBuySortType sort,
                                         LocalDateTime now, Pageable pageable) {
        BooleanBuilder where = adminFilter(tab, pattern, now);

        List<GroupBuy> content = queryFactory
                .selectFrom(groupBuy)
                .join(groupBuy.contract, contract).fetchJoin()
                .join(groupBuy.market, market).fetchJoin()
                .join(groupBuy.creator, creator).fetchJoin()
                .where(where)
                .orderBy(adminOrderOf(sort, now))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(groupBuy.count())
                .from(groupBuy)
                .join(groupBuy.contract, contract)
                .join(groupBuy.market, market)
                .join(groupBuy.creator, creator)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public List<Long> findOrderedIdsForAdmin(AdminGroupBuyTab tab, String pattern, AdminGroupBuySortType sort,
                                             LocalDateTime now) {
        return queryFactory
                .select(groupBuy.id)
                .from(groupBuy)
                .join(groupBuy.contract, contract)
                .join(groupBuy.market, market)
                .join(groupBuy.creator, creator)
                .where(adminFilter(tab, pattern, now))
                .orderBy(adminOrderOf(sort, now))
                .fetch();
    }

    @Override
    public long countAdminQueue(AdminGroupBuyQueue queue, LocalDateTime now) {
        Long count = queryFactory
                .select(groupBuy.count())
                .from(groupBuy)
                .where(AdminGroupBuyQueuePredicate.of(queue, groupBuy, now))
                .fetchOne();
        return count == null ? 0L : count;
    }

    @Override
    public Set<Long> findAdminActionRequiredIds(Collection<Long> groupBuyIds, LocalDateTime now) {
        if (groupBuyIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(queryFactory
                .select(groupBuy.id)
                .from(groupBuy)
                .where(groupBuy.id.in(groupBuyIds), AdminGroupBuyQueuePredicate.any(groupBuy, now))
                .fetch());
    }

    @Override
    public DeadlineRow findEarliestOpenReview() {
        QGroupBuyPost post = QGroupBuyPost.groupBuyPost;
        Tuple row = queryFactory
                .select(groupBuy.id, contract.title, post.submittedAt)
                .from(post)
                .join(post.groupBuy, groupBuy)
                .join(groupBuy.contract, contract)
                .where(groupBuy.status.eq(GroupBuyStatus.PREPARING),
                        post.reviewStatus.eq(GroupBuyPostReviewStatus.PENDING),
                        post.submittedAt.isNotNull())
                .orderBy(post.submittedAt.asc(), groupBuy.id.asc())
                .fetchFirst();
        return row == null ? null
                : new DeadlineRow(row.get(groupBuy.id), row.get(contract.title), row.get(post.submittedAt));
    }

    @Override
    public DeadlineRow findEarliestAppealReview(LocalDateTime now) {
        QGroupBuyAdminSuspension notice = QGroupBuyAdminSuspension.groupBuyAdminSuspension;
        Tuple row = queryFactory
                .select(groupBuy.id, contract.title, notice.executeScheduledAt)
                .from(notice)
                .join(notice.groupBuy, groupBuy)
                .join(groupBuy.contract, contract)
                .where(notice.status.eq(AdminSuspensionStatus.NOTICED),
                        notice.appealSubmittedAt.isNotNull().or(notice.appealDeadlineAt.lt(now)),
                        notice.executeScheduledAt.isNotNull())
                .orderBy(notice.executeScheduledAt.asc(), groupBuy.id.asc())
                .fetchFirst();
        return row == null ? null
                : new DeadlineRow(row.get(groupBuy.id), row.get(contract.title), row.get(notice.executeScheduledAt));
    }

    @Override
    public DeadlineRow findEarliestEnded() {
        Tuple row = queryFactory
                .select(groupBuy.id, contract.title, groupBuy.endedAt)
                .from(groupBuy)
                .join(groupBuy.contract, contract)
                .where(groupBuy.status.eq(GroupBuyStatus.ENDED), groupBuy.endedAt.isNotNull())
                .orderBy(groupBuy.endedAt.asc(), groupBuy.id.asc())
                .fetchFirst();
        return row == null ? null
                : new DeadlineRow(row.get(groupBuy.id), row.get(contract.title), row.get(groupBuy.endedAt));
    }

    @Override
    public long countEndedAtOrBefore(LocalDateTime threshold) {
        Long count = queryFactory
                .select(groupBuy.count())
                .from(groupBuy)
                .where(groupBuy.status.eq(GroupBuyStatus.ENDED), groupBuy.endedAt.loe(threshold))
                .fetchOne();
        return count == null ? 0L : count;
    }

    private static BooleanBuilder adminFilter(AdminGroupBuyTab tab, String pattern, LocalDateTime now) {
        BooleanBuilder where = new BooleanBuilder().and(groupBuy.status.in(tab.getStatuses()));
        if (tab.isActionRequiredOnly()) {
            where.and(AdminGroupBuyQueuePredicate.any(groupBuy, now));
        }
        if (pattern != null) {
            where.and(contract.title.like(pattern)
                    .or(groupBuy.groupBuyNumber.like(pattern))
                    .or(market.marketName.like(pattern))
                    .or(creator.showroomName.like(pattern)));
        }
        return where;
    }

    /**
     * 32 설계 2-4. 「종료 임박순」은 비종결을 먼저 두고 종결은 최근 종결순으로 — 순수 {@code end_at ASC}면 끝난 지
     * 두 달 된 공구가 맨 위에 온다. 다른 쪽 그룹에서는 그 열이 전부 NULL이라 정렬에 영향이 없다.
     */
    private static OrderSpecifier<?>[] adminOrderOf(AdminGroupBuySortType sort, LocalDateTime now) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        switch (sort) {
            case ACTION_REQUIRED_FIRST -> {
                orders.add(new CaseBuilder()
                        .when(AdminGroupBuyQueuePredicate.any(groupBuy, now)).then(0)
                        .otherwise(1).asc());
                orders.add(groupBuy.createdAt.desc());
            }
            case CREATED_DESC -> orders.add(groupBuy.createdAt.desc());
            case END_AT_ASC -> {
                BooleanExpression terminal = groupBuy.status.in(GroupBuyStatus.TERMINAL);
                orders.add(new CaseBuilder().when(terminal).then(1).otherwise(0).asc());
                orders.add(new OrderSpecifier<>(Order.ASC, new CaseBuilder()
                        .when(terminal).then(Expressions.nullExpression(LocalDateTime.class))
                        .otherwise(groupBuy.endAt)));
                orders.add(new OrderSpecifier<>(Order.DESC, new CaseBuilder()
                        .when(terminal).then(groupBuy.endedAt)
                        .otherwise(Expressions.nullExpression(LocalDateTime.class))));
            }
        }
        orders.add(groupBuy.id.desc());
        return orders.toArray(OrderSpecifier[]::new);
    }
}
