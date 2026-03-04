package showroomz.api.common.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import showroomz.global.dto.PaginationInfo;
import showroomz.api.app.auth.entity.RoleType;

import java.util.List;

/**
 * 추천 마켓(쇼룸) 목록 응답 DTO.
 * content + pageInfo 공통 응답 형식
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "추천 마켓 목록 페이징 응답")
public class MarketRecommendationResponse {

    @Schema(description = "추천 마켓 목록")
    private List<MarketRecommendationItem> content;

    @Schema(description = "페이징 메타데이터")
    private PaginationInfo pageInfo;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "추천 마켓 항목")
    public static class MarketRecommendationItem {

        @Schema(description = "마켓 ID", example = "1")
        private Long marketId;

        @Schema(description = "마켓명", example = "M 브라이튼")
        private String marketName;

        @Schema(description = "판매자 ID", example = "1")
        private Long sellerId;

        @Schema(description = "마켓 대표 이미지 URL")
        private String marketImageUrl;

        @Schema(description = "대표 상품 목록 (최대 3개, 우선순위: 추천상품 → 최신상품, 이미지 클릭 시 상세 페이지 이동용)")
        private List<RepresentativeProduct> representativeProducts;

        @Schema(description = "마켓 소개")
        private String marketDescription;

        @Schema(description = "마켓 URL")
        private String marketUrl;

        @Schema(description = "판매자 유형", allowableValues = {"SELLER", "CREATOR"})
        private RoleType shopType;

        @Schema(description = "팔로워 수", example = "1200")
        private Long followCount;

        @Schema(description = "현재 로그인 유저의 팔로우 여부 (비회원: false)")
        private Boolean isFollowing;

        @Schema(description = "대표 카테고리 ID", example = "1")
        private Long mainCategoryId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "대표 상품 항목 (productId: 상세 페이지 이동용)")
    public static class RepresentativeProduct {
        @Schema(description = "상품 ID", example = "1024")
        private Long productId;

        @Schema(description = "대표 이미지 URL", example = "https://example.com/image.jpg")
        private String imageUrl;
    }

    public static MarketRecommendationResponse of(List<MarketRecommendationItem> content, Page<?> page) {
        return MarketRecommendationResponse.builder()
                .content(content)
                .pageInfo(new PaginationInfo(page))
                .build();
    }
}
