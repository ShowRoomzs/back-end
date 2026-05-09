package showroomz.api.admin.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.coupon.dto.*;
import showroomz.api.admin.coupon.service.AdminCouponService;
import showroomz.api.admin.coupon.docs.AdminCouponControllerDocs;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

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
            @ParameterObject @ModelAttribute PagingRequest pagingRequest,
            @ParameterObject @ModelAttribute AdminCouponSearchCondition condition) {
        PageResponse<AdminCouponResponse> response = adminCouponService.getCouponList(pagingRequest, condition);
        return ResponseEntity.ok(response);
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

    @Override
    @GetMapping("/{couponId}")
    public ResponseEntity<AdminCouponDetailResponse> getCouponDetail(@PathVariable Long couponId) {
        return ResponseEntity.ok(adminCouponService.getCouponDetail(couponId));
    }

    @Override
    @PutMapping("/{couponId}")
    public ResponseEntity<Void> updateCoupon(@PathVariable Long couponId, @Valid @RequestBody AdminCouponUpdateRequest request) {
        adminCouponService.updateCoupon(couponId, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/bulk-stop")
    public ResponseEntity<AdminCouponBulkResponse> bulkStop(@Valid @RequestBody AdminCouponBulkRequest request) {
        return ResponseEntity.ok(adminCouponService.bulkStop(request.getCouponIds()));
    }

    @Override
    @PostMapping("/bulk-delete")
    public ResponseEntity<AdminCouponBulkResponse> bulkDelete(@Valid @RequestBody AdminCouponBulkRequest request) {
        return ResponseEntity.ok(adminCouponService.bulkDelete(request.getCouponIds()));
    }
}
