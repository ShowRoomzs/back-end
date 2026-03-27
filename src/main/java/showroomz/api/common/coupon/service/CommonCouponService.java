package showroomz.api.common.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.coupon.dto.CommonProductCouponItem;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.repository.CouponProductRepository;
import showroomz.domain.coupon.repository.UserCouponRepository;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonCouponService {

    private final ProductRepository productRepository;
    private final CouponProductRepository couponProductRepository;
    private final UserCouponRepository userCouponRepository;

    public List<CommonProductCouponItem> getIssuableCouponsForProduct(Long productId, Long userIdOrNull) {
        productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = couponProductRepository.findActiveCouponsForProduct(productId, now);
        if (coupons.isEmpty()) {
            return List.of();
        }

        Set<Long> downloadedIds = new HashSet<>();
        if (userIdOrNull != null) {
            List<Long> couponIds = coupons.stream().map(Coupon::getId).toList();
            downloadedIds = userCouponRepository.findCouponIdsIssuedToUser(userIdOrNull, couponIds);
        }

        Set<Long> finalDownloadedIds = downloadedIds;
        return coupons.stream()
                .map(c -> toItem(c, finalDownloadedIds.contains(c.getId())))
                .toList();
    }

    private static CommonProductCouponItem toItem(Coupon c, boolean downloaded) {
        return CommonProductCouponItem.builder()
                .couponId(c.getId())
                .name(c.getName())
                .discountType(c.getDiscountType())
                .discountValue(c.getDiscountValue())
                .minimumOrderPrice(c.getMinOrderAmount())
                .validUntil(c.getEndAt())
                .isDownloaded(downloaded)
                .build();
    }
}
