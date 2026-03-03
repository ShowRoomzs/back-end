package showroomz.api.common.review.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.common.review.dto.ProductReviewResponse;
import showroomz.api.common.review.dto.ProductReviewSortType;

import java.util.List;

@Tag(name = "Common - Review", description = "공용 상품 리뷰 조회 API")
public interface CommonProductReviewControllerDocs {

    @Operation(
            summary = "비회원/회원 상품 전체 리뷰 조회",
            description = "특정 상품의 리뷰 목록을 조회합니다.\n\n" +
                    "**조회 조건:**\n" +
                    "- productId: 상품 ID\n" +
                    "- optionIds: 필터링할 상품 옵션 ID 목록 (선택)\n" +
                    "- sortType: LATEST(최신순), RECOMMENDED(좋아요순/추천순)\n\n" +
                    "**페이징:**\n" +
                    "- page: 페이지 번호 (0부터 시작, 기본값: 0)\n" +
                    "- size: 페이지당 항목 수 (기본값: 10)\n\n" +
                    "**참고사항:**\n" +
                    "- 비회원도 조회 가능합니다 (Authorization 헤더 불필요).\n" +
                    "- 로그인한 사용자: 각 리뷰에 isLikedByMe가 본인 좋아요 여부로 설정됩니다.\n" +
                    "- 비로그인: isLikedByMe는 false입니다.\n" +
                    "- 작성자명은 마스킹 처리됩니다 (예: 이종훈 -> 이*훈).\n\n" +
                    "**권한:** 선택사항 (비회원 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductReviewResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"reviewId\": 1,\n" +
                                                    "      \"authorName\": \"이*훈\",\n" +
                                                    "      \"rating\": 5,\n" +
                                                    "      \"content\": \"너무 만족합니다!\",\n" +
                                                    "      \"imageUrls\": [\"https://example.com/review1.jpg\"],\n" +
                                                    "      \"createdAt\": \"2025-12-28T14:30:00\",\n" +
                                                    "      \"likeCount\": 10,\n" +
                                                    "      \"isLikedByMe\": false,\n" +
                                                    "      \"optionName\": \"S\"\n" +
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
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "상품을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ProductReviewResponse> getProductReviews(
            @Parameter(name = "productId", description = "상품 ID", required = true, example = "1")
            @PathVariable("productId") Long productId,
            @Parameter(description = "페이지 번호 (0부터 시작, 기본값: 0)", example = "0")
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "페이지당 항목 수 (기본값: 10)", example = "10")
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @Parameter(description = "정렬 타입 (LATEST: 최신순, RECOMMENDED: 좋아요순)", example = "LATEST")
            @RequestParam(required = false, defaultValue = "LATEST") ProductReviewSortType sortType,
            @Parameter(description = "필터링할 상품 옵션 ID 목록 (선택)")
            @RequestParam(required = false) List<Long> optionIds,
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal userPrincipal
    );
}
