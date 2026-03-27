package showroomz.domain.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.entity.UserCoupon;
import showroomz.domain.member.user.entity.Users;

import java.util.Optional;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    Page<UserCoupon> findByUserOrderByRegisteredAtDesc(Users user, Pageable pageable);

    boolean existsByUserAndCoupon(Users user, Coupon coupon);

    @Query("SELECT uc FROM UserCoupon uc JOIN FETCH uc.coupon WHERE uc.id = :userCouponId AND uc.user.id = :userId")
    Optional<UserCoupon> findByIdAndUserIdWithCoupon(@Param("userCouponId") Long userCouponId, @Param("userId") Long userId);
}
