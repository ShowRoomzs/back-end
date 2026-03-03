package showroomz.domain.review.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.api.common.review.dto.ProductReviewSortType;
import showroomz.domain.review.entity.Review;

import java.util.List;

public interface ReviewRepositoryCustom {

    /**
     * 상품 상세 페이지용 리뷰 목록 조회 (동적 쿼리)
     * - productId 일치, optionIds 필터(선택), sortType 정렬
     */
    Page<Review> findAllByProductIdWithFilter(
            Long productId,
            List<Long> optionIds,
            ProductReviewSortType sortType,
            Pageable pageable
    );
}
