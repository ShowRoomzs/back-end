package showroomz.api.seller.coupon.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.coupon.dto.SellerCouponCreateRequest;
import showroomz.api.seller.coupon.dto.SellerCouponCreateResponse;

@Tag(name = "Seller - Coupon", description = "Seller Coupon API")
public interface SellerCouponControllerDocs {

    @Operation(
            summary = "판매자 쿠폰 등록",
            description = "로그인한 판매자가 자신의 상품에 적용할 쿠폰을 등록합니다.\n\n" +
                    "**검증:**\n" +
                    "- `productIds`의 모든 상품은 본인(판매자) 마켓 소속이어야 함 (아니면 PRODUCT_NOT_OWNED_BY_SELLER)\n" +
                    "- 쿠폰 코드 전역 중복 불가 (COUPON_CODE_DUPLICATE)\n" +
                    "- 유효기간 선후 관계 (INVALID_COUPON_VALIDITY_PERIOD)\n\n" +
                    "**권한:** SELLER / CREATOR\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SellerCouponCreateResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류",
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
                    description = "타 판매자 상품 포함 등 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "판매자 정보 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SellerCouponCreateResponse> createCoupon(@Valid @RequestBody SellerCouponCreateRequest request);
}
