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
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.review.entity.Review;
import showroomz.domain.review.entity.ReviewImage;
import showroomz.domain.review.repository.ReviewRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final UserRepository userRepository;
    private final OrderProductRepository orderProductRepository;
    private final ReviewRepository reviewRepository;

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
}
