package showroomz.api.admin.terms.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.terms.dto.AdminTermsDocumentDetailResponse;
import showroomz.api.admin.terms.dto.AdminTermsDocumentRegisterRequest;
import showroomz.api.admin.terms.dto.AdminTermsListRequest;
import showroomz.api.admin.terms.dto.AdminTermsPageResponse;
import showroomz.api.admin.terms.dto.AdminTermsVersionDetailResponse;
import showroomz.api.admin.terms.dto.AdminTermsVersionRegisterRequest;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Terms", description = "관리자 약관·정책 관리 API (기획 §21)")
public interface AdminTermsControllerDocs {

    @Operation(
            summary = "문서 등록 (신규)",
            description = "아직 없는 약관·정책 문서를 처음 등록합니다. 목록의 `＋ 문서 등록`에서 진입하는 화면입니다.\n\n"
                    + "**FAQ·공지와 결정적으로 다릅니다** — 원문 수정 불가 · 내리기 불가(새 버전으로 대체만) · 이력 영구 보관. "
                    + "동의 기록이 \"동의한 버전\"을 참조하므로 원문이 바뀌면 누가 무엇에 동의했는지가 무너집니다.\n\n"
                    + "**필수 값:** 문서명 · 유형 · 대상 · 시행일 · 본문\n\n"
                    + "**유형·대상은 등록 후 고정입니다.** 대상이 바뀌면 동의 대상 집단이 달라져 같은 문서로 볼 수 없습니다 — "
                    + "잘못 지정하면 문서를 새로 만들어야 합니다.\n\n"
                    + "**버전 번호는 받지 않습니다.** 최초 버전은 `v1.0`으로 자동 부여됩니다 — "
                    + "최초 등록에 버전을 고르게 하면 `v0.9` 같은 값이 들어옵니다.\n\n"
                    + "**등록 후 상태는 `시행 예정`**이며, 시행일 00:00에 서버 배치가 `시행중`으로 전환합니다. "
                    + "그전까지 소비자 화면에 노출되지 않습니다.\n\n"
                    + "**중복 판정:** 문서명 + 대상 조합입니다 — 마케팅 동의는 대상별로 같은 이름의 문서를 따로 둡니다.\n\n"
                    + "**권한:** ADMIN\n"
                    + "**요청 헤더:** Authorization: Bearer {accessToken}\n\n"
                    + "**응답:** 201 Created, Location 헤더에 생성된 문서 경로 (`/v1/admin/terms/{documentId}`)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "등록 성공 - Location 헤더에 생성된 리소스 경로 반환"),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 (필수 누락, 시행일이 오늘 이전, 문서명·대상 중복)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "시행일 제한",
                                            value = "{\n"
                                                    + "  \"code\": \"INVALID_INPUT\",\n"
                                                    + "  \"message\": \"시행일은 오늘 이후 날짜만 선택할 수 있습니다.\"\n"
                                                    + "}"
                                    ),
                                    @ExampleObject(
                                            name = "문서명 중복",
                                            value = "{\n"
                                                    + "  \"code\": \"INVALID_INPUT\",\n"
                                                    + "  \"message\": \"같은 대상으로 이미 등록된 문서명입니다. 개정이라면 해당 문서에 새 버전을 등록해 주세요.\"\n"
                                                    + "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (ADMIN 권한 필요)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "문서 등록 요청",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminTermsDocumentRegisterRequest.class),
                    examples = @ExampleObject(
                            name = "소비자 이용약관 최초 등록",
                            value = "{\n"
                                    + "  \"name\": \"소비자 이용약관\",\n"
                                    + "  \"type\": \"TERMS_OF_SERVICE\",\n"
                                    + "  \"target\": \"USER\",\n"
                                    + "  \"effectiveDate\": \"2026-09-01\",\n"
                                    + "  \"content\": \"제1조(목적) 본 약관은 SHOWROOMZ가 제공하는 서비스의 이용 조건 및 절차를 규정함을 목적으로 합니다.\"\n"
                                    + "}"
                    )
            )
    )
    ResponseEntity<Void> registerDocument(
            @Valid @RequestBody AdminTermsDocumentRegisterRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(
            summary = "문서 목록 조회 및 검색",
            description = "약관·정책 문서 목록을 페이징 조회합니다. **문서 1건 = 1행**이며, "
                    + "표시 버전은 현재 시행중(없으면 시행 예정) 1개입니다 — 과거 버전은 문서 상세의 이력에서 봅니다.\n\n"
                    + "**정렬:** 유형 순 → 등록 순 고정입니다.\n\n"
                    + "**유형 탭:** `type` 으로 필터링합니다 — `ALL`(기본 진입 탭) · `TERMS_OF_SERVICE` · "
                    + "`PRIVACY_POLICY` · `MARKETING_CONSENT`. `typeCounts` 에 탭별 건수가 함께 내려갑니다.\n\n"
                    + "**검색:** 문서명 단일 대상입니다. 대상·상태 셀렉트는 두지 않습니다 — "
                    + "문서가 10건 안팎이라 유형 탭 + 문서명 검색으로 충분하고, 대상은 목록 열에서 바로 읽힙니다.\n\n"
                    + "**상태 3종:** `EFFECTIVE`(시행중, 성공) · `SCHEDULED`(시행 예정, 정보) · `SUPERSEDED`(구버전, 중립).\n\n"
                    + "**관리 열:** `canRegisterNewVersion` 이 false면 관리 열을 비웁니다(`—`) — "
                    + "구버전 문서는 새 버전을 붙일 대상이 아니라 조회만 합니다.\n\n"
                    + "**툴바:** `pageInfo.totalResults` = '총 N건', `scheduledCount` = '시행 예정 N건', "
                    + "`supersededCount` = '구버전 N건'.\n\n"
                    + "**상세 진입은 행 클릭입니다** — 관리 열에 `[상세]` 버튼을 두지 않습니다."
    )
    @Parameters({
            @Parameter(
                    name = "type",
                    description = "유형 탭 (미입력/ALL 시 전체)",
                    example = "TERMS_OF_SERVICE",
                    schema = @Schema(allowableValues = {"ALL", "TERMS_OF_SERVICE", "PRIVACY_POLICY", "MARKETING_CONSENT"})
            ),
            @Parameter(name = "keyword", description = "문서명 키워드 검색 (부분 일치)", example = "이용약관"),
            @Parameter(name = "page", description = "페이지 번호 (1부터 시작)", example = "1"),
            @Parameter(name = "size", description = "페이지당 항목 수 (20 / 50 / 100)", example = "20")
    })
    ResponseEntity<AdminTermsPageResponse> getDocuments(
            @Parameter(hidden = true) AdminTermsListRequest request,
            @Parameter(hidden = true) PagingRequest pagingRequest
    );

    @Operation(
            summary = "문서 상세 조회 (문서 정보 · 시행 원문 · 버전 이력)",
            description = "목록 행 클릭으로 진입하는 문서 상세입니다.\n\n"
                    + "**문서 정보:** 유형 · 대상 · 시행 버전(번호 + 시행일) · 보관 버전 수(`pastVersionCount`). "
                    + "유형·대상은 문서 속성이라 조회만 합니다.\n\n"
                    + "**시행 원문(`content`):** 현재 시행 버전의 원문이며 **조회 전용**입니다 — 수정 API가 없습니다.\n\n"
                    + "**버전 이력(`versions`):** 버전 · 시행일 · 등록자 · 등록일시 · 상태. 행 클릭 시 버전 상세로 이동합니다. "
                    + "같은 문서 안에서 교체된 지난 버전은 `PAST`(과거 버전)이며, 목록 층의 `SUPERSEDED`(구버전)와는 층이 다릅니다.\n\n"
                    + "**보관 버전을 삭제하지 않는 이유:** 동의 기록이 \"동의한 버전\"을 참조합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 문서",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<AdminTermsDocumentDetailResponse> getDocument(
            @Parameter(description = "조회할 문서 ID", required = true)
            @PathVariable("documentId") Long documentId
    );

    @Operation(
            summary = "새 버전 등록 (개정)",
            description = "기존 문서를 개정합니다. 문서 상세·목록 행의 `새 버전 등록`에서 진입합니다. "
                    + "**원문 수정이 아니라 새 버전 등록만이 개정 수단입니다.**\n\n"
                    + "**문서명·유형·대상은 받지 않습니다** — 문서 속성이라 고정 표시만 합니다.\n\n"
                    + "**버전 번호(`versionNumber`):** 숫자와 점만 받습니다. 접두 `v`는 화면에서 필드 밖에 고정된 표기라 "
                    + "값에 포함하지 않습니다 — `v3.2`·`V3.2`·`3.2`가 섞여 들어오는 것을 막습니다. "
                    + "서버에서 **중복·역행 번호를 검증**합니다.\n\n"
                    + "**시행일:** 오늘 이후만 가능하며, 기존 버전의 시행일보다 뒤여야 합니다 — "
                    + "같은 날 두 버전이 시행되면 어느 쪽이 시행중인지 정할 수 없습니다.\n\n"
                    + "**등록 후 상태는 `시행 예정`**입니다. 시행일 00:00에 서버 배치가 새 버전을 `시행중`으로 올리고 "
                    + "기존 시행 버전을 `과거 버전`으로 내립니다 — 기존 버전은 **삭제되지 않고 계속 보관**됩니다.\n\n"
                    + "**구버전 문서에는 등록할 수 없습니다.**\n\n"
                    + "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "등록 성공 - Location 헤더에 생성된 버전 경로 반환"),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 (버전 번호 형식·중복·역행, 시행일 제한, 구버전 문서)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "접두 v 포함",
                                            value = "{\n"
                                                    + "  \"code\": \"INVALID_INPUT\",\n"
                                                    + "  \"message\": \"버전 번호에 접두 v는 포함하지 않습니다. 숫자와 점만 입력해 주세요. (예: 3.2)\"\n"
                                                    + "}"
                                    ),
                                    @ExampleObject(
                                            name = "역행 번호",
                                            value = "{\n"
                                                    + "  \"code\": \"INVALID_INPUT\",\n"
                                                    + "  \"message\": \"새 버전은 기존 버전보다 높은 번호여야 합니다. (기존 v3.1)\"\n"
                                                    + "}"
                                    ),
                                    @ExampleObject(
                                            name = "구버전 문서",
                                            value = "{\n"
                                                    + "  \"code\": \"INVALID_INPUT\",\n"
                                                    + "  \"message\": \"구버전 문서에는 새 버전을 등록할 수 없습니다.\"\n"
                                                    + "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 문서",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "새 버전 등록 요청",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminTermsVersionRegisterRequest.class),
                    examples = @ExampleObject(
                            name = "소비자 이용약관 v3.2 개정",
                            value = "{\n"
                                    + "  \"versionNumber\": \"3.2\",\n"
                                    + "  \"effectiveDate\": \"2026-09-01\",\n"
                                    + "  \"content\": \"제1조(목적) 본 약관은 SHOWROOMZ(이하 \\\"회사\\\")가 제공하는 서비스의 이용 조건 및 절차를 규정함을 목적으로 합니다.\"\n"
                                    + "}"
                    )
            )
    )
    ResponseEntity<Void> registerVersion(
            @Parameter(description = "새 버전을 등록할 문서 ID", required = true)
            @PathVariable("documentId") Long documentId,
            @Valid @RequestBody AdminTermsVersionRegisterRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(
            summary = "버전 상세 조회 (조회 전용)",
            description = "버전 이력 행 클릭으로 진입합니다. **액션이 없습니다** — 수정·삭제 모두 불가합니다. "
                    + "시행중 버전도 같은 응답을 쓰며 상태 배지만 다릅니다.\n\n"
                    + "**시행 기간:** `effectiveStartDate` ~ `effectiveEndDate`. 종료일은 다음 버전 시행일의 하루 전이며, "
                    + "아직 교체되지 않은 버전(시행중·시행 예정)은 비어 있습니다.\n\n"
                    + "**우측 카드:** 상태 · 다음 버전(`nextVersion`) · 교체일(`replacedAt`).\n\n"
                    + "**이동:** `previousVersionId`(‹ 이전) · `nextVersionId`(다음 ›) — 시행일 순서 기준이며 "
                    + "끝에 다다르면 비어 있습니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 문서 또는 버전",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<AdminTermsVersionDetailResponse> getVersion(
            @Parameter(description = "문서 ID", required = true)
            @PathVariable("documentId") Long documentId,
            @Parameter(description = "버전 ID", required = true)
            @PathVariable("versionId") Long versionId
    );
}
