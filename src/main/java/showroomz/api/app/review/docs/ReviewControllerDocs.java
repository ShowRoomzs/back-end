package showroomz.api.app.review.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.review.dto.ReviewDto;
import showroomz.api.app.review.dto.ReviewRegisterRequest;
import showroomz.api.app.review.dto.ReviewRegisterResponse;
import showroomz.api.app.review.dto.ReviewUpdateRequest;
import showroomz.global.dto.PageResponse;

@Tag(name = "User - Review")
public interface ReviewControllerDocs {

    @Operation(
            summary = "사용자 리뷰 작성 가능한 상품 목록 조회",
            description = "구매 확정 상태이면서 아직 리뷰를 남기지 않은 주문 상품 목록을 반환합니다.\n\n" +
                    "**응답:** content 내부에 orderProductId, productName, optionName, quantity, price, imageUrl, orderDate 포함\n\n" +
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
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"orderProductId\": 1,\n" +
                                                    "      \"productName\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "      \"optionName\": \"블랙 / M\",\n" +
                                                    "      \"quantity\": 1,\n" +
                                                    "      \"price\": 49000,\n" +
                                                    "      \"imageUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "      \"orderDate\": \"2025-02-19T10:00:00\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 1,\n" +
                                                    "    \"totalResults\": 5,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PageResponse<ReviewDto.WritableItem>> getWritableList(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(
            summary = "사용자 리뷰 작성",
            description = "주문 상품에 대한 리뷰를 등록합니다.\n\n" +
                    "**요청 바디:** orderProductId(Long), rating(Integer 1-5), content(String, 20자 이상), imageUrls(List), isPromotionAgreed(Boolean), isPersonalInfoAgreed(Boolean)\n\n" +
                    "**유효성 검사:** 리뷰 내용은 반드시 20자 이상이어야 합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공 - Status: 201 Created",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewRegisterResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"reviewId\": 1\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (유효성 검증 실패, 이미 리뷰 작성됨, 리뷰 작성 불가 상태 등) - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "리뷰 내용 20자 미만",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"리뷰 내용은 20자 이상이어야 합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "이미 리뷰 작성됨",
                                            value = "{\n" +
                                                    "  \"code\": \"REVIEW_ALREADY_EXISTS\",\n" +
                                                    "  \"message\": \"이미 해당 주문 상품에 리뷰를 작성하셨습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 주문 상품에 대한 권한 없음 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "주문 상품을 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ReviewRegisterResponse> registerReview(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ReviewRegisterRequest request
    );

    @Operation(
            summary = "사용자 리뷰 목록 조회",
            description = "현재 로그인한 사용자가 작성한 리뷰를 최신순으로 페이징하여 조회합니다.\n\n" +
                    "**응답:** content 내부에 reviewId, rating, content, imageUrls, createdAt, product 정보(상품명, 옵션명) 포함\n\n" +
                    "**페이징 파라미터:**\n" +
                    "- page: 페이지 번호 (1부터 시작) - 기본값: 1\n" +
                    "- size: 페이지당 항목 수 - 기본값: 20\n\n" +
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
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"reviewId\": 1,\n" +
                                                    "      \"rating\": 5,\n" +
                                                    "      \"content\": \"상품 품질이 매우 좋고 배송도 빨라서 만족스럽습니다.\",\n" +
                                                    "      \"imageUrls\": [\"https://example.com/review1.jpg\"],\n" +
                                                    "      \"createdAt\": \"2025-02-19T10:00:00\",\n" +
                                                    "      \"product\": {\n" +
                                                    "        \"productName\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "        \"optionName\": \"블랙 / M\"\n" +
                                                    "      }\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 1,\n" +
                                                    "    \"totalResults\": 10,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<PageResponse<ReviewDto.ReviewItem>> getMyReviews(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1") Integer page,
            @Parameter(description = "페이지당 항목 수", example = "20") Integer size
    );

    @Operation(
            summary = "사용자 리뷰 수정",
            description = "본인이 작성한 리뷰를 수정합니다.\n\n" +
                    "**경로 파라미터:** reviewId - 수정할 리뷰 ID\n\n" +
                    "**요청 바디:** rating(Integer 1-5), content(String, 20자 이상), imageUrls(List<String>)\n\n" +
                    "**로직:** 기존 이미지를 삭제하고 새로운 imageUrls로 교체합니다.\n\n" +
                    "**권한:** USER (리뷰 작성자만)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "reviewId", description = "리뷰 ID", required = true, example = "1", in = ParameterIn.PATH)
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewDto.UpdateResponse.class),
                            examples = @ExampleObject(
                                    value = "{\n" +
                                            "  \"reviewId\": 501,\n" +
                                            "  \"message\": \"리뷰가 성공적으로 수정되었습니다.\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효성 검증 실패 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 리뷰에 대한 수정 권한 없음 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\n" +
                                            "  \"code\": \"REVIEW_ACCESS_DENIED\",\n" +
                                            "  \"message\": \"해당 리뷰에 대한 수정/삭제 권한이 없습니다.\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "리뷰를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ReviewDto.UpdateResponse> updateReview(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "리뷰 ID", required = true) @PathVariable Long reviewId,
            @RequestBody ReviewUpdateRequest request
    );

    @Operation(
            summary = "사용자 리뷰 삭제",
            description = "본인이 작성한 리뷰를 삭제합니다. 연관된 ReviewImage, ReviewLike도 함께 삭제됩니다.\n\n" +
                    "**경로 파라미터:** reviewId - 삭제할 리뷰 ID\n\n" +
                    "**권한:** USER (리뷰 작성자만)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "reviewId", description = "리뷰 ID", required = true, example = "1", in = ParameterIn.PATH)
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewDto.DeleteResponse.class),
                            examples = @ExampleObject(
                                    value = "{\n" +
                                            "  \"reviewId\": 501,\n" +
                                            "  \"message\": \"리뷰가 성공적으로 삭제되었습니다.\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 리뷰에 대한 삭제 권한 없음 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "리뷰를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ReviewDto.DeleteResponse> deleteReview(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "리뷰 ID", required = true) @PathVariable Long reviewId
    );

    @Operation(
            summary = "사용자 리뷰 좋아요 토글",
            description = "리뷰에 좋아요를 누르거나 취소합니다.\n\n" +
                    "**경로 파라미터:** reviewId - 좋아요 토글할 리뷰 ID\n\n" +
                    "**로직:** 이미 좋아요를 눌렀다면 취소, 누르지 않았다면 좋아요 추가\n\n" +
                    "**응답:** reviewId, isLiked(현재 유저 좋아요 여부), likeCount(총 좋아요 수)\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "reviewId", description = "리뷰 ID", required = true, example = "1", in = ParameterIn.PATH)
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "토글 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewDto.LikeToggleResponse.class),
                            examples = @ExampleObject(
                                    value = "{\n" +
                                            "  \"reviewId\": 1,\n" +
                                            "  \"isLiked\": true,\n" +
                                            "  \"likeCount\": 25\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "리뷰를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ReviewDto.LikeToggleResponse> toggleLike(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "리뷰 ID", required = true) @PathVariable Long reviewId
    );
}
