package showroomz.domain.order.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.entity.QOrder;
import showroomz.domain.order.entity.QOrderDeliveryGroup;
import showroomz.domain.order.entity.QOrderRefundTask;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.domain.payment.entity.QPayment;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class OrderRefundTaskRepositoryImpl implements OrderRefundTaskRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<OrderRefundTask> searchForAdmin(AdminRefundSearchCondition condition, Pageable pageable) {
        if (condition.noMatch() || (condition.sources() != null && condition.sources().isEmpty())) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        QOrderRefundTask t = QOrderRefundTask.orderRefundTask;
        QOrder o = QOrder.order;
        QOrderDeliveryGroup g = QOrderDeliveryGroup.orderDeliveryGroup;
        BooleanBuilder where = where(condition, t, o);

        List<OrderRefundTask> content = queryFactory.selectFrom(t)
                .join(t.order, o).fetchJoin()
                .join(t.deliveryGroup, g).fetchJoin()
                .where(where)
                .orderBy(order(condition, t))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = queryFactory.select(t.count()).from(t).join(t.order, o).where(where).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<Stat> summarizeForAdmin(LocalDateTime doneFrom) {
        QOrderRefundTask t = QOrderRefundTask.orderRefundTask;
        List<Tuple> rows = queryFactory.select(t.status, t.origin, t.count(), t.refundAmount.sum())
                .from(t)
                .where(t.status.ne(RefundTaskStatus.DONE).or(t.executedAt.goe(doneFrom)))
                .groupBy(t.status, t.origin)
                .fetch();
        // SUM(int) 의 실행 타입은 구현마다 다르다(Long · Integer) — Number 로 받는다.
        return rows.stream().map(row -> new Stat(row.get(t.status), row.get(t.origin),
                number(row.get(2, Number.class)), number(row.get(3, Number.class)))).toList();
    }

    @Override
    public LocalDateTime oldestFailedCreatedAt() {
        QOrderRefundTask t = QOrderRefundTask.orderRefundTask;
        return queryFactory.select(t.createdAt.min()).from(t).where(t.status.eq(RefundTaskStatus.FAILED)).fetchOne();
    }

    private static BooleanBuilder where(AdminRefundSearchCondition condition, QOrderRefundTask t, QOrder o) {
        BooleanBuilder where = new BooleanBuilder().and(t.status.in(condition.statuses()));
        if (condition.origin() != null) {
            where.and(t.origin.eq(condition.origin()));
        }
        if (condition.executedFrom() != null) {
            where.and(t.executedAt.goe(condition.executedFrom()));
        }
        if (condition.sources() != null) {
            where.and(t.source.in(condition.sources()));
        }
        BooleanBuilder keyword = new BooleanBuilder();
        if (condition.refundTaskId() != null) {
            keyword.or(t.id.eq(condition.refundTaskId()));
        }
        String text = condition.keyword();
        if (text != null) {
            QPayment p = QPayment.payment;
            keyword.or(o.orderNumber.eq(text))
                    .or(t.paymentId.eq(text))
                    .or(t.paymentId.in(JPAExpressions.select(p.paymentId).from(p).where(p.pgTxId.eq(text))));
        }
        if (keyword.hasValue()) {
            where.and(keyword);
        }
        return where;
    }

    private static OrderSpecifier<?>[] order(AdminRefundSearchCondition condition, QOrderRefundTask t) {
        if (condition.amountDesc()) {
            return new OrderSpecifier<?>[]{t.refundAmount.desc(), t.id.desc()};
        }
        DateTimePath<LocalDateTime> column = switch (condition.dateColumn()) {
            case CREATED -> t.createdAt;
            case MODIFIED -> t.modifiedAt;
            case EXECUTED -> t.executedAt;
        };
        return new OrderSpecifier<?>[]{column.desc().nullsLast(), t.id.desc()};
    }

    private static long number(Number value) {
        return value == null ? 0 : value.longValue();
    }
}
