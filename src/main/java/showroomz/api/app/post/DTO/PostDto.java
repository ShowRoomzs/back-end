package showroomz.api.app.post.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class PostDto {

    @Schema(description = "포스트에 등록된 상품 한 건 응답")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostProductResponse {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;
        @Schema(description = "상품 대표 이미지 URL")
        private String productImageUrl;
        @Schema(description = "마켓명")
        private String marketName;
        @Schema(description = "상품명")
        private String productName;
        @Schema(description = "할인율 (%)", example = "10")
        private Integer discountRate;
        @Schema(description = "가격", example = "29900")
        private Integer price;
        @Schema(description = "위시리스트 수")
        private Long wishlistCount;
        @Schema(description = "리뷰 수")
        private Long reviewCount;
        @Schema(description = "현재 사용자 위시리스트 여부")
        private Boolean isWishlisted;
    }

    @Schema(description = "게시글 상세 응답 (포스트에 등록된 상품 목록 포함)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostDetailResponse {
        @Schema(description = "게시글 ID", example = "123")
        private Long postId;
        @Schema(description = "쇼룸 ID", example = "10")
        private Long showroomId;
        @Schema(description = "쇼룸명")
        private String showroomName;
        @Schema(description = "쇼룸 대표 이미지 URL")
        private String showroomImageUrl;
        @Schema(description = "제목")
        private String title;
        @Schema(description = "본문 내용")
        private String content;
        @Schema(description = "게시글 이미지 URL 목록 (다중 이미지)")
        private List<String> imageUrls;
        @Schema(description = "조회수")
        private Long viewCount;
        @Schema(description = "현재 사용자 위시리스트 여부")
        private Boolean isWishlisted;
        @Schema(description = "위시리스트 수")
        private Long wishlistCount;
        @Schema(description = "포스트에 등록된 상품 목록")
        private List<PostProductResponse> registeredProducts;

        @Schema(description = "생성 일시", example = "2026-03-04T12:34:56")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;

        @Schema(description = "수정 일시", example = "2026-03-04T13:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime modifiedAt;
    }

    @Schema(description = "피드 아이템 래퍼 (contentType으로 추후 추첨 등 확장 가능)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedItemResponse {
        @Schema(description = "콘텐츠 타입", example = "POST")
        @Builder.Default
        private String contentType = "POST";
        @Schema(description = "게시글 목록 항목")
        private PostListItem post;
    }

    @Schema(description = "게시글 목록 항목")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostListItem {
        @Schema(description = "게시글 ID", example = "123")
        private Long postId;
        @Schema(description = "쇼룸 ID", example = "10")
        private Long showroomId;
        @Schema(description = "쇼룸명")
        private String showroomName;
        @Schema(description = "쇼룸 대표 이미지 URL")
        private String showroomImageUrl;
        @Schema(description = "제목")
        private String title;
        @Schema(description = "게시글 이미지 URL 목록 (다중 이미지)")
        private List<String> imageUrls;
        @Schema(description = "조회수")
        private Long viewCount;
        @Schema(description = "현재 사용자 위시리스트 여부")
        private Boolean isWishlisted;
        @Schema(description = "위시리스트 수")
        private Long wishlistCount;

        @Schema(description = "생성 일시", example = "2026-03-04T12:34:56")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
