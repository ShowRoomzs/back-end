package showroomz.domain.review.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import showroomz.api.common.review.dto.ProductReviewSortType;
import showroomz.domain.review.entity.Review;

import java.util.List;

import static showroomz.domain.order.entity.QOrderProduct.orderProduct;
import static showroomz.domain.product.entity.QProduct.product;
import static showroomz.domain.product.entity.QProductOption.productOption;
import static showroomz.domain.product.entity.QProductVariant.productVariant;
import static showroomz.domain.review.entity.QReview.review;

@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Review> findAllByProductIdWithFilter(
            Long productId,
            List<Long> optionIds,
            ProductReviewSortType sortType,
            Pageable pageable
    ) {
        boolean hasOptionFilter = optionIds != null && !optionIds.isEmpty();

        JPAQuery<Review> query = queryFactory
                .selectFrom(review)
                .join(review.orderProduct, orderProduct).fetchJoin()
                .join(orderProduct.variant, productVariant).fetchJoin()
                .join(productVariant.product, product).fetchJoin()
                .join(review.user).fetchJoin();

        if (hasOptionFilter) {
            query.join(productVariant.options, productOption)
                    .where(productOption.optionId.in(optionIds));
        }

        query.where(product.productId.eq(productId))
                .distinct()
                .orderBy(getOrderSpecifiers(sortType))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());

        List<Review> content = query.fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(review.countDistinct())
                .from(review)
                .join(review.orderProduct, orderProduct)
                .join(orderProduct.variant, productVariant)
                .join(productVariant.product, product)
                .where(product.productId.eq(productId));

        if (hasOptionFilter) {
            countQuery
                    .join(productVariant.options, productOption)
                    .where(productOption.optionId.in(optionIds));
        }

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(ProductReviewSortType sortType) {
        if (sortType != null && sortType == ProductReviewSortType.RECOMMENDED) {
            return new OrderSpecifier<?>[]{
                    review.likeCount.desc(),
                    review.createdAt.desc()
            };
        }
        // LATEST or default
        return new OrderSpecifier<?>[]{
                review.createdAt.desc()
        };
    }
}
