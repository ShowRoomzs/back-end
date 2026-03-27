package showroomz.api.seller.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.coupon.docs.SellerCouponControllerDocs;
import showroomz.api.seller.coupon.dto.SellerCouponCreateRequest;
import showroomz.api.seller.coupon.dto.SellerCouponCreateResponse;
import showroomz.api.seller.coupon.service.SellerCouponService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/seller/coupons")
@RequiredArgsConstructor
public class SellerCouponController implements SellerCouponControllerDocs {

    private final SellerCouponService sellerCouponService;

    @Override
    @PostMapping
    public ResponseEntity<SellerCouponCreateResponse> createCoupon(@Valid @RequestBody SellerCouponCreateRequest request) {
        String sellerEmail = getCurrentSellerEmail();
        SellerCouponCreateResponse response = sellerCouponService.createCoupon(sellerEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private static String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}
