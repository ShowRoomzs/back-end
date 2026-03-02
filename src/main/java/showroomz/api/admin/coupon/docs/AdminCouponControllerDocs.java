package showroomz.api.admin.coupon.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.coupon.dto.AdminCouponCreateRequest;
import showroomz.api.admin.coupon.dto.AdminCouponCreateResponse;
import showroomz.api.admin.coupon.dto.AdminCouponResponse;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.global.dto.PageResponse;

@Tag(name = "Admin - Coupon", description = "관리자 쿠폰 관리 API")
public interface AdminCouponControllerDocs {

    @Operation(
            summary = "관리자 쿠폰 목록 조회",
            description = "등록된 쿠폰 전체 목록을 페이징하여 조회합니다.\n\n" +
                    "**동적 필터링:**\n" +
                    "- status: ACTIVE(활성), EXPIRED(만료), SCHEDULED(예정) 중 선택 (미입력 시 전체 조회)\n\n" +
                    "**정렬:** 최신 등록순(createdAt DESC)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", example = "0", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "페이지당 항목 수", example = "10", in = ParameterIn.QUERY),
                    @Parameter(
                            name = "status",
                            description = "쿠폰 상태 필터 (ACTIVE, EXPIRED, SCHEDULED)",
                            example = "ACTIVE",
                            in = ParameterIn.QUERY,
                            schema = @Schema(allowableValues = {"ACTIVE", "EXPIRED", "SCHEDULED"})
                    )
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "목록 조회 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"couponId\": 1,\n" +
                                                    "      \"name\": \"신규 가입 10% 할인\",\n" +
                                                    "      \"code\": \"WELCOME10\",\n" +
                                                    "      \"discountType\": \"PERCENTAGE\",\n" +
                                                    "      \"discountValue\": 10,\n" +
                                                    "      \"minimumOrderPrice\": 30000,\n" +
                                                    "      \"validFrom\": \"2026-01-01T00:00:00\",\n" +
                                                    "      \"validUntil\": \"2026-12-31T23:59:59\",\n" +
                                                    "      \"totalQuantity\": null,\n" +
                                                    "      \"remainingQuantity\": null,\n" +
                                                    "      \"status\": \"ACTIVE\",\n" +
                                                    "      \"createdAt\": \"2026-01-01T10:00:00\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 5,\n" +
                                                    "    \"totalResults\": 42,\n" +
                                                    "    \"limit\": 10,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
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
    ResponseEntity<PageResponse<AdminCouponResponse>> getCouponList(
            @Parameter(name = "page") Integer page,
            @Parameter(name = "size") Integer size,
            @Parameter(name = "status") String status
    );

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
