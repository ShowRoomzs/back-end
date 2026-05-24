package showroomz.api.admin.creator.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.creator.dto.CreatorApplicationRejectRequest;
import showroomz.api.admin.creator.dto.CreatorApplicationResponse;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Creator Application", description = "크리에이터 지원서 관리 API")
@SecurityRequirement(name = "Authorization")
public interface AdminCreatorApplicationControllerDocs {

    @Operation(
            summary = "크리에이터 지원서 목록 조회",
            description = "관리자용 크리에이터 지원서 페이징 목록을 조회합니다.\n\n" +
                    "**정렬:** 신청일 최신순 (createdAt 내림차순)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**페이징 파라미터:**\n" +
                    "- page: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- size: 페이지당 항목 수 (기본값: 20)",
            parameters = {
                    @Parameter(name = "page", description = "페이지 번호 (1부터 시작)", example = "1", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "페이지당 항목 수", example = "20", in = ParameterIn.QUERY)
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "지원서 목록 조회 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"applicationId\": 12,\n" +
                                                    "      \"nickname\": \"뷰티마스터\",\n" +
                                                    "      \"email\": \"business@creator.com\",\n" +
                                                    "      \"snsType\": \"YOUTUBE\",\n" +
                                                    "      \"channelUrl\": \"https://youtube.com/c/example\",\n" +
                                                    "      \"followerCount\": 155000,\n" +
                                                    "      \"appliedAt\": \"2024-03-01T14:00:00\",\n" +
                                                    "      \"processedAt\": null,\n" +
                                                    "      \"status\": \"PENDING\",\n" +
                                                    "      \"rejectReason\": null\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"applicationId\": 11,\n" +
                                                    "      \"nickname\": \"패션크리에이터\",\n" +
                                                    "      \"email\": \"contact@creator.com\",\n" +
                                                    "      \"snsType\": \"INSTAGRAM\",\n" +
                                                    "      \"channelUrl\": \"https://instagram.com/example\",\n" +
                                                    "      \"followerCount\": 82000,\n" +
                                                    "      \"appliedAt\": \"2024-02-20T09:30:00\",\n" +
                                                    "      \"processedAt\": \"2024-02-21T10:00:00\",\n" +
                                                    "      \"status\": \"REJECTED\",\n" +
                                                    "      \"rejectReason\": \"팔로워 수 기준 미달 - 제출하신 채널의 팔로워 수가 기준에 미달합니다.\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 2,\n" +
                                                    "    \"totalResults\": 15,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  }\n" +
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
                                            name = "권한 없음",
                                            value = "{\"code\": \"FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<PageResponse<CreatorApplicationResponse>> getApplications(
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "크리에이터 지원 승인",
            description = "검수 대기(PENDING) 상태의 지원서를 승인하고 유저 권한을 CREATOR로 승격합니다.\n\n" +
                    "**처리 내용:**\n" +
                    "- 지원서 상태를 `APPROVED`로 변경\n" +
                    "- 유저 역할(RoleType)을 `CREATOR`로 변경\n" +
                    "- 승인 이력 저장 및 신청 시 입력한 업무 이메일(businessEmail)로 승인 안내 메일 발송\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "승인 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "PENDING 상태가 아닌 지원서",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "잘못된 신청 상태",
                                            value = "{\"code\": \"INVALID_APPLICATION_STATUS\", \"message\": \"검수 대기 상태인 신청만 처리할 수 있습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "지원서 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "신청 없음",
                                            value = "{\"code\": \"APPLICATION_NOT_FOUND\", \"message\": \"존재하지 않는 신청입니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> approveApplication(
            @Parameter(
                    description = "승인할 지원서 ID",
                    required = true,
                    example = "12",
                    in = ParameterIn.PATH
            )
            @PathVariable Long applicationId);

    @Operation(
            summary = "크리에이터 지원 반려",
            description = "검수 대기(PENDING) 상태의 지원서를 반려하고 안내 메일을 발송합니다.\n\n" +
                    "**처리 내용:**\n" +
                    "- 지원서 상태를 `REJECTED`로 변경\n" +
                    "- 반려 사유 저장 및 신청 시 입력한 업무 이메일(businessEmail)로 반려 안내 메일 발송\n\n" +
                    "**요청 필드:**\n" +
                    "- `rejectReasonType`: 반려 사유 유형 (필수)\n" +
                    "- `rejectReasonDetail`: 상세 반려 사유 (선택, `OTHER` 선택 시 권장)\n\n" +
                    "**`rejectReasonType` 목록:**\n" +
                    "- `CHANNEL_INFO_MISMATCH`: 채널 정보 미일치 또는 확인 불가\n" +
                    "- `FOLLOWER_COUNT_SHORTFALL`: 팔로워 수 기준 미달\n" +
                    "- `OTHER`: 기타 (`rejectReasonDetail` 선택 입력)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "반려 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 또는 PENDING 상태가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "반려 사유 미입력",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"입력값이 올바르지 않습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "잘못된 신청 상태",
                                            value = "{\"code\": \"INVALID_APPLICATION_STATUS\", \"message\": \"검수 대기 상태인 신청만 처리할 수 있습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "지원서 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "신청 없음",
                                            value = "{\"code\": \"APPLICATION_NOT_FOUND\", \"message\": \"존재하지 않는 신청입니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "반려 사유",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CreatorApplicationRejectRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "채널 정보 미일치",
                                    value = "{\n" +
                                            "  \"rejectReasonType\": \"CHANNEL_INFO_MISMATCH\"\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "팔로워 수 기준 미달",
                                    value = "{\n" +
                                            "  \"rejectReasonType\": \"FOLLOWER_COUNT_SHORTFALL\"\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "기타 사유 (상세 입력)",
                                    value = "{\n" +
                                            "  \"rejectReasonType\": \"OTHER\",\n" +
                                            "  \"rejectReasonDetail\": \"제출하신 인스타그램 계정이 비공개 상태입니다.\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> rejectApplication(
            @Parameter(
                    description = "반려할 지원서 ID",
                    required = true,
                    example = "12",
                    in = ParameterIn.PATH
            )
            @PathVariable Long applicationId,
            @Valid @RequestBody CreatorApplicationRejectRequest request);
}
