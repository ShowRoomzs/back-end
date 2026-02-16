package showroomz.api.admin.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.coupon.dto.AdminCouponCreateRequest;
import showroomz.api.admin.coupon.service.AdminCouponService;
import showroomz.api.admin.docs.AdminCouponControllerDocs;

import java.net.URI;
import java.util.Objects;

@RestController
@RequestMapping("/v1/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController implements AdminCouponControllerDocs {

    private final AdminCouponService adminCouponService;

    @Override
    @PostMapping
    public ResponseEntity<Void> createCoupon(@Valid @RequestBody AdminCouponCreateRequest request) {
        Long couponId = adminCouponService.createCoupon(request);
        URI location = Objects.requireNonNull(URI.create("/v1/admin/coupons/" + couponId));
        return ResponseEntity.created(location).build();
    }
}
