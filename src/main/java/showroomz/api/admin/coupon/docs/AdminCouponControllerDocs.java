package showroomz.api.admin.coupon.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.coupon.dto.*;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Coupon", description = "관리자 쿠폰 관리 API")
public interface AdminCouponControllerDocs {

    @Operation(summary = "관리자 쿠폰 목록 조회", description = "동적 필터(searchType, keyword, targetAudience, status, dateFrom, dateTo) + 페이징")
    ResponseEntity<PageResponse<AdminCouponResponse>> getCouponList(
            @ParameterObject PagingRequest pagingRequest,
            @ParameterObject @ModelAttribute AdminCouponSearchCondition condition
    );

    @Operation(summary = "관리자 쿠폰 생성", description = "쿠폰 발급번호를 시스템 자동 채번하여 생성")
    ResponseEntity<AdminCouponCreateResponse> createCoupon(@Valid @RequestBody AdminCouponCreateRequest request);

    @Operation(summary = "관리자 쿠폰 상세 조회", description = "쿠폰 상세, KPI, 쇼룸 수락 현황 조회")
    ResponseEntity<AdminCouponDetailResponse> getCouponDetail(Long couponId);

    @Operation(summary = "관리자 쿠폰 단건 수정", description = "ACTIVE 상태에서는 할인/최소주문금액/유효기간 필드 수정 차단")
    ResponseEntity<Void> updateCoupon(Long couponId, @Valid @RequestBody AdminCouponUpdateRequest request);

    @Operation(summary = "관리자 쿠폰 일괄 중지", description = "couponIds 대상 상태를 STOPPED로 변경")
    ResponseEntity<AdminCouponBulkResponse> bulkStop(@Valid @RequestBody AdminCouponBulkRequest request);

    @Operation(summary = "관리자 쿠폰 일괄 삭제", description = "couponIds 대상 쿠폰 일괄 삭제")
    ResponseEntity<AdminCouponBulkResponse> bulkDelete(@Valid @RequestBody AdminCouponBulkRequest request);
}
