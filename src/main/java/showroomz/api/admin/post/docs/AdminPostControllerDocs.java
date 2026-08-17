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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Tag(name = "Admin - Post", description = "운영자 게시물 조치 API (§24-5 · §24-6)")
public interface AdminPostControllerDocs {

    @Operation(
            summary = "게시물 조회",
            description = """
                    **삭제·보관 중인 게시물도 함께** 조회된다 — §24-6이 요구하는 "운영자 콘솔에서만 조회"가 여기다.

                    보관하는 이유는 세 가지다. 플랫폼이 내린 삭제 판단의 정당성 입증 자료,
                    표시광고·명예훼손·저작권 건의 사후 자료 요청 대응, 전자상거래법상 기록 보존 대상과의 중첩.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<PageResponse<AdminPostDto.AdminPostListItem>> getPosts(
            @Parameter(description = "쇼룸 ID로 좁히기", example = "10")
            @RequestParam(value = "showroomId", required = false) Long showroomId,
            @Parameter(description = "상태로 좁히기", example = "SUSPENDED")
            @RequestParam(value = "status", required = false) PostStatus status,
            @Parameter(description = "페이징 정보") @ModelAttribute PagingRequest pagingRequest);

    @Operation(summary = "게시물 상세 조회",
            description = "조치 이력 전체와 이의 신청을 함께 내려준다. 재게시 후 재조치가 가능하므로 조치는 여러 건일 수 있다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminPostDto.AdminPostDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminPostDto.AdminPostDetailResponse> getPost(
            @Parameter(description = "게시물 ID", required = true, example = "301", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId);

    @Operation(summary = "중지 사유 코드 목록",
            description = "어드민 드롭다운이 서버와 같은 코드를 쓰도록 내려준다. `detailRequired`가 true면 상세 사유가 필수다.")
    ResponseEntity<List<AdminPostDto.SuspensionReasonItem>> getSuspensionReasons();

    @Operation(
            summary = "노출 중지",
            description = """
                    게시물을 즉시 내리고 **이의 신청 기간을 함께 시작**한다(§24-5).

                    - 사유 · 근거 규정 · 조치 시각 · 처리자 · 기한이 전부 기록되고 통지 문구로 굳는다 —
                      **알리지 않고 사라지는 경우는 없다**
                    - 중지 중에는 인플루언서가 수정할 수 없다(심사 대상이 도중에 바뀌면 안 된다)
                    - 좋아요·인사이트는 **보존**되고 재게시되면 그대로 복원된다
                    - 기한(기본 7일)은 설정값이다 — 법률 자문 결과에 따라 바뀐다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "중지 완료",
                    content = @Content(schema = @Schema(implementation = AdminPostDto.AdminPostActionResponse.class),
                            examples = @ExampleObject(value = """
                                    { "postId": 301, "status": "SUSPENDED", "appealDeadline": "2026-08-19T10:00:00" }
                                    """))),
            @ApiResponse(responseCode = "400", description = "기타 사유인데 상세 설명이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "게시중이 아닌 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminPostDto.AdminPostActionResponse> suspend(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "게시물 ID", required = true, example = "301", in = ParameterIn.PATH)
            @PathVariable("postId") Long postId,
            @Valid @RequestBody AdminPostDto.SuspendRequest request);

    @Operation(summary = "이의 신청 목록",
            description = "심사 대기(`PENDING`)는 **오래 기다린 순**으로 나온다 — 기한이 걸린 절차라 순서가 곧 형평이다.")
    ResponseEntity<PageResponse<AdminPostDto.AppealItem>> getAppeals(
            @Parameter(description = "심사 상태로 좁히기", example = "PENDING")
            @RequestParam(value = "status", required = false) PostAppealStatus status,
            @Parameter(description = "페이징 정보") @ModelAttribute PagingRequest pagingRequest);

    @Operation(summary = "이의 신청 승인 (→ 재게시)",
            description = """
                    게시물이 다시 노출되고 **좋아요·인사이트가 그대로 복원**된다.
                    중지 기간에도 카운터를 깎거나 로그를 지우지 않았기 때문에 별도 복원 작업이 없다.
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

    @Operation(summary = "이의 신청 반려 (→ 영구 삭제)",
            description = """
                    **반려는 곧 영구 삭제다.** "고쳐서 다시"라는 경로가 없기 때문에 소명·재검토 같은 중간 단계도 없다.

                    다만 통지 직후 완전히 지우지 않는다 — 유예 기간 동안 본인이 사진 원본을 내려받을 수 있고(§24-6),
                    서버는 보관 기간이 끝날 때까지 비공개로 들고 있다가 파기한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 완료"),
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
