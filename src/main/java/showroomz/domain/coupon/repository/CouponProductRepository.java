package showroomz.domain.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.entity.CouponProduct;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CouponProductRepository extends JpaRepository<CouponProduct, Long> {

    @Query("""
            SELECT cp.coupon FROM CouponProduct cp
            WHERE cp.product.productId = :productId
            AND cp.coupon.startAt <= :now
            AND cp.coupon.endAt >= :now
            ORDER BY cp.coupon.createdAt DESC
            """)
    List<Coupon> findActiveCouponsForProduct(@Param("productId") Long productId, @Param("now") LocalDateTime now);
}
