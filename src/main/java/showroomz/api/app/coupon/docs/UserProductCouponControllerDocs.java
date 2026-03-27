package showroomz.api.app.coupon.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.coupon.dto.ProductApplicableCouponDto;

import java.util.List;

@Tag(name = "User - Coupon", description = "사용자 쿠폰 API")
public interface UserProductCouponControllerDocs {

    @Operation(
            summary = "사용자 상품 적용 가능 쿠폰 조회",
            description = "특정 상품 결제 시 적용할 수 있는 본인 보유 쿠폰 목록을 조회합니다.\n\n" +
                    "**조건 (DB 쿼리):**\n" +
                    "- 본인 `UserCoupon`이며 상태 `AVAILABLE`\n" +
                    "- 마스터 `Coupon` 유효기간(`startAt`~`endAt`) 내 현재 시각\n" +
                    "- 상품의 마켓 판매자와 쿠폰 발행 판매자(`coupon.seller`) 동일\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ProductApplicableCouponDto.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 상품",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<List<ProductApplicableCouponDto>> getApplicableCouponsForProduct(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(
                    name = "productId",
                    description = "대상 상품 ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @org.springframework.web.bind.annotation.PathVariable("productId") Long productId
    );
}
