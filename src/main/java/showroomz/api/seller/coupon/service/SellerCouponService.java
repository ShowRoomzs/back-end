package showroomz.api.seller.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.coupon.dto.SellerCouponCreateRequest;
import showroomz.api.seller.coupon.dto.SellerCouponCreateResponse;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.entity.CouponProduct;
import showroomz.domain.coupon.repository.CouponProductRepository;
import showroomz.domain.coupon.repository.CouponRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerCouponService {

    private final SellerRepository sellerRepository;
    private final CouponRepository couponRepository;
    private final CouponProductRepository couponProductRepository;
    private final ProductRepository productRepository;

    @Transactional
    public SellerCouponCreateResponse createCoupon(String sellerEmail, SellerCouponCreateRequest request) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));

        if (couponRepository.existsByCode(request.getCouponCode())) {
            throw new BusinessException(ErrorCode.COUPON_CODE_DUPLICATE);
        }
        if (!request.getValidFrom().isBefore(request.getValidTo())) {
            throw new BusinessException(ErrorCode.INVALID_COUPON_VALIDITY_PERIOD);
        }

        LinkedHashSet<Long> uniqueProductIds = new LinkedHashSet<>(request.getProductIds());
        List<Product> products = productRepository.findAllByProductIdsAndSellerId(uniqueProductIds, seller.getId());
        if (products.size() != uniqueProductIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED_BY_SELLER);
        }

        Integer totalQty = request.getTotalQuantity();
        Integer remainingQty = (totalQty != null) ? totalQty : null;

        Coupon coupon = new Coupon(
                request.getName(),
                request.getCouponCode(),
                request.getDiscountType(),
                request.getDiscountValue(),
                request.getMinOrderAmount(),
                request.getMaxDiscountAmount(),
                request.getValidFrom(),
                request.getValidTo(),
                totalQty,
                remainingQty,
                seller
        );
        Coupon saved = couponRepository.save(coupon);

        List<CouponProduct> mappings = products.stream()
                .map(p -> new CouponProduct(saved, p))
                .toList();
        couponProductRepository.saveAll(mappings);

        return SellerCouponCreateResponse.builder()
                .couponId(saved.getId())
                .message("쿠폰이 등록되었습니다.")
                .build();
    }
}
