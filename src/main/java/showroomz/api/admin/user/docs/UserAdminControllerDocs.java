package showroomz.api.admin.user.docs;

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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.dto.AdminUserMemoUpdateRequest;
import showroomz.api.admin.user.type.AdminUserSort;
import showroomz.api.admin.user.type.AdminUserTab;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - User", description = "관리자 일반 유저 관리 API")
public interface UserAdminControllerDocs {

    @Operation(
            summary = "소비자 목록 조회",
            description = "어드민 소비자 목록입니다. 컬럼 8종 — 회원번호 · 닉네임 · 이름 · 휴대폰 · 가입 수단 · 가입일 · 누적 주문 · 상태\n\n" +
                    "**마스킹은 서버가 끝냅니다.** 이름은 가운데 1자, 휴대폰은 가운데 4자리를 가린 값만 내려갑니다. " +
                    "목록에는 해제 경로가 없으므로 원본은 응답에 포함되지 않습니다.\n\n" +
                    "**검색 3축** — 입력 형태로 축을 하나 고릅니다(OR로 묶지 않습니다).\n" +
                    "- `CST-`로 시작 → 회원번호 정확히 일치 (접두사 뒤가 숫자가 아니면 0건)\n" +
                    "- 숫자 4자리 → 휴대폰 **뒤 4자리**\n" +
                    "- 그 외 → 닉네임 부분 일치\n\n" +
                    "이메일 검색 축은 없습니다 — 소셜 이메일(특히 Apple 릴레이)은 연락 가능한 주소가 아니라 화면에서 뺐습니다.\n\n" +
                    "**요약(`summary`)** — 상태 조건만 제외하고 검색어·가입 수단 필터는 그대로 반영한 건수입니다. " +
                    "`newSuspendedIn30Days`는 **정지 탭에서만** 값이 있고 다른 탭에서는 null입니다.\n\n" +
                    "**누적 주문** — 취소되지 않은 주문 상품이 하나라도 있는 주문의 수입니다(취소 포함 여부는 기획 확인 대기).\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "page", description = "페이지 번호 (1부터 시작)", example = "1", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "페이지당 항목 수", example = "20", in = ParameterIn.QUERY),
                    @Parameter(
                            name = "tab",
                            description = "상태 탭 — 기본 진입은 전체",
                            example = "ALL",
                            in = ParameterIn.QUERY,
                            schema = @Schema(allowableValues = {"ALL", "ACTIVE", "SUSPENDED", "WITHDRAWN"})
                    ),
                    @Parameter(
                            name = "keyword",
                            description = "회원번호(CST-) · 닉네임 · 휴대폰 뒤 4자리",
                            example = "CST-88231",
                            in = ParameterIn.QUERY
                    ),
                    @Parameter(
                            name = "providerType",
                            description = "가입 수단",
                            example = "KAKAO",
                            in = ParameterIn.QUERY,
                            schema = @Schema(allowableValues = {"KAKAO", "NAVER", "APPLE", "GOOGLE"})
                    ),
                    @Parameter(
                            name = "sort",
                            description = "정렬 — 기본은 최근 가입순",
                            example = "RECENT_JOINED",
                            in = ParameterIn.QUERY,
                            schema = @Schema(allowableValues = {"RECENT_JOINED", "ORDER_COUNT_DESC", "MEMBER_NO"})
                    )
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserDto.ListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "전체 탭 조회 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"userId\": 88231,\n" +
                                                    "      \"memberNo\": \"CST-88231\",\n" +
                                                    "      \"nickname\": \"홍길동\",\n" +
                                                    "      \"maskedName\": \"홍*동\",\n" +
                                                    "      \"maskedPhone\": \"010-****-1234\",\n" +
                                                    "      \"providerType\": \"KAKAO\",\n" +
                                                    "      \"joinedAt\": \"2026-02-01T10:12:00\",\n" +
                                                    "      \"orderCount\": 14,\n" +
                                                    "      \"status\": \"NORMAL\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"userId\": 88190,\n" +
                                                    "      \"memberNo\": \"CST-88190\",\n" +
                                                    "      \"nickname\": \"유리\",\n" +
                                                    "      \"maskedName\": \"박*은\",\n" +
                                                    "      \"maskedPhone\": \"010-****-2031\",\n" +
                                                    "      \"providerType\": \"APPLE\",\n" +
                                                    "      \"joinedAt\": \"2026-01-22T09:03:00\",\n" +
                                                    "      \"orderCount\": 0,\n" +
                                                    "      \"status\": \"SUSPENDED\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 117,\n" +
                                                    "    \"totalResults\": 2340,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  },\n" +
                                                    "  \"summary\": {\n" +
                                                    "    \"total\": 2340,\n" +
                                                    "    \"active\": 2268,\n" +
                                                    "    \"suspended\": 12,\n" +
                                                    "    \"withdrawn\": 60,\n" +
                                                    "    \"newSuspendedIn30Days\": null\n" +
                                                    "  }\n" +
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
                                            name = "인증 실패",
                                            value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"인증 정보가 유효하지 않습니다.\"}"
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
    ResponseEntity<AdminUserDto.ListResponse> getUsers(
            @RequestParam(value = "tab", defaultValue = "ALL") AdminUserTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "providerType", required = false) ProviderType providerType,
            @RequestParam(value = "sort", defaultValue = "RECENT_JOINED") AdminUserSort sort,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "일반 유저 상세 조회",
            description = "유저 ID를 통해 상세 정보를 조회합니다.\n\n" +
            "주문 내역은 추후 개발 시 추가할 예정입니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserDto.UserDetailResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "유저를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유저 없음",
                                            value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AdminUserDto.UserDetailResponse> getUserDetail(
            @Parameter(
                    description = "조회할 유저 ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable("userId") Long userId
    );

    @Operation(
            summary = "유저 관리자 메모 수정",
            description = "특정 유저에 대한 관리자 메모를 수정합니다. (최대 500자)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "메모 수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 입력값 (500자 초과 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "유저를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유저 없음",
                                            value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> updateAdminMemo(
            @Parameter(
                    description = "유저 ID",
                    required = true,
                    example = "1",
                    in = ParameterIn.PATH
            )
            @PathVariable("userId") Long userId,
            @Valid @RequestBody AdminUserMemoUpdateRequest request
    );

    @Operation(
            summary = "일반 유저 상태 변경 (정지/활성 처리)",
            description = "특정 유저의 계정 상태를 활성(NORMAL) 또는 정지(SUSPENDED) 상태로 변경 처리합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "유저 상태 변경 완료 (응답 본문 없음)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 상태 값 요청 (NORMAL, SUSPENDED 이외의 값)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 ID의 유저를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> updateUserStatus(
            @Parameter(description = "상태를 변경할 대상 유저의 식별자(ID)", required = true, example = "1", in = ParameterIn.PATH)
            @PathVariable("userId") Long userId,
            @RequestBody AdminUserDto.UserStatusUpdateRequest request
    );
}
