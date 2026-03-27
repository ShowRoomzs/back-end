package showroomz.api.common.coupon.controller;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.common.coupon.docs.CommonCouponControllerDocs;
import showroomz.api.common.coupon.dto.CommonProductCouponItem;
import showroomz.api.common.coupon.service.CommonCouponService;

import java.util.List;

@RestController
@RequestMapping("/v1/common/products")
@RequiredArgsConstructor
public class CommonCouponController implements CommonCouponControllerDocs {

    private final CommonCouponService commonCouponService;

    @Override
    @GetMapping("/{productId}/coupons")
    public ResponseEntity<List<CommonProductCouponItem>> getProductCoupons(
            @PathVariable("productId") Long productId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long userId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
        return ResponseEntity.ok(commonCouponService.getIssuableCouponsForProduct(productId, userId));
    }
}
