package showroomz.api.seller.category.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.admin.category.DTO.CategoryDto;

import java.util.List;

@Tag(name = "Seller - Category", description = "Seller Category API")
public interface CategoryControllerDocs {

    @Operation(
            summary = "전체 카테고리 조회",
            description = "판매자가 상품 등록 시 사용할 수 있는 전체 카테고리 목록을 조회합니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
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
                                                    "    \"name\": \"옷\",\n" +
                                                    "    \"order\": 1,\n" +
                                                    "    \"iconUrl\": \"https://example.com/icon/clothing.png\",\n" +
                                                    "    \"parentId\": null\n" +
                                                    "  },\n" +
                                                    "  {\n" +
                                                    "    \"categoryId\": 2,\n" +
                                                    "    \"name\": \"상의\",\n" +
                                                    "    \"order\": 1,\n" +
                                                    "    \"iconUrl\": null,\n" +
                                                    "    \"parentId\": 1\n" +
                                                    "  },\n" +
                                                    "  {\n" +
                                                    "    \"categoryId\": 3,\n" +
                                                    "    \"name\": \"하의\",\n" +
                                                    "    \"order\": 2,\n" +
                                                    "    \"iconUrl\": null,\n" +
                                                    "    \"parentId\": 1\n" +
                                                    "  }\n" +
                                                    "]"
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
                    description = "권한 없음",
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
            )
    })
    ResponseEntity<List<CategoryDto.CategoryResponse>> getAllCategories();
}
