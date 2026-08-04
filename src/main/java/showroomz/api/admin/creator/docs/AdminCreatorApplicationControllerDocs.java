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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.creator.dto.CreatorApplicationDetailResponse;
import showroomz.api.admin.creator.dto.CreatorApplicationListResponse;
import showroomz.api.admin.creator.dto.CreatorApplicationRejectRequest;
import showroomz.api.admin.creator.dto.CreatorApplicationSearchCondition;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Creator Application", description = "크리에이터 지원서 관리 API")
@SecurityRequirement(name = "Authorization")
public interface AdminCreatorApplicationControllerDocs {

    @Operation(
            summary = "크리에이터 지원서 목록 조회",
            description = "관리자용 크리에이터 지원서 페이징 목록을 조회합니다.\n\n" +
                    "**필터:**\n" +
                    "- `status`: PENDING(심사대기) / APPROVED(승인) / REJECTED(반려) / 미입력(전체)\n\n" +
                    "**검색:**\n" +
                    "- `keyword`: 활동명·계정 아이디를 한 번에 부분 일치(OR) 검색\n\n" +
                    "**응답:** 글로벌 `PageResponse`(`content` + `pageInfo`) + 상태별 건수(`statusCounts`)\n" +
                    "- `statusCounts`: 검색어 반영, 상태 필터 미반영\n\n" +
                    "**정렬:** 신청일 최신순 (createdAt 내림차순)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "status", description = "신청 상태 (미입력 시 전체)", example = "PENDING", in = ParameterIn.QUERY),
                    @Parameter(name = "keyword", description = "활동명·계정 아이디 통합 검색어", example = "뷰티마스터", in = ParameterIn.QUERY),
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
                            schema = @Schema(implementation = CreatorApplicationListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "지원서 목록 조회 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"applicationId\": 12,\n" +
                                                    "      \"activityName\": \"뷰티마스터\",\n" +
                                                    "      \"email\": \"business@creator.com\",\n" +
                                                    "      \"snsType\": \"YOUTUBE\",\n" +
                                                    "      \"channelUrl\": \"https://youtube.com/c/example\",\n" +
                                                    "      \"accountId\": \"example\",\n" +
                                                    "      \"followerCount\": 155000,\n" +
                                                    "      \"appliedAt\": \"2024-03-01T14:00:00\",\n" +
                                                    "      \"processedAt\": null,\n" +
                                                    "      \"status\": \"PENDING\",\n" +
                                                    "      \"rejectReason\": null\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 2,\n" +
                                                    "    \"totalResults\": 15,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  },\n" +
                                                    "  \"statusCounts\": {\n" +
                                                    "    \"all\": 42,\n" +
                                                    "    \"pending\": 10,\n" +
                                                    "    \"approved\": 25,\n" +
                                                    "    \"rejected\": 7\n" +
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
    ResponseEntity<CreatorApplicationListResponse> getApplications(
            @ParameterObject @ModelAttribute CreatorApplicationSearchCondition condition,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "크리에이터 입점 신청 상세 조회",
            description = "관리자용 크리에이터 입점 신청 상세 정보를 조회합니다.\n\n" +
                    "**포함 정보:**\n" +
                    "- 본인 인증: 실명·생년월일(더미, 반려 시 파기), 연락처(신청서 저장, 반려 시 해시), 인증 수단(PASS)\n" +
                    "- 플랫폼: SNS 유형, 채널 주소, 계정 아이디, 팔로워 수·업무용 이메일·마케팅 동의(반려 시 파기)\n" +
                    "- 처리 이력: 신청 접수 / 승인 처리 / 반려 처리 (승인·반려 시 운영자 이메일 포함)\n" +
                    "- 심사 상태: 신청번호, 상태, 신청일, 처리 운영자 이메일, 반려 유형·상세\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreatorApplicationDetailResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "입점 신청 상세 예시",
                                            value = "{\n" +
                                                    "  \"applicationId\": 12,\n" +
                                                    "  \"status\": \"APPROVED\",\n" +
                                                    "  \"appliedAt\": \"2026-07-13T18:04:00\",\n" +
                                                    "  \"processedAt\": \"2026-07-09T11:20:00\",\n" +
                                                    "  \"processorEmail\": \"admin@showroomz.com\",\n" +
                                                    "  \"rejectReasonType\": null,\n" +
                                                    "  \"rejectReasonDetail\": null,\n" +
                                                    "  \"name\": \"홍길동\",\n" +
                                                    "  \"birthday\": \"1990-01-01\",\n" +
                                                    "  \"phoneNumber\": \"010-1234-5678\",\n" +
                                                    "  \"verificationMethod\": \"PASS\",\n" +
                                                    "  \"verificationMethodLabel\": \"휴대폰 본인인증(PASS)\",\n" +
                                                    "  \"snsType\": \"INSTAGRAM\",\n" +
                                                    "  \"channelUrl\": \"https://instagram.com/my_channel\",\n" +
                                                    "  \"accountId\": \"my_channel\",\n" +
                                                    "  \"followerCount\": 10000,\n" +
                                                    "  \"businessEmail\": \"business@creator.com\",\n" +
                                                    "  \"marketingAgree\": true,\n" +
                                                    "  \"processingHistory\": [\n" +
                                                    "    {\n" +
                                                    "      \"type\": \"APPLICATION_RECEIVED\",\n" +
                                                    "      \"label\": \"신청 접수\",\n" +
                                                    "      \"processedAt\": \"2026-07-13T18:04:00\",\n" +
                                                    "      \"processorEmail\": null\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"type\": \"APPLICATION_APPROVED\",\n" +
                                                    "      \"label\": \"승인 처리\",\n" +
                                                    "      \"processedAt\": \"2026-07-09T11:20:00\",\n" +
                                                    "      \"processorEmail\": \"admin@showroomz.com\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
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
    ResponseEntity<CreatorApplicationDetailResponse> getApplicationDetail(
            @Parameter(
                    name = "applicationId",
                    description = "조회할 지원서 ID",
                    required = true,
                    example = "12",
                    in = ParameterIn.PATH,
                    schema = @Schema(type = "integer", format = "int64")
            )
            @PathVariable("applicationId") Long applicationId);

    @Operation(
            summary = "크리에이터 지원 승인",
            description = "검수 대기(PENDING) 상태의 지원서를 승인하고 유저 권한을 CREATOR로 승격합니다.\n\n" +
                    "**처리 내용:**\n" +
                    "- 지원서 상태를 `APPROVED`로 변경\n" +
                    "- 처리 운영자 이메일을 지원서·이력에 저장\n" +
                    "- 유저 역할(RoleType)을 `CREATOR`로 변경\n" +
                    "- Creator 엔티티 생성 시 실명·생년월일·연락처를 신청서에서 복사\n" +
                    "- 크리에이터 `isNewMember`를 `true`로 설정 (추가 정보 입력 필요)\n" +
                    "- 승인 이력 저장 및 신청 시 입력한 업무 이메일(businessEmail)로 승인 안내 메일 발송\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "승인 성공"),
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
                    name = "applicationId",
                    description = "승인할 지원서 ID",
                    required = true,
                    example = "12",
                    in = ParameterIn.PATH,
                    schema = @Schema(type = "integer", format = "int64")
            )
            @PathVariable("applicationId") Long applicationId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);

    @Operation(
            summary = "크리에이터 지원 반려",
            description = "검수 대기(PENDING) 상태의 지원서를 반려하고 안내 메일을 발송합니다.\n\n" +
                    "**처리 내용:**\n" +
                    "- 지원서 상태를 `REJECTED`로 변경\n" +
                    "- 처리 운영자 이메일을 지원서·이력에 저장\n" +
                    "- 반려 사유 저장 (이메일 미발송)\n" +
                    "- 개인정보 파기: 신청서의 실명·생년월일·팔로워 수·업무용 이메일·약관 동의 이력 삭제, 신청 연락처는 일방향 해시만 보존\n" +
                    "- 신청자는 반려 처리일로부터 14일 이후 재신청 가능\n\n" +
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
            @ApiResponse(responseCode = "204", description = "반려 성공"),
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
                    name = "applicationId",
                    description = "반려할 지원서 ID",
                    required = true,
                    example = "12",
                    in = ParameterIn.PATH,
                    schema = @Schema(type = "integer", format = "int64")
            )
            @PathVariable("applicationId") Long applicationId,
            @Valid @RequestBody CreatorApplicationRejectRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);
}
