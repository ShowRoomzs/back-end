package showroomz.api.admin.productinquiry.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import showroomz.api.admin.productinquiry.type.AdminProductInquiryStatusFilter;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.entity.QProductInquiry;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.product.entity.QProduct;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 어드민 상품 문의 모니터링 목록 조회 (§18-2). */
@Repository
@RequiredArgsConstructor
public class AdminProductInquiryQueryRepository {

    private final JPAQueryFactory queryFactory;

    private static final QProductInquiry inquiry = QProductInquiry.productInquiry;
    private static final QProduct product = QProduct.product;

    public Page<ProductInquiry> search(AdminProductInquiryStatusFilter statusFilter, ProductInquiryType type,
                                       String keyword, Pageable pageable) {
        BooleanBuilder where = createWhere(statusFilter, type, keyword);

        List<ProductInquiry> content = queryFactory
                .selectFrom(inquiry)
                .join(inquiry.product, product).fetchJoin()
                .join(product.market).fetchJoin()
                .where(where)
                .orderBy(inquiry.createdAt.desc(), inquiry.id.desc())
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

    /** 상세의 ‹ 이전 · 다음 › — 현재 탭·필터의 목록 순서를 따른다 (§18-3) */
    public List<Long> findOrderedIds(AdminProductInquiryStatusFilter statusFilter, ProductInquiryType type,
                                     String keyword) {
        return queryFactory
                .select(inquiry.id)
                .from(inquiry)
                .join(inquiry.product, product)
                .where(createWhere(statusFilter, type, keyword))
                .orderBy(inquiry.createdAt.desc(), inquiry.id.desc())
                .fetch();
    }

    /** 탭 건수 — 유형·검색어는 그대로 반영하고 상태 조건만 제외한다 */
    public Map<AdminProductInquiryStatusFilter, Long> countByStatus(ProductInquiryType type, String keyword) {
        List<Tuple> rows = queryFactory
                .select(inquiry.status, inquiry.exposureStatus, inquiry.count())
                .from(inquiry)
                .join(inquiry.product, product)
                .where(createWhere(AdminProductInquiryStatusFilter.ALL, type, keyword))
                .groupBy(inquiry.status, inquiry.exposureStatus)
                .fetch();

        Map<AdminProductInquiryStatusFilter, Long> counts = new EnumMap<>(AdminProductInquiryStatusFilter.class);
        for (AdminProductInquiryStatusFilter tab : AdminProductInquiryStatusFilter.values()) {
            counts.put(tab, 0L);
        }

        for (Tuple row : rows) {
            InquiryStatus status = row.get(inquiry.status);
            InquiryExposureStatus exposureStatus = row.get(inquiry.exposureStatus);
            long count = row.get(inquiry.count()) != null ? row.get(inquiry.count()) : 0L;

            counts.merge(AdminProductInquiryStatusFilter.ALL, count, Long::sum);
            AdminProductInquiryStatusFilter tab = resolveTab(status, exposureStatus);
            if (tab != null) {
                counts.merge(tab, count, Long::sum);
            }
        }
        return counts;
    }

    /** 운영자가 봐야 할 유일한 수치 — 필터와 무관한 전체 삭제 요청 건수 (§18-2 · 18-7 GNB 배지) */
    public long countAllDeleteRequested() {
        Long count = queryFactory
                .select(inquiry.count())
                .from(inquiry)
                .where(inquiry.exposureStatus.eq(InquiryExposureStatus.DELETE_REQUESTED))
                .fetchOne();
        return count != null ? count : 0L;
    }

    private BooleanBuilder createWhere(AdminProductInquiryStatusFilter statusFilter, ProductInquiryType type,
                                       String keyword) {
        BooleanBuilder where = new BooleanBuilder();

        if (statusFilter != null) {
            if (statusFilter.getStatus() != null) {
                where.and(inquiry.status.eq(statusFilter.getStatus()));
            }
            if (statusFilter.getExposureStatus() != null) {
                where.and(inquiry.exposureStatus.eq(statusFilter.getExposureStatus()));
            }
        }
        if (type != null) {
            where.and(inquiry.type.eq(type));
        }
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            where.and(inquiry.content.containsIgnoreCase(trimmed)
                    .or(product.name.containsIgnoreCase(trimmed))
                    .or(product.market.marketName.containsIgnoreCase(trimmed)));
        }
        return where;
    }

    private AdminProductInquiryStatusFilter resolveTab(InquiryStatus status, InquiryExposureStatus exposureStatus) {
        if (exposureStatus == InquiryExposureStatus.DELETE_REQUESTED) {
            return AdminProductInquiryStatusFilter.DELETE_REQUESTED;
        }
        if (exposureStatus == InquiryExposureStatus.DELETED) {
            return AdminProductInquiryStatusFilter.DELETED;
        }
        return status == InquiryStatus.ANSWERED
                ? AdminProductInquiryStatusFilter.ANSWERED
                : AdminProductInquiryStatusFilter.WAITING;
    }
}
