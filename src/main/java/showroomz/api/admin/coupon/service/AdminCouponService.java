package showroomz.api.admin.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.coupon.dto.AdminCouponCreateRequest;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.repository.CouponRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class AdminCouponService {

    private final CouponRepository couponRepository;

    /**
     * 관리자 쿠폰 생성.
     * - couponCode 중복 체크
     * - validFrom < validTo 선후 관계 검증
     */
    @Transactional
    public Coupon createCoupon(AdminCouponCreateRequest request) {
        if (couponRepository.existsByCode(request.getCouponCode())) {
            throw new BusinessException(ErrorCode.COUPON_CODE_DUPLICATE);
        }
        if (!request.getValidFrom().isBefore(request.getValidTo())) {
            throw new BusinessException(ErrorCode.INVALID_COUPON_VALIDITY_PERIOD);
        }

        Coupon coupon = new Coupon(
                request.getName(),
                request.getCouponCode(),
                request.getDiscountType(),
                request.getDiscountValue(),
                request.getMinOrderAmount(),
                request.getMaxDiscountAmount(),
                request.getValidFrom(),
                request.getValidTo()
        );
        return couponRepository.save(coupon);
    }
}
