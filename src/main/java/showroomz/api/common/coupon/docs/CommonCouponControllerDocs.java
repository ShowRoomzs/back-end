package showroomz.api.common.coupon.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.common.coupon.dto.CommonProductCouponItem;

import java.util.List;

@Tag(name = "Common - Coupon", description = "공용 쿠폰 조회 API")
public interface CommonCouponControllerDocs {

    @Operation(
            summary = "특정 상품 적용 가능 쿠폰 목록 조회",
            description = """
                    `CouponProduct`에 매핑된 상품 기준으로, 유효 기간 내 마스터 쿠폰 목록을 반환합니다.

                    - 비회원: `isDownloaded`는 항상 false입니다.
                    - 로그인: `UserCoupon`에 해당 쿠폰이 있으면 isDownloaded true입니다.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "array", implementation = CommonProductCouponItem.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "상품 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<List<CommonProductCouponItem>> getProductCoupons(
            @Parameter(name = "productId", description = "상품 ID", required = true, example = "1")
            @PathVariable("productId") Long productId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal userPrincipal
    );
}
