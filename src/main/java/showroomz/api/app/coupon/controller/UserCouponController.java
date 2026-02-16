package showroomz.api.app.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.coupon.dto.CouponRegisterRequest;
import showroomz.api.app.coupon.dto.UserCouponDto;
import showroomz.api.app.coupon.service.UserCouponService;
import showroomz.api.app.docs.UserCouponControllerDocs;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

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
    @PostMapping
    public ResponseEntity<Void> registerCoupon(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CouponRegisterRequest request) {
        userCouponService.registerCoupon(principal.getUsername(), request.getCode());
        return ResponseEntity.noContent().build();
    }
}
