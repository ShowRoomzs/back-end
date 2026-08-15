package showroomz.api.seller.inquiry.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import showroomz.api.seller.inquiry.dto.SellerInquirySearchCondition;
import showroomz.api.seller.inquiry.type.InquiryVisibility;
import showroomz.api.seller.inquiry.type.SellerInquirySort;
import showroomz.api.seller.inquiry.type.SellerInquiryStatusFilter;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.entity.QProductInquiry;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.product.entity.QProduct;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 파트너센터 문의 목록 조회 (§23-2).
 * 1:1 문의는 어드민으로만 접수되므로 이 목록은 상품 문의 전용이다.
 */
@Repository
@RequiredArgsConstructor
public class SellerInquiryQueryRepository {

    private final JPAQueryFactory queryFactory;

    private static final QProductInquiry inquiry = QProductInquiry.productInquiry;
    private static final QProduct product = QProduct.product;

    public Page<ProductInquiry> search(Long marketId, SellerInquirySearchCondition condition, Pageable pageable) {
        BooleanBuilder where = createWhere(marketId, condition);

        List<ProductInquiry> content = queryFactory
                .selectFrom(inquiry)
                .join(inquiry.product, product).fetchJoin()
                .where(where)
                .orderBy(orderBy(condition))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(inquiry.count())
                .from(inquiry)
                .join(inquiry.product, product)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /** 상세의 ‹ 이전 · 다음 › — 현재 탭·필터의 목록 순서를 따른다 (§23-3) */
    public List<Long> findOrderedIds(Long marketId, SellerInquirySearchCondition condition) {
        return queryFactory
                .select(inquiry.id)
                .from(inquiry)
                .join(inquiry.product, product)
                .where(createWhere(marketId, condition))
                .orderBy(orderBy(condition))
                .fetch();
    }

    /**
     * 상태 탭 건수 — 마켓 전체 기준 (§23-2).
     * 검색 결과가 없어도 카운트는 그대로 두는 화면 규칙이라 필터·검색어를 반영하지 않는다.
     */
    public Map<SellerInquiryStatusFilter, Long> countByStatus(Long marketId) {
        List<Tuple> rows = queryFactory
                .select(inquiry.status, inquiry.exposureStatus, inquiry.count())
                .from(inquiry)
                .join(inquiry.product, product)
                .where(product.market.id.eq(marketId))
                .groupBy(inquiry.status, inquiry.exposureStatus)
                .fetch();

        Map<SellerInquiryStatusFilter, Long> counts = new EnumMap<>(SellerInquiryStatusFilter.class);
        for (SellerInquiryStatusFilter tab : SellerInquiryStatusFilter.values()) {
            counts.put(tab, 0L);
        }

        for (Tuple row : rows) {
            InquiryStatus status = row.get(inquiry.status);
            InquiryExposureStatus exposureStatus = row.get(inquiry.exposureStatus);
            long count = Objects.requireNonNullElse(row.get(inquiry.count()), 0L);

            counts.merge(SellerInquiryStatusFilter.ALL, count, Long::sum);
            SellerInquiryStatusFilter tab = resolveTab(status, exposureStatus);
            if (tab != null) {
                counts.merge(tab, count, Long::sum);
            }
        }
        return counts;
    }

    /** 문의 유형 필터 건수 — 마켓 전체 기준 (§23-2) */
    public Map<ProductInquiryType, Long> countByType(Long marketId) {
        List<Tuple> rows = queryFactory
                .select(inquiry.type, inquiry.count())
                .from(inquiry)
                .join(inquiry.product, product)
                .where(product.market.id.eq(marketId))
                .groupBy(inquiry.type)
                .fetch();

        Map<ProductInquiryType, Long> counts = new EnumMap<>(ProductInquiryType.class);
        for (ProductInquiryType type : ProductInquiryType.values()) {
            counts.put(type, 0L);
        }
        for (Tuple row : rows) {
            ProductInquiryType type = row.get(inquiry.type);
            if (type != null) {
                counts.put(type, Objects.requireNonNullElse(row.get(inquiry.count()), 0L));
            }
        }
        return counts;
    }

    /** 공개여부 필터 건수 — 마켓 전체 기준 (§23-2) */
    public Map<InquiryVisibility, Long> countByVisibility(Long marketId) {
        List<Tuple> rows = queryFactory
                .select(inquiry.secret, inquiry.count())
                .from(inquiry)
                .join(inquiry.product, product)
                .where(product.market.id.eq(marketId))
                .groupBy(inquiry.secret)
                .fetch();

        Map<InquiryVisibility, Long> counts = new EnumMap<>(InquiryVisibility.class);
        for (InquiryVisibility visibility : InquiryVisibility.values()) {
            counts.put(visibility, 0L);
        }
        for (Tuple row : rows) {
            Boolean secret = row.get(inquiry.secret);
            if (secret != null) {
                counts.put(secret ? InquiryVisibility.SECRET : InquiryVisibility.PUBLIC,
                        Objects.requireNonNullElse(row.get(inquiry.count()), 0L));
            }
        }
        return counts;
    }

    private BooleanBuilder createWhere(Long marketId, SellerInquirySearchCondition condition) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(product.market.id.eq(marketId));

        if (condition == null) {
            return where;
        }

        SellerInquiryStatusFilter tab = condition.getStatus();
        if (tab != null) {
            if (tab.getStatus() != null) {
                where.and(inquiry.status.eq(tab.getStatus()));
            }
            if (tab.getExposureStatus() != null) {
                where.and(inquiry.exposureStatus.eq(tab.getExposureStatus()));
            }
        }

        List<ProductInquiryType> types = condition.getTypes();
        if (types != null && !types.isEmpty()) {
            where.and(inquiry.type.in(types.stream().filter(Objects::nonNull).toList()));
        }

        BooleanExpression visibility = visibilityPredicate(condition.getVisibilities());
        if (visibility != null) {
            where.and(visibility);
        }

        String keyword = condition.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            where.and(inquiry.content.containsIgnoreCase(trimmed)
                    .or(product.name.containsIgnoreCase(trimmed)));
        }

