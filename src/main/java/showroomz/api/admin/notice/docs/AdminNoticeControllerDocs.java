package showroomz.api.admin.notice.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.notice.dto.AdminNoticeDetailResponse;
import showroomz.api.admin.notice.dto.AdminNoticeListRequest;
import showroomz.api.admin.notice.dto.AdminNoticePageResponse;
import showroomz.api.admin.notice.dto.AdminNoticeRegisterRequest;
import showroomz.api.admin.notice.dto.AdminNoticeUpdateRequest;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Notice", description = "관리자 공지 관리 API (기획 §20)\n\n"
        + "**상태 2종** — 게시(PUBLISHED) / 게시 종료(ENDED)\n\n"
        + "**삭제는 없다.** 내릴 때는 게시 종료로 처리하며, 공지는 목록에 그대로 남는다 — "
        + "\"그때 무엇을 알렸는가\"가 기록으로 남아야 하기 때문이다.\n\n"
        + "**임시저장·예약 게시·게시 기간은 두지 않는다.** 등록 = 즉시 게시다.\n\n")
public interface AdminNoticeControllerDocs {

    @Operation(
            summary = "공지 등록",
            description = "관리자가 새 공지를 등록합니다. **등록 즉시 게시** 상태가 되어 소비자 앱 공지사항에 노출됩니다.\n\n"
                    + "**필수 값:** 제목 · 본문 2종\n\n"
                    + "**중요(pinned):** 상태가 아니라 분류입니다. 체크하면 목록 상단에 고정 노출됩니다.\n\n"
                    + "**본문 이미지:** 최대 3장. 클라이언트 제한은 우회 가능하므로 서버에서 본문의 `<img>` 개수를 다시 검증합니다.\n\n"
                    + "**작성자:** 요청한 운영자 계정으로 자동 기록되며, 수정 페이지에서만 확인합니다.\n\n"
                    + "**권한:** ADMIN\n"
                    + "**요청 헤더:** Authorization: Bearer {accessToken}\n\n"
                    + "**응답:** 201 Created, Location 헤더에 생성된 공지 경로 반환 (`/v1/admin/notices/{noticeId}`)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "등록 성공 - Location 헤더에 생성된 리소스 경로 반환"),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 (제목/본문 필수, 본문 이미지 3장 초과)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "제목 필수",
                                            value = "{\n"
                                                    + "  \"code\": \"INVALID_INPUT\",\n"
                                                    + "  \"message\": \"제목은 필수 입력값입니다.\"\n"
                                                    + "}"
                                    ),
                                    @ExampleObject(
                                            name = "본문 필수",
                                            value = "{\n"
                                                    + "  \"code\": \"INVALID_INPUT\",\n"
                                                    + "  \"message\": \"본문은 필수 입력값입니다.\"\n"
                                                    + "}"
                                    ),
                                    @ExampleObject(
                                            name = "이미지 장수 초과",
                                            value = "{\n"
                                                    + "  \"code\": \"INVALID_INPUT\",\n"
                                                    + "  \"message\": \"본문 이미지는 최대 3장까지 넣을 수 있습니다. (현재 5장)\"\n"
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
            description = "공지 등록 요청",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminNoticeRegisterRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "일반 공지 등록",
                                    value = "{\n"
                                            + "  \"title\": \"정기 서버 점검 안내 (7월 20일 02:00~06:00)\",\n"
                                            + "  \"content\": \"<p>더 안정적인 서비스를 위해 시스템 점검을 진행합니다.</p>\",\n"
                                            + "  \"pinned\": false\n"
                                            + "}"
                            ),
                            @ExampleObject(
                                    name = "중요 공지 등록 (상단 고정)",
                                    value = "{\n"
                                            + "  \"title\": \"SHOWROOMZ 앱 v1.2 업데이트 안내\",\n"
                                            + "  \"content\": \"<p>앱 v1.2 업데이트를 진행했습니다.</p>\",\n"
                                            + "  \"pinned\": true\n"
                                            + "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> registerNotice(
            @Valid @RequestBody AdminNoticeRegisterRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(
            summary = "공지 목록 조회 및 검색",
            description = "공지 목록을 페이징 조회합니다.\n\n"
                    + "**정렬:** 중요 고정 상단 + 등록일 최신순. 수동 순서 조정은 두지 않습니다 "
                    + "(§19 FAQ의 드래그 순서와 갈리는 지점).\n\n"
                    + "**상태 탭:** `status` 로 필터링합니다 — `ALL`(기본 진입 탭) · `PUBLISHED`(게시) · `ENDED`(게시 종료). "
                    + "`statusCounts` 에 탭별 건수가 함께 내려갑니다.\n\n"
                    + "**검색:** 제목 단일 대상입니다 (본문은 검색하지 않습니다).\n\n"
                    + "**툴바:** `pageInfo.totalResults` = '총 N건', `pinnedCount` = '중요 N건' (둘 다 현재 탭·검색어 기준).\n\n"
                    + "**번호:** 등록 순 채번으로, 삭제가 없어 번호가 비지 않습니다 (공지 ID와 동일).\n\n"
                    + "**표시 건수:** `size` 로 20 / 50 / 100 을 전달합니다."
    )
    ResponseEntity<AdminNoticePageResponse> getNotices(
            @Parameter(description = "상태 탭 · 제목 검색어")
            @ModelAttribute AdminNoticeListRequest request,
            @Parameter(description = "페이징 요청 객체 (page, size 값을 쿼리 파라미터로 전달)")
            @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "공지 상세 조회 (수정 페이지)",
            description = "수정 페이지에 필요한 공지 전체 정보를 조회합니다. 게시 종료된 공지도 조회됩니다.\n\n"
                    + "**우측 게시 설정 영역:** 상태 · 등록일시 · 최종 수정 · 작성자 · 중요 여부. "
                    + "`endedAt`(게시 종료 일시)은 게시 종료 상태에서만 값이 있습니다.\n\n"
                    + "목록의 행 클릭으로는 진입하지 않으며, `수정` 버튼으로만 진입합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 공지",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<AdminNoticeDetailResponse> getNotice(
            @Parameter(description = "조회할 공지 ID", required = true)
            @PathVariable("noticeId") Long noticeId
    );

    @Operation(
            summary = "공지 수정",
            description = "공지의 제목 · 본문 · 중요 여부를 수정합니다.\n\n"
                    + "**저장은 상태를 건드리지 않습니다.** 게시 종료 상태에서 저장해도 재게시되지 않으며, "
                    + "재게시는 목록의 `게시` 버튼(게시 API)에서만 일어납니다 — 저장이 곧 게시가 되면 의도치 않은 노출이 생깁니다.\n\n"
                    + "**게시 종료 상태의 수정도 허용합니다.** 내려간 공지의 문구를 미리 손봐 두고 준비되면 게시하는 운영 흐름을 지원합니다.\n\n"
                    + "**중요 체크는 수정 시 해제도 가능합니다.**\n\n"
                    + "본문 이미지 장수(최대 3장)는 등록과 동일하게 서버에서 다시 검증합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "수정 성공 (반환 데이터 없음)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 (제목/본문 필수, 본문 이미지 3장 초과)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 공지",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> updateNotice(
            @Parameter(description = "수정할 공지 ID", required = true)
            @PathVariable("noticeId") Long noticeId,
            @Valid @RequestBody AdminNoticeUpdateRequest request
    );

    @Operation(
            summary = "공지 게시 종료",
            description = "게시 중인 공지를 내립니다. **삭제가 아니라 노출만 중단**하는 동작입니다.\n\n"
                    + "- 소비자 앱 공지사항에서 즉시 내려갑니다.\n"
                    + "- 공지는 삭제되지 않고 `게시 종료` 상태로 목록에 남아, 언제든 다시 게시할 수 있습니다.\n"
                    + "- `endedAt`(게시 종료 일시)이 기록됩니다.\n"
                    + "- 등록일·수정일은 갱신하지 않습니다 — 상태 전이는 본문 수정이 아닙니다.\n"
                    + "- 사유는 받지 않습니다 (운영자 내부 콘텐츠라 소명할 상대가 없습니다).\n\n"
                    + "확인 모달은 필수이나, 위험색이 아닌 **주 액션(파랑)** 버튼을 씁니다 — 되돌릴 수 있는 동작이기 때문입니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "게시 종료 성공 (반환 데이터 없음)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 게시 종료된 공지",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "이미 게시 종료",
                                    value = "{\n"
                                            + "  \"code\": \"INVALID_INPUT\",\n"
                                            + "  \"message\": \"이미 게시 종료된 공지입니다.\"\n"
                                            + "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 공지",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> endNotice(
            @Parameter(description = "게시 종료할 공지 ID", required = true)
            @PathVariable("noticeId") Long noticeId
    );

    @Operation(
            summary = "공지 재게시",
            description = "게시 종료된 공지를 다시 게시합니다.\n\n"
                    + "- 소비자 앱 공지사항에 즉시 노출됩니다.\n"
                    + "- **등록일·수정일은 그대로 유지됩니다** — 갱신하면 오래된 공지가 목록 최상단으로 튀어 올라갑니다. "
                    + "재게시는 새 공지가 아닙니다.\n"
                    + "- `endedAt`(게시 종료 일시)은 비워집니다.\n"
                    + "- 목록에서는 중요 여부와 등록일 순서에 따라 배치됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "재게시 성공 (반환 데이터 없음)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 게시 중인 공지",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "이미 게시 중",
                                    value = "{\n"
                                            + "  \"code\": \"INVALID_INPUT\",\n"
                                            + "  \"message\": \"이미 게시 중인 공지입니다.\"\n"
                                            + "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 공지",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> publishNotice(
            @Parameter(description = "재게시할 공지 ID", required = true)
            @PathVariable("noticeId") Long noticeId
    );
}
