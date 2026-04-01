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
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;
import showroomz.domain.inquiry.type.InquiryStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
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
                .append("  SELECT pi.product_inquiry_id AS id, 'PRODUCT' AS source, ")
                .append("         CASE ")
                .append("              WHEN pi.type = 'PRODUCT_INQUIRY' THEN 'PRODUCT' ")
                .append("              WHEN pi.type = 'SIZE_INQUIRY' THEN 'SIZE' ")
                .append("              WHEN pi.type = 'STOCK_INQUIRY' THEN 'STOCK' ")
                .append("         END AS filter_type, ")
                .append("         pi.content AS content, COALESCE(u.name, u.nickname) AS customer_name, ")
                .append("         p.name AS product_name, pi.created_at AS created_at, pi.status AS status ")
                .append("  FROM product_inquiry pi ")
                .append("  JOIN users u ON pi.user_id = u.user_id ")
                .append("  JOIN product p ON pi.product_id = p.product_id ")
                .append("  WHERE p.market_id = :marketId ")
                .append("  UNION ALL ")
                .append("  SELECT o2o.inquiry_id AS id, 'ONE_TO_ONE' AS source, ")
                .append("         CASE ")
                .append("              WHEN o2o.type = 'DELIVERY' THEN 'DELIVERY' ")
                .append("              WHEN o2o.type = 'ORDER_PAYMENT' THEN 'ORDER_PAYMENT' ")
                .append("              WHEN o2o.type = 'CANCEL_REFUND_EXCHANGE' THEN 'CANCEL_REFUND_EXCHANGE' ")
                .append("              WHEN o2o.category IN ('DEFECT', 'AS') THEN 'DEFECT_AS' ")
                .append("         END AS filter_type, ")
                .append("         o2o.content AS content, COALESCE(u.name, u.nickname) AS customer_name, ")
                .append("         ( ")
                .append("             SELECT p2.name ")
                .append("             FROM order_product op2 ")
                .append("             JOIN product_variant pv2 ON op2.variant_id = pv2.variant_id ")
                .append("             JOIN product p2 ON pv2.product_id = p2.product_id ")
                .append("             WHERE op2.order_id = o2o.order_id ")
                .append("               AND p2.market_id = :marketId ")
                .append("             ORDER BY p2.product_id ")
                .append("             LIMIT 1 ")
                .append("         ) AS product_name, ")
                .append("         o2o.created_at AS created_at, o2o.status AS status ")
                .append("  FROM one_to_one_inquiry o2o ")
                .append("  JOIN users u ON o2o.user_id = u.user_id ")
                .append("  WHERE (o2o.type IN ('DELIVERY', 'ORDER_PAYMENT', 'CANCEL_REFUND_EXCHANGE') ")
                .append("         OR o2o.category IN ('DEFECT', 'AS')) ")
                .append("    AND EXISTS ( ")
                .append("         SELECT 1 ")
                .append("         FROM order_product op ")
                .append("         JOIN product_variant pv ON op.variant_id = pv.variant_id ")
                .append("         JOIN product p ON pv.product_id = p.product_id ")
                .append("         WHERE op.order_id = o2o.order_id ")
                .append("           AND p.market_id = :marketId ")
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
            List<String> typeNames = new ArrayList<>();
            for (MarketInquiryFilterType inquiryType : condition.getInquiryTypes()) {
                typeNames.add(inquiryType.name());
            }
            sql.append(" AND combined.filter_type IN (:inquiryTypes) ");
            params.addValue("inquiryTypes", typeNames);
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
                .inquiryType(MarketInquiryFilterType.valueOf(rs.getString("filter_type")).getDescription())
                .content(rs.getString("content"))
                .customerName(rs.getString("customer_name"))
                .productName(rs.getString("product_name"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .status(InquiryStatus.valueOf(rs.getString("status")))
                .build();
    }
}
