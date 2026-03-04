package showroomz.domain.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.review.entity.ReviewLike;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    boolean existsByReview_IdAndUser_Id(Long reviewId, Long userId);

    Optional<ReviewLike> findByReview_IdAndUser_Id(Long reviewId, Long userId);

    void deleteByReview_IdAndUser_Id(Long reviewId, Long userId);

    @Query("SELECT rl.review.id FROM ReviewLike rl WHERE rl.user.id = :userId AND rl.review.id IN :reviewIds")
    Set<Long> findReviewIdsLikedByUserAndReviewIdIn(
            @Param("userId") Long userId,
            @Param("reviewIds") List<Long> reviewIds
    );
}
