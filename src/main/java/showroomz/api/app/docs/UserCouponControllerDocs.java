package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.coupon.dto.CouponRegisterRequest;
import showroomz.api.app.coupon.dto.UserCouponDto;
import showroomz.api.app.coupon.dto.UserCouponRegisterResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import jakarta.validation.Valid;

@Tag(name = "User - Coupon", description = "사용자 쿠폰 API")
public interface UserCouponControllerDocs {

    @Operation(
            summary = "사용자 쿠폰 목록 조회",
            description = "로그인한 사용자가 보유한 쿠폰 목록을 페이징하여 조회합니다.\n\n" +
                    "**정렬:** 최신 등록 순(registeredAt 내림차순)\n" +
                    "**페이징:** page(1부터), size(기본 20)\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {}
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PageResponse<UserCouponDto>> getMyCoupons(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "사용자 쿠폰 등록",
            description = "쿠폰 코드로 쿠폰을 내 쿠폰함에 등록합니다.\n\n" +
                    "**검증:**\n" +
                    "- 존재하지 않는 코드: COUPON_NOT_FOUND\n" +
                    "- 유효기간 외: COUPON_EXPIRED\n" +
                    "- 이미 등록된 쿠폰: COUPON_ALREADY_REGISTERED\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserCouponRegisterResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (만료/기간 외, 이미 등록됨)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {}
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
                    description = "존재하지 않는 쿠폰 코드",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {}
                    )
            )
    })
    ResponseEntity<UserCouponRegisterResponse> registerCoupon(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CouponRegisterRequest request
    );
}
