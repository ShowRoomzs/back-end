package showroomz.api.common.review.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.review.dto.ProductReviewResponse;
import showroomz.api.common.review.dto.ProductReviewSortType;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.review.entity.Review;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.domain.review.repository.ReviewLikeRepository;
import showroomz.domain.review.repository.ReviewRepository;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonProductReviewService {

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;

    /**
     * 상품 상세 페이지용 리뷰 목록 조회.
     * - 비회원: isLikedByMe = false
     * - 회원: ReviewLike 조회로 isLikedByMe 매핑
     * - authorName 마스킹 적용 (예: 이종훈 -> 이*훈)
     */
    public ProductReviewResponse getProductReviews(
            Long productId,
            int page,
            int size,
            ProductReviewSortType sortType,
            List<Long> optionIds,
            Long currentUserId
    ) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Pageable pageable = createPageable(page, size);
        Page<Review> reviewPage = reviewRepository.findAllByProductIdWithFilter(
                productId, optionIds, sortType, pageable);

        List<Review> reviews = reviewPage.getContent();
        if (reviews.isEmpty()) {
            return ProductReviewResponse.of(List.of(), reviewPage);
        }

        Set<Long> likedReviewIds = currentUserId != null
                ? reviewLikeRepository.findReviewIdsLikedByUserAndReviewIdIn(
                        currentUserId, reviews.stream().map(Review::getId).toList())
                : Set.of();

        List<ProductReviewResponse.ProductReviewItem> items = reviews.stream()
                .map(review -> toProductReviewItem(review, likedReviewIds.contains(review.getId())))
                .toList();

        return ProductReviewResponse.of(items, reviewPage);
    }

    private Pageable createPageable(int page, int size) {
        int pageNumber = page >= 0 ? page : 0;
        return PageRequest.of(pageNumber, size);
    }

    private ProductReviewResponse.ProductReviewItem toProductReviewItem(Review review, boolean isLikedByMe) {
        String authorName = maskAuthorName(review.getUser().getNickname());
        if (authorName == null || authorName.isBlank()) {
            authorName = maskAuthorName(review.getUser().getName());
        }
        if (authorName == null || authorName.isBlank()) {
            authorName = "***";
        }

        return ProductReviewResponse.ProductReviewItem.builder()
                .reviewId(review.getId())
                .authorName(authorName)
                .rating(review.getRating())
                .content(review.getContent())
                .imageUrls(review.getImageUrlsOrdered())
                .createdAt(review.getCreatedAt())
                .likeCount(review.getLikeCount())
                .isLikedByMe(isLikedByMe)
                .optionName(review.getOrderProduct().getOptionName())
                .build();
    }

    /**
     * 작성자명 마스킹: 이종훈 -> 이*훈 (첫글자 + * + 마지막글자)
     * - 1글자: * 로 대체
     * - 2글자: 첫글자 + *
     * - 3글자 이상: 첫글자 + * + 마지막글자
     */
    private String maskAuthorName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        int len = trimmed.length();
        if (len == 0) {
            return null;
        }
        if (len == 1) {
            return "*";
        }
        if (len == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0) + "*" + trimmed.charAt(len - 1);
    }
}
