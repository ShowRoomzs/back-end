package showroomz.api.app.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.review.entity.Review;

import java.time.LocalDateTime;
import java.util.List;

public class ReviewDto {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "리뷰 작성 가능 상품 목록 항목")
    public static class WritableItem {
        @Schema(description = "주문 상품 ID")
        private Long orderProductId;

        @Schema(description = "상품명")
        private String productName;

        @Schema(description = "옵션명")
        private String optionName;

        @Schema(description = "수량")
        private Integer quantity;

        @Schema(description = "가격")
        private Integer price;

        @Schema(description = "이미지 URL")
        private String imageUrl;

        @Schema(description = "주문 일시")
        private LocalDateTime orderDate;

        public static WritableItem from(OrderProduct op) {
            return WritableItem.builder()
                    .orderProductId(op.getId())
                    .productName(op.getProductName())
                    .optionName(op.getOptionName())
                    .quantity(op.getQuantity())
                    .price(op.getPrice())
                    .imageUrl(op.getImageUrl())
                    .orderDate(op.getOrderDate())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "내가 작성한 리뷰 목록 항목")
    public static class ReviewItem {
        @Schema(description = "리뷰 ID")
        private Long reviewId;

        @Schema(description = "평점 (1-5)")
        private Integer rating;

        @Schema(description = "리뷰 내용")
        private String content;

        @Schema(description = "리뷰 이미지 URL 목록")
        private List<String> imageUrls;

        @Schema(description = "작성 일시")
        private LocalDateTime createdAt;

        @Schema(description = "상품 정보")
        private ProductInfo product;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @Schema(description = "상품 정보 (상품명, 옵션명)")
        public static class ProductInfo {
            @Schema(description = "상품명")
            private String productName;

            @Schema(description = "옵션명")
            private String optionName;
        }

        public static ReviewItem from(Review review) {
            return ReviewItem.builder()
                    .reviewId(review.getId())
                    .rating(review.getRating())
                    .content(review.getContent())
                    .imageUrls(review.getImageUrlsOrdered())
                    .createdAt(review.getCreatedAt())
                    .product(ProductInfo.builder()
                            .productName(review.getOrderProduct().getProductName())
                            .optionName(review.getOrderProduct().getOptionName())
                            .build())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "리뷰 좋아요 토글 응답")
    public static class LikeToggleResponse {

        @Schema(description = "리뷰 ID")
        private Long reviewId;

        @Schema(description = "현재 유저의 좋아요 여부 (true: 좋아요 누름, false: 좋아요 취소)")
        private Boolean isLiked;

        @Schema(description = "리뷰의 총 좋아요 수")
        private Integer likeCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "리뷰 수정 응답")
    public static class UpdateResponse {

        @Schema(description = "수정된 리뷰 ID")
        private Long reviewId;

        @Schema(description = "결과 메시지")
        private String message;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "리뷰 삭제 응답")
    public static class DeleteResponse {

        @Schema(description = "삭제된 리뷰 ID")
        private Long reviewId;

        @Schema(description = "결과 메시지")
        private String message;
    }
}
