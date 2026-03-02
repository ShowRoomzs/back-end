package showroomz.domain.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.review.entity.ReviewLike;

import java.util.Optional;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    boolean existsByReview_IdAndUser_Id(Long reviewId, Long userId);

    Optional<ReviewLike> findByReview_IdAndUser_Id(Long reviewId, Long userId);

    void deleteByReview_IdAndUser_Id(Long reviewId, Long userId);
}
