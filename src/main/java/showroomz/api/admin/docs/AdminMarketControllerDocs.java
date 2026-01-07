package showroomz.api.admin.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.auth.DTO.SellerDto;
import showroomz.api.seller.market.DTO.MarketDto;

@Tag(name = "Admin - Market", description = "관리자 마켓 관리 API")
public interface AdminMarketControllerDocs {

    @Operation(
            summary = "가입 대기 판매자 목록 조회",
            description = "회원가입 후 승인을 기다리고 있는(PENDING 상태) 판매자들의 목록을 조회합니다.\n\n" +
                    "**반환 정보:** 판매자 ID, 이메일, 이름, 마켓명, 연락처, 신청일 (페이징)\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**페이징 파라미터:**\n" +
                    "- page: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- size: 페이지당 항목 수 (기본값: 20)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = showroomz.global.dto.PageResponse.class)
                    )
            )
    })
    ResponseEntity<showroomz.global.dto.PageResponse<SellerDto.PendingSellerResponse>> getPendingSellers(
            @ParameterObject showroomz.global.dto.PagingRequest pagingRequest
    );

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
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable Long marketId,
            @RequestBody MarketDto.UpdateImageStatusRequest request
    );

    @Operation(
            summary = "판매자 계정 상태 변경 (승인/반려)",
            description = "회원가입을 신청한 판매자 계정의 상태를 변경합니다. (APPROVED, REJECTED)\n\n" +
                    "**상태값:**\n" +
                    "- `APPROVED`: 승인 (로그인 가능)\n" +
                    "- `REJECTED`: 반려 (로그인 불가)\n\n" +
                    "\n" +
                    "- `rejectionReason` 필드는 선택 사항입니다. REJECTED 상태일 때 거부 사유를 입력할 수 있습니다.\n" +
                    "- APPROVED 상태로 변경 시 `rejectionReason` 필드는 무시됩니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "상태 변경 성공 - Status: 204 No Content",
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
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"입력값이 올바르지 않습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "판매자 계정을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "계정 없음",
                                            value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "변경할 계정 상태 (APPROVED 또는 REJECTED)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SellerDto.UpdateStatusRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "승인 요청 예시",
                                    value = "{\n  \"status\": \"APPROVED\"\n}"
                            ),
                            @ExampleObject(
                                    name = "반려 요청 예시",
                                    value = "{\n  \"status\": \"REJECTED\",\n  \"rejectionReason\": \"서류 미비로 인한 반려\"\n}"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateSellerStatus(
            @Parameter(
                    description = "상태를 변경할 판매자(Seller) ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable Long sellerId,
            @RequestBody SellerDto.UpdateStatusRequest request
    );
}


