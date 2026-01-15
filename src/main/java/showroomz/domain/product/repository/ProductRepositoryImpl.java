package showroomz.domain.product.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.QProduct;
import showroomz.domain.product.entity.QProductOption;
import showroomz.domain.product.entity.QProductOptionGroup;
import showroomz.domain.product.type.ProductGender;

import java.util.List;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> searchProductsForUser(
            String keyword,
            List<Long> categoryIds,
            Long marketId,
            ProductGender gender,
            String color,
            Integer minPrice,
            Integer maxPrice,
            String sortType,
            Pageable pageable
    ) {
        QProduct product = QProduct.product;
        QProductOptionGroup optionGroup = QProductOptionGroup.productOptionGroup;
        QProductOption option = QProductOption.productOption;

        BooleanBuilder where = new BooleanBuilder();
        where.and(product.isDisplay.isTrue());

        if (keyword != null && !keyword.isBlank()) {
            where.and(
                    product.name.containsIgnoreCase(keyword)
                            .or(product.market.marketName.containsIgnoreCase(keyword))
            );
        }

        if (categoryIds != null && !categoryIds.isEmpty()) {
            where.and(product.category.categoryId.in(categoryIds));
        }

        if (marketId != null) {
            where.and(product.market.id.eq(marketId));
        }

        if (gender != null) {
            where.and(product.gender.eq(gender));
        }

        if (minPrice != null) {
            where.and(product.salePrice.goe(minPrice));
        }

        if (maxPrice != null) {
            where.and(product.salePrice.loe(maxPrice));
        }

        JPAQuery<Product> query = queryFactory.selectFrom(product);
        if (color != null && !color.isBlank()) {
            query.leftJoin(product.optionGroups, optionGroup)
                    .leftJoin(optionGroup.options, option);
            where.and(optionGroup.name.eq("색상").and(option.name.eq(color)));
        }

        query.where(where)
                .distinct()
                .orderBy(getOrderSpecifiers(sortType, product))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());

        List<Product> content = query.fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(product.productId.countDistinct())
                .from(product);
        if (color != null && !color.isBlank()) {
            countQuery.leftJoin(product.optionGroups, optionGroup)
                    .leftJoin(optionGroup.options, option);
        }
        countQuery.where(where);

        Long total = countQuery.fetchOne();
        long totalElements = total != null ? total : 0L;

        return new PageImpl<>(content, pageable, totalElements);
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(String sortType, QProduct product) {
        if (sortType == null || sortType.isBlank() || "recommend".equals(sortType)) {
            return new OrderSpecifier<?>[]{
                    product.isRecommended.desc(),
                    product.createdAt.desc()
            };
        }

        return switch (sortType) {
            case "popular" -> new OrderSpecifier<?>[]{
                    product.createdAt.desc()
            };
            case "newest" -> new OrderSpecifier<?>[]{
                    product.createdAt.desc()
            };
            case "price_asc" -> new OrderSpecifier<?>[]{
                    product.salePrice.asc()
            };
            case "price_desc" -> new OrderSpecifier<?>[]{
                    product.salePrice.desc()
            };
            default -> new OrderSpecifier<?>[]{
                    product.createdAt.desc()
            };
        };
    }
}
