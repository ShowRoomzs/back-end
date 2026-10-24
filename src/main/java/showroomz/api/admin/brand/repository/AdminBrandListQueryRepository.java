package showroomz.api.admin.brand.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import showroomz.api.admin.common.AdminMemberListKeyword;
import showroomz.api.admin.common.AdminMemberListSort;
import showroomz.api.admin.common.AdminMemberListTab;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.entity.QMarket;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.entity.QBrandWithdrawalRequest;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.seller.entity.QSeller;
import showroomz.domain.member.seller.entity.Seller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 어드민 브랜드 회원 목록(ui-admin-14 rev.7 · 55 계획서 M2) — 입점이 끝난 브랜드(셀러 APPROVED · SELLER)만. 탈퇴 승인 대기는 회원 상태가 아니라
 * 열린 신청 행이라 LEFT JOIN 으로 붙인다 — 브랜드당 열린 신청은 많아야 1건({@code uk_brand_withdrawal_pending})이라 행이 늘지 않는다.
 *
 * <p>탭 건수는 서로 겹치지 않는다 — 대기 행은 활성 · 정지 탭에서 빠진다. 전체 탭은 대기 행을 맨 위에 고정한다(시안 「승인 대기 상단 고정」).
 */
@Repository
@RequiredArgsConstructor
public class AdminBrandListQueryRepository {

    private static final QMarket market = QMarket.market;
    private static final QSeller seller = QSeller.seller;
    private static final QBrandWithdrawalRequest pending = QBrandWithdrawalRequest.brandWithdrawalRequest;

    private final JPAQueryFactory queryFactory;

    /** 목록 한 행 — 마켓 · 셀러 · 열린 탈퇴 신청 시각(없으면 null). */
    public record Row(Market market, Seller seller, LocalDateTime withdrawalRequestedAt) {
    }

    public Page<Row> search(AdminMemberListTab tab, AdminMemberListKeyword keyword, AdminMemberListSort sort,
                            Pageable pageable) {
        BooleanBuilder where = base(keyword).and(tabCondition(tab));
        List<Tuple> tuples = joined(queryFactory.select(market, seller, pending.requestedAt))
                .where(where)
                .orderBy(order(tab, sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        List<Row> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            rows.add(new Row(t.get(market), t.get(seller), t.get(pending.requestedAt)));
        }
        Long total = joined(queryFactory.select(market.count())).where(where).fetchOne();
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    /** 탭별 건수 — 상태 조건만 빼고 검색어는 그대로(D13-2). 대기 행은 대기에만 센다. */
    public Map<AdminMemberListTab, Long> countByTab(AdminMemberListKeyword keyword) {
        Map<AdminMemberListTab, Long> counts = new EnumMap<>(AdminMemberListTab.class);
        for (AdminMemberListTab tab : AdminMemberListTab.values()) {
            counts.put(tab, 0L);
        }
        List<Tuple> byStatus = joined(queryFactory.select(market.status, market.count()))
                .where(base(keyword).and(pending.id.isNull()))
                .groupBy(market.status)
                .fetch();
        for (Tuple t : byStatus) {
            MarketStatus status = t.get(market.status);
            long count = t.get(market.count()) == null ? 0 : t.get(market.count());
            counts.merge(tabOf(status), count, Long::sum);
        }
        Long waiting = joined(queryFactory.select(market.count())).where(base(keyword).and(pending.id.isNotNull()))
                .fetchOne();
        counts.put(AdminMemberListTab.WITHDRAWAL_PENDING, waiting == null ? 0 : waiting);
        counts.put(AdminMemberListTab.ALL, counts.get(AdminMemberListTab.ACTIVE) + counts.get(AdminMemberListTab.SUSPENDED)
                + counts.get(AdminMemberListTab.WITHDRAWAL_PENDING) + counts.get(AdminMemberListTab.WITHDRAWN));
        return counts;
    }

    /** 대기 탭 요약 「최장 대기 N일」의 재료 — 가장 오래된 열린 신청 시각. */
    public LocalDateTime oldestPendingRequestedAt(AdminMemberListKeyword keyword) {
        return joined(queryFactory.select(pending.requestedAt.min())).where(base(keyword).and(pending.id.isNotNull()))
                .fetchOne();
    }

    // ------------------------------------------------------------------

    private <T> JPAQuery<T> joined(JPAQuery<T> query) {
        return query.from(market)
                .join(market.seller, seller)
                .leftJoin(pending).on(pending.marketId.eq(market.id).and(pending.status.eq(MemberWithdrawalStatus.PENDING)));
    }

    private static BooleanBuilder base(AdminMemberListKeyword keyword) {
        BooleanBuilder where = new BooleanBuilder()
                .and(seller.status.eq(SellerStatus.APPROVED))
                .and(seller.roleType.eq(RoleType.SELLER));
        if (keyword.memberId() != null) {
            where.and(market.id.eq(keyword.memberId()));
        } else if (keyword.text() != null) {
            where.and(market.marketName.contains(keyword.text()));
        }
        return where;
    }

    private static BooleanExpression tabCondition(AdminMemberListTab tab) {
        return switch (tab) {
            case ALL -> null;
            case ACTIVE -> market.status.in(MarketStatus.ACTIVE, MarketStatus.DORMANT).and(pending.id.isNull());
            case SUSPENDED -> market.status.eq(MarketStatus.SUSPENDED).and(pending.id.isNull());
            case WITHDRAWAL_PENDING -> pending.id.isNotNull();
            case WITHDRAWN -> market.status.eq(MarketStatus.WITHDRAWN);
        };
    }

    private static AdminMemberListTab tabOf(MarketStatus status) {
        return switch (status) {
            case SUSPENDED -> AdminMemberListTab.SUSPENDED;
            case WITHDRAWN -> AdminMemberListTab.WITHDRAWN;
            default -> AdminMemberListTab.ACTIVE;
        };
    }

    private static OrderSpecifier<?>[] order(AdminMemberListTab tab, AdminMemberListSort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        if (tab == AdminMemberListTab.ALL) {
            orders.add(new CaseBuilder().when(pending.id.isNotNull()).then(0).otherwise(1).asc());
        }
        if (sort.byMemberNo()) {
            orders.add(market.id.asc());
        } else {
            orders.add(seller.createdAt.desc());
            orders.add(market.id.desc());
        }
        return orders.toArray(OrderSpecifier[]::new);
    }
}
