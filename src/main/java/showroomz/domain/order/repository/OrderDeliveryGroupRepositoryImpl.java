package showroomz.domain.order.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import showroomz.domain.contract.entity.QContract;
import showroomz.domain.groupbuy.entity.QGroupBuy;
import showroomz.domain.order.entity.QOrder;
import showroomz.domain.order.entity.QOrderCancelRequest;
import showroomz.domain.order.entity.QOrderDeliveryGroup;
import showroomz.domain.order.entity.QOrderProduct;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 파트너센터 주문 목록(34 설계서 4-1). 탭·조회 기준 5종·검색 4타입·정렬이 전부 동적이라 QueryDSL 로 간다.
 *
 * <p>취소 요청 오버레이는 상태가 아니라 PENDING 행의 존재다(설계서 0-2) — NEW·PREPARING 탭은 NOT EXISTS,
 * 취소 요청 탭은 EXISTS 가 상태 필터에 더해진다.
 */
@RequiredArgsConstructor
public class OrderDeliveryGroupRepositoryImpl implements OrderDeliveryGroupRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<SellerOrderRow> searchForSeller(SellerOrderSearchCondition condition, Pageable pageable) {
        QOrderDeliveryGroup g = QOrderDeliveryGroup.orderDeliveryGroup;
        QOrder o = QOrder.order;
        QGroupBuy gb = QGroupBuy.groupBuy;
        QContract c = QContract.contract;

        BooleanBuilder where = new BooleanBuilder()
                .and(g.market.id.eq(condition.marketId()))
                .and(o.status.eq(OrderStatus.PAID))
                .and(g.fulfillmentStatus.in(condition.tab().getStatuses()));
        applyPendingCancelFilter(where, condition, g);
        applyDateRange(where, condition, g, o);
        applySearch(where, condition, g, o);

        List<SellerOrderRow> content = queryFactory
                .select(Projections.constructor(SellerOrderRow.class, g, o.orderNumber, o.paidAt, o.recipientName, c.title))
                .from(g)
                .join(g.order, o)
                .leftJoin(g.groupBuy, gb)
                .leftJoin(gb.contract, c)
                .where(where)
                .orderBy(orderSpecifiers(condition, g, o))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(g.count()).from(g).join(g.order, o).where(where).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private void applyPendingCancelFilter(BooleanBuilder where, SellerOrderSearchCondition condition,
                                          QOrderDeliveryGroup g) {
        QOrderCancelRequest r = QOrderCancelRequest.orderCancelRequest;
        BooleanExpression pendingExists = JPAExpressions.selectOne().from(r)
                .where(r.deliveryGroup.eq(g).and(r.status.eq(CancelRequestStatus.PENDING)))
                .exists();
        switch (condition.tab().getPendingCancelFilter()) {
            case NONE -> where.and(pendingExists.not());
            case EXISTS -> where.and(pendingExists);
            case ANY -> { /* 전체·조회 탭 — 오버레이 무관 */ }
        }
    }

    private void applyDateRange(BooleanBuilder where, SellerOrderSearchCondition condition,
                                QOrderDeliveryGroup g, QOrder o) {
        DateTimePath<LocalDateTime> basis = switch (condition.dateBasis()) {
            case PAID -> o.paidAt;
            case PREPARE_STARTED -> g.prepareStartedAt;
            case SHIPPED -> g.shippedAt;
            case DELIVERED -> g.deliveredAt;
            case CONFIRMED -> g.confirmedAt;
        };
        if (condition.from() != null) {
            where.and(basis.goe(condition.from()));
        }
        if (condition.to() != null) {
            where.and(basis.loe(condition.to()));
        }
    }

    private void applySearch(BooleanBuilder where, SellerOrderSearchCondition condition,
                             QOrderDeliveryGroup g, QOrder o) {
        String keyword = condition.keyword() == null ? null : condition.keyword().trim();
        if (condition.searchType() == null || keyword == null || keyword.isEmpty()) {
            return;
        }
        switch (condition.searchType()) {
            case ORDER_NUMBER -> where.and(o.orderNumber.contains(keyword).or(g.subOrderNumber.contains(keyword)));
            case RECIPIENT_NAME -> where.and(o.recipientName.contains(keyword));
            case TRACKING_NUMBER -> where.and(g.trackingNumber.contains(keyword.replaceAll("[^0-9]", "")));
            case PRODUCT_NAME -> {
                QOrderProduct op = QOrderProduct.orderProduct;
                where.and(JPAExpressions.selectOne().from(op)
                        .where(op.deliveryGroup.eq(g).and(op.productName.contains(keyword)))
                        .exists());
            }
        }
    }

    private OrderSpecifier<?>[] orderSpecifiers(SellerOrderSearchCondition condition,
                                                QOrderDeliveryGroup g, QOrder o) {
        return switch (condition.sort()) {
            case OLDEST_FIRST -> new OrderSpecifier<?>[]{o.paidAt.asc(), g.id.asc()};
            case LATEST_FIRST -> new OrderSpecifier<?>[]{o.paidAt.desc(), g.id.desc()};
            case SHIP_DUE_ASC -> new OrderSpecifier<?>[]{g.shipDueAt.asc().nullsLast(), g.id.asc()};
        };
    }
}
