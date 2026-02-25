package showroomz.domain.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.type.CouponStatus;

public interface CouponRepositoryCustom {

    /**
     * 관리자용 쿠폰 목록 조회 (status 필터링, createdAt DESC 정렬)
     */
    Page<Coupon> findAllWithStatusFilter(CouponStatus status, Pageable pageable);
}
