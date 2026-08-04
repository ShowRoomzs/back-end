package showroomz.api.test.docs;

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
import showroomz.api.app.auth.DTO.ErrorResponse;

import java.util.Map;

@Tag(name = "Test", description = "테스트 전용 API (인증 불필요)")
public interface TestCreatorApplicationControllerDocs {

    @Operation(
            summary = "크리에이터 신청서 삭제 (테스트)",
            description = "신청서 ID로 크리에이터 신청서와 처리 이력을 삭제합니다. 반려 쿨다운 테스트용입니다.\n\n" +
                    "**권한:** 없음 (테스트 전용)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = "{\n" +
                                            "  \"message\": \"크리에이터 신청서(반려 기록)가 삭제되었습니다.\",\n" +
                                            "  \"applicationId\": \"123\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "신청서 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "미존재",
                                    value = "{\"code\": \"APPLICATION_NOT_FOUND\", \"message\": \"존재하지 않는 신청입니다.\"}"
                            )
                    )
            )
    })
    ResponseEntity<Map<String, String>> deleteApplication(
            @Parameter(
                    name = "applicationId",
                    description = "삭제할 지원서 ID",
                    required = true,
                    example = "123",
                    in = ParameterIn.PATH,
                    schema = @Schema(type = "integer", format = "int64")
            )
            @PathVariable("applicationId") Long applicationId
    );
}
