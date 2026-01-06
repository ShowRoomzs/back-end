package showroomz.swaggerDocs;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import showroomz.auth.DTO.ErrorResponse;
import showroomz.auth.DTO.ValidationErrorResponse;
import showroomz.product.DTO.CategoryDto;
import showroomz.market.DTO.MarketDto;

@Tag(name = "Admin", description = "관리자 API")
public interface SuperAdminControllerDocs {

    @Operation(
            summary = "마켓 이미지 검수 상태 변경",
            description = "특정 마켓의 대표 이미지 검수 상태를 변경합니다. (APPROVED, REJECTED)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "이미지 검수 상태 변경 성공 - Status: 204 No Content",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 상태값 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "잘못된 상태값",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\"\n" +
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
                                                    "  \"message\": \"접근 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "마켓 정보를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "마켓 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "변경할 이미지 검수 상태 (APPROVED 또는 REJECTED)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MarketDto.UpdateImageStatusRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "상태 변경 요청 예시",
                                    value = "{\n" +
                                            "  \"status\": \"APPROVED\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateMarketImageStatus(
            @Parameter(
                    description = "상태를 변경할 마켓의 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long marketId,
            @RequestBody MarketDto.UpdateImageStatusRequest request
    );

    @Operation(
            summary = "카테고리 생성",
            description = "관리자가 새로운 카테고리를 생성합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "카테고리 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CategoryDto.CreateCategoryResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"categoryId\": 1,\n" +
                                                    "  \"name\": \"옷\",\n" +
                                                    "  \"order\": 1,\n" +
                                                    "  \"message\": \"카테고리가 성공적으로 생성되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "카테고리명 중복",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "중복 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"DUPLICATE_CATEGORY_NAME\",\n" +
                                                    "  \"message\": \"이미 존재하는 카테고리명입니다.\"\n" +
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
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "카테고리 생성 정보",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CategoryDto.CreateCategoryRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"name\": \"옷\",\n" +
                                            "  \"order\": 1,\n" +
                                            "  \"iconUrl\": \"https://example.com/icon/clothing.png\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<CategoryDto.CreateCategoryResponse> createCategory(
            @RequestBody CategoryDto.CreateCategoryRequest request
    );

    @Operation(
            summary = "카테고리 조회 (단일)",
            description = "관리자가 특정 카테고리의 정보를 조회합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "카테고리 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CategoryDto.CategoryResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "카테고리를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<CategoryDto.CategoryResponse> getCategory(
            @Parameter(
                    description = "조회할 카테고리 ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable Long categoryId
    );

    @Operation(
            summary = "카테고리 목록 조회",
            description = "관리자가 모든 카테고리 목록을 조회합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "카테고리 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CategoryDto.CategoryResponse.class)
                    )
            )
    })
    ResponseEntity<java.util.List<CategoryDto.CategoryResponse>> getAllCategories();

    @Operation(
            summary = "카테고리 수정",
            description = "관리자가 카테고리 정보를 수정합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "수정할 필드만 전달하면 됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "카테고리 수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CategoryDto.UpdateCategoryResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "카테고리명 중복",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "카테고리를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "카테고리 수정 정보 (수정할 필드만 전달)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CategoryDto.UpdateCategoryRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"name\": \"옷\",\n" +
                                            "  \"order\": 1,\n" +
                                            "  \"iconUrl\": \"https://example.com/icon/clothing.png\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<CategoryDto.UpdateCategoryResponse> updateCategory(
            @Parameter(
                    description = "수정할 카테고리 ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable Long categoryId,
            @RequestBody CategoryDto.UpdateCategoryRequest request
    );

    @Operation(
            summary = "카테고리 삭제",
            description = "관리자가 카테고리를 삭제합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "카테고리 삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"message\": \"카테고리가 성공적으로 삭제되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "카테고리를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<?> deleteCategory(
            @Parameter(
                    description = "삭제할 카테고리 ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable Long categoryId
    );
}

