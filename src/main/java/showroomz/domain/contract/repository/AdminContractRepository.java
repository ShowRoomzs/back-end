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
    private static final QContractTerminationRequest t = QContractTerminationRequest.contractTerminationRequest;

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

    /**
     * 큐에서 가장 오래 기다린 건의 기준 시각(55 계획서 A1 — 어드민 홈 대기 행의 경과). 큐 정의는 {@link #filter}를 그대로 쓰고, 기준 열은
     * 큐별 고정 정렬({@link #search})과 같다 — 검토 요청 · 서명 확인 기준 · 서명 기한 · 미처리 재발송 요청 · 처리 대기 해지 요청. 비면 null.
     */
    public LocalDateTime oldestInQueue(AdminContractQueue queue, LocalDateTime now) {
        BooleanBuilder inQueue = filter(AdminContractTab.ALL, queue, now);
        return switch (queue) {
            case REVIEW -> queries.select(c.reviewRequestedAt.min()).from(c).where(inQueue).fetchOne();
            case CONCLUSION -> queries.select(c.signatureAsOf.min()).from(c).where(inQueue).fetchOne();
            case EXPIRY -> queries.select(c.signatureDeadlineAt.min()).from(c).where(inQueue).fetchOne();
            // 요청 행 기준 — 큐 조건의 EXISTS 가 r · t 별칭을 쓰므로 바깥 요청 행은 별칭을 따로 둔다.
            case RESEND -> {
                QContractResendRequest oldest = new QContractResendRequest("oldestResend");
                yield queries.select(oldest.requestedAt.min()).from(oldest)
                        .where(oldest.contract.id.in(JPAExpressions.select(c.id).from(c).where(inQueue)),
                                oldest.handledAt.isNull())
                        .fetchOne();
            }
            case TERMINATION -> {
                QContractTerminationRequest oldest = new QContractTerminationRequest("oldestTermination");
                yield queries.select(oldest.requestedAt.min()).from(oldest)
                        .where(oldest.contract.id.in(JPAExpressions.select(c.id).from(c).where(inQueue)),
                                oldest.status.eq(ContractTerminationRequestStatus.PENDING))
                        .fetchOne();
            }
        };
    }

    private BooleanBuilder filter(AdminContractTab tab, AdminContractQueue queue, LocalDateTime now) {
        BooleanBuilder where = new BooleanBuilder(c.status.ne(ContractStatus.DRAFT)).and(c.deletedAt.isNull());
        // Queue selection overrides the tab and sort, as each queue has its own status definition.
        if (queue != null) {
            switch (queue) {
                case REVIEW -> where.and(c.status.eq(ContractStatus.REVIEW_PENDING));
                case CONCLUSION -> where.and(c.status.eq(ContractStatus.CONCLUSION_PENDING));
                case EXPIRY -> where.and(c.status.eq(ContractStatus.SIGNING)).and(c.signatureDeadlineAt.lt(now));
                // 재발송 처리는 서명 진행 중에만 된다(handleResend) — 거절·만료·취소·서명 완료로 넘어간 계약의
                // 미처리 요청은 처리할 방법이 없으므로 큐에 남기지 않는다.
                case RESEND -> where.and(c.status.eq(ContractStatus.SIGNING)).and(JPAExpressions.selectOne().from(r)
                        .where(r.contract.eq(c), r.handledAt.isNull()).exists());
                // 해지 요청(1021 설계서 1-4) — 처리 대기 요청이 있는 계약. 오픈 · 종결로 요청이 닫히면 큐에서 빠진다.
                case TERMINATION -> where.and(JPAExpressions.selectOne().from(t)
                        .where(t.contract.eq(c), t.status.eq(ContractTerminationRequestStatus.PENDING)).exists());
            }
        } else if (tab == AdminContractTab.CLOSED) {
            where.and(c.status.in(ContractStatus.DECLINED, ContractStatus.EXPIRED, ContractStatus.CANCELED, ContractStatus.TERMINATED));
        } else if (tab != null && tab != AdminContractTab.ALL) {
            where.and(c.status.eq(ContractStatus.valueOf(tab.name())));
        }
        return where;
    }
}
