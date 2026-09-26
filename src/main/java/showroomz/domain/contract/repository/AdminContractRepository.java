package showroomz.domain.contract.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import showroomz.domain.contract.entity.*;
import showroomz.domain.contract.type.*;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminContractRepository {
    private final JPAQueryFactory queries;
    private static final QContract c = QContract.contract;
    private static final QContractResendRequest r = QContractResendRequest.contractResendRequest;

    public Page<Contract> search(AdminContractTab tab, AdminContractQueue queue, String keyword,
                                 AdminContractSort sort, Pageable page, LocalDateTime now) {
        BooleanBuilder where = filter(tab, queue, now);
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            where.and(c.title.containsIgnoreCase(term).or(c.contractNumber.containsIgnoreCase(term))
                    .or(c.market.marketName.containsIgnoreCase(term)).or(c.creator.showroomName.containsIgnoreCase(term)));
        }
        var query = queries.selectFrom(c).leftJoin(c.market).fetchJoin().leftJoin(c.creator).fetchJoin().where(where);
        if (queue == AdminContractQueue.RESEND) {
            query.leftJoin(r).on(r.contract.eq(c).and(r.handledAt.isNull()))
                    .groupBy(c.id).orderBy(r.requestedAt.min().asc(), c.id.asc());
        } else {
            OrderSpecifier<?> order = queue == AdminContractQueue.CONCLUSION ? c.signatureAsOf.asc()
                    : queue == AdminContractQueue.EXPIRY ? c.signatureDeadlineAt.asc()
                    : queue == AdminContractQueue.REVIEW ? c.reviewRequestedAt.asc()
                    : sort == AdminContractSort.CREATED_DESC ? c.createdAt.desc()
                    : sort == AdminContractSort.START_AT_ASC ? c.groupBuyStartAt.asc().nullsLast()
                    : c.reviewRequestedAt.asc();
            query.orderBy(order, c.id.asc());
        }
        List<Contract> rows = query.offset(page.getOffset()).limit(page.getPageSize()).fetch();
        Long count = queries.select(c.count()).from(c).leftJoin(c.market).leftJoin(c.creator).where(where).fetchOne();
        return new PageImpl<>(rows, page, count == null ? 0 : count);
    }

    public long countQueue(AdminContractQueue queue, LocalDateTime now) {
        Long count = queries.select(c.count()).from(c).where(filter(AdminContractTab.ALL, queue, now)).fetchOne();
        return count == null ? 0 : count;
    }

    private BooleanBuilder filter(AdminContractTab tab, AdminContractQueue queue, LocalDateTime now) {
        BooleanBuilder where = new BooleanBuilder(c.status.ne(ContractStatus.DRAFT)).and(c.deletedAt.isNull());
        // Queue selection overrides the tab and sort, as each queue has its own status definition.
        if (queue != null) {
            switch (queue) {
                case REVIEW -> where.and(c.status.eq(ContractStatus.REVIEW_PENDING));
                case CONCLUSION -> where.and(c.status.eq(ContractStatus.CONCLUSION_PENDING));
                case EXPIRY -> where.and(c.status.eq(ContractStatus.SIGNING)).and(c.signatureDeadlineAt.lt(now));
                case RESEND -> where.and(JPAExpressions.selectOne().from(r)
                        .where(r.contract.eq(c), r.handledAt.isNull()).exists());
            }
        } else if (tab == AdminContractTab.CLOSED) {
            where.and(c.status.in(ContractStatus.DECLINED, ContractStatus.EXPIRED, ContractStatus.CANCELED));
        } else if (tab != null && tab != AdminContractTab.ALL) {
            where.and(c.status.eq(ContractStatus.valueOf(tab.name())));
        }
        return where;
    }
}
