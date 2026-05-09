package showroomz.domain.coupon.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.type.CouponStatus;
import showroomz.domain.coupon.type.TargetAudience;

import java.time.LocalDateTime;
import java.util.List;

import static showroomz.domain.coupon.entity.QCoupon.coupon;

@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Coupon> searchAdminCoupons(String searchType, String keyword, TargetAudience targetAudience,
                                           CouponStatus status, LocalDateTime dateFrom, LocalDateTime dateTo,
                                           Pageable pageable) {
        List<Coupon> content = queryFactory
                .selectFrom(coupon)
                .where(
                        searchCondition(searchType, keyword),
                        targetAudienceCondition(targetAudience),
                        statusCondition(status),
                        dateFromCondition(dateFrom),
                        dateToCondition(dateTo)
                )
                .orderBy(coupon.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(coupon.count())
                .from(coupon)
                .where(
                        searchCondition(searchType, keyword),
                        targetAudienceCondition(targetAudience),
                        statusCondition(status),
                        dateFromCondition(dateFrom),
                        dateToCondition(dateTo)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression searchCondition(String searchType, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalized = keyword.trim();
        if ("couponIssueNumber".equalsIgnoreCase(searchType)) {
            return coupon.couponIssueNumber.containsIgnoreCase(normalized);
        }
        return coupon.name.containsIgnoreCase(normalized);
    }

    private BooleanExpression targetAudienceCondition(TargetAudience targetAudience) {
        return targetAudience == null ? null : coupon.targetAudience.eq(targetAudience);
    }

    private BooleanExpression statusCondition(CouponStatus status) {
        return status == null ? null : coupon.status.eq(status);
    }

    private BooleanExpression dateFromCondition(LocalDateTime dateFrom) {
        return dateFrom == null ? null : coupon.issueStartDate.goe(dateFrom);
    }

    private BooleanExpression dateToCondition(LocalDateTime dateTo) {
        return dateTo == null ? null : coupon.issueEndDate.loe(dateTo);
    }
}
