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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.market.DTO.MarketAdminDto;
import showroomz.api.app.auth.DTO.ErrorResponse;

@Tag(name = "Admin - Market", description = "관리자 마켓 관리 API")
public interface AdminMarketMemoControllerDocs {

    @Operation(
            summary = "마켓 관리자 메모 수정",
            description = "운영 관리자가 특정 마켓(쇼룸)에 대한 내부 메모를 작성 및 수정합니다.\n\n" +
                    "이 메모는 판매자에게는 노출되지 않으며, 관리자 백오피스에서 마켓을 관리할 목적으로만 사용됩니다.\n\n" +
                    "**권한:** ADMIN\n\n" +
                    "**요청 헤더:** `Authorization: Bearer {accessToken}`"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "수정 완료"),
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
            description = "관리자 메모 본문 (`null`이면 빈 메모로 덮어쓸 수 있음)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MarketAdminDto.UpdateAdminMemoRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "메모 저장",
                                    value = "{\"adminMemo\": \"배송 지연으로 인한 1차 경고 발송 (2026.05.03)\"}"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateMarketAdminMemo(
            @Parameter(
                    description = "대상 마켓 ID",
                    required = true,
                    example = "10",
                    in = ParameterIn.PATH
            )
            @PathVariable("marketId") Long marketId,
            @RequestBody MarketAdminDto.UpdateAdminMemoRequest request
    );
}
