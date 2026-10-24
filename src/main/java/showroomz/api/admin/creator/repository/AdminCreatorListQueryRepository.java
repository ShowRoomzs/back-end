package showroomz.api.admin.creator.repository;

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
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.QCreator;
import showroomz.domain.member.creator.entity.QCreatorWithdrawalRequest;
import showroomz.domain.member.creator.type.CreatorStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 어드민 인플루언서 회원 목록(ui-admin-16 rev.7 · 55 계획서 M1) — 브랜드 목록({@code AdminBrandListQueryRepository})과 대칭이다. Creator 행은
 * 입점 승인 때만 생기므로 별도 모집단 조건이 없다. 열린 탈퇴 신청은 인플루언서당 1건({@code uk_creator_withdrawal_pending})이라 LEFT JOIN 이
 * 행을 늘리지 않는다.
 *
 * <p>검색은 {@code INF-} 회원번호 정확 일치 · 그 밖은 쇼룸명 또는 업무용 이메일 부분 일치(대소문자 무시 · D13-6).
 */
@Repository
@RequiredArgsConstructor
public class AdminCreatorListQueryRepository {

    private static final QCreator creator = QCreator.creator;
    private static final QCreatorWithdrawalRequest pending = QCreatorWithdrawalRequest.creatorWithdrawalRequest;

    private final JPAQueryFactory queryFactory;

    /** 목록 한 행 — 인플루언서 · 열린 탈퇴 신청 시각(없으면 null). */
    public record Row(Creator creator, LocalDateTime withdrawalRequestedAt) {
    }

    public Page<Row> search(AdminMemberListTab tab, AdminMemberListKeyword keyword, AdminMemberListSort sort,
                            Pageable pageable) {
        BooleanBuilder where = base(keyword).and(tabCondition(tab));
        List<Tuple> tuples = joined(queryFactory.select(creator, pending.requestedAt))
                .where(where)
                .orderBy(order(tab, sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        List<Row> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            rows.add(new Row(t.get(creator), t.get(pending.requestedAt)));
        }
        Long total = joined(queryFactory.select(creator.count())).where(where).fetchOne();
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    /** 탭별 건수 — 상태 조건만 빼고 검색어는 그대로(D13-2). 대기 행은 대기에만 센다. */
    public Map<AdminMemberListTab, Long> countByTab(AdminMemberListKeyword keyword) {
        Map<AdminMemberListTab, Long> counts = new EnumMap<>(AdminMemberListTab.class);
        for (AdminMemberListTab tab : AdminMemberListTab.values()) {
            counts.put(tab, 0L);
        }
        List<Tuple> byStatus = joined(queryFactory.select(creator.status, creator.count()))
                .where(base(keyword).and(pending.id.isNull()))
                .groupBy(creator.status)
                .fetch();
        for (Tuple t : byStatus) {
            CreatorStatus status = t.get(creator.status);
            long count = t.get(creator.count()) == null ? 0 : t.get(creator.count());
            counts.merge(tabOf(status), count, Long::sum);
        }
        Long waiting = joined(queryFactory.select(creator.count())).where(base(keyword).and(pending.id.isNotNull()))
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
        return query.from(creator)
                .leftJoin(pending).on(pending.creatorId.eq(creator.id).and(pending.status.eq(MemberWithdrawalStatus.PENDING)));
    }

    private static BooleanBuilder base(AdminMemberListKeyword keyword) {
        BooleanBuilder where = new BooleanBuilder();
        if (keyword.memberId() != null) {
            where.and(creator.id.eq(keyword.memberId()));
        } else if (keyword.text() != null) {
            where.and(creator.showroomName.containsIgnoreCase(keyword.text())
                    .or(creator.businessEmail.containsIgnoreCase(keyword.text())));
        }
        return where;
    }

    private static BooleanExpression tabCondition(AdminMemberListTab tab) {
        return switch (tab) {
            case ALL -> null;
            case ACTIVE -> creator.status.eq(CreatorStatus.ACTIVE).and(pending.id.isNull());
            case SUSPENDED -> creator.status.eq(CreatorStatus.SUSPENDED).and(pending.id.isNull());
            case WITHDRAWAL_PENDING -> pending.id.isNotNull();
            case WITHDRAWN -> creator.status.eq(CreatorStatus.WITHDRAWN);
        };
    }

    private static AdminMemberListTab tabOf(CreatorStatus status) {
        return switch (status) {
            case SUSPENDED -> AdminMemberListTab.SUSPENDED;
            case WITHDRAWN -> AdminMemberListTab.WITHDRAWN;
            case ACTIVE -> AdminMemberListTab.ACTIVE;
        };
    }

    private static OrderSpecifier<?>[] order(AdminMemberListTab tab, AdminMemberListSort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        if (tab == AdminMemberListTab.ALL) {
            orders.add(new CaseBuilder().when(pending.id.isNotNull()).then(0).otherwise(1).asc());
        }
        if (sort.byMemberNo()) {
            orders.add(creator.id.asc());
        } else {
            orders.add(creator.createdAt.desc());
            orders.add(creator.id.desc());
        }
        return orders.toArray(OrderSpecifier[]::new);
    }
}
