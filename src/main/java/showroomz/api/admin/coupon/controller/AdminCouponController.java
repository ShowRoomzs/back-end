package showroomz.api.admin.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.coupon.dto.AdminCouponCreateRequest;
import showroomz.api.admin.coupon.dto.AdminCouponCreateResponse;
import showroomz.api.admin.coupon.dto.AdminCouponResponse;
import showroomz.api.admin.coupon.service.AdminCouponService;
import showroomz.api.admin.coupon.docs.AdminCouponControllerDocs;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.type.CouponStatus;
import showroomz.global.dto.PageResponse;

import java.net.URI;
import java.util.Objects;

@RestController
@RequestMapping("/v1/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController implements AdminCouponControllerDocs {

    private final AdminCouponService adminCouponService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminCouponResponse>> getCouponList(
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "10") Integer size,
            @RequestParam(name = "status", required = false) String status) {

        CouponStatus statusEnum = parseCouponStatus(status);
        PageResponse<AdminCouponResponse> response = adminCouponService.getCouponList(
                page, size, statusEnum);
        return ResponseEntity.ok(response);
    }

    private static CouponStatus parseCouponStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CouponStatus.valueOf(status.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    @PostMapping
    public ResponseEntity<AdminCouponCreateResponse> createCoupon(@Valid @RequestBody AdminCouponCreateRequest request) {
        Coupon coupon = adminCouponService.createCoupon(request);
        URI location = Objects.requireNonNull(URI.create("/v1/admin/coupons/" + coupon.getId()));
        AdminCouponCreateResponse response = AdminCouponCreateResponse.builder()
                .message("쿠폰이 정상적으로 등록되었습니다.")
                .id(coupon.getId())
                .name(coupon.getName())
                .build();
        return ResponseEntity.created(location).body(response);
    }
}
