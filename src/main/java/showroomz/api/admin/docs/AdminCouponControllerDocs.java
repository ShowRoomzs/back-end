package showroomz.api.admin.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.coupon.dto.AdminCouponCreateRequest;
import showroomz.api.admin.coupon.dto.AdminCouponCreateResponse;
import showroomz.api.app.auth.DTO.ErrorResponse;

@Tag(name = "Admin - Coupon", description = "관리자 쿠폰 관리 API")
public interface AdminCouponControllerDocs {

    @Operation(
            summary = "관리자 쿠폰 생성",
            description = "관리자가 새 쿠폰을 생성합니다. 생성된 쿠폰 코드는 사용자가 POST /v1/user/coupons로 등록할 수 있습니다.\n\n" +
                    "**검증:**\n" +
                    "- 쿠폰 코드(couponCode) 중복 시 COUPON_CODE_DUPLICATE (400)\n" +
                    "- validFrom이 validTo보다 같거나 이후이면 INVALID_COUPON_VALIDITY_PERIOD (400)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "생성 성공 - Location 헤더에 생성된 쿠폰 경로 반환, 본문에 message, id, name 포함",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminCouponCreateResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 (코드 중복, 유효기간 선후 관계 오류 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (ADMIN 권한 필요)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<AdminCouponCreateResponse> createCoupon(@Valid @RequestBody AdminCouponCreateRequest request);
}
