package showroomz.api.common.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PaginationInfo;

import java.util.List;

/**
 * 특정 쇼룸 인기 상품 Top 10 응답
 * - content: 상품 리스트 (최대 10개)
 * - pageInfo: 고정값 (currentPage=1, totalPages=1, totalResults, limit=10, hasNext=false)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "특정 쇼룸 인기 상품 Top 10 응답")
public class PopularProductResponse {

    @Schema(description = "인기 상품 목록 (최대 10개)")
    private List<ProductDto.ProductItem> content;

    @Schema(description = "페이징 메타데이터 (고정: currentPage=1, totalPages=1, limit=10, hasNext=false)")
    private PaginationInfo pageInfo;

    /**
     * Top 10 고정 페이징 응답 생성
     */
    public static PopularProductResponse of(List<ProductDto.ProductItem> content) {
        int limit = 10;
        int totalResults = content.size();
        PaginationInfo pageInfo = new PaginationInfo(
                1,
                1,
                totalResults,
                limit,
                false
        );
        return PopularProductResponse.builder()
                .content(content)
                .pageInfo(pageInfo)
                .build();
    }
}
