package showroomz.api.common.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import showroomz.global.dto.PaginationInfo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 상세 페이지용 리뷰 목록 조회 API 응답 DTO.
 * content 리스트와 페이징 메타데이터를 포함합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "상품 리뷰 목록 페이징 응답")
public class ProductReviewResponse {

    @Schema(description = "리뷰 목록")
    private List<ProductReviewItem> content;

    @Schema(description = "페이징 메타데이터")
    private PaginationInfo pageInfo;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상품 리뷰 항목")
    public static class ProductReviewItem {

        @Schema(description = "리뷰 ID")
        private Long reviewId;

        @Schema(description = "작성자명 (마스킹 적용)")
        private String authorName;

        @Schema(description = "평점 (1-5)")
        private Integer rating;

        @Schema(description = "리뷰 내용")
        private String content;

        @Schema(description = "리뷰 이미지 URL 목록")
        private List<String> imageUrls;

        @Schema(description = "작성 일시")
        private LocalDateTime createdAt;

        @Schema(description = "좋아요 수")
        private Integer likeCount;

        @Schema(description = "현재 로그인 유저의 좋아요 여부 (비회원: false)")
        private Boolean isLikedByMe;

        @Schema(description = "구매 옵션명")
        private String optionName;
    }

    /**
     * PageResponse와 동일한 구조로 생성 (content + pageInfo)
     */
    public static ProductReviewResponse of(List<ProductReviewItem> content, Page<?> page) {
        return ProductReviewResponse.builder()
                .content(content)
                .pageInfo(new PaginationInfo(page))
                .build();
    }
}
