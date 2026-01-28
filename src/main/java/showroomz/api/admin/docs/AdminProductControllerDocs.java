package showroomz.api.admin.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.product.DTO.AdminProductDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;

@Tag(name = "Admin - Product", description = "관리자 상품 관리 API")
public interface AdminProductControllerDocs {

    @Operation(
            summary = "관리자 상품 추천 상태 변경",
            description = "관리자가 특정 상품의 추천 상태를 변경합니다.\n\n" +
                    "**기능:**\n" +
                    "- 상품의 isRecommended 필드를 요청받은 값으로 변경\n" +
                    "- 추천 상태가 변경된 상품 정보와 성공 메시지를 반환\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "추천 상태 변경 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminProductDto.UpdateRecommendationResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"productId\": 1,\n" +
                                                    "  \"productNumber\": \"SRZ-20251228-001\",\n" +
                                                    "  \"isRecommended\": true,\n" +
                                                    "  \"message\": \"상품 추천 상태가 성공적으로 변경되었습니다.\"\n" +
                                                    "}",
                                            description = "상품 추천 상태가 성공적으로 변경되었습니다."
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "입력값 오류 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\",\n" +
                                                    "  \"errors\": [\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"isRecommended\",\n" +
                                                    "      \"reason\": \"추천 여부는 필수 입력값입니다.\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
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
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (ADMIN 권한 필요)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"FORBIDDEN\",\n" +
                                                    "  \"message\": \"권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "상품을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "상품 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"PRODUCT_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 상품입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "상품 추천 상태 변경 정보",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminProductDto.UpdateRecommendationRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"isRecommended\": true\n" +
                                            "}",
                                    description = "상품을 추천 상품으로 설정"
                            )
                    }
            )
    )
    ResponseEntity<AdminProductDto.UpdateRecommendationResponse> updateRecommendation(
            @Parameter(
                    description = "추천 상태를 변경할 상품 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long productId,
            @RequestBody AdminProductDto.UpdateRecommendationRequest request
    );
}
