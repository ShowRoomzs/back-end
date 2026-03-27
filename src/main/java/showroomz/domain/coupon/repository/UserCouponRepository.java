package showroomz.domain.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.entity.UserCoupon;
import showroomz.domain.coupon.type.UserCouponStatus;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    Page<UserCoupon> findByUserOrderByRegisteredAtDesc(Users user, Pageable pageable);

    boolean existsByUserAndCoupon(Users user, Coupon coupon);

    @Query("SELECT uc FROM UserCoupon uc JOIN FETCH uc.coupon WHERE uc.id = :userCouponId AND uc.user.id = :userId")
    Optional<UserCoupon> findByIdAndUserIdWithCoupon(@Param("userCouponId") Long userCouponId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT uc FROM UserCoupon uc
            INNER JOIN FETCH uc.coupon c
            INNER JOIN FETCH c.seller s
            WHERE uc.user.id = :userId
            AND uc.status = :status
            AND s.id = :sellerId
            AND c.startAt <= :now
            AND c.endAt >= :now
            AND (
                NOT EXISTS (SELECT 1 FROM CouponProduct cp0 WHERE cp0.coupon = c)
                OR EXISTS (SELECT 1 FROM CouponProduct cp1 WHERE cp1.coupon = c AND cp1.product.productId = :productId)
            )
            """)
    List<UserCoupon> findApplicableForProductCheckout(
            @Param("userId") Long userId,
            @Param("sellerId") Long sellerId,
            @Param("productId") Long productId,
            @Param("status") UserCouponStatus status,
            @Param("now") LocalDateTime now);

    @Query("SELECT uc.coupon.id FROM UserCoupon uc WHERE uc.user.id = :userId AND uc.coupon.id IN :couponIds")
    Set<Long> findCouponIdsIssuedToUser(@Param("userId") Long userId, @Param("couponIds") Collection<Long> couponIds);
}