        return where;
    }

    /** 다중선택이라 둘 다 고르면 미선택과 같다 — 미선택이 곧 전체다 (§23-2) */
    private BooleanExpression visibilityPredicate(List<InquiryVisibility> visibilities) {
        if (visibilities == null || visibilities.isEmpty()) {
            return null;
        }
        boolean withPublic = visibilities.contains(InquiryVisibility.PUBLIC);
        boolean withSecret = visibilities.contains(InquiryVisibility.SECRET);
        if (withPublic == withSecret) {
            return null;
        }
        return inquiry.secret.eq(withSecret);
    }

    /**
     * 정렬 (§23-2). 기본값인 `답변대기 우선`은 답변대기 건을 위로 올리되
     * 그 안에서는 오래 기다린 순으로, 나머지는 최신순으로 늘어놓는다.
     */
    private OrderSpecifier<?>[] orderBy(SellerInquirySearchCondition condition) {
        SellerInquirySort sort = condition != null && condition.getSort() != null
                ? condition.getSort()
                : SellerInquirySort.WAITING_FIRST;

        if (sort == SellerInquirySort.CREATED_AT) {
            return new OrderSpecifier<?>[]{inquiry.createdAt.desc(), inquiry.id.desc()};
        }

        BooleanExpression waiting = inquiry.status.eq(InquiryStatus.WAITING)
                .and(inquiry.exposureStatus.eq(InquiryExposureStatus.NORMAL));

        // 답변대기 묶음을 위로 올리고(0) 그 안에서는 오래 기다린 순, 나머지 묶음(1)은 최신순으로 둔다.
        // 각 묶음에서 상대 묶음용 정렬 키는 NULL이라 서로의 순서에 끼어들지 않는다.
        NumberExpression<Integer> group =
                Expressions.numberTemplate(Integer.class, "CASE WHEN {0} THEN 0 ELSE 1 END", waiting);
        DateTimeExpression<LocalDateTime> waitingOldestFirst =
                Expressions.dateTimeTemplate(LocalDateTime.class, "CASE WHEN {0} THEN {1} END", waiting, inquiry.createdAt);
        DateTimeExpression<LocalDateTime> restLatestFirst =
                Expressions.dateTimeTemplate(LocalDateTime.class, "CASE WHEN {0} THEN NULL ELSE {1} END", waiting, inquiry.createdAt);

        return new OrderSpecifier<?>[]{
                group.asc(),
                waitingOldestFirst.asc(),
                restLatestFirst.desc(),
                inquiry.id.desc()
        };
    }

    private SellerInquiryStatusFilter resolveTab(InquiryStatus status, InquiryExposureStatus exposureStatus) {
        if (exposureStatus == InquiryExposureStatus.DELETE_REQUESTED) {
            return SellerInquiryStatusFilter.DELETE_REQUESTED;
        }
        if (exposureStatus == InquiryExposureStatus.DELETED) {
            return SellerInquiryStatusFilter.DELETED;
        }
        return status == InquiryStatus.ANSWERED
                ? SellerInquiryStatusFilter.ANSWERED
                : SellerInquiryStatusFilter.WAITING;
    }
}
