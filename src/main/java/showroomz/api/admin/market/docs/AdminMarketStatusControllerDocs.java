package showroomz.api.admin.market.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.app.auth.DTO.ErrorResponse;

import java.util.Map;

@Tag(name = "Admin - Market", description = "관리자 마켓 관리 API")
public interface AdminMarketStatusControllerDocs {

    @Operation(
            summary = "마켓 운영 상태 변경 (활성/정지)",
            description = "마켓 계정의 **운영 상태**를 `ACTIVE`(활성) 또는 `SUSPENDED`(정지)로 변경합니다.\n\n" +
                    "**정지 (`SUSPENDED`) 시 동작:**\n" +
                    "- 해당 마켓의 모든 상품에 대해 현재 `isDisplay` 값을 `previousIsDisplay`에 저장한 뒤, `isDisplay`를 `false`로 설정합니다.\n\n" +
                    "**활성 (`ACTIVE`) 시 동작:**\n" +
                    "- `previousIsDisplay`가 있으면 그 값으로 `isDisplay`를 복구하고, 백업 컬럼은 `null`로 초기화합니다.\n" +
                    "- 백업이 없으면 `isDisplay`를 `true`로 둡니다.\n\n" +
                    "**기타:**\n" +
                    "- 이미 요청한 상태와 같으면 DB 변경 없이 성공 응답만 반환합니다.\n\n" +
                    "**권한:** ADMIN\n\n" +
                    "**요청 헤더:** `Authorization: Bearer {accessToken}`"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "변경 완료 (또는 이미 동일 상태)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    type = "object",
                                    description = "처리 결과 메시지",
                                    example = "{\"message\": \"마켓이 활성 처리되었으며 상품 노출 상태가 복구되었습니다.\"}"
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "활성 처리",
                                            value = "{\"message\": \"마켓이 활성 처리되었으며 상품 노출 상태가 복구되었습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "정지 처리",
                                            value = "{\"message\": \"마켓이 정지 처리되었으며 모든 상품이 미노출 전환되었습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 검증 실패 (`status` 누락 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "상태 누락",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"변경할 마켓 상태를 입력해주세요.\"}"
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
                                            name = "인증 실패",
                                            value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"인증 정보가 유효하지 않습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (ADMIN 아님)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\"code\": \"FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "마켓 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "마켓 없음",
                                            value = "{\"code\": \"MARKET_NOT_FOUND\", \"message\": \"존재하지 않는 마켓입니다.\"}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "변경할 마켓 운영 상태",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminMarketDto.UpdateMarketStatusRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "활성으로 변경",
                                    value = "{\"status\": \"ACTIVE\"}"
                            ),
                            @ExampleObject(
                                    name = "정지",
                                    value = "{\"status\": \"SUSPENDED\"}"
                            )
                    }
            )
    )
    ResponseEntity<Map<String, String>> updateMarketStatus(
            @Parameter(
                    description = "대상 마켓 ID",
                    required = true,
                    example = "10",
                    in = ParameterIn.PATH
            )
            @PathVariable Long marketId,
            @Valid @RequestBody AdminMarketDto.UpdateMarketStatusRequest request
    );
}
