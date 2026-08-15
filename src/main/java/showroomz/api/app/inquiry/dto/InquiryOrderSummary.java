package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderProduct;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 문의에 연결된 주문 카드 (C12) — 주문번호 · 주문일 · 대표 상품명 · 썸네일.
 * 주문을 연결하지 않은 문의는 이 블록 자체가 null이며, 화면에서도 노출하지 않는다.
 */
@Getter
@Builder
@Schema(description = "문의에 연결된 주문 요약 — 주문을 연결하지 않았으면 null")
public class InquiryOrderSummary {

    private static final DateTimeFormatter ORDER_NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Schema(description = "주문 ID — 주문 상세 이동용", example = "1147")
    private Long orderId;

    @Schema(description = "주문번호 (주문일자 + 주문 ID 4자리)", example = "20260803-1147")
    private String orderNumber;

    @Schema(description = "주문 일시", example = "2026-08-03T13:20:00")
    private LocalDateTime orderDate;

    @Schema(description = "대표 상품명 — 주문의 첫 상품", example = "시카 리페어 앰플 30ml 리필 2개 세트")
    private String productName;

    @Schema(description = "대표 상품 썸네일 URL")
    private String productImageUrl;

    @Schema(description = "주문에 포함된 상품 수 — 2 이상이면 화면에서 `외 N건`으로 표기한다", example = "1")
    private int productCount;

    public static InquiryOrderSummary from(Order order) {
        List<OrderProduct> products = order.getOrderProducts();
        OrderProduct representative = (products == null || products.isEmpty()) ? null : products.get(0);
        LocalDateTime orderDate = representative != null && representative.getOrderDate() != null
                ? representative.getOrderDate()
                : order.getCreatedAt();

        return InquiryOrderSummary.builder()
                .orderId(order.getId())
                .orderNumber(formatOrderNumber(order.getId(), orderDate))
                .orderDate(orderDate)
                .productName(representative != null ? representative.getProductName() : null)
                .productImageUrl(representative != null ? representative.getImageUrl() : null)
                .productCount(products == null ? 0 : products.size())
                .build();
    }

    private static String formatOrderNumber(Long orderId, LocalDateTime orderDate) {
        if (orderDate == null) {
            return String.format("%04d", orderId);
        }
        return ORDER_NUMBER_DATE.format(orderDate) + "-" + String.format("%04d", orderId);
    }
}