package showroomz.api.seller.inquiry.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import showroomz.api.seller.inquiry.dto.SellerInquiryDto;
import showroomz.api.seller.inquiry.dto.SellerInquiryListResponse;
import showroomz.api.seller.inquiry.dto.SellerInquirySearchCondition;
import showroomz.domain.inquiry.type.InquiryStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class SellerInquiryQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SellerInquiryListResponse searchMarketInquiries(Long marketId,
                                                           SellerInquirySearchCondition condition,
                                                           Pageable pageable) {
        StringBuilder baseSql = new StringBuilder();
        baseSql.append("SELECT * FROM ( ")
                .append("  SELECT pi.PRODUCT_INQUIRY_ID AS id, 'PRODUCT' AS source, ")
                .append("         CASE ")
                .append("              WHEN pi.TYPE = 'PRODUCT_INQUIRY' THEN '상품 문의' ")
                .append("              WHEN pi.TYPE = 'SIZE_INQUIRY' THEN '사이즈 문의' ")
                .append("              WHEN pi.TYPE = 'STOCK_INQUIRY' THEN '재고/재입고 문의' ")
                .append("         END AS type_str, ")
                .append("         pi.CONTENT AS content, COALESCE(u.NAME, u.NICKNAME) AS customer_name, ")
                .append("         p.NAME AS product_name, pi.CREATED_AT AS created_at, pi.STATUS AS status ")
                .append("  FROM PRODUCT_INQUIRY pi ")
                .append("  JOIN USERS u ON pi.USER_ID = u.USER_ID ")
                .append("  JOIN PRODUCT p ON pi.PRODUCT_ID = p.PRODUCT_ID ")
                .append("  WHERE p.MARKET_ID = :marketId ")
                .append("  UNION ALL ")
                .append("  SELECT o2o.INQUIRY_ID AS id, 'ONE_TO_ONE' AS source, ")
                .append("         CASE ")
                .append("              WHEN o2o.TYPE = 'DELIVERY' THEN '배송' ")
                .append("              WHEN o2o.TYPE = 'ORDER_PAYMENT' THEN '주문/결제' ")
                .append("              WHEN o2o.TYPE = 'CANCEL_REFUND_EXCHANGE' THEN '취소/교환/환불' ")
                .append("              WHEN o2o.CATEGORY IN ('DEFECT', 'AS') THEN '불량/AS' ")
                .append("         END AS type_str, ")
                .append("         o2o.CONTENT AS content, COALESCE(u.NAME, u.NICKNAME) AS customer_name, ")
                .append("         ( ")
                .append("             SELECT p2.NAME ")
                .append("             FROM ORDER_PRODUCT op2 ")
                .append("             JOIN PRODUCT p2 ON op2.PRODUCT_ID = p2.PRODUCT_ID ")
                .append("             WHERE op2.ORDER_ID = o2o.ORDER_ID ")
                .append("               AND p2.MARKET_ID = :marketId ")
                .append("             ORDER BY p2.PRODUCT_ID ")
                .append("             LIMIT 1 ")
                .append("         ) AS product_name, ")
                .append("         o2o.CREATED_AT AS created_at, o2o.STATUS AS status ")
                .append("  FROM ONE_TO_ONE_INQUIRY o2o ")
                .append("  JOIN USERS u ON o2o.USER_ID = u.USER_ID ")
                .append("  WHERE (o2o.TYPE IN ('DELIVERY', 'ORDER_PAYMENT', 'CANCEL_REFUND_EXCHANGE') ")
                .append("         OR o2o.CATEGORY IN ('DEFECT', 'AS')) ")
                .append("    AND EXISTS ( ")
                .append("         SELECT 1 ")
                .append("         FROM ORDER_PRODUCT op ")
                .append("         JOIN PRODUCT p ON op.PRODUCT_ID = p.PRODUCT_ID ")
                .append("         WHERE op.ORDER_ID = o2o.ORDER_ID ")
                .append("           AND p.MARKET_ID = :marketId ")
                .append("    ) ")
                .append(") AS combined ")
                .append("WHERE 1 = 1 ");

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("marketId", marketId);

        appendConditions(baseSql, params, condition);

        String countSql = "SELECT COUNT(*) AS total_count, "
                + "SUM(CASE WHEN status = 'WAITING' THEN 1 ELSE 0 END) AS waiting_count "
                + "FROM (" + baseSql + ") AS count_table";
        Map<String, Object> counts = jdbcTemplate.queryForMap(countSql, params);
        long totalCount = ((Number) counts.get("total_count")).longValue();
        long waitingCount = counts.get("waiting_count") != null
                ? ((Number) counts.get("waiting_count")).longValue()
                : 0L;

        baseSql.append(" ORDER BY combined.created_at DESC ")
                .append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<SellerInquiryDto> content = jdbcTemplate.query(baseSql.toString(), params, inquiryRowMapper());
        Page<SellerInquiryDto> page = new PageImpl<>(content, pageable, totalCount);

        return SellerInquiryListResponse.builder()
                .totalCount(totalCount)
                .waitingCount(waitingCount)
                .inquiries(page)
                .build();
    }

    private void appendConditions(StringBuilder sql,
                                  MapSqlParameterSource params,
                                  SellerInquirySearchCondition condition) {
        if (condition == null) {
            return;
        }

        if (condition.getStartDate() != null) {
            sql.append(" AND combined.created_at >= :startDate ");
            params.addValue("startDate", condition.getStartDate().atStartOfDay());
        }
        if (condition.getEndDate() != null) {
            sql.append(" AND combined.created_at <= :endDate ");
            params.addValue("endDate", condition.getEndDate().atTime(LocalTime.MAX));
        }
        if (condition.getInquiryTypes() != null && !condition.getInquiryTypes().isEmpty()) {
            sql.append(" AND combined.type_str IN (:inquiryTypes) ");
            params.addValue("inquiryTypes", condition.getInquiryTypes());
        }
        if (condition.getStatus() != null) {
            sql.append(" AND combined.status = :status ");
            params.addValue("status", condition.getStatus().name());
        }
        if (condition.getKeyword() != null && !condition.getKeyword().trim().isEmpty()) {
            sql.append(" AND (combined.content LIKE :keyword ")
                    .append(" OR combined.customer_name LIKE :keyword ")
                    .append(" OR combined.product_name LIKE :keyword) ");
            params.addValue("keyword", "%" + condition.getKeyword().trim() + "%");
        }
    }

    private RowMapper<SellerInquiryDto> inquiryRowMapper() {
        return (rs, rowNum) -> SellerInquiryDto.builder()
                .inquiryId(rs.getLong("id"))
                .source(rs.getString("source"))
                .inquiryType(rs.getString("type_str"))
                .content(rs.getString("content"))
                .customerName(rs.getString("customer_name"))
                .productName(rs.getString("product_name"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .status(InquiryStatus.valueOf(rs.getString("status")))
                .build();
    }
}
