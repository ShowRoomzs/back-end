package showroomz.api.app.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.app.product.DTO.ProductDto;

import java.util.List;

public class CartDto {

    /** 수량 상한 — 화면의 수량 스테퍼가 99에서 멈춘다(C8). 서버도 같은 선에서 막는다 */
    public static final int MAX_QUANTITY = 99;

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
        @Max(value = MAX_QUANTITY, message = "수량은 " + MAX_QUANTITY + " 이하여야 합니다.")
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
        @Max(value = MAX_QUANTITY, message = "수량은 " + MAX_QUANTITY + " 이하여야 합니다.")
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
    @Schema(description = "장바구니 조회 응답 (페이징 미적용) — 항목은 공구(쇼룸) 단위로 묶여 내려옵니다.")
    public static class CartListResponse {
        @Schema(description = "공구(쇼룸) 단위 그룹 목록")
        private List<CartGroup> groups;

        @Schema(description = "선택된 항목 기준 요약 정보")
        private CartSummary summary;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "공구(쇼룸) 단위 장바구니 그룹")
    public static class CartGroup {
        @Schema(description = "쇼룸(마켓) ID", example = "5")
        private Long marketId;

        @Schema(description = "쇼룸(마켓)명", example = "제니의 뷰티룸")
        private String marketName;

        @Schema(description = "쇼룸 대표 이미지 URL — 그룹 머리의 아바타", example = "https://example.com/market.jpg")
        private String marketImageUrl;

        @Schema(
                description = "그룹 전체가 마감·미진열이라 살 수 없는 상태인지 — true면 화면에서 D-day 배지를 그리지 않습니다.",
                example = "false"
        )
        private Boolean isClosed;

        @Schema(description = "그룹에 속한 장바구니 항목")
        private List<CartItem> items;

        @Schema(description = "이 그룹(공구)의 배송비 정보")
        private GroupShipping shipping;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "그룹(공구)별 배송비 — 결제 화면에서 처음 알게 되는 배송비가 가장 흔한 이탈 원인이라 담는 단계에서 내려줍니다.")
    public static class GroupShipping {
        @Schema(description = "이 그룹의 기본 배송비", example = "3000")
        private Integer deliveryFee;

        @Schema(description = "무료배송 기준 금액 (없으면 null)", example = "30000")
        private Integer freeShippingThreshold;

        @Schema(
                description = "이 그룹에서 선택된 항목이 하나라도 있는지 — false면 배송비 줄을 '—'로 그립니다.",
                example = "true"
        )
        private Boolean hasSelectedItems;

        @Schema(description = "선택된 항목 기준 그룹 상품 금액", example = "16800")
        private Long selectedProductTotal;

        @Schema(description = "선택된 항목 기준 실제 부과 배송비 — 무료 조건 충족 시 0", example = "3000")
        private Integer chargedDeliveryFee;

        @Schema(description = "무료배송 적용 여부", example = "false")
        private Boolean isFreeShipping;

        @Schema(
                description = "무료배송까지 남은 금액 — 무료 기준이 없거나 이미 충족했거나 선택된 항목이 없으면 null입니다.",
                example = "13200"
        )
        private Long amountToFreeShipping;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "구매 가능 여부 — 담은 뒤 마감·품절된 항목을 화면이 걸러 낼 수 있게 합니다.")
    public static class Availability {
        @Schema(description = "지금 주문할 수 있는지", example = "true")
        private Boolean isPurchasable;

        @Schema(
                description = "구매 불가 사유 — 구매 가능하면 null",
                example = "SOLD_OUT",
                allowableValues = {"GROUP_BUY_CLOSED", "SOLD_OUT"}
        )
        private String reason;

        @Schema(description = "썸네일 위 라벨 (구매 가능하면 null)", example = "품절")
        private String label;

        @Schema(description = "수량·가격 자리를 대신하는 사유 문구 (구매 가능하면 null)", example = "품절되어 주문할 수 없어요")
        private String message;
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

        @Schema(description = "구매 가능 여부 및 사유")
        private Availability availability;

        @Schema(
                description = "이번 응답의 합계에 포함된 항목인지 — 구매 불가 항목은 요청에 담겨 있어도 항상 false입니다.",
                example = "true"
        )
        private Boolean isSelected;
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

        @Schema(description = "합계에 포함된(선택된) 항목 수 — [주문하기] 버튼의 (N)", example = "3")
        private Integer selectedCount;

        @Schema(description = "선택할 수 있는 항목 수 — 전체 선택 N/M의 M. 구매 불가 항목은 빠집니다.", example = "3")
        private Integer selectableCount;

        @Schema(description = "장바구니에 담긴 전체 항목 수 (구매 불가 포함)", example = "4")
        private Integer totalCount;
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
