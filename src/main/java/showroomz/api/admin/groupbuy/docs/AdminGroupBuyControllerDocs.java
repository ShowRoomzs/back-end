package showroomz.api.admin.groupbuy.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDetailResponse;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDto;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyListItem;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuySummaryResponse;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.groupbuy.type.AdminGroupBuySortType;
import showroomz.domain.groupbuy.type.AdminGroupBuyTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Tag(name = "Admin - GroupBuy", description = "어드민 공구 관리 API (§29·§32) — 목록 · 조치 큐 · 상세 · 판정 6종")
public interface AdminGroupBuyControllerDocs {

    // ── 조회 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "공구 목록",
            description = """
                    모든 공구를 탭 · 검색 · 정렬로 조회한다(A1 · A2 · A3). 가시성 필터가 없다 — 어드민은 모든 공구를 본다.

                    **권한:** ADMIN

                    - `tab` 8종 — `ALL` · `ACTION_REQUIRED`(조치 큐 술어 탭) · `PREPARING` · `READY` · `IN_PROGRESS`(중단 예정 포함) ·
                      `ENDED` · `SETTLED` · `SUSPENDED`. 파트너·스튜디오와 달리 **종료와 정산완료를 가른다** — 정산 지연 감시는 종료 탭에서 본다.
                    - `sort` 기본 `ACTION_REQUIRED_FIRST` — 조치 큐 해당 행이 먼저(「상단 고정」은 별도 목록이 아니라 정렬 키다) → 최근 등록순.
                      `END_AT_ASC`는 비종결을 종료 임박순으로, 종결은 최근 종결순으로 뒤에 둔다.
                    - `actionRequired` — 요약의 `actionRequiredCount`와 같은 판정식. 경고 배경은 FE가 탭으로 끈다(조치 필요 탭에서는 그리지 않는다).
                    - **목록에 실행 액션이 없다** — `permissions`도, 무엇이 걸렸는지(`queueKind`)도 싣지 않는다. 상세가 답한다.
                    - 검색 대상: 공구명 · 공구번호 · 브랜드명 · 쇼룸명.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<PageResponse<AdminGroupBuyListItem>> getGroupBuys(
            @Parameter(description = "탭 — 기본 ALL") @RequestParam(required = false) AdminGroupBuyTab tab,
            @Parameter(description = "공구명 · 공구번호 · 브랜드명 · 쇼룸명 검색") @RequestParam(required = false) String keyword,
            @Parameter(description = "정렬 — ACTION_REQUIRED_FIRST(기본) / CREATED_DESC / END_AT_ASC")
            @RequestParam(required = false) AdminGroupBuySortType sort,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "조치 큐 · 탭 카운트 · 최단 기한 · 정산 지연 감시",
            description = """
                    GNB 배지 · 탭 배지 · 툴바 요약이 이 응답 하나를 쓴다. **파라미터를 받지 않는다** — 검색어가 걸린 카운트는 처리할 건을 가린다.

                    **권한:** ADMIN

                    조치 큐 = 「내가 눌러야 다음으로 가는 것」 4종 — 서로 배타적이라 `actionRequiredCount` = 큐 합 = 조치 필요 탭 행 수다.
                    - `OPEN_REVIEW` 오픈 승인 — 준비중 ∧ 게시물 승인대기
                    - `SUSPEND_REQUEST` 중단 요청 · `EARLY_CLOSE_REQUEST` 조기 마감 요청 — 검토 중 요청
                    - `APPEAL_REVIEW` 소명 검토 — 직권 중단 통지 중 ∧ (소명 제출됨 ∨ **소명 기한 경과**)

                    게시물 숨김(다음 차례 인플루언서) · 소명 대기(브랜드) · 이행 미합의(3자 스레드)는 넣지 않는다.

