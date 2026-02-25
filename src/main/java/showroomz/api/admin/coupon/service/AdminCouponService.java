package showroomz.api.admin.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.coupon.dto.AdminCouponCreateRequest;
import showroomz.api.admin.coupon.dto.AdminCouponResponse;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.repository.CouponRepository;
import showroomz.domain.coupon.type.CouponStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCouponService {

    private final CouponRepository couponRepository;

    /**
     * 관리자 쿠폰 목록 조회 (status 필터링, 최신 등록순 페이징)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminCouponResponse> getCouponList(Integer page, Integer size, CouponStatus status) {
        int pageNumber = (page != null && page >= 0) ? page : 0;
        int pageSize = (size != null && size > 0) ? size : 10;

        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Coupon> couponPage = couponRepository.findAllWithStatusFilter(status, pageable);

        List<AdminCouponResponse> content = couponPage.getContent().stream()
                .map(AdminCouponResponse::from)
                .toList();

        return new PageResponse<>(content, couponPage);
    }

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
