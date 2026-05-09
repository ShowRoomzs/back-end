package showroomz.domain.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.type.CouponStatus;
import showroomz.domain.coupon.type.TargetAudience;

import java.time.LocalDateTime;

public interface CouponRepositoryCustom {

    Page<Coupon> searchAdminCoupons(String searchType, String keyword, TargetAudience targetAudience,
                                    CouponStatus status, LocalDateTime dateFrom, LocalDateTime dateTo,
                                    Pageable pageable);
}
