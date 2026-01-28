package showroomz.domain.wishlist.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import showroomz.domain.wishlist.entitiy.Wishlist;

import java.util.List;
import java.util.Objects;

import static showroomz.domain.category.entity.QCategory.category;
import static showroomz.domain.market.entity.QMarket.market;
import static showroomz.domain.product.entity.QProduct.product;
import static showroomz.domain.product.entity.QProductImage.productImage;
import static showroomz.domain.wishlist.entitiy.QWishlist.wishlist;

@RequiredArgsConstructor
public class WishlistRepositoryImpl implements WishlistRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Wishlist> findByUserWithProduct(Long userId, Long categoryId, Pageable pageable) {
        List<Wishlist> content = Objects.requireNonNullElse(
                queryFactory
                .selectFrom(wishlist)
                .join(wishlist.product, product).fetchJoin()
                .leftJoin(product.productImages, productImage).fetchJoin()
                .leftJoin(product.category, category).fetchJoin()
                .leftJoin(product.market, market).fetchJoin()
                .where(
                        wishlist.user.id.eq(userId),
                        eqCategoryId(categoryId)
                )
                .orderBy(wishlist.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .distinct()
                .fetch(),
                List.of()
        );

        JPAQuery<Long> countQuery = queryFactory
                .select(wishlist.count())
                .from(wishlist)
                .join(wishlist.product, product)
                .where(
                        wishlist.user.id.eq(userId),
                        eqCategoryId(categoryId)
                );

        @SuppressWarnings("null")
        Page<Wishlist> page = PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
        return page;
    }

    private BooleanExpression eqCategoryId(Long categoryId) {
        return categoryId != null ? product.category.categoryId.eq(categoryId) : null;
    }
}
