package showroomz.api.app.product.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

public class ProductDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 검색 요청")
    public static class ProductSearchRequest {
        @Schema(description = "검색어", example = "화이트 린넨 셔츠")
        private String q;

        @Schema(description = "카테고리 ID", example = "1")
        private Long categoryId;

        @Schema(description = "쇼룸 ID", example = "5")
        private Long marketId;

        @Schema(description = "정렬 기준", example = "RECOMMEND", allowableValues = {"RECOMMEND", "POPULAR", "NEWEST", "PRICE_ASC", "PRICE_DESC"})
        private String sort;

        @Schema(description = "필터 목록")
        private List<FilterRequest> filters;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "필터 요청")
    public static class FilterRequest {
        @Schema(description = "필터 키", example = "gender")
        private String key;

        @Schema(description = "필터 값 목록", example = "[\"MALE\", \"FEMALE\"]")
        private List<String> values;

        @Schema(description = "최소값 (RANGE)", example = "10000")
        private Integer minValue;

        @Schema(description = "최대값 (RANGE)", example = "100000")
        private Integer maxValue;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 검색 응답")
    public static class ProductSearchResponse {
        @Schema(description = "상품 목록")
        private List<ProductItem> products;

        @Schema(description = "페이지 정보")
        private PageInfo pageInfo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 아이템")
    public static class ProductItem {
        @Schema(description = "상품 ID", example = "1024")
        private Long id;

        @Schema(description = "상품 번호", example = "SRZ-20251228-001")
        private String productNumber;

        @Schema(description = "상품명", example = "프리미엄 린넨 셔츠")
        private String name;

        @Schema(description = "판매자 상품 코드", example = "PROD-001")
        private String sellerProductCode;

        @Schema(description = "대표 이미지 URL", example = "https://example.com/image.jpg")
        private String representativeImageUrl;

        @Schema(description = "썸네일 URL", example = "https://example.com/image.jpg")
        private String thumbnailUrl;

        @Schema(description = "카테고리 ID", example = "1")
        private Long categoryId;

        @Schema(description = "카테고리명", example = "의류")
        private String categoryName;

        @Schema(description = "쇼룸 ID", example = "5")
        private Long marketId;

        @Schema(description = "쇼룸명", example = "M 브라이튼")
        private String marketName;

        @Schema(description = "가격 정보")
        private PriceInfo price;

        @Schema(description = "매입가", example = "30000")
        private Integer purchasePrice;

        @Schema(description = "성별", example = "UNISEX", allowableValues = {"MALE", "FEMALE", "UNISEX"})
        private String gender;

        @Schema(description = "진열 상태", example = "true")
        private Boolean isDisplay;

        @Schema(description = "추천 상품 여부", example = "false")
        private Boolean isRecommended;

        @Schema(description = "상품정보제공고시 (JSON 문자열)")
        private String productNotice;

        @Schema(description = "상품 상세 설명 (HTML)")
        private String description;

        @Schema(description = "태그 (JSON 문자열)")
        private String tags;

        @Schema(description = "배송 유형", example = "STANDARD")
        private String deliveryType;

        @Schema(description = "배송비", example = "3000")
        private Integer deliveryFee;

        @Schema(description = "무료 배송 최소 금액", example = "50000")
        private Integer deliveryFreeThreshold;

        @Schema(description = "배송 예상 일수", example = "3")
        private Integer deliveryEstimatedDays;

        @Schema(description = "등록일", example = "2025-12-28T14:30:00Z")
        private String createdAt;

        @Schema(description = "재고 상태")
        private StockStatus status;

        @Schema(description = "좋아요 수", example = "1200")
        private Long likeCount;

        @Schema(description = "리뷰 수", example = "850")
        private Long reviewCount;

        @Schema(description = "찜 여부", example = "true")
        private Boolean isWished;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "가격 정보")
    public static class PriceInfo {
        @Schema(description = "정가", example = "113000")
        private Integer regularPrice;

        @Schema(description = "할인율 (%)", example = "70")
        private Integer discountRate;

        @Schema(description = "할인 판매가", example = "33900")
        private Integer salePrice;

        @Schema(description = "최대 혜택가", example = "31000")
        private Integer maxBenefitPrice;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "재고 상태")
    public static class StockStatus {
        @Schema(description = "재고 기반 품절 여부", example = "false")
        private Boolean isOutOfStock;

        @Schema(description = "관리자 강제 품절 여부", example = "false")
        private Boolean isOutOfStockForced;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "페이지 정보")
    public static class PageInfo {
        @Schema(description = "현재 페이지 번호", example = "1")
        private Integer currentPage;

        @Schema(description = "한 페이지당 보여줄 개수", example = "20")
        private Integer pageSize;

        @Schema(description = "검색 조건에 맞는 전체 상품 수", example = "1540")
        private Long totalElements;

        @Schema(description = "전체 페이지 수", example = "77")
        private Integer totalPages;

        @Schema(description = "마지막 페이지 여부", example = "false")
        private Boolean isLast;

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        private Boolean hasNext;
    }
}
