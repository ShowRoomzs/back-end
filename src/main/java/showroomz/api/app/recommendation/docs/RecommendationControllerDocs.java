package showroomz.api.app.recommendation.docs;

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
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.global.dto.PageResponse;

@Tag(name = "Common - Product", description = "공용 상품 API")
public interface RecommendationControllerDocs {

    @Operation(
            summary = "비회원/회원 추천 상품 조회",
            description = "추천 상품(isRecommended=true)만 조회합니다.\n\n" +
                    "**추천 로직:**\n" +
                    "- isRecommended=true인 상품만 조회\n" +
                    "- 로그인 시 사용자 성별 기반 필터 (MALE/FEMALE/UNISEX)\n" +
                    "- 비회원은 성별 필터 없이 전체 추천 상품 노출\n" +
                    "- isRecommended DESC, createdAt DESC 정렬\n\n" +
                    "**응답 구조:**\n" +
                    "- content: 추천 상품 목록\n" +
                    "- pageInfo: 페이징 메타데이터 (currentPage, totalPages, totalResults, limit, hasNext)\n\n" +
                    "**파라미터:**\n" +
                    "- categoryId: 카테고리 ID 필터 (선택, 하위 카테고리 포함)\n" +
                    "- page: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- limit: 페이지당 항목 수 (기본값: 20)\n\n" +
                    "**권한:** 비회원/USER (Authorization 헤더 선택)\n" +
                    "**isWished:** 로그인 시 본인 찜 여부 반영, 비회원은 false"
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
                                            name = "추천 상품 응답 예시",
                                            value = "{\n" +
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
    ResponseEntity<PageResponse<ProductDto.ProductItem>> getRecommendations(
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
