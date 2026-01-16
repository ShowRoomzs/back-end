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
            List<ProductFilterCriteria> filters,
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
}
