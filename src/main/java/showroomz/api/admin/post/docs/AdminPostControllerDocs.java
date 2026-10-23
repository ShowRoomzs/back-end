package showroomz.api.admin.post.docs;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.admin.post.dto.AdminPostDto;
import showroomz.api.admin.post.dto.AdminPostMonitoringDto;
import showroomz.api.admin.post.dto.AdminPostReportDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostMonitoringSort;
import showroomz.domain.post.type.PostMonitoringTab;
import showroomz.domain.post.type.PostReportStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Tag(name = "Admin - Post", description = "관리자 게시물 모니터링 API")
public interface AdminPostControllerDocs {

    // ------------------------------------------------------------------ 조회

    @Operation(
            summary = "일반 게시물 목록 (C1 · C9)",
            description = """
                    일반 게시물만 나온다 — **작성중(노출된 적 없음) · 공구 게시물 제외**. 영구 삭제 · 보관분은 포함된다(운영자 콘솔 전용 조회).

                    **모니터링 상태(`monitoringStatus`)는 저장하지 않는 파생값**이다.
                    | 값 | 조건 | 색 |
                    | --- | --- | --- |
                    | `PUBLISHED` 게시중 | 게시중 · 대기 신고 0 | 성공 |
                    | `REPORTED` 신고 접수 | 게시중 · 대기 신고 ≥ 1 | 경고 |
                    | `SUSPENDED` 노출 중지 | 이의 신청 전 | 위험 |
                    | `UNDER_REVIEW` 이의 신청 심사 중 | — | 경고 |
                    | `CLOSED` 영구 삭제 | 비공개 보관 중 | 중립 |

                    - **정렬 `ACTION_FIRST`(기본)** — 심사 중 · 신고 접수(오래 기다린 순) → 노출 중지(기한 급한 순) → 게시중(최신) → 영구 삭제(최신)
                    - **검색** — 쇼룸명(없으면 닉네임) · 본문 부분 일치
                    - **기한은 절대 시각**(`appealDeadline` · `reviewDueAt` · `purgeAt`)만 내린다 — D-n은 FE가 센다
                    - `reportCount`는 목록의 「신고」 열이다(대기 + 조치로 이어진 신고). 신고 종결로 닫은 신고는 빠져 0으로 돌아간다
                    - `editedAfterFirstReport` — 최초 대기 신고 뒤에 본문이 고쳐졌는가(조치 판단 재료)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<AdminPostMonitoringDto.ListItem>> getPosts(
            @Parameter(description = "탭 — ALL(기본) · REPORTED · SUSPENDED · UNDER_REVIEW(이의 신청) · CLOSED(종결)", example = "ALL")
            @RequestParam(value = "tab", required = false) PostMonitoringTab tab,
            @Parameter(description = "정렬 — ACTION_FIRST(기본) · LATEST · MOST_REPORTED", example = "ACTION_FIRST")
            @RequestParam(value = "sort", required = false) PostMonitoringSort sort,
            @Parameter(description = "쇼룸명 · 본문 검색어", example = "루틴")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "쇼룸 ID로 좁히기 — 인플루언서 상세의 「일반 게시물 N건」 딥링크", example = "10")
            @RequestParam(value = "showroomId", required = false) Long showroomId,
            @Parameter(description = "페이징 — 20 · 50건씩") @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "탭 건수 · GNB 배지",
            description = """
                    탭 5개 건수를 한 번에 센다(검색어와 무관한 전체 기준).
                    `actionRequired` = 신고 접수 + 이의 신청 심사 중 — **GNB 「게시물 · 소통 모니터링 › 일반 게시물」 배지**다.
                    신고 접수 탭이 비어도 심사 1건이 남으면 1이다(C9).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminPostMonitoringDto.SummaryResponse.class),
                            examples = @ExampleObject(value = """
                                    { "all": 6, "reported": 2, "suspended": 1, "underReview": 1, "closed": 1, "actionRequired": 3 }
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminPostMonitoringDto.SummaryResponse> getSummary();

    @Operation(
            summary = "게시물 상세 (C2 · C3 · C5 · C6 · C8 · C10 · C11)",
            description = """
                    상세 진입 **1회 호출**로 모든 카드의 재료를 내린다. 카드 배치는 FE가 `monitoringStatus`로 정한다
                    (신고 접수면 신고 카드가 원문 위 · 노출 중지면 조치 내역 최상단 · 심사 중이면 이의 신청 최상단).

                    - `images[]` — 사진 객체(원본 뷰어용 파일명 · 해상도 · 업로드 시각 · 「신고 지목」)
                    - `metrics` — 스튜디오 인사이트와 같은 집계. 노출 중지 · 심사 중이면 조치 시각, 삭제면 삭제 시각으로 **동결**(`frozenAt`)
                    - `reports.items[]` — 처리분 포함 전부 · 신고자는 **마스킹(한*준)** + 회원 상세용 id
                    - `suspensions[]` — 조치 기록 5항목(사유 · 근거 규정 · 조치 시각 · 처리자 · 기한) · `deadlinePaused` = 이의 신청 접수로 정지
                    - `deletion` — 삭제 유형 · 반려 사유 · 유예 · **원본 내려받기 기록** · 보관 만료(영구 삭제만)
                    - `author` — 앱 내 팔로워 수 · **작성자 조치 이력(횟수만)** — 판단 지표로 가공하지 않는다
                    - `timeline[]` — 처리 이력(최신순). 알림 이력은 영구 보존
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "게시물 없음 · 공구 게시물 · 파기됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminPostMonitoringDto.DetailResponse> getPost(
            @Parameter(description = "게시물 ID", required = true, example = "342", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(summary = "중지 사유 코드 목록 (C4)",
            description = """
                    7종 — 소비자 신고 사유 6종과 같은 축 + 운영자 전용 「개인정보 노출」(`operatorOnly`).
                    `detailRequired`가 true(기타)면 설명이 필수다. 「의료적 효능 표현」은 사유가 아니라 허위 · 과장 정보의 근거 규정(3조 4항)이다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<AdminPostDto.SuspensionReasonItem>> getSuspensionReasons();

    @Operation(summary = "근거 규정 조항 목록 (C4)",
            description = "운영정책 문서가 아직 없어 **가정값**이다. `suggestedFor`는 FE 기본 선택용 추천이고 서버는 조합을 검증하지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<AdminPostDto.PolicyClauseItem>> getPolicyClauses();

    // ------------------------------------------------------------------ 조치

    @Operation(
            summary = "노출 중지 (C4)",
            description = """
                    게시물을 즉시 내리고 **이의 신청 기간(기본 7일)을 함께 시작**한다.

                    - **사유 · 근거 규정 필수**(`policyClause` 누락 → 400 `POST_POLICY_CLAUSE_REQUIRED`). 설명은 선택(기타면 필수)
                    - 조치 기록 5항목이 조치 행과 통지 이력에 동시에 굳는다 — **알리지 않고 사라지는 경우는 없다**
                    - 이 게시물의 대기 신고가 **전부 함께 닫힌다**(조치로 이어짐)
                    - 중지 중에는 작성자도 수정할 수 없다 · 좋아요 · 인사이트는 보존된다
                    - 공구 게시물은 409 — 공구 관리의 숨김으로 처리한다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "중지 완료",
                    content = @Content(schema = @Schema(implementation = AdminPostDto.AdminPostActionResponse.class),
                            examples = @ExampleObject(value = """
                                    { "postId": 342, "status": "SUSPENDED", "monitoringStatus": "SUSPENDED",
                                      "appealDeadline": "2026-08-19T10:00:00", "closedReportCount": 3 }
                                    """))),
            @ApiResponse(responseCode = "400", description = "근거 규정 누락 · 기타 사유인데 설명 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "게시중이 아님 · 공구 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminPostDto.AdminPostActionResponse> suspend(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "게시물 ID", required = true, example = "342", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId,
            @Valid @RequestBody AdminPostDto.SuspendRequest request);

    @Operation(
            summary = "신고 종결 (C3)",
            description = """
                    확인했으나 조치가 필요 없을 때 — 이 게시물의 **대기 신고 전부를 반려로 닫는다**.
                    게시물에는 아무 일도 일어나지 않고 게시중으로 돌아간다(목록 신고 수 0). 신고 이력은 남는다.

                    작성자 · 신고자 **누구에게도 알리지 않는다**. 메모(`note`)는 선택이며 저장하지 않는다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "종결 완료 — closedReportCount에 닫은 건수"),
            @ApiResponse(responseCode = "404", description = "게시물 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "대기 신고 없음 · 게시중 아님(POST_REPORT_NOTHING_TO_CLOSE) · 공구 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminPostDto.AdminPostActionResponse> closeReports(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "게시물 ID", required = true, example = "342", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId,
            @Valid @RequestBody(required = false) AdminPostDto.ActionNoteRequest request);

    @Operation(
            summary = "조치 취소 · 재게시 (C5)",
            description = """
                    노출 중지 상태(이의 신청 전)에서 운영자가 판단을 거둔다 — 다시 노출되고 좋아요 · 인사이트가 그대로 복원된다.
                    작성자에게 「조치 취소」 알림이 간다(`note`가 있으면 함께 실린다). 작성자 조치 이력의 노출 중지 횟수에서 빠진다.

                    이의 신청이 들어온 뒤(심사 중)에는 쓸 수 없다 — 「승인 · 재게시」가 같은 자리다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 완료"),
            @ApiResponse(responseCode = "404", description = "게시물 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "노출 중지 상태가 아님(POST_SUSPENSION_NOT_CANCELABLE) · 공구 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminPostDto.AdminPostActionResponse> cancelSuspension(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "게시물 ID", required = true, example = "342", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId,
            @Valid @RequestBody(required = false) AdminPostDto.ActionNoteRequest request);

    @Operation(summary = "사진 「신고 지목」 켜기 (C3 · C11)",
            description = """
                    신고 문장에 특정 장이 적혀 오면 운영자가 그 사진에 표시한다 — 읽기 보조 표식이고 사진 단위 조치가 아니다.
                    이미 켜져 있으면 처음 켠 시각 · 운영자를 유지한다. 알림 · 이력은 남기지 않는다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "표시 완료"),
            @ApiResponse(responseCode = "404", description = "게시물 없음 · 그 게시물의 사진이 아님(POST_IMAGE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "공구 게시물(POST_NOT_EDITABLE) — 공구 관리에서 다룬다",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> flagImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "게시물 ID", required = true, example = "342", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId,
            @Parameter(description = "사진 ID", required = true, example = "9003", in = ParameterIn.PATH)
            @PathVariable("imageId") Long imageId);

    @Operation(summary = "사진 「신고 지목」 끄기",
            description = "켜기로 붙인 표시를 지운다(켠 시각 · 운영자도 함께 비운다). 이미 꺼져 있어도 204다. 알림 · 이력은 남기지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "해제 완료"),
            @ApiResponse(responseCode = "404", description = "게시물 없음 · 그 게시물의 사진이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "공구 게시물(POST_NOT_EDITABLE) — 공구 관리에서 다룬다",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> unflagImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "게시물 ID", required = true, example = "342", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId,
            @Parameter(description = "사진 ID", required = true, example = "9003", in = ParameterIn.PATH)
            @PathVariable("imageId") Long imageId);

    // ------------------------------------------------------------------ 신고 대기열 · 이의 신청

    @Operation(
            summary = "신고 대기열 조회",
            description = """
                    소비자가 올린 게시물 신고다(공구 게시물 신고 포함 — `postType`으로 가른다).

                    - `status=PENDING`이면 **오래 기다린 순**. 지정하지 않으면 처리분까지 최신순
                    - **신고자는 마스킹(`reporterMaskedName` · 한*준)** + 회원 상세용 `reporterUserId` — 허위 신고 누적 판단용
                    - `pendingCountOnPost` — 같은 게시물을 몇 명이 지목했는지
                    - `suspensionReasonCode` — 이 신고를 받아 내릴 때 그대로 넣는 값(같은 코드 축)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<AdminPostReportDto.ReportItem>> getReports(
            @Parameter(description = "게시물 ID로 좁히기", example = "301")
            @RequestParam(value = "postId", required = false) Long postId,
            @Parameter(description = "처리 상태로 좁히기 — PENDING / ACCEPTED / DISMISSED", example = "PENDING")
            @RequestParam(value = "status", required = false) PostReportStatus status,
            @Parameter(description = "페이징 정보") @ModelAttribute PagingRequest pagingRequest);

    @Operation(summary = "신고 1건 반려 (보조)",
            description = """
                    게시물 단위 「신고 종결」(`POST /posts/{postId}/reports/close`)이 주 경로다. 이건 대기열에서 한 건만 내릴 때 쓴다.
                    게시물에는 아무 일도 일어나지 않고, 신고자에게 알리지 않는다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "반려 완료"),
            @ApiResponse(responseCode = "404", description = "신고를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 처리가 끝난 신고",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> dismissReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "신고 ID", required = true, example = "17", in = ParameterIn.PATH)
            @PathVariable("reportId") Long reportId);

    @Operation(summary = "이의 신청 목록",
            description = "심사 대기(`PENDING`)는 **오래 기다린 순**이다. `reviewDueAt` = 접수 + 3영업일(주말 · 공휴일 제외).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<AdminPostDto.AppealItem>> getAppeals(
            @Parameter(description = "심사 상태로 좁히기", example = "PENDING")
            @RequestParam(value = "status", required = false) PostAppealStatus status,
            @Parameter(description = "페이징 정보") @ModelAttribute PagingRequest pagingRequest);

    @Operation(summary = "이의 신청 승인 · 재게시 (C6)",
            description = """
                    게시물이 다시 노출되고 **좋아요 · 인사이트가 그대로 복원**된다. 신청은 게시물당 1회라 이 판단이 최종이다.
                    심사 의견(`comment`)은 선택이며 작성자 알림에 실린다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 완료"),
            @ApiResponse(responseCode = "404", description = "이의 신청을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 심사가 끝난 신청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminPostDto.AdminPostActionResponse> approveAppeal(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "이의 신청 ID", required = true, example = "2", in = ParameterIn.PATH)
            @PathVariable("appealId") Long appealId,
            @Valid @RequestBody AdminPostDto.AppealReviewRequest request);

    @Operation(summary = "이의 신청 반려 → 영구 삭제 (C7)",
            description = """
                    **반려 사유(`comment`) 필수** — 작성자에게 그대로 전달되고 알림 이력에 영구 보존된다.

                    반려 시각에 영구 삭제로 확정된다. 이후 **유예(기본 7일)** 동안 작성자만 사진 원본을 내려받을 수 있고,
                    원문은 운영자 콘솔에서만 조회되다가 보관 기간이 지나면 파기된다. 되돌릴 수 없다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 완료"),
            @ApiResponse(responseCode = "400", description = "반려 사유 없음(POST_APPEAL_REJECT_REASON_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "이의 신청을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 심사가 끝난 신청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminPostDto.AdminPostActionResponse> rejectAppeal(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "이의 신청 ID", required = true, example = "2", in = ParameterIn.PATH)
            @PathVariable("appealId") Long appealId,
            @Valid @RequestBody AdminPostDto.AppealReviewRequest request);
}
