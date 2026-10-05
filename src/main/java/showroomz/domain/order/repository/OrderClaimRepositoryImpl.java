package showroomz.domain.order.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.QOrder;
import showroomz.domain.order.entity.QOrderClaim;
import showroomz.domain.order.entity.QOrderClaimCollection;
import showroomz.domain.order.entity.QOrderDeliveryGroup;
import showroomz.domain.order.entity.QOrderProduct;

import java.util.List;

/**
 * 파트너센터 반품·교환 목록(35 설계서 4-1). 탭 · 유형 · 사유 · 기간 · 키워드가 동적이라 QueryDSL 로 간다.
 *
 * <p>행은 클레임이고 묶음(박스)은 FE 가 그린다 — 같은 묶음이 인접하도록 정렬의 2차 키가 요청 id, 3차 키가 클레임 id 다.
 */
@RequiredArgsConstructor
public class OrderClaimRepositoryImpl implements OrderClaimRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<OrderClaim> searchForSeller(SellerClaimSearchCondition condition, Pageable pageable) {
        QOrderClaim c = QOrderClaim.orderClaim;
        QOrderClaimCollection k = QOrderClaimCollection.orderClaimCollection;
        QOrderDeliveryGroup g = QOrderDeliveryGroup.orderDeliveryGroup;
        QOrder o = QOrder.order;
        QOrderProduct p = QOrderProduct.orderProduct;

        BooleanBuilder where = new BooleanBuilder()
                .and(c.marketId.eq(condition.marketId()))
                .and(c.status.in(condition.tab().getStatuses()))
                .and(c.requestedAt.goe(condition.from()))
                .and(c.requestedAt.loe(condition.to()));
        if (condition.types() != null && !condition.types().isEmpty()) {
            where.and(c.type.in(condition.types()));
        }
        if (condition.reason() != null) {
            where.and(c.reasonCode.eq(condition.reason()));
        }
        if (condition.claimId() != null) {
            where.and(c.id.eq(condition.claimId()));
        }
        if (condition.orderNumber() != null) {
            where.and(o.orderNumber.eq(condition.orderNumber()));
        }

        List<OrderClaim> content = queryFactory.selectFrom(c)
                .join(c.collection, k).fetchJoin()
                .join(c.deliveryGroup, g).fetchJoin()
                .join(g.order, o).fetchJoin()
                .join(c.orderProduct, p).fetchJoin()
                .where(where)
                .orderBy(primaryOrder(condition, c, k), k.id.asc(), c.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(c.count()).from(c)
                .join(c.deliveryGroup, g).join(g.order, o)
                .where(where).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    /** 탭의 기본 정렬 — 작업 탭은 오래된 것부터, 조회 탭은 최신부터. */
    private OrderSpecifier<?> primaryOrder(SellerClaimSearchCondition condition, QOrderClaim c,
                                           QOrderClaimCollection k) {
        return switch (condition.tab()) {
            case ALL -> c.requestedAt.desc();
            case COLLECT_WAIT -> c.requestedAt.asc();
            case COLLECTING, RESHIP -> c.stageEnteredAt.asc();
            // 도착 순 — 입고 확인을 눌러도 순서가 뒤로 밀리지 않는다. 추적이 꺼진 기간에는 도착 시각이 없어 입고 확인 시각.
            case INSPECTION -> k.arrivedAt.coalesce(c.receivedAt, c.stageEnteredAt).asc();
            case REJECT_HOLD -> c.rejectedAt.asc();
            case DONE -> c.completedAt.coalesce(c.stageEnteredAt).desc();
        };
    }
}
