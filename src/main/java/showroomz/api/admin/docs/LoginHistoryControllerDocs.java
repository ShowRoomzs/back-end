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
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.history.DTO.LocationFilterResponse;
import showroomz.api.admin.history.DTO.LoginHistoryResponse;
import showroomz.api.admin.history.DTO.LoginHistorySearchCondition;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Tag(name = "Admin - Social Login", description = "관리자 소셜 로그인 활성/비활성 관리 API 및 로그인 이력 조회 API")
public interface LoginHistoryControllerDocs {

    @Operation(
            summary = "로그인 이력 조회",
            description = "사용자 로그인 이력을 필터링하여 조회합니다.\n\n" +
                    "**필터 기능:**\n" +
                    "- `startDate` / `endDate`: 로그인 날짜 범위 검색 (yyyy-MM-dd 형식)\n" +
                    "- `deviceType`: 디바이스 타입 필터링\n" +
                    "  - `ANDROID`: 안드로이드 기기\n" +
                    "  - `IPHONE`: 아이폰 기기\n" +
                    "  - `DESKTOP_CHROME`: 데스크톱 Chrome 브라우저\n" +
                    "  - `DESKTOP_EDGE`: 데스크톱 Edge 브라우저\n" +
                    "  - `null`: 전체 (기본값)\n" +
                    "- `country`: 국가명으로 필터링 (예: \"대한민국\", \"미국\")\n" +
                    "- `city`: 도시명으로 필터링 (예: \"서울\", \"부산\")\n" +
                    "  - `country`와 함께 사용하면 해당 국가의 특정 도시만 검색\n" +
                    "  - `country`만 지정하면 해당 국가 전체 검색\n" +
                    "- `status`: 로그인 상태 필터링\n" +
                    "  - `SUCCESS`: 정상 로그인\n" +
                    "  - `ABNORMAL`: 이상 로그인\n" +
                    "  - `null`: 전체 (기본값)\n\n" +
                    "**정렬:** 최신순 (loginAt 내림차순)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**페이징 파라미터:**\n" +
                    "- `page`: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- `size`: 페이지당 항목 수 (기본값: 20)",
            parameters = {
                    @Parameter(
                            name = "startDate",
                            description = "검색 시작 날짜 (yyyy-MM-dd)",
                            example = "2024-01-01",
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", format = "date")
                    ),
                    @Parameter(
                            name = "endDate",
                            description = "검색 종료 날짜 (yyyy-MM-dd)",
                            example = "2024-12-31",
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", format = "date")
                    ),
                    @Parameter(
                            name = "deviceType",
                            description = "디바이스 타입",
                            example = "ANDROID",
                            in = ParameterIn.QUERY,
                            schema = @Schema(
                                    type = "string",
                                    allowableValues = {"ANDROID", "IPHONE", "DESKTOP_CHROME", "DESKTOP_EDGE"}
                            )
                    ),
                    @Parameter(
                            name = "country",
                            description = "국가명",
                            example = "대한민국",
                            in = ParameterIn.QUERY
                    ),
                    @Parameter(
                            name = "city",
                            description = "도시명",
                            example = "서울",
                            in = ParameterIn.QUERY
                    ),
                    @Parameter(
                            name = "status",
                            description = "로그인 상태",
                            example = "SUCCESS",
                            in = ParameterIn.QUERY,
                            schema = @Schema(
                                    type = "string",
                                    allowableValues = {"SUCCESS", "ABNORMAL"}
                            )
                    ),
                    @Parameter(
                            name = "page",
                            description = "페이지 번호 (1부터 시작)",
                            example = "1",
                            in = ParameterIn.QUERY
                    ),
                    @Parameter(
                            name = "size",
                            description = "페이지당 항목 수",
                            example = "20",
                            in = ParameterIn.QUERY
                    )
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 이력 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 응답 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"id\": 1,\n" +
                                                    "      \"userId\": 123,\n" +
                                                    "      \"email\": \"user1@example.com\",\n" +
                                                    "      \"loginAt\": \"2024-01-15 14:30:25\",\n" +
                                                    "      \"clientIp\": \"192.168.1.100\",\n" +
                                                    "      \"deviceType\": \"DESKTOP_CHROME\",\n" +
                                                    "      \"country\": \"대한민국\",\n" +
                                                    "      \"city\": \"서울\",\n" +
                                                    "      \"status\": \"SUCCESS\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"id\": 2,\n" +
                                                    "      \"userId\": 124,\n" +
                                                    "      \"email\": \"user2@example.com\",\n" +
                                                    "      \"loginAt\": \"2024-01-15 13:20:10\",\n" +
                                                    "      \"clientIp\": \"192.168.1.101\",\n" +
                                                    "      \"deviceType\": \"IPHONE\",\n" +
                                                    "      \"country\": \"대한민국\",\n" +
                                                    "      \"city\": \"부산\",\n" +
                                                    "      \"status\": \"SUCCESS\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 8,\n" +
                                                    "    \"totalResults\": 150,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 파라미터",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "날짜 형식 오류",
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
                    description = "인증 실패 - 유효하지 않은 토큰 또는 토큰 만료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 - ADMIN 권한이 필요합니다",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"FORBIDDEN\",\n" +
                                                    "  \"message\": \"접근 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "서버 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INTERNAL_SERVER_ERROR\",\n" +
                                                    "  \"message\": \"서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<PageResponse<LoginHistoryResponse>> getLoginHistories(
            @Parameter(hidden = true) LoginHistorySearchCondition condition,
            @Parameter(hidden = true) PagingRequest pagingRequest
    );

    @Operation(
            summary = "로그인 이력 필터 옵션 조회 (국가별 도시 목록)",
            description = "로그인 이력 조회 시 사용할 수 있는 국가/도시 필터 옵션을 국가별로 그룹화하여 조회합니다.\n\n" +
                    "**응답 형식:**\n" +
                    "- 각 항목은 `country`(국가명)와 `cities`(해당 국가의 도시 목록) 필드를 포함합니다\n" +
                    "- 프론트엔드에서 이 데이터를 계층적 드롭다운이나 멀티 셀렉트 박스에 표시할 수 있습니다\n\n" +
                    "**사용 예시:**\n" +
                    "1. 필터 목록 조회: `GET /v1/admin/history/login/filters/locations`\n" +
                    "2. 사용자가 \"대한민국\" 국가와 \"서울\" 도시 선택\n" +
                    "3. 검색 요청: `GET /v1/admin/history/login?country=대한민국&city=서울`\n" +
                    "4. 전체 국가 검색: `GET /v1/admin/history/login?country=대한민국` (city 생략)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "필터 옵션 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LocationFilterResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 응답 예시",
                                            value = "[\n" +
                                                    "  {\n" +
                                                    "    \"country\": \"대한민국\",\n" +
                                                    "    \"cities\": [\"부산\", \"서울\", \"인천\"]\n" +
                                                    "  },\n" +
                                                    "  {\n" +
                                                    "    \"country\": \"미국\",\n" +
                                                    "    \"cities\": [\"뉴욕\", \"로스앤젤레스\"]\n" +
                                                    "  },\n" +
                                                    "  {\n" +
                                                    "    \"country\": \"일본\",\n" +
                                                    "    \"cities\": [\"도쿄\", \"오사카\"]\n" +
                                                    "  }\n" +
                                                    "]"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 - 유효하지 않은 토큰 또는 토큰 만료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 - ADMIN 권한이 필요합니다",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"FORBIDDEN\",\n" +
                                                    "  \"message\": \"접근 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "서버 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INTERNAL_SERVER_ERROR\",\n" +
                                                    "  \"message\": \"서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<List<LocationFilterResponse>> getLocationFilters();
}
