package showroomz.domain.product.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import showroomz.domain.market.entity.QMarket;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.QProduct;
import showroomz.domain.product.entity.QProductOption;
import showroomz.domain.product.entity.QProductOptionGroup;
import showroomz.domain.product.entity.QProductVariant;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGender;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.domain.product.type.ProductListSortType;
import showroomz.domain.wishlist.entitiy.QWishlist;

import java.util.List;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> searchProductsForUser(
            String keyword,
            List<Long> categoryIds,
            Long marketId,
            List<ProductFilterCriteria> filters,
            String sortType,
            Pageable pageable
    ) {
        QProduct product = QProduct.product;
        QProductOptionGroup optionGroup = QProductOptionGroup.productOptionGroup;
        QProductOption option = QProductOption.productOption;

        BooleanBuilder where = new BooleanBuilder();
        where.and(product.displayStatus.eq(ProductDisplayStatus.DISPLAY));

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

        JPAQuery<Product> query = queryFactory.selectFrom(product);
        boolean needsColorJoin = filters != null && filters.stream()
                .anyMatch(criteria -> "color".equalsIgnoreCase(criteria.key()));
        if (needsColorJoin) {
            query.leftJoin(product.optionGroups, optionGroup)
                    .leftJoin(optionGroup.options, option);
        }

        if (filters != null) {
            for (ProductFilterCriteria criteria : filters) {
                applyFilter(criteria, product, optionGroup, option, where);
            }
        }

        query.where(where)
                .distinct()
                .orderBy(getOrderSpecifiers(sortType, product))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());

        @SuppressWarnings("null")
        List<Product> content = java.util.Objects.requireNonNullElse(query.fetch(), List.of());

        JPAQuery<Long> countQuery = queryFactory
                .select(product.productId.countDistinct())
                .from(product);
        if (needsColorJoin) {
            countQuery.leftJoin(product.optionGroups, optionGroup)
                    .leftJoin(optionGroup.options, option);
        }
        countQuery.where(where);

        Long total = countQuery.fetchOne();
        long totalElements = total != null ? total : 0L;

        @SuppressWarnings("null")
        PageImpl<Product> page = new PageImpl<>(content, pageable, totalElements);
        return page;
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(String sortType, QProduct product) {
        if (sortType == null || sortType.isBlank() || "RECOMMEND".equals(sortType)) {
            return new OrderSpecifier<?>[]{
                    product.isRecommended.desc(),
                    product.createdAt.desc()
            };
        }

        return switch (sortType) {
            case "POPULAR" -> new OrderSpecifier<?>[]{
                    product.createdAt.desc()
            };
            case "NEWEST" -> new OrderSpecifier<?>[]{
                    product.createdAt.desc()
            };
            case "PRICE_ASC" -> new OrderSpecifier<?>[]{
                    product.salePrice.asc()
            };
            case "PRICE_DESC" -> new OrderSpecifier<?>[]{
                    product.salePrice.desc()
            };
            default -> new OrderSpecifier<?>[]{
                    product.createdAt.desc()
            };
        };
    }

    private void applyFilter(
            ProductFilterCriteria criteria,
            QProduct product,
            QProductOptionGroup optionGroup,
            QProductOption option,
            BooleanBuilder where
    ) {
        if (criteria == null || criteria.key() == null) {
            return;
        }
        String key = criteria.key().toLowerCase();

        switch (key) {
            case "gender" -> applyGenderFilter(criteria, product, where);
            case "color" -> applyColorFilter(criteria, optionGroup, option, where);
            case "price" -> applyPriceFilter(criteria, product, where);
            default -> {
                // no-op for unknown filter keys
            }
        }
    }

    private void applyGenderFilter(ProductFilterCriteria criteria, QProduct product, BooleanBuilder where) {
        if (criteria.values() == null || criteria.values().isEmpty()) {
            return;
        }
        BooleanBuilder genderBuilder = new BooleanBuilder();
        for (String value : criteria.values()) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                ProductGender gender = ProductGender.valueOf(value.trim().toUpperCase());
                if (criteria.condition() == showroomz.domain.filter.type.FilterCondition.AND) {
                    genderBuilder.and(product.gender.eq(gender));
                } else {
                    genderBuilder.or(product.gender.eq(gender));
                }
            } catch (IllegalArgumentException ignored) {
                // ignore invalid value
            }
        }
        if (genderBuilder.hasValue()) {
            where.and(genderBuilder);
        }
    }

    private void applyColorFilter(
            ProductFilterCriteria criteria,
            QProductOptionGroup optionGroup,
            QProductOption option,
            BooleanBuilder where
    ) {
        if (criteria.values() == null || criteria.values().isEmpty()) {
            return;
        }
        BooleanBuilder colorBuilder = new BooleanBuilder();
        for (String value : criteria.values()) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (criteria.condition() == showroomz.domain.filter.type.FilterCondition.AND) {
                colorBuilder.and(optionGroup.name.eq("색상").and(option.name.eq(value)));
            } else {
                colorBuilder.or(optionGroup.name.eq("색상").and(option.name.eq(value)));
            }
        }
        if (colorBuilder.hasValue()) {
            where.and(colorBuilder);
        }
    }

    private void applyPriceFilter(ProductFilterCriteria criteria, QProduct product, BooleanBuilder where) {
        if (criteria.minValue() != null) {
            where.and(product.salePrice.goe(criteria.minValue()));
        }
        if (criteria.maxValue() != null) {
            where.and(product.salePrice.loe(criteria.maxValue()));
        }
    }

    @Override
    public List<Product> findPopularProductsByMarketId(Long marketId, int limit) {
        if (marketId == null || limit <= 0) {
            return List.of();
        }
        QProduct product = QProduct.product;
        QWishlist wishlist = QWishlist.wishlist;

        return queryFactory
                .selectFrom(product)
                .leftJoin(product.market).fetchJoin()
                .leftJoin(product.category).fetchJoin()
                .leftJoin(wishlist).on(wishlist.product.eq(product))
                .where(
                        product.market.id.eq(marketId),
                        product.displayStatus.eq(ProductDisplayStatus.DISPLAY)
                )
                .groupBy(product)
                .orderBy(wishlist.count().desc(), product.createdAt.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public Page<Product> searchSellerProducts(
            Long marketId,
            ProductDisplayStatus displayStatus,
            ProductGroupBuyStatus groupBuyStatus,
            String keyword,
            ProductListSortType sortType,
            Pageable pageable
    ) {
        QProduct product = QProduct.product;
        QProductVariant variant = QProductVariant.productVariant;

        BooleanBuilder where = buildSellerProductWhere(product, marketId, displayStatus, groupBuyStatus, keyword);

        Expression<Integer> totalStock = JPAExpressions
                .select(variant.stock.sum().coalesce(0))
                .from(variant)
                .where(variant.product.eq(product));

        ProductListSortType resolvedSort = sortType != null ? sortType : ProductListSortType.CREATED_AT;
        OrderSpecifier<?> primaryOrder = switch (resolvedSort) {
            case MODIFIED_AT -> product.modifiedAt.desc().nullsLast();
            case STOCK_ASC -> new OrderSpecifier<>(Order.ASC, totalStock);
            case CREATED_AT -> product.createdAt.desc();
        };

        List<Product> content = queryFactory
                .selectFrom(product)
                .where(where)
                .orderBy(primaryOrder, product.productId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Object[]> countSellerProductsByDisplayStatus(
            Long marketId,
            ProductGroupBuyStatus groupBuyStatus,
            String keyword
    ) {
        QProduct product = QProduct.product;
        // 진열상태 필터는 미반영
        BooleanBuilder where = buildSellerProductWhere(product, marketId, null, groupBuyStatus, keyword);

        return queryFactory
                .select(product.displayStatus, product.count())
                .from(product)
                .where(where)
                .groupBy(product.displayStatus)
                .fetch()
                .stream()
                .map(tuple -> new Object[]{tuple.get(product.displayStatus), tuple.get(product.count())})
                .toList();
    }

    @Override
    public List<Object[]> countSellerProductsByGroupBuyStatus(
            Long marketId,
            ProductDisplayStatus displayStatus,
            String keyword
    ) {
        QProduct product = QProduct.product;
        // 공구상태 필터는 미반영
        BooleanBuilder where = buildSellerProductWhere(product, marketId, displayStatus, null, keyword);

        return queryFactory
                .select(product.groupBuyStatus, product.count())
                .from(product)
                .where(where)
                .groupBy(product.groupBuyStatus)
                .fetch()
                .stream()
                .map(tuple -> new Object[]{tuple.get(product.groupBuyStatus), tuple.get(product.count())})
                .toList();
    }

    private BooleanBuilder buildSellerProductWhere(
            QProduct product,
            Long marketId,
            ProductDisplayStatus displayStatus,
            ProductGroupBuyStatus groupBuyStatus,
            String keyword
    ) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(product.market.id.eq(marketId));

        if (displayStatus != null) {
            where.and(product.displayStatus.eq(displayStatus));
        }

        if (groupBuyStatus != null) {
            where.and(product.groupBuyStatus.eq(groupBuyStatus));
        }

        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            where.and(
                    product.name.containsIgnoreCase(k)
                            .or(product.sellerProductCode.containsIgnoreCase(k))
            );
        }

        return where;
    }

    @Override
    public Page<Product> searchAdminProducts(
            ProductDisplayStatus displayStatus,
            ProductGroupBuyStatus groupBuyStatus,
            String keyword,
            ProductListSortType sortType,
            Pageable pageable
    ) {
        QProduct product = QProduct.product;
        QMarket market = QMarket.market;
        QProductVariant variant = QProductVariant.productVariant;

        BooleanBuilder where = buildAdminProductWhere(product, market, displayStatus, groupBuyStatus, keyword);

        Expression<Integer> totalStock = JPAExpressions
                .select(variant.stock.sum().coalesce(0))
                .from(variant)
                .where(variant.product.eq(product));

        ProductListSortType resolvedSort = sortType != null ? sortType : ProductListSortType.CREATED_AT;
        OrderSpecifier<?> primaryOrder = switch (resolvedSort) {
            case MODIFIED_AT -> product.modifiedAt.desc().nullsLast();
            case STOCK_ASC -> new OrderSpecifier<>(Order.ASC, totalStock);
            case CREATED_AT -> product.createdAt.desc();
        };

        List<Product> content = queryFactory
                .selectFrom(product)
                .leftJoin(product.market, market).fetchJoin()
                .where(where)
                .orderBy(primaryOrder, product.productId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .leftJoin(product.market, market)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Object[]> countAdminProductsByDisplayStatus(
            ProductGroupBuyStatus groupBuyStatus,
            String keyword
    ) {
        QProduct product = QProduct.product;
        QMarket market = QMarket.market;
        BooleanBuilder where = buildAdminProductWhere(product, market, null, groupBuyStatus, keyword);

        return queryFactory
                .select(product.displayStatus, product.count())
                .from(product)
                .leftJoin(product.market, market)
                .where(where)
                .groupBy(product.displayStatus)
                .fetch()
                .stream()
                .map(tuple -> new Object[]{tuple.get(product.displayStatus), tuple.get(product.count())})
                .toList();
    }

    @Override
    public List<Object[]> countAdminProductsByGroupBuyStatus(
            ProductDisplayStatus displayStatus,
            String keyword
    ) {
        QProduct product = QProduct.product;
        QMarket market = QMarket.market;
        // 공구상태 필터는 미반영
        BooleanBuilder where = buildAdminProductWhere(product, market, displayStatus, null, keyword);

        return queryFactory
                .select(product.groupBuyStatus, product.count())
                .from(product)
                .leftJoin(product.market, market)
                .where(where)
                .groupBy(product.groupBuyStatus)
                .fetch()
                .stream()
                .map(tuple -> new Object[]{tuple.get(product.groupBuyStatus), tuple.get(product.count())})
                .toList();
    }

    private BooleanBuilder buildAdminProductWhere(
            QProduct product,
            QMarket market,
            ProductDisplayStatus displayStatus,
            ProductGroupBuyStatus groupBuyStatus,
            String keyword
    ) {
        BooleanBuilder where = new BooleanBuilder();

        if (displayStatus != null) {
            where.and(product.displayStatus.eq(displayStatus));
        }

        if (groupBuyStatus != null) {
            where.and(product.groupBuyStatus.eq(groupBuyStatus));
        }

        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            where.and(
                    product.name.containsIgnoreCase(k)
                            .or(product.productNumber.containsIgnoreCase(k))
                            .or(market.marketName.containsIgnoreCase(k))
            );
        }

        return where;
    }
}
