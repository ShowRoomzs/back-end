package showroomz.api.app.recommendation.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.global.dto.PageInfo;

import java.util.List;

public class RecommendationDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "추천 응답 (상품)")
    public static class ProductRecommendationResponse {
        @Schema(description = "상품 목록")
        private List<ProductDto.ProductItem> products;

        @Schema(description = "페이지 정보")
        private PageInfo pageInfo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "마켓 추천 항목")
    public static class MarketRecommendationItem {
        @Schema(description = "마켓 ID", example = "1")
        private Long marketId;

        @Schema(description = "마켓명", example = "M 브라이튼")
        private String marketName;

        @Schema(description = "마켓 이미지 URL", example = "https://example.com/market.jpg")
        private String marketImageUrl;

        @Schema(description = "대표 카테고리 ID", example = "1")
        private Long mainCategoryId;

        @Schema(description = "대표 카테고리명", example = "의류")
        private String mainCategoryName;

        @Schema(description = "팔로워 수", example = "1200")
        private Long followerCount;

        @Schema(description = "팔로우 여부", example = "true")
        private Boolean isFollowing;

        @Schema(description = "대표 상품 3개")
        private List<ProductDto.ProductItem> representativeProducts;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "추천 응답 (마켓)")
    public static class MarketRecommendationResponse {
        @Schema(description = "마켓 목록")
        private List<MarketRecommendationItem> markets;

        @Schema(description = "페이지 정보")
        private PageInfo pageInfo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "통합 추천 응답")
    public static class UnifiedRecommendationResponse {
        @Schema(description = "추천 마켓 목록")
        private List<MarketRecommendationItem> recommendedMarkets;

        @Schema(description = "추천 상품 목록")
        private List<ProductDto.ProductItem> recommendedProducts;

        @Schema(description = "페이지 정보 (상품용)")
        private PageInfo pageInfo;
    }
}
