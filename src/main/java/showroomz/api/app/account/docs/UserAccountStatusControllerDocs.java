package showroomz.api.app.account.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.account.dto.AccountStatusResponse;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;

@Tag(name = "User - Account Status", description = "사용자 계정 상태 API")
public interface UserAccountStatusControllerDocs {

    @Operation(
            summary = "계정 이용 상태 조회",
            description = "이용 정지 여부와 앱이 띄울 1회성 안내를 내립니다(C20).\n\n" +
                    "**부르는 때:** 콜드 스타트 · 포그라운드 복귀 · 409 `USER_SUSPENDED` 수신(세션 중 정지) · 푸시 `type=ACCOUNT_STATUS` 수신\n\n" +
                    "**화면 매핑**\n" +
                    "- `status = SUSPENDED` → 모든 탭 헤더 아래 배너(닫기 없음 · 1a) · 마이 「이용 정지」 배지(1f) · 구매/문의/팔로우/좋아요/신고/닉네임·사진 변경 비활성 + 안내 줄(1b · C15-1) · " +
                    "1:1 문의 유형 목록에 「계정 이용 정지」 1개 추가(1d — 등록은 `POST /v1/user/inquiries` 에 `sanctionAppeal: true`)\n" +
                    "- `suspension.noticePending = true` → 정지 안내 시트(1c)를 자동으로 1회 띄운 뒤 `POST /suspension-notice/ack`\n" +
                    "- `suspension.pendingAppealInquiryId` 가 있으면 시트의 「1:1 문의로 이의 신청」이 그 문의 상세로 간다(정지당 답변 전 소명 1건)\n" +
                    "- `releaseNotice` 가 있으면 해제 토스트 1회 뒤 `POST /release-notice/ack`\n" +
                    "- `suspension.term` → 시트의 「정지 기간 N일 · 자동 해제 YYYY-MM-DD · 이의 신청 기한」(기간 없는 정지면 `endsOn` · `autoReleaseAt` · `periodDays` 가 null)\n\n" +
                    "상세 사유는 내리지 않습니다 — 정형 사유(`reasonLabel`)만 노출합니다(§51-6). 진행 중 주문(배송 조회 · 취소 · 구매확정 · 반품 · 교환)과 배송지 관리 · 1:1 문의는 정지와 관계없이 그대로 동작합니다(1e).\n\n" +
                    "**권한:** USER/CREATOR (로그인 필수)\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountStatusResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "이용 정지 · 첫 진입",
                                            value = "{\n" +
                                                    "  \"status\": \"SUSPENDED\",\n" +
                                                    "  \"suspension\": {\n" +
                                                    "    \"reasonCode\": \"ABUSIVE_LANGUAGE\",\n" +
                                                    "    \"reasonLabel\": \"욕설·비방\",\n" +
                                                    "    \"suspendedAt\": \"2026-10-10T14:20:00\",\n" +
                                                    "    \"noticePending\": true,\n" +
                                                    "    \"pendingAppealInquiryId\": null,\n" +
                                                    "    \"term\": {\n" +
                                                    "      \"endsOn\": \"2026-10-16\",\n" +
                                                    "      \"autoReleaseAt\": \"2026-10-17T00:00:00\",\n" +
                                                    "      \"periodDays\": 7,\n" +
                                                    "      \"appealDeadlineAt\": \"2026-10-14T23:59:59\",\n" +
                                                    "      \"appealReviewBusinessDays\": null\n" +
                                                    "    }\n" +
                                                    "  },\n" +
                                                    "  \"releaseNotice\": null\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "해제 직후",
                                            value = "{\n" +
                                                    "  \"status\": \"NORMAL\",\n" +
                                                    "  \"suspension\": null,\n" +
                                                    "  \"releaseNotice\": { \"releasedAt\": \"2026-10-14T09:00:00\" }\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "정상",
                                            value = "{\n  \"status\": \"NORMAL\",\n  \"suspension\": null,\n  \"releaseNotice\": null\n}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "탈퇴 회원",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_WITHDRAWN\", \"message\": \"탈퇴한 회원입니다.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "토큰의 회원을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}")
                    )
            )
    })
    ResponseEntity<AccountStatusResponse> getAccountStatus(@Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "정지 안내 시트 확인",
            description = "정지 안내 시트(1c)를 자동으로 띄운 뒤 부릅니다. 이후 `noticePending` 이 false 가 됩니다 — 기기를 바꿔도 다시 뜨지 않습니다.\n\n" +
                    "멱등입니다. 그 사이 정지가 해제됐으면 아무 일도 하지 않습니다.\n\n" +
                    "**권한:** USER/CREATOR (로그인 필수)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "확인 기록"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "탈퇴 회원",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_WITHDRAWN\", \"message\": \"탈퇴한 회원입니다.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "토큰의 회원을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}")
                    )
            )
    })
    ResponseEntity<Void> ackSuspensionNotice(@Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "해제 토스트 확인",
            description = "해제 토스트를 띄운 뒤 부릅니다. 확인하지 않은 해제가 여럿이어도 한 번에 모두 확인됩니다. 멱등입니다.\n\n" +
                    "**권한:** USER/CREATOR (로그인 필수)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "확인 기록"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "탈퇴 회원",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_WITHDRAWN\", \"message\": \"탈퇴한 회원입니다.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "토큰의 회원을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}")
                    )
            )
    })
    ResponseEntity<Void> ackReleaseNotice(@Parameter(hidden = true) UserPrincipal principal);
}
