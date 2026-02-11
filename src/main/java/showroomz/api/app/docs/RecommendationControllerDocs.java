package showroomz.api.app.docs;

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
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.recommendation.DTO.RecommendationDto;

@Tag(name = "Common - Product", description = "공용 상품 API")
public interface RecommendationControllerDocs {

    @Operation(
            summary = "비회원/회원 추천 상품 조회",
            description = "비회원/회원 모두 추천 상품과 마켓을 통합 조회합니다.\n\n" +
                    "**추천 로직:**\n" +
                    "- 로그인 시 사용자 성별 기반 추천 (MALE/FEMALE/UNISEX)\n" +
                    "- 비회원은 성별 필터 없이 전체 추천 상품 노출\n" +
                    "- 비회원은 isWished/isFollowing 등 사용자 필드가 false\n" +
                    "- isRecommended=true인 상품을 우선 노출\n" +
                    "- 최신순 정렬\n\n" +
                    "**응답 구조:**\n" +
                    "- recommendedMarkets: 추천 마켓 목록 (각 마켓에 대표 상품 3개 포함)\n" +
                    "- content: 추천 상품 목록\n" +
                    "- pageInfo: 페이지 정보 (상품용)\n\n" +
                    "**파라미터:**\n" +
                    "- categoryId: 카테고리 ID 필터 (선택사항, 마켓과 상품 모두에 적용)\n" +
                    "- page: 페이지 번호 (1부터 시작) - 기본값: 1 (상품용)\n" +
                    "- limit: 페이지당 항목 수 - 기본값: 20 (상품용)\n\n" +
                    "**권한:** 비회원/USER\n" +
                    "**요청 헤더(선택):** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecommendationDto.UnifiedRecommendationPageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "통합 추천 응답 예시",
                                            value = "{\n" +
                                                    "  \"recommendedMarkets\": [\n" +
                                                    "    {\n" +
                                                    "      \"marketId\": 1,\n" +
                                                    "      \"marketName\": \"M 브라이튼\",\n" +
                                                    "      \"marketImageUrl\": \"https://example.com/market.jpg\",\n" +
                                                    "      \"mainCategoryId\": 1,\n" +
                                                    "      \"mainCategoryName\": \"의류\",\n" +
                                                    "      \"followerCount\": 1200,\n" +
                                                    "      \"isFollowing\": false,\n" +
                                                    "      \"representativeProducts\": [\n" +
                                                    "        {\n" +
                                                    "          \"id\": 1,\n" +
                                                    "          \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "          \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "          \"thumbnailUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "          \"price\": {\n" +
                                                    "            \"regularPrice\": 59000,\n" +
                                                    "            \"salePrice\": 49000,\n" +
                                                    "            \"discountRate\": 17\n" +
                                                    "          },\n" +
                                                    "          \"isRecommended\": true\n" +
                                                    "        }\n" +
                                                    "      ]\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                            "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"id\": 1,\n" +
                                                    "      \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "      \"name\": \"프리미엄 린넨 셔츠\",\n" +
                                                    "      \"thumbnailUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "      \"price\": {\n" +
                                                    "        \"regularPrice\": 59000,\n" +
                                                    "        \"salePrice\": 49000,\n" +
                                                    "        \"discountRate\": 17\n" +
                                                    "      },\n" +
                                                    "      \"discountRate\": 17,\n" +
                                                    "      \"wishCount\": 120,\n" +
                                                    "      \"reviewCount\": 45,\n" +
                                                    "      \"isRecommended\": true,\n" +
                                                    "      \"isWished\": false\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 8,\n" +
                                            "    \"totalResults\": 150,\n" +
                                            "    \"limit\": 20,\n" +
                                            "    \"hasNext\": true\n" +
                                                    "  }\n" +
                                                    "}",
                                            description = "통합 추천 응답 (마켓 + 상품)"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<RecommendationDto.UnifiedRecommendationPageResponse> getRecommendations(
            @Parameter(
                    name = "categoryId",
                    description = "카테고리 ID 필터 (마켓과 상품 모두에 적용)",
                    example = "1",
                    in = ParameterIn.QUERY
            )
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Parameter(
                    name = "page",
                    description = "페이지 번호 (1부터 시작, 상품용)",
                    example = "1",
                    in = ParameterIn.QUERY
            )
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(
                    name = "limit",
                    description = "페이지당 항목 수 (상품용)",
                    example = "20",
                    in = ParameterIn.QUERY
            )
            @RequestParam(value = "limit", required = false) Integer limit
    );
}
