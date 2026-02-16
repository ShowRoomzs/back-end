package showroomz.api.app.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.app.product.DTO.ProductDto;

import java.util.List;

public class CartDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 추가 요청")
    public static class AddCartRequest {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @NotNull(message = "옵션(Variant) ID는 필수입니다.")
        @Schema(description = "옵션(Variant) ID", example = "1")
        private Long variantId;

        @NotNull(message = "수량은 필수입니다.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        @Schema(description = "수량", example = "2")
        private Integer quantity;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 추가 응답")
    public static class AddCartResponse {
        @Schema(description = "장바구니 ID", example = "10")
        private Long cartId;

        @Schema(description = "옵션(Variant) ID", example = "1")
        private Long variantId;

        @Schema(description = "최종 수량", example = "3")
        private Integer quantity;

        @Schema(description = "응답 메시지", example = "장바구니에 추가되었습니다.")
        private String message;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 다중 추가 응답")
    public static class BulkAddCartResponse {
        @Schema(description = "추가된 상품 수", example = "2")
        private Integer addedCount;

        @Schema(description = "응답 메시지", example = "상품 2개가 장바구니에 추가되었습니다.")
        private String message;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 수정 요청")
    public static class UpdateCartRequest {
        @Schema(description = "옵션(Variant) ID", example = "1")
        private Long variantId;

        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        @Schema(description = "수량", example = "2")
        private Integer quantity;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 수정 응답")
    public static class UpdateCartResponse {
        @Schema(description = "장바구니 ID", example = "10")
        private Long cartId;

        @Schema(description = "옵션(Variant) ID", example = "1")
        private Long variantId;

        @Schema(description = "수량", example = "2")
        private Integer quantity;

        @Schema(description = "요약 정보")
        private UpdateSummary summary;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 수정 요약 정보")
    public static class UpdateSummary {
        @Schema(description = "정가 합계", example = "200000")
        private Long regularTotal;

        @Schema(description = "할인가 합계", example = "150000")
        private Long saleTotal;

        @Schema(description = "할인액 합계", example = "50000")
        private Long discountTotal;

        @Schema(description = "배송비 합계", example = "3000")
        private Long deliveryFeeTotal;

        @Schema(description = "상품 총액", example = "150000")
        private Long totalProductPrice;

        @Schema(description = "예상 결제 금액", example = "153000")
        private Long expectedTotalPrice;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 삭제 응답 (개별/선택/전체 통합)")
    public static class DeleteCartResponse {
        @Schema(description = "삭제된 장바구니 ID 목록")
        private List<Long> deletedCartItemIds;

        @Schema(description = "삭제된 항목 수", example = "3")
        private Integer deletedCount;

        @Schema(description = "응답 메시지", example = "3개 항목이 삭제되었습니다.")
        private String message;

        @Schema(description = "삭제 후 요약 정보")
        private UpdateSummary summary;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 조회 응답 (페이징 미적용)")
    public static class CartListResponse {
        @Schema(description = "장바구니 항목 목록")
        private List<CartItem> items;

        @Schema(description = "요약 정보")
        private CartSummary summary;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 아이템")
    public static class CartItem {
        @Schema(description = "장바구니 ID", example = "10")
        private Long cartId;

        @Schema(description = "상품 ID", example = "1024")
        private Long productId;

        @Schema(description = "옵션(Variant) ID", example = "1")
        private Long variantId;

        @Schema(description = "상품명", example = "프리미엄 린넨 셔츠")
        private String productName;

        @Schema(description = "썸네일 URL", example = "https://example.com/image.jpg")
        private String thumbnailUrl;

        @Schema(description = "쇼룸 ID", example = "5")
        private Long marketId;

        @Schema(description = "쇼룸명", example = "M 브라이튼")
        private String marketName;

        @Schema(description = "옵션명", example = "색상: 블랙 / 사이즈: L")
        private String optionName;

        @Schema(description = "수량", example = "2")
        private Integer quantity;

        @Schema(description = "가격 정보")
        private ProductDto.PriceInfo price;

        @Schema(description = "배송비", example = "3000")
        private Integer deliveryFee;

        @Schema(description = "재고 상태")
        private StockInfo stock;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "재고 정보")
    public static class StockInfo {
        @Schema(description = "재고 수량", example = "10")
        private Integer stock;

        @Schema(description = "재고 기반 품절 여부", example = "false")
        private Boolean isOutOfStock;

        @Schema(description = "관리자 강제 품절 여부", example = "false")
        private Boolean isOutOfStockForced;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 요약 정보")
    public static class CartSummary {
        @Schema(description = "정가 합계", example = "200000")
        private Long regularTotal;

        @Schema(description = "할인가 합계", example = "150000")
        private Long saleTotal;

        @Schema(description = "할인액 합계", example = "50000")
        private Long discountTotal;

        @Schema(description = "배송비 합계", example = "3000")
        private Long deliveryFeeTotal;

        @Schema(description = "최종 결제 금액", example = "153000")
        private Long finalTotal;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "페이지 정보")
    public static class PageInfo {
        @Schema(description = "현재 페이지 번호", example = "1")
        private Integer currentPage;

        @Schema(description = "한 페이지당 개수", example = "20")
        private Integer pageSize;

        @Schema(description = "전체 항목 수", example = "5")
        private Long totalElements;

        @Schema(description = "전체 페이지 수", example = "1")
        private Integer totalPages;

        @Schema(description = "마지막 페이지 여부", example = "true")
        private Boolean isLast;

        @Schema(description = "다음 페이지 존재 여부", example = "false")
        private Boolean hasNext;
    }
}
