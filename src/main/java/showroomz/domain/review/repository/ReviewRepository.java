package showroomz.domain.review.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.review.entity.Review;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByOrderProduct_Id(Long orderProductId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT COUNT(r) FROM Review r
            JOIN r.orderProduct op
            JOIN op.variant v
            WHERE v.product.productId = :productId
            """)
    long countByProductId(@Param("productId") Long productId);

    @Query("""
            SELECT AVG(r.rating) FROM Review r
            JOIN r.orderProduct op
            JOIN op.variant v
            WHERE v.product.productId = :productId
            """)
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.user
            JOIN r.orderProduct op
            JOIN op.variant v
            WHERE v.product.productId = :productId
            ORDER BY r.createdAt DESC
            """)
    List<Review> findTop3ByProductIdOrderByCreatedAtDesc(
            @Param("productId") Long productId,
            Pageable pageable);
}
