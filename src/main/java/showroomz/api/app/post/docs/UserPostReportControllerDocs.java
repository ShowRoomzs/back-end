package showroomz.api.app.post.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostReportReasonItem;
import showroomz.api.app.post.DTO.PostReportRequest;

import java.util.List;

@Tag(name = "User - Post Report", description = "게시물 신고 API (C4 ⋯ 시트 · 하단 고지)")
public interface UserPostReportControllerDocs {

    @Operation(
            summary = "신고 사유 목록",
            description = """
                    신고 시트의 선택지를 내려준다. 사유는 운영정책과 함께 움직이는 값이라 앱이 문구를
                    직접 들고 있으면 규정이 바뀔 때마다 재배포해야 한다.

                    - **detailRequired** — true인 항목(기타)을 고르면 상세 사유 입력란을 필수로 연다
                    - 코드 축은 어드민 조치 화면(`GET /v1/admin/posts/suspension-reasons`)과 **같다**.
                      신고를 받아 노출 중지를 누를 때 사유가 그대로 이어진다
                    - **목록은 잠정이다** — C5 §남은 결정 ③ "신고 사유 선택 화면"이 확정되면 값이 바뀔 수 있다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PostReportReasonItem.class))))
    })
    ResponseEntity<List<PostReportReasonItem>> getReportReasons();

    @Operation(
            summary = "게시물 신고",
            description = """
                    게시물을 운영자에게 신고한다. 접수만 하고 **아무 조치도 자동으로 일어나지 않는다** —
                    반려 시 영구 삭제가 걸린 절차라(§24-5) 판단은 사람이 하고, 서버는 대기열에 쌓아
                    오래 기다린 순서를 지킨다. 화면은 "접수됐습니다" 하나로 끝내고 처리 상태를 노출하지 않는다.

                    - **로그인 필요** — C4의 비로그인 열람은 읽기에 걸린 규칙이다. 팔로우·♥와 같이
                      C0 로그인으로 전환한 뒤 중단한 액션을 이어서 실행한다
                    - **사람당 게시물당 1회** — 다시 보내면 409다. 건수가 "몇 명이 문제라고 봤는가"를
                      뜻해야 운영자가 그 수로 우선순위를 매길 수 있다
                    - **자기 게시물은 신고할 수 없다** — 내리고 싶으면 스튜디오에서 삭제한다(§24-3)
                    - 이미 내려간 게시물은 **404**다. 조치가 들어갔다는 사실을 신고자에게 흘리지 않는다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "접수 완료"),
            @ApiResponse(responseCode = "400", description = "사유 누락 · 기타인데 상세 사유 없음 · 자기 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "상세 사유 필요", value = """
                                            { "code": "POST_REPORT_DETAIL_REQUIRED", "message": "기타 사유를 선택한 경우 상세 사유는 필수입니다." }
                                            """),
                                    @ExampleObject(name = "자기 게시물", value = """
                                            { "code": "POST_REPORT_SELF_NOT_ALLOWED", "message": "자신의 게시물은 신고할 수 없습니다." }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 이미 내려간 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "POST_NOT_FOUND", "message": "존재하지 않는 게시글입니다." }
                                    """))),
            @ApiResponse(responseCode = "409", description = "이미 신고한 게시물",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "POST_REPORT_ALREADY_SUBMITTED", "message": "이미 신고한 게시물입니다." }
                                    """)))
    })
    ResponseEntity<Void> reportPost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody PostReportRequest request);
}