                    - `nearestDeadlines` — 큐마다 가장 이른 1건. 요청 2종은 검토 SLA 미정이라 행이 없다(기한을 지어내지 않는다).
                    - `settlementWatch` — 조치와 다른 축. 기한(종료 +30일)을 넘겨도 `actionRequiredCount`에 더하지 않는다. 톤은 `reached`로 고른다.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<AdminGroupBuySummaryResponse> getSummary();

    @Operation(
            summary = "공구 상세",
            description = """
                    상세 13종(B1~B6)과 모달(M1~M7)의 배경이 **이 응답 하나**를 쓴다. 화면 분기는 FE가 상태 × 사실 조합으로 고른다.

                    **권한:** ADMIN

                    - **판단 근거 숫자는 포트에서 오고, 없으면 null이다** — 요청 후 증가분 · CS 문의 · 통지 후 증가분 · 미종결. 거짓 0은 오판으로 직행한다.
                      판매 모듈 연동 전이라 `sales` · `decisionBasis.ordersSinceRequest` · `inquiries.total` · `closure.acceptedOrderCount`는 현재 null이다.
                    - `decisionBasis.inquiries.defectRelated`는 항상 null — 문의 유형에 하자 분류가 없다.
                    - `post` — 원문 전체 · 판본(`latestRevisionNo`) · 수정 횟수. 숨김 해제 요청에 `latestRevisionNo`를 그대로 돌려준다.
                    - `adminSuspension.noticeRevisionNo` ≠ `post.latestRevisionNo`면 통지 후 게시물이 바뀌었다 — 판본 목록 API로 대조한다.
                    - 소명 첨부 URL은 싣지 않는다 — 첨부 다운로드 API로 클릭 시 발급한다.
                    - `afterEnd.settlement.blockers` — 「왜 아직 정산이 안 되나」를 서버가 판정한다. 사유가 둘이면 카드도 둘.
                    - `afterEnd.fulfillment` — 방향 이름(`brandToCreator` · `creatorToBrand`). 미이행 행은 합의 후에도 UNFULFILLED 그대로다.
                    - `permissions` — 실행 API가 같은 판정으로 409를 낸다. `noticeUnavailableReason`으로 통지 버튼이 왜 잠겼는지 보인다.
                    - `history` — 최신순 · 이벤트 전량 · 운영자 실명. `synthetic: true`는 게시물 리비전에서 합성한 `POST_EDITED` 행이다.
                    - `tab` · `keyword` · `sort`를 넘기면 `navigation`에 목록 기준 이전·다음 공구를 채운다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDetailResponse> getGroupBuy(
            @PathVariable Long groupBuyId,
            @Parameter(description = "목록 탭 — 이전·다음 계산용") @RequestParam(required = false) AdminGroupBuyTab tab,
            @Parameter(description = "목록 검색어 — 이전·다음 계산용") @RequestParam(required = false) String keyword,
            @Parameter(description = "목록 정렬 — 이전·다음 계산용") @RequestParam(required = false) AdminGroupBuySortType sort);

    @Operation(
            summary = "게시물 판본 목록",
            description = """
                    「수정 전후 본문 대조 ↗」 — 원문 판본을 전부 내린다. **차분은 서버가 계산하지 않는다**(표시 규칙 미정) — FE가 두 판본을 나란히 놓는다.

                    **권한:** ADMIN

                    판본마다 표지 — `approved`(승인된 판) · `hiddenBasis`(숨김 기준) · `unhiddenBasis`(해제 판단) · `noticeBasis`(최근 통지 기준) · `latest`.
                    게시물이 없으면 빈 배열이다. 페이징하지 않는다.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<List<AdminGroupBuyDto.PostRevisionItem>> getPostRevisions(@PathVariable Long groupBuyId);

    @Operation(
            summary = "직권 중단 통지 날짜 선택지(M6)",
            description = """
                    M6의 소명 기한 · 집행 예정 칩을 **서버가 만든다** — 서버 시각(Asia/Seoul) · 공휴일 설정 기준. FE가 달력 규칙을 다시 계산하지 않는다.

                    **권한:** ADMIN

                    - 소명 기한 하한 = 통지일 다음 날부터 3영업일째 23:59:59(제17조④ · 통지일 불산입)
                    - 집행 예정 = 통지일 +3영업일 이후(제17조②) ∧ **소명 기한보다 엄격히 뒤** ∧ 공구 종료 전
                    - `executionDates`는 영업일만(주말이 빠지는 것이 칩에 그대로 보인다) · 종료일까지(종료일은 `latestExecutionBefore` 전 시각만 유효 · 종료가 자정 정각이면 전날까지). 잠긴 날은 `selectable: false`(회색 취소선)
                    - `available: false`면 M6을 열 수 없다 — `unavailableReason`(STATUS · REQUEST_PENDING · NO_WINDOW_BEFORE_END)
                    - 「직접 입력」도 통지 API가 같은 규칙으로 검증한다
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<AdminGroupBuyDto.NoticeOptionsResponse> getNoticeOptions(@PathVariable Long groupBuyId);

    @Operation(
            summary = "소명 첨부 다운로드 URL",
            description = """
                    B2c 「첨부 N건」 — 클릭 시 발급한다(유효 5분). 업로드가 확인된 첨부만.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_APPEAL_ATTACHMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.AttachmentUrlResponse> getAppealAttachmentUrl(
            @PathVariable Long groupBuyId, @PathVariable Long attachmentId);

    // ── 오픈 승인 · 게시물 ────────────────────────────────────────────────────

    @Operation(
            summary = "오픈 승인(B1)",
            description = """
                    게이트 ③. 바디 없음. 브랜드 물량 확인(게이트 ①)이 이미 끝났으면 **이 요청이 준비완료를 만든다**.

                    **권한:** ADMIN

                    - 응답 `status`로 완료 문구를 고른다 — `READY`(준비완료 · `openAt` = 시작 일시) / `PREPARING`(브랜드 물량 확인 대기)
                    - 게시물 노출은 이 API가 하지 않는다 — 준비완료의 게시물은 「예약」이고 시작 시각에 스케줄러가 연다
                    - 응답·통지·이력 어디에도 고정 지급비를 언급하지 않는다(§29-9)
                    - 운영자 둘이 동시에 승인·반려하면 늦은 쪽이 409
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 완료"),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_OPEN_REVIEW_NOT_PENDING",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> approveOpen(
            @PathVariable Long groupBuyId, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "오픈 반려(M1)",
            description = """
                    사유 7종(심사 기준과 같은 축) + **설명 필수**(사유와 무관) — 고칠 문장을 지목하지 않으면 재등록이 반복된다.
                    공구는 준비중에서 멈춘다. 반려 횟수는 세지 않는다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 완료"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_REJECT_DETAIL_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_OPEN_REVIEW_NOT_PENDING",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> rejectOpen(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.OpenRejectRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "게시물 숨김(M5)",
            description = """
                    사유 7종 + 설명 필수. **공구는 멈추지 않는다** — 게시물만 소비자 화면에서 내려간다. 숨김 중에는 양측의 연장·중단·조기 마감 요청이 막힌다.

                    **권한:** ADMIN

                    - 승인된 게시물 · 준비완료 · 진행중 · 중단 예정에서 받는다(준비완료에서 숨기면 숨긴 채로 열린다)
                    - 숨김 판본은 서버가 잠금 안에서 읽은 최신 판본을 박는다. `observedRevisionNo`와 다르면 응답 `revisionAdvanced: true`
                      — 「숨기는 사이 게시물이 수정됐습니다 — 최신 본문을 확인하세요」
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "숨김 완료"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_REJECT_DETAIL_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_POST_NOT_HIDEABLE · GROUP_BUY_POST_ALREADY_HIDDEN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> hidePost(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.PostHideRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "게시물 숨김 해제(B2b)",
            description = """
                    **읽은 글 = 여는 글** — 상세에서 받은 `post.latestRevisionNo`를 `expectedRevisionNo`로 돌려준다. 그사이 인플루언서가 고쳤으면
                    409 `GROUP_BUY_POST_CHANGED_SINCE_VIEW`(운영자에게 보이는 게 정상이다 — 다시 읽고 판단한다).

                    **권한:** ADMIN

                    - 「고쳤는가」를 서버가 판정하지 않는다 — 숨김이 잘못이었다고 판단할 수 있어야 한다
                    - 해제 사유를 받지 않는다 — 판본 번호가 근거 기록이다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 완료"),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_POST_NOT_HIDDEN · GROUP_BUY_POST_CHANGED_SINCE_VIEW",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> unhidePost(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.PostUnhideRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    // ── 직권 중단 ────────────────────────────────────────────────────────────

    @Operation(
            summary = "직권 중단 사전 통지(M6)",
            description = """
                    진행중 → 중단 예정. **판매는 멈추지 않는다.** 브랜드는 소명 기한까지 소명할 수 있다.

                    **권한:** ADMIN

                    - 사유는 제17조① 1~4호 고정(기타 없음) · 본문 필수 2,000자
                    - 날짜 규칙은 통지 날짜 선택지 API와 같다 — 어기면 400 `GROUP_BUY_SUSPENSION_SCHEDULE_INVALID`
                    - **검토 중 중단·조기 마감 요청이 있으면 409 `GROUP_BUY_REQUEST_DECIDE_FIRST`** — 요청을 먼저 판정한다
                    - 3호 선택 시 응답 `clauseCaution: "C2_POST_ALTERATION"` — 게시물 변경을 이유로 삼으면 브랜드가 소명할 수 없는 사유다
                    - 숨김 중에도 받는다 · 대기 중인 연장은 그대로 둔다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "통지 완료"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_SUSPENSION_SCHEDULE_INVALID · GROUP_BUY_DECISION_REASON_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_STATUS_CONFLICT · GROUP_BUY_REQUEST_DECIDE_FIRST · GROUP_BUY_SUSPENSION_ALREADY_NOTICED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> noticeSuspension(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.SuspensionNoticeRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "직권 중단 집행(B2c)",
            description = """
                    중단 예정 → 중단(재개 불가). **소명 기한이 지나고 통지한 집행 예정 일시가 되어야** 열린다 — 통지 내용보다 먼저 판매를 끊지 않는다.
                    자동 집행하지 않는다.

                    **권한:** ADMIN (판매를 멈추는 판정 — 최고관리자 한정 정책 확정 시 검사 지점)

                    - `executionNote` 필수 — 소명을 낸 브랜드는 그 소명이 왜 받아들여지지 않았는지를 들어야 한다(집행 모달 M8 추가 요청)
                    - 종결 부수 효과(대기 연장 만료 · 게시물 내림 · 상품 판매 차단 재계산)는 종결 경로 공용 처리로 한다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "집행 완료"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_DECISION_REASON_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_SUSPENSION_NOT_NOTICED · GROUP_BUY_SUSPENSION_EXECUTION_NOT_DUE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> executeSuspension(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.SuspensionExecuteRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "직권 중단 철회(M7)",
            description = """
                    중단 예정 → 진행중. 공구는 원래 일정대로(종료 예정 불변). 소명 기한 전에도 철회할 수 있다.

                    **권한:** ADMIN

                    - 사유 4종 + 설명 필수 — 사유 코드로 제재 이력 연동(「귀책 없음」은 세지 않는다)을 가른다
                    - 같은 사유로 다시 통지하면 절차가 처음부터다(3영업일이 다시 흐른다)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "철회 완료"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_DECISION_REASON_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_SUSPENSION_NOT_NOTICED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> withdrawSuspension(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.SuspensionWithdrawRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "긴급 직권 중단(M4)",
            description = """
                    진행중·중단 예정 → 중단(즉시 · 재개 불가). 사후 통지. **사유 3종 고정 · 기타 없음**(제17조③).

                    **권한:** ADMIN (판매를 멈추는 판정)

                    - 진행 중 사전 통지가 있으면 SUPERSEDED(긴급 집행으로 대체)로 닫는다
                    - 준비완료에서는 받지 않는다 — 판매가 없어 피해 급증이 성립하지 않는다(브랜드 중단 요청 경로가 있다)
                    - 긴급 집행 후 소명 API는 없다 — 사후 이의는 이슈 스레드가 받는다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "중단 완료"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_DECISION_REASON_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_STATUS_CONFLICT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> emergencySuspend(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.EmergencySuspensionRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    // ── 요청 판정 ────────────────────────────────────────────────────────────

    @Operation(
            summary = "중단·조기 마감 요청 승인(M2 · B4)",
            description = """
                    **경로의 요청 id를 판정한다** — 운영자가 읽은 그 요청이다. 사유 필수 · 요청자와 상대 모두에게 간다.

                    **권한:** ADMIN (중단 승인은 판매를 멈추는 판정)

                    - 중단 요청: 준비완료·진행중 → 중단 · 조기 마감 요청: 진행중 → 종료(조기 마감 · 승인 시각이 종료 시각)
                    - 조기 마감도 사유 필수 — 인플루언서에게는 처음 듣는 종료다(모달 추가 요청)
                    - 스케줄러 종료가 먼저면 요청이 만료돼 409
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 완료"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_DECISION_REASON_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_CHANGE_REQUEST_NOT_PENDING · GROUP_BUY_STATUS_CONFLICT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> approveRequest(
            @PathVariable Long groupBuyId, @PathVariable Long requestId,
            @Valid @RequestBody AdminGroupBuyDto.ChangeRequestDecisionRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "중단·조기 마감 요청 반려(M3 · B4)",
            description = """
                    요청만 기각되고 공구는 일정대로. 사유 필수 — 재요청 조건을 함께 적도록 유도한다(서버가 구조화하지 않는다).
                    요청자와 상대 모두에게 통지한다. 반려 후 재요청은 막지 않는다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 완료"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_DECISION_REASON_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_CHANGE_REQUEST_NOT_PENDING",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> rejectRequest(
            @PathVariable Long groupBuyId, @PathVariable Long requestId,
            @Valid @RequestBody AdminGroupBuyDto.ChangeRequestDecisionRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    // ── 이슈 · 정산 ──────────────────────────────────────────────────────────

    @Operation(
            summary = "이슈 스레드 개설(B5 · B5b)",
            description = """
                    종료 · 정산완료 · 중단에서 연다(열린 이슈 1건). 정산을 보류하지 않는다 — 이슈는 정산과 무관한 이견이다.
                    중단은 긴급 건의 사후 이의 창구다.

                    **권한:** ADMIN

                    🚧 스레드 포트 선행 — 연결·소통의 스레드 모델 변경 전에는 503 `GROUP_BUY_THREAD_UNAVAILABLE`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "개설 완료"),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_ISSUE_ALREADY_OPEN · GROUP_BUY_ACTION_NOT_ALLOWED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "GROUP_BUY_THREAD_UNAVAILABLE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminGroupBuyDto.IssueOpenResponse> openIssue(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.IssueOpenRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "정산 확인(B5 계열)",
            description = """
                    정산대기 → 운영자 확인. **정산 모듈에 위임**하고 공구 상태는 바꾸지 않는다(정산완료는 이체 완료 통보가 만든다).

                    **권한:** ADMIN

                    🚧 착수 게이트 — 정산 모듈 연동 전에는 `permissions.canConfirmSettlement`가 항상 false이고 이 API는 409다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "확인 완료"),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_SETTLEMENT_NOT_READY",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> confirmSettlement(@PathVariable Long groupBuyId, @Parameter(hidden = true) UserPrincipal principal);
}
