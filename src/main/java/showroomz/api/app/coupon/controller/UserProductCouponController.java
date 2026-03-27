package showroomz.api.app.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.coupon.docs.UserProductCouponControllerDocs;
import showroomz.api.app.coupon.dto.ProductApplicableCouponDto;
import showroomz.api.app.coupon.service.UserCouponService;

import java.util.List;

@RestController
@RequestMapping("/v1/user/products")
@RequiredArgsConstructor
public class UserProductCouponController implements UserProductCouponControllerDocs {

    private final UserCouponService userCouponService;

    @Override
    @GetMapping("/{productId}/coupons")
    public ResponseEntity<List<ProductApplicableCouponDto>> getApplicableCouponsForProduct(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("productId") Long productId) {
        List<ProductApplicableCouponDto> list = userCouponService.getApplicableCouponsForProduct(
                principal.getUsername(), productId);
        return ResponseEntity.ok(list);
    }
}
