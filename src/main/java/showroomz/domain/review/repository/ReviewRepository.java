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

public interface ReviewRepository extends JpaRepository<Review, Long>, ReviewRepositoryCustom {

    long countByUser_Id(Long userId);

    Page<Review> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByOrderProduct_Id(Long orderProductId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") Long id);

    /**
     * 상품별 리뷰 수 일괄 조회 (Batch Fetching)
     * @return List of [productId, count]
     */
    @Query("""
            SELECT v.product.productId, COUNT(r) FROM Review r
            JOIN r.orderProduct op
            JOIN op.variant v
            WHERE v.product.productId IN :productIds
            GROUP BY v.product.productId
            """)
    List<Object[]> countByProductIds(@Param("productIds") List<Long> productIds);
}
