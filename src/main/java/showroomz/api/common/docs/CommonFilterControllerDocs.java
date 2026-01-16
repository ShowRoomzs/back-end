package showroomz.api.common.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.admin.filter.DTO.FilterDto;

import java.util.List;

@Tag(name = "Common - Filter", description = "공용 필터 조회 API")
public interface CommonFilterControllerDocs {

    @Operation(
            summary = "필터 목록 조회",
            description = "전체 또는 특정 필터 목록을 조회합니다.\n\n" +
                    "**권한:** 없음 (비회원 가능)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = FilterDto.FilterResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "[\n" +
                                                    "  {\n" +
                                                    "    \"id\": 1,\n" +
                                                    "    \"filterKey\": \"gender\",\n" +
                                                    "    \"label\": \"성별\",\n" +
                                                    "    \"filterType\": \"CHECKBOX\",\n" +
                                                    "    \"condition\": \"OR\",\n" +
                                                    "    \"sortOrder\": 1,\n" +
                                                    "    \"isActive\": true,\n" +
                                                    "    \"values\": [\n" +
                                                    "      {\"id\": 11, \"value\": \"MALE\", \"label\": \"남성\", \"extra\": null, \"sortOrder\": 1, \"isActive\": true},\n" +
                                                    "      {\"id\": 12, \"value\": \"FEMALE\", \"label\": \"여성\", \"extra\": null, \"sortOrder\": 2, \"isActive\": true}\n" +
                                                    "    ]\n" +
                                                    "  }\n" +
                                                    "]"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<List<FilterDto.FilterResponse>> getFilters(
            @Parameter(description = "필터 키 (선택)", required = false)
            @RequestParam(required = false) String filterKey,
            @Parameter(description = "카테고리 ID (선택)", required = false)
            @RequestParam(required = false) Long categoryId
    );
}
