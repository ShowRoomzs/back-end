package showroomz.api.admin.category.docs;

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

import showroomz.api.admin.category.DTO.CategoryDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;

@Tag(name = "Admin - Category", description = "관리자 카테고리 관리 API")
public interface AdminCategoryControllerDocs {

    @Operation(
            summary = "카테고리 생성",
            description = "관리자가 새로운 카테고리를 생성합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**카테고리 depth:**\n" +
                    "- 1depth: parentId 없이 생성 (최상위 카테고리)\n" +
                    "- 2depth 이상: parentId에 상위 카테고리 ID 지정"
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
                                                    "  \"categoryId\": 2,\n" +
                                                    "  \"name\": \"블레이저\",\n" +
                                                    "  \"order\": 2,\n" +
                                                    "  \"parentId\": 1,\n" +
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
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "부모 카테고리를 찾을 수 없음 (parentId가 존재하지 않음)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "부모 카테고리 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"CATEGORY_NOT_FOUND\",\n" +
                                                    "  \"message\": \"카테고리를 찾을 수 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "카테고리 생성 정보\n\n" +
                    "**필드 설명:**\n" +
                    "- `name`: 필수, 카테고리명\n" +
                    "- `order`: 필수, 카테고리 순서 (0 이상)\n" +
                    "- `iconUrl`: 선택, 아이콘 URL\n" +
                    "- `parentId`: 선택, 부모 카테고리 ID\n\n" +
                    "**사용 방법:**\n" +
                    "- 1depth (최상위): `parentId` 필드를 제거하거나 null로 설정\n" +
                    "- 2depth 이상: `parentId`에 상위 카테고리 ID를 입력 (예: `\"parentId\": 1`)\n\n" +
                    "**아래 예시는 2depth 카테고리 생성 예시입니다. 1depth를 생성하려면 `parentId` 필드를 삭제하세요.**",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CategoryDto.CreateCategoryRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"name\": \"블레이저\",\n" +
                                            "  \"order\": 2,\n" +
                                            "  \"iconUrl\": \"https://example.com/icon/clothing.png\",\n" +
                                            "  \"parentId\": 1\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<CategoryDto.CreateCategoryResponse> createCategory(
            @RequestBody CategoryDto.CreateCategoryRequest request
    );

    @Operation(
            summary = "카테고리 수정",
            description = "관리자가 카테고리 정보를 수정합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "수정할 필드만 전달하면 됩니다. filters가 포함되면 필터 매핑도 동기화됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "카테고리 수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CategoryDto.UpdateCategoryResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"categoryId\": 1,\n" +
                                                    "  \"name\": \"옷\",\n" +
                                                    "  \"order\": 1,\n" +
                                                    "  \"iconUrl\": \"https://example.com/icon/clothing.png\",\n" +
                                                    "  \"parentId\": null,\n" +
                                                    "  \"message\": \"카테고리가 성공적으로 수정되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
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
                                            "  \"iconUrl\": \"https://example.com/icon/clothing.png\",\n" +
                                            "  \"filters\": [\n" +
                                            "    {\"filterId\": 1, \"selectedValueIds\": [11, 12]},\n" +
                                            "    {\"filterId\": 2, \"selectedValueIds\": []}\n" +
                                            "  ]\n" +
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

