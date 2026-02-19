package showroomz.domain.review.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByOrderProduct_Id(Long orderProductId);
}
