package showroomz.api.common.docs;

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

import showroomz.api.admin.category.DTO.CategoryDto;
import showroomz.api.app.auth.DTO.ErrorResponse;

import java.util.List;

@Tag(name = "Common - Category", description = "공용 카테고리 조회 API")
public interface CommonCategoryControllerDocs {

    @Operation(
            summary = "카테고리 목록 조회",
            description = "모든 사용자 및 비회원이 카테고리 목록을 조회합니다.\n\n" +
                    "**권한:** 없음 (비회원 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "카테고리 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CategoryDto.CategoryResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "[\n" +
                                                    "  {\n" +
                                                    "    \"categoryId\": 1,\n" +
                                                    "    \"name\": \"의류\",\n" +
                                                    "    \"order\": 1,\n" +
                                                    "    \"iconUrl\": \"https://example.com/icon/clothing.png\",\n" +
                                                    "    \"parentId\": null,\n" +
                                                    "    \"filters\": [\n" +
                                                    "      {\n" +
                                                    "        \"id\": 1,\n" +
                                                    "        \"filterKey\": \"gender\",\n" +
                                                    "        \"label\": \"성별\",\n" +
                                                    "        \"filterType\": \"CHECKBOX\",\n" +
                                                    "        \"condition\": \"OR\",\n" +
                                                    "        \"sortOrder\": 1,\n" +
                                                    "        \"isActive\": true,\n" +
                                                    "        \"values\": [\n" +
                                                    "          {\"id\": 11, \"value\": \"MALE\", \"label\": \"남성\", \"extra\": null, \"sortOrder\": 1, \"isActive\": true}\n" +
                                                    "        ]\n" +
                                                    "      }\n" +
                                                    "    ]\n" +
                                                    "  }\n" +
                                                    "]"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<List<CategoryDto.CategoryResponse>> getAllCategories();

    @Operation(
            summary = "카테고리 개별 조회",
            description = "모든 사용자 및 비회원이 특정 카테고리 정보를 조회합니다.\n\n" +
                    "**권한:** 없음 (비회원 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "카테고리 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CategoryDto.CategoryResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"categoryId\": 2,\n" +
                                                    "  \"name\": \"셔츠\",\n" +
                                                    "  \"order\": 2,\n" +
                                                    "  \"iconUrl\": \"https://example.com/icon/shirt.png\",\n" +
                                                    "  \"parentId\": 1,\n" +
                                                    "  \"filters\": [\n" +
                                                    "    {\n" +
                                                    "      \"id\": 2,\n" +
                                                    "      \"filterKey\": \"color\",\n" +
                                                    "      \"label\": \"색상\",\n" +
                                                    "      \"filterType\": \"COLOR\",\n" +
                                                    "      \"condition\": \"OR\",\n" +
                                                    "      \"sortOrder\": 2,\n" +
                                                    "      \"isActive\": true,\n" +
                                                    "      \"values\": [\n" +
                                                    "        {\"id\": 21, \"value\": \"블랙\", \"label\": \"블랙\", \"extra\": \"#000000\", \"sortOrder\": 1, \"isActive\": true},\n" +
                                                    "        {\"id\": 22, \"value\": \"화이트\", \"label\": \"화이트\", \"extra\": \"#FFFFFF\", \"sortOrder\": 2, \"isActive\": true}\n" +
                                                    "      ]\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
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
    ResponseEntity<CategoryDto.CategoryResponse> getCategory(
            @Parameter(
                    description = "조회할 카테고리 ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable Long categoryId
    );
}
