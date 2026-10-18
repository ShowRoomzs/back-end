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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.type.AdminUserSort;
import showroomz.api.admin.user.type.AdminUserTab;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.UserPrincipal;
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
            summary = "소비자 상세 조회",
            description = "소비자 상세입니다. 주문 내역은 추후 개발 시 추가할 예정입니다.\n\n" +
                    "**현재 상태 카드(`statusCard`)** — 정지 상태면 사유 · 처리자 · 일시 · 상세 사유 · **소명 문의**(`appealInquiries` — 해제 모달의 「소명 문의 번호」)가 함께 내려갑니다. " +
                    "`actions.canSuspend` = 정상일 때만 · `actions.canRelease` = 정지일 때만 · 탈퇴 회원은 둘 다 false(운영자 액션 없음). " +
                    "탈퇴 회원도 열린 정지가 있으면 그대로 보입니다 — 같은 계정으로 재가입하면 그 정지로 돌아옵니다.\n\n" +
                    "**메모(`memos`)** — 최신 3건 + 전체 건수. `editable`은 요청 운영자 기준(작성자 본인 ∧ 24시간 이내 ∧ 탈퇴 아님)입니다.\n\n" +
                    "**처리 이력(`histories`)** — 최신 5건 + 전체 건수. 저장 사건(정지 · 해제 · 메모)과 파생 사건(가입 · 탈퇴 · 재가입 · 정지 소명 문의 접수)을 합친 최신순입니다.\n\n" +
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
            @Parameter(description = "조회할 유저 ID", required = true, example = "1", in = ParameterIn.PATH)
            @PathVariable("userId") Long userId,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "소비자 계정 정지",
            description = "정지 모달(M1)의 실행입니다. 「변경할 상태」를 받지 않습니다 — 버튼이 이미 목적지를 말합니다(§25-4).\n\n" +
                    "**사유 6종** — `ABUSIVE_LANGUAGE`(욕설·비방) · `RETURN_ABUSE`(반품 남용) · `FRAUDULENT_ORDER_SUSPECTED`(부정 주문 의심) · " +
                    "`PAYMENT_FRAUD_SUSPECTED`(결제 도용 의심) · `MULTIPLE_REPORTS`(신고 다수 접수) · `OTHER`(기타 — `reasonDetail` 필수)\n\n" +
                    "**효과(§51-1)** — 로그인은 그대로 · 새로 벌이는 일만 막힙니다: 구매(장바구니 담기 · 주문서 · 주문 생성 · 결제 재시도) · 상품 문의 · 팔로우 · 좋아요 · 게시물 신고 · 닉네임/프로필 사진 변경. " +
                    "진행 중 주문 · 클레임 · 배송지 · 1:1 문의는 그대로입니다. 앱에는 정형 사유만 보이고 상세 사유는 보이지 않습니다. 정지 푸시가 커밋 뒤 나갑니다.\n\n" +
                    "정지 기간은 없습니다(수동 해제). 대상은 소비자(USER) 계정뿐입니다 — 인플루언서는 인플루언서 상세에서 정지합니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "정지 완료 — 갱신된 상태 카드 + 처리 이력 최신 5건",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.StatusResult.class))),
            @ApiResponse(responseCode = "400", description = "기타 사유 · 상세 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_SUSPEND_DETAIL_REQUIRED\", \"message\": \"기타 사유를 선택하면 상세 사유를 입력해야 합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "소비자 아님 · 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 정지(`USER_ALREADY_SUSPENDED`) · 탈퇴 회원(`USER_SANCTION_WITHDRAWN`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_ALREADY_SUSPENDED\", \"message\": \"이미 정지된 회원입니다.\"}")))
    })
    ResponseEntity<AdminUserDto.StatusResult> suspend(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @RequestBody AdminUserDto.SuspendRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "소비자 정지 해제",
            description = "해제 모달의 실행입니다. **사유 필수 · 상세 사유란 없음**(§25-4) — `RESOLVED`(소명 확인 완료) · `MISTAKE`(오처리 정정) · `TERM_ENDED`(제재 기간 종료 — 자동 해제는 없다).\n\n" +
                    "해제는 회원 상세에서만 합니다(1:1 문의 답변 화면에 해제 버튼을 두지 않습니다). 즉시 모든 기능이 다시 열리고, 해제 푸시가 나가며, 앱은 다음 진입 때 해제 토스트를 1회 띄웁니다. " +
                    "정지 이력과 사유는 그대로 보존됩니다. 사유 기록 없이 정지된 옛 회원도 해제할 수 있습니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "해제 완료 — 갱신된 상태 카드 + 처리 이력 최신 5건",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.StatusResult.class))),
            @ApiResponse(responseCode = "400", description = "해제 사유 누락(`USER_RELEASE_REASON_REQUIRED`) · 소비자 해제 사유 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "정지 아님(`USER_NOT_SUSPENDED`) · 탈퇴 회원(`USER_SANCTION_WITHDRAWN`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserDto.StatusResult> release(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @RequestBody AdminUserDto.ReleaseRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "소비자 처리 이력",
            description = "처리 이력 전체(최신순)입니다. 저장 사건 — 정지 · 해제 · 메모 기입/수정/삭제. 파생 사건(`historyId = null`) — 가입 · 탈퇴 · 재가입 · 정지 소명 문의 접수(`refType = INQUIRY`).\n\n" +
                    "옛 상태 변경 API로 처리된 정지 · 활성 전이도 이관돼 있습니다(처리자 · 사유 미기록).\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.HistoryPage.class)))
    })
    ResponseEntity<AdminUserDto.HistoryPage> getHistories(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @Parameter(description = "페이지(1부터)", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기(최대 100)", example = "20") @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "관리자 메모 목록",
            description = "누적 메모(최신순)입니다(§25-6). `editable` = 작성자 본인 ∧ 24시간 이내 ∧ 탈퇴 아님. " +
                    "옛 단일 메모는 「이전 단일 메모(작성자 미기록)」 한 건으로 이관돼 있고 수정 · 삭제할 수 없습니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.MemoPage.class)))
    })
    ResponseEntity<AdminUserDto.MemoPage> getMemos(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @Parameter(description = "페이지(1부터)", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기(최대 100)", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "관리자 메모 기입",
            description = "덮어쓰기가 아니라 누적입니다. 공백 제거 후 1 ~ 300자(서버 검증). 소비자에게 노출되지 않지만 개인정보 열람청구 시 제공 대상이 될 수 있습니다. 탈퇴 회원은 조회 전용(409).\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "기입 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.Memo.class))),
            @ApiResponse(responseCode = "400", description = "빈 내용 · 300자 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "탈퇴 회원(`USER_SANCTION_WITHDRAWN`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserDto.Memo> createMemo(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @RequestBody AdminUserDto.MemoRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "관리자 메모 수정",
            description = "작성자 본인 · 작성 후 24시간 내에만 고칠 수 있습니다. 저장하면 `modifiedAt`이 채워집니다(「수정됨」).\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.Memo.class))),
            @ApiResponse(responseCode = "403", description = "작성자 아님(`USER_MEMO_NOT_AUTHOR`) · 24시간 경과(`USER_MEMO_EDIT_WINDOW_CLOSED`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "메모 없음(`USER_MEMO_NOT_FOUND`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserDto.Memo> updateMemo(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @Parameter(description = "메모 ID", required = true, example = "3", in = ParameterIn.PATH) @PathVariable("memoId") Long memoId,
            @RequestBody AdminUserDto.MemoRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "관리자 메모 삭제",
            description = "작성자 본인 · 작성 후 24시간 내에만 지울 수 있습니다. 삭제해도 처리 이력의 기입 기록은 남습니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "작성자 아님 · 24시간 경과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteMemo(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @Parameter(description = "메모 ID", required = true, example = "3", in = ParameterIn.PATH) @PathVariable("memoId") Long memoId,
            @Parameter(hidden = true) UserPrincipal principal
    );
}
