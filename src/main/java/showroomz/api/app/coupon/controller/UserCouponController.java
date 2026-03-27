package showroomz.api.app.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.coupon.docs.UserCouponControllerDocs;
import showroomz.api.app.coupon.dto.CouponDownloadResponse;
import showroomz.api.app.coupon.dto.CouponRegisterRequest;
import showroomz.api.app.coupon.dto.CouponUseRequest;
import showroomz.api.app.coupon.dto.CouponUseResponse;
import showroomz.api.app.coupon.dto.UserCouponDto;
import showroomz.api.app.coupon.dto.UserCouponRegisterResponse;
import showroomz.api.app.coupon.service.UserCouponService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.net.URI;

@RestController
@RequestMapping("/v1/user/coupons")
@RequiredArgsConstructor
public class UserCouponController implements UserCouponControllerDocs {

    private final UserCouponService userCouponService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<UserCouponDto>> getMyCoupons(
            @AuthenticationPrincipal UserPrincipal principal,
            @ModelAttribute PagingRequest pagingRequest) {
        PageResponse<UserCouponDto> response = userCouponService.getMyCoupons(principal.getUsername(), pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{couponId}/download")
    public ResponseEntity<CouponDownloadResponse> downloadCoupon(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("couponId") Long couponId) {
        CouponDownloadResponse response = userCouponService.downloadCoupon(principal.getUsername(), couponId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{userCouponId}/use")
    public ResponseEntity<CouponUseResponse> useCoupon(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("userCouponId") Long userCouponId,
            @Valid @RequestBody CouponUseRequest request) {
        CouponUseResponse response = userCouponService.useCoupon(
                principal.getUsername(), userCouponId, request.getOrderAmount());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping
    public ResponseEntity<UserCouponRegisterResponse> registerCoupon(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CouponRegisterRequest request) {
        var userCoupon = userCouponService.registerCoupon(principal.getUsername(), request.getCode());
        var coupon = userCoupon.getCoupon();
        URI location = URI.create("/v1/user/coupons/" + userCoupon.getId());
        UserCouponRegisterResponse response = UserCouponRegisterResponse.builder()
                .message("쿠폰이 정상적으로 등록되었습니다.")
                .userCouponId(userCoupon.getId())
                .name(coupon.getName())
                .build();
        return ResponseEntity.created(location).body(response);
    }
}
