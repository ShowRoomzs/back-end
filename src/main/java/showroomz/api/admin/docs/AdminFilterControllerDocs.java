package showroomz.api.admin.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.filter.DTO.FilterDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;

@Tag(name = "Admin - Filter", description = "관리자 필터 관리 API")
public interface AdminFilterControllerDocs {

    @Operation(
            summary = "필터 및 세부 값 등록",
            description = "관리자가 필터와 세부 값을 등록합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공",
                    content = @Content(schema = @Schema(implementation = FilterDto.FilterResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "필터 생성 요청",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = FilterDto.CreateFilterRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"filterKey\": \"gender\",\n" +
                                            "  \"label\": \"성별\",\n" +
                                            "  \"filterType\": \"CHECKBOX\",\n" +
                                            "  \"condition\": \"OR\",\n" +
                                            "  \"sortOrder\": 1,\n" +
                                            "  \"isActive\": true,\n" +
                                            "  \"values\": [\n" +
                                            "    {\"value\": \"MALE\", \"label\": \"남성\", \"extra\": null, \"sortOrder\": 1, \"isActive\": true},\n" +
                                            "    {\"value\": \"FEMALE\", \"label\": \"여성\", \"extra\": null, \"sortOrder\": 2, \"isActive\": true}\n" +
                                            "  ]\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<FilterDto.FilterResponse> createFilter(@RequestBody FilterDto.CreateFilterRequest request);

    @Operation(
            summary = "필터 수정",
            description = "관리자가 필터 정보를 수정합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = FilterDto.FilterResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "필터를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<FilterDto.FilterResponse> updateFilter(
            @PathVariable Long filterId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "필터 수정 요청",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FilterDto.UpdateFilterRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "요청 예시",
                                            value = "{\n" +
                                                    "  \"label\": \"성별\",\n" +
                                                    "  \"filterType\": \"CHECKBOX\",\n" +
                                                    "  \"condition\": \"OR\",\n" +
                                                    "  \"sortOrder\": 1,\n" +
                                                    "  \"isActive\": true,\n" +
                                                    "  \"values\": [\n" +
                                                    "    {\"value\": \"MALE\", \"label\": \"남성\", \"extra\": null, \"sortOrder\": 1, \"isActive\": true},\n" +
                                                    "    {\"value\": \"FEMALE\", \"label\": \"여성\", \"extra\": null, \"sortOrder\": 2, \"isActive\": true}\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            )
            @RequestBody FilterDto.UpdateFilterRequest request
    );

    @Operation(
            summary = "필터 삭제",
            description = "관리자가 필터를 삭제합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"message\": \"필터가 성공적으로 삭제되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "필터를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<?> deleteFilter(@PathVariable Long filterId);
}
