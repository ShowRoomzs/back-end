package showroomz.api.app.review.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.app.review.dto.ReviewDto;
import showroomz.api.app.review.dto.ReviewRegisterRequest;
import showroomz.api.app.review.dto.ReviewRegisterResponse;
import showroomz.api.app.review.dto.ReviewUpdateRequest;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.review.entity.Review;
import showroomz.domain.review.entity.ReviewImage;
import showroomz.domain.review.entity.ReviewLike;
import showroomz.domain.review.repository.ReviewLikeRepository;
import showroomz.domain.review.repository.ReviewRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final UserRepository userRepository;
    private final OrderProductRepository orderProductRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;

    public PageResponse<ReviewDto.WritableItem> getWritableList(Long userId) {
        List<OrderProduct> list = orderProductRepository.findWritableByUserId(
                userId, OrderProductStatus.PURCHASE_CONFIRMED);
        List<ReviewDto.WritableItem> content = list.stream().map(ReviewDto.WritableItem::from).toList();
        Page<ReviewDto.WritableItem> page = new PageImpl<>(
                content,
                PageRequest.of(0, Math.max(1, content.size())),
                content.size());
        return new PageResponse<>(content, page);
    }

    @Transactional
    public ReviewRegisterResponse registerReview(Long userId, ReviewRegisterRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        OrderProduct orderProduct = orderProductRepository.findById(request.getOrderProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_PRODUCT_NOT_FOUND));

        // 본인 주문인지 확인
        if (!orderProductRepository.existsByIdAndOrder_User_Id(
                request.getOrderProductId(), userId)) {
            throw new BusinessException(ErrorCode.ORDER_PRODUCT_ACCESS_DENIED);
        }

        // 구매 확정 상태인지 확인
        if (!orderProduct.isPurchaseConfirmed()) {
            throw new BusinessException(ErrorCode.ORDER_PRODUCT_NOT_WRITABLE);
        }

        // 이미 리뷰 작성 여부 확인
        if (reviewRepository.existsByOrderProduct_Id(request.getOrderProductId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = Review.builder()
                .orderProduct(orderProduct)
                .user(user)
                .rating(request.getRating())
                .content(request.getContent())
                .isPromotionAgreed(request.getIsPromotionAgreed())
                .isPersonalInfoAgreed(request.getIsPersonalInfoAgreed())
                .build();

        List<String> imageUrls = request.getImageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            List<ReviewImage> images = IntStream.range(0, imageUrls.size())
                    .mapToObj(i -> ReviewImage.builder()
                            .review(review)
                            .url(imageUrls.get(i))
                            .sequence(i)
                            .build())
                    .toList();
            review.addImages(images);
        }

        Review saved = reviewRepository.save(review);

        return ReviewRegisterResponse.builder()
                .reviewId(saved.getId())
                .build();
    }

    public PageResponse<ReviewDto.ReviewItem> getMyReviews(Long userId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
        return new PageResponse<>(
                page.getContent().stream().map(ReviewDto.ReviewItem::from).toList(),
                page);
    }

    @Transactional
    public ReviewDto.UpdateResponse updateReview(Long userId, Long reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        validateReviewAuthor(review, userId);

        review.update(request.getRating(), request.getContent());

        List<String> imageUrls = request.getImageUrls();
        List<ReviewImage> newImages = new ArrayList<>();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            newImages = IntStream.range(0, imageUrls.size())
                    .mapToObj(i -> ReviewImage.builder()
                            .review(review)
                            .url(imageUrls.get(i))
                            .sequence(i)
                            .build())
                    .toList();
        }
        review.replaceImages(newImages);

        Review saved = reviewRepository.save(review);
        return ReviewDto.UpdateResponse.builder()
                .reviewId(saved.getId())
                .message("리뷰가 성공적으로 수정되었습니다.")
                .build();
    }

    @Transactional
    public ReviewDto.DeleteResponse deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        validateReviewAuthor(review, userId);

        Long deletedReviewId = review.getId();
        reviewRepository.delete(review);

        return ReviewDto.DeleteResponse.builder()
                .reviewId(deletedReviewId)
                .message("리뷰가 성공적으로 삭제되었습니다.")
                .build();
    }

    @Transactional
    public ReviewDto.LikeToggleResponse toggleLike(Long userId, Long reviewId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        boolean alreadyLiked = reviewLikeRepository.existsByReview_IdAndUser_Id(reviewId, userId);

        if (alreadyLiked) {
            reviewLikeRepository.deleteByReview_IdAndUser_Id(reviewId, userId);
            review.decreaseLikeCount();
            return ReviewDto.LikeToggleResponse.builder()
                    .reviewId(review.getId())
                    .isLiked(false)
                    .likeCount(review.getLikeCount())
                    .build();
        } else {
            ReviewLike reviewLike = ReviewLike.builder()
                    .review(review)
                    .user(user)
                    .build();
            reviewLikeRepository.save(reviewLike);
            review.increaseLikeCount();
            return ReviewDto.LikeToggleResponse.builder()
                    .reviewId(review.getId())
                    .isLiked(true)
                    .likeCount(review.getLikeCount())
                    .build();
        }
    }

    private void validateReviewAuthor(Review review, Long userId) {
        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }
    }
}
