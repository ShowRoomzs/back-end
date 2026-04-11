package showroomz.api.seller.inquiry.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import showroomz.api.seller.inquiry.dto.SellerInquiryDto;
import showroomz.api.seller.inquiry.dto.SellerInquiryListResponse;
import showroomz.api.seller.inquiry.dto.SellerInquirySearchCondition;
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;
import showroomz.domain.inquiry.entity.MarketInquiryView;
import showroomz.domain.inquiry.entity.QMarketInquiryView;
import showroomz.domain.inquiry.type.InquiryStatus;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class SellerInquiryQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final QMarketInquiryView marketInquiryView = QMarketInquiryView.marketInquiryView;

    public SellerInquiryListResponse searchMarketInquiries(Long marketId,
                                                           SellerInquirySearchCondition condition,
                                                           Pageable pageable) {
        BooleanBuilder whereClause = createWhereClause(marketId, condition);

        List<SellerInquiryDto> content = queryFactory
                .selectFrom(marketInquiryView)
                .where(whereClause)
                .orderBy(marketInquiryView.createdAt.desc(), marketInquiryView.inquiryKey.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch()
                .stream()
                .map(this::toDto)
                .toList();

        Long total = queryFactory
                .select(marketInquiryView.count())
                .from(marketInquiryView)
                .where(whereClause)
                .fetchOne();

        Long waiting = queryFactory
                .select(marketInquiryView.count())
                .from(marketInquiryView)
                .where(whereClause.and(marketInquiryView.status.eq(InquiryStatus.WAITING)))
                .fetchOne();

        long totalCount = total != null ? total : 0L;
        long waitingCount = waiting != null ? waiting : 0L;
        Page<SellerInquiryDto> page = new PageImpl<>(content, pageable, totalCount);

        return new SellerInquiryListResponse(totalCount, waitingCount, page);
    }

    private BooleanBuilder createWhereClause(Long marketId, SellerInquirySearchCondition condition) {
        BooleanBuilder whereClause = new BooleanBuilder();
        whereClause.and(marketInquiryView.marketId.eq(marketId));

        if (condition == null) {
            return whereClause;
        }

        if (condition.getStartDate() != null) {
            whereClause.and(marketInquiryView.createdAt.goe(condition.getStartDate().atStartOfDay()));
        }
        if (condition.getEndDate() != null) {
            whereClause.and(marketInquiryView.createdAt.loe(condition.getEndDate().atTime(LocalTime.MAX)));
        }
        if (condition.getInquiryTypes() != null && !condition.getInquiryTypes().isEmpty()) {
            List<String> inquiryTypes = condition.getInquiryTypes().stream()
                    .filter(Objects::nonNull)
                    .map(MarketInquiryFilterType::name)
                    .toList();
            if (!inquiryTypes.isEmpty()) {
                whereClause.and(marketInquiryView.filterType.in(inquiryTypes));
            }
        }
        if (condition.getStatus() != null) {
            whereClause.and(marketInquiryView.status.eq(condition.getStatus()));
        }
        if (condition.getKeyword() != null && !condition.getKeyword().trim().isEmpty()) {
            String keyword = condition.getKeyword().trim();
            whereClause.and(
                    marketInquiryView.content.containsIgnoreCase(keyword)
                            .or(marketInquiryView.customerName.containsIgnoreCase(keyword))
                            .or(marketInquiryView.productName.containsIgnoreCase(keyword))
            );
        }

        return whereClause;
    }

    private SellerInquiryDto toDto(MarketInquiryView inquiry) {
        return SellerInquiryDto.builder()
                .inquiryId(inquiry.getInquiryId())
                .source(inquiry.getSource())
                .inquiryType(MarketInquiryFilterType.valueOf(inquiry.getFilterType()).getDescription())
                .content(inquiry.getContent())
                .customerName(inquiry.getCustomerName())
                .productName(inquiry.getProductName())
                .createdAt(inquiry.getCreatedAt())
                .status(inquiry.getStatus())
                .build();
    }
}
