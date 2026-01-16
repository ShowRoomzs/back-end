package showroomz.api.common.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
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
                            schema = @Schema(implementation = CategoryDto.CategoryResponse.class)
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
}
