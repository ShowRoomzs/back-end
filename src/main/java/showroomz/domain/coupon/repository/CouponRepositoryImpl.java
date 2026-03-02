package showroomz.domain.coupon.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.type.CouponStatus;

import java.time.LocalDateTime;
import java.util.List;

import static showroomz.domain.coupon.entity.QCoupon.coupon;

@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Coupon> findAllWithStatusFilter(CouponStatus status, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();

        List<Coupon> content = queryFactory
                .selectFrom(coupon)
                .where(statusCondition(status, now))
                .orderBy(coupon.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(coupon.count())
                .from(coupon)
                .where(statusCondition(status, now));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * status별 동적 조건
     * - ACTIVE: startAt <= now AND endAt >= now
     * - EXPIRED: endAt < now
     * - SCHEDULED: startAt > now
     * - null: 전체 조회
     */
    private BooleanExpression statusCondition(CouponStatus status, LocalDateTime now) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case ACTIVE -> coupon.startAt.loe(now).and(coupon.endAt.goe(now));
            case EXPIRED -> coupon.endAt.lt(now);
            case SCHEDULED -> coupon.startAt.gt(now);
        };
    }
}
