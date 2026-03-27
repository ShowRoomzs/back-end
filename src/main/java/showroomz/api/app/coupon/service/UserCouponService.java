package showroomz.api.app.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.coupon.dto.CouponDownloadResponse;
import showroomz.api.app.coupon.dto.CouponUseResponse;
import showroomz.api.app.coupon.dto.ProductApplicableCouponDto;
import showroomz.api.app.coupon.dto.UserCouponDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.entity.UserCoupon;
import showroomz.domain.coupon.repository.CouponRepository;
import showroomz.domain.coupon.repository.UserCouponRepository;
import showroomz.domain.coupon.type.DiscountType;
import showroomz.domain.coupon.type.UserCouponStatus;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserCouponService {

    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductApplicableCouponDto> getApplicableCouponsForProduct(String username, Long productId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findByProductIdWithMarketAndSeller(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        Long sellerId = product.getMarket().getSeller().getId();
        LocalDateTime now = LocalDateTime.now();

        List<UserCoupon> userCoupons = userCouponRepository.findApplicableForProductCheckout(
                user.getId(), sellerId, productId, UserCouponStatus.AVAILABLE, now);

        return userCoupons.stream()
                .map(ProductApplicableCouponDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponUseResponse useCoupon(String username, Long userCouponId, BigDecimal orderAmount) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserCoupon userCoupon = userCouponRepository.findByIdAndUserIdWithCoupon(userCouponId, user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_COUPON_NOT_FOUND));

        Coupon coupon = userCoupon.getCoupon();
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }

        BigDecimal minOrder = coupon.getMinOrderAmount();
        if (minOrder != null && orderAmount.compareTo(minOrder) < 0) {
            throw new BusinessException(ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET);
        }

        BigDecimal discountAmount = calculateDiscountAmount(coupon, orderAmount);
        BigDecimal finalOrderAmount = orderAmount.subtract(discountAmount);
        if (finalOrderAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalOrderAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            finalOrderAmount = finalOrderAmount.setScale(2, RoundingMode.HALF_UP);
        }

        return CouponUseResponse.builder()
                .userCouponId(userCouponId)
                .discountAmount(discountAmount)
                .finalOrderAmount(finalOrderAmount)
                .message("쿠폰이 성공적으로 적용되었습니다.")
                .build();
    }

    private static BigDecimal calculateDiscountAmount(Coupon coupon, BigDecimal orderAmount) {
        if (coupon.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            return coupon.getDiscountValue().min(orderAmount).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal raw = orderAmount.multiply(coupon.getDiscountValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal maxDiscount = coupon.getMaxDiscountAmount();
        if (maxDiscount != null && raw.compareTo(maxDiscount) > 0) {
            raw = maxDiscount;
        }
        if (raw.compareTo(orderAmount) > 0) {
            raw = orderAmount;
        }
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserCouponDto> getMyCoupons(String username, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = pagingRequest.toPageable(Sort.by(Sort.Direction.DESC, "registeredAt"));
        Page<UserCoupon> page = userCouponRepository.findByUserOrderByRegisteredAtDesc(user, pageable);
        List<UserCouponDto> content = page.getContent().stream()
                .map(UserCouponDto::from)
                .collect(Collectors.toList());
        return new PageResponse<>(content, page);
    }

    @Transactional
    public UserCoupon registerCoupon(String username, String code) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Coupon coupon = couponRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }

        if (userCouponRepository.existsByUserAndCoupon(user, coupon)) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_REGISTERED);
        }

        assertRemainingStock(coupon);

        coupon.decreaseRemainingForIssuance();
        UserCoupon userCoupon = userCouponRepository.save(new UserCoupon(user, coupon));
        return userCoupon;
    }

    @Transactional
    public CouponDownloadResponse downloadCoupon(String username, Long couponId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }

        if (userCouponRepository.existsByUserAndCoupon(user, coupon)) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_REGISTERED);
        }

        assertRemainingStock(coupon);

        coupon.decreaseRemainingForIssuance();
        UserCoupon saved = userCouponRepository.save(new UserCoupon(user, coupon));
        return CouponDownloadResponse.builder()
                .userCouponId(saved.getId())
                .message("쿠폰이 성공적으로 발급되었습니다.")
                .build();
    }

    private static void assertRemainingStock(Coupon coupon) {
        Integer remaining = coupon.getRemainingQuantity();
        if (remaining != null && remaining <= 0) {
            throw new BusinessException(ErrorCode.COUPON_QUANTITY_EXHAUSTED);
        }
    }
}
