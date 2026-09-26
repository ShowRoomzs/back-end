package showroomz.api.seller.groupbuy.docs;

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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyAppealAttachmentPresignRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyAppealAttachmentPresignResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyAppealSubmitRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyDetailResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyEarlyCloseRequestRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyExtensionRequestRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyFulfillmentCheckRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyIssueOpenRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyIssueOpenResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyListItem;
import showroomz.api.seller.groupbuy.dto.GroupBuySummaryResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuySuspensionRequestRequest;
import showroomz.domain.groupbuy.type.GroupBuySortType;
import showroomz.domain.groupbuy.type.GroupBuyTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Seller - GroupBuy", description = "파트너센터 공구 관리 API (§29·§30)")
public interface SellerGroupBuyControllerDocs {

    @Operation(
            summary = "공구 목록",
            description = """
                    상태 탭 · 검색 · 정렬로 내 브랜드의 공구를 조회한다(A1~A3).

                    **권한:** SELLER

                    - **목록은 조회 전용이다.** 연장·중단 등 실행은 상세에서만 시작한다(§30-1).
                    - 공구는 계약 체결 시 자동으로 생성된다 — 생성·수정·삭제 API가 없다.
                    - `tab`은 필터일 뿐이며 응답의 `status`는 항상 7종 개별 값이다. 진행중 탭에는 `SUSPENSION_SCHEDULED`(중단 예정)도 들어온다.
                    - `postStatus`는 게시물 8종 — 저장값이 아니라 공구 상태에서 파생한 값이다.
                    - `remark`는 상태만으로 알 수 없는 예외만 **하나** 내린다. 우선순위: 직권 중단 예정 > 중단 요청 검토 > 조기 마감 요청 검토 > 연장 대기 > 운영자 직권 중단. 대부분 행은 `null`이고 문구는 FE가 코드로 고른다.
                    - 빈 상태(A2)와 검색 결과 없음(A3)은 같은 응답이다 — `summary`의 `tabCounts.ALL == 0`으로 구분한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "브랜드(마켓) 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<GroupBuyListItem>> getGroupBuys(
            @Parameter(description = "상태 탭 — 기본 ALL") @RequestParam(required = false) GroupBuyTab tab,
            @Parameter(description = "공구명 · 인플루언서 표시명 · 공구번호 검색") @RequestParam(required = false) String keyword,
            @Parameter(description = "정렬 — START_AT_ASC(기본 · 시작일 빠른순) / CREATED_DESC(생성일순)")
            @RequestParam(required = false) GroupBuySortType sort,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "탭 카운트 · GNB 배지",
            description = """
                    탭별 건수와 「지금 조치해야 하는」 건수를 돌려준다(설계서 4-3).

                    **권한:** SELLER

                    `actionRequiredCount`에 들어가는 것은 셋뿐이다.
                    - 최소 물량 확인 대기 — `PREPARING`이고 아직 확인 전(B1)
                    - 소명 가능 — 직권 중단 통지 중 · 미제출 · 기한 이내(B4i)
                    - 이행 확인 대기 — `ENDED`이고 내 측 확인 전(B5)

                    게시물 반려·숨김, 요청 대기는 넣지 않는다 — 고칠 권한이 브랜드에게 없어 끌 수 없는 배지가 된다.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<GroupBuySummaryResponse> getSummary();

    @Operation(
            summary = "공구 상세",
            description = """
                    상세 화면 22종(B1~B7a)이 **이 응답 하나**를 쓴다. 화면 분기는 FE가 값으로 고른다(설계서 4-4).

                    **권한:** SELLER (본인 브랜드 공구만)

                    - 계약 조건(공구명·상품·고정 지급비·콘텐츠 의무)은 계약에서 그대로 읽는다 — 공구는 복사본을 갖지 않는다.
                    - `permissions` — 버튼 노출 판정. 실행 API가 같은 판정으로 409를 내므로 FE가 복제하지 않는다.
                    - `sales` — 진행중·중단 예정(LIVE) · 정산완료 · 중단에서만 내린다. **종료(ENDED)는 항상 null** — 잠정치가 지급액으로 오해된다(§30-4).
                    - `sales` · `orderClosure` — 판매 모듈 연동 전이라 현재 항상 `null`이다. **0이 아니다** — 미종결 0은 「정산해도 된다」는 뜻이 된다.
                    - `fixedFee.displayText` — 3서피스 문자 단위 동일 표기를 서버가 짓는다. 지급 여부는 싣지 않는다.
                    - `timeline`의 일수는 서버 now(Asia/Seoul) 기준이다.
                    - `history[].actorDisplayName` — 운영자·시스템 주체는 `null`이고 호칭은 FE가 고른다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "해당 브랜드의 공구가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 공구",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> getGroupBuy(@PathVariable Long groupBuyId);

    @Operation(
            summary = "최소 물량 확보 확인",
            description = """
                    B1 `[확보 완료]` — 준비 게이트 ①. 바디 없음.

                    **권한:** SELLER

                    - 서버는 재고를 판정하지 않는다 — 브랜드의 자기 확인이다. 이 기록은 제25조 제재 판정의 증거가 된다.
                    - 운영자 오픈 승인이 이미 끝났으면 이 요청으로 공구가 `READY`(준비완료)가 된다.
                    - **되돌리는 API가 없다**(§33-1 #10 미정).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_STOCK_ALREADY_CONFIRMED · GROUP_BUY_STATUS_CONFLICT(준비중 아님)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> confirmStock(@PathVariable Long groupBuyId);

    @Operation(
            summary = "기간 연장 요청",
            description = """
                    C1 — 인플루언서에게 연장을 요청한다. **공구당 1회**이며 수락·거절과 무관하게 기회가 소진된다.

                    **권한:** SELLER

                    - 진행중에서만 · 공구 종료 12시간 전까지 · 연장 후 총 기간 30일 이하(시작일~새 종료일 양끝 포함).
                    - 새 종료 = 현재 종료 + N일, 시각은 그대로다. 요청만으로 종료일은 바뀌지 않는다 — 인플루언서가 수락해야 바뀐다.
                    - 인플루언서가 종료 시각까지 응답하지 않으면 변경 없이 종결된다(시스템 자동 만료).
                    - 상세의 `extension.maxDays` · `requestCutoffAt`으로 FE가 즉시 계산할 수 있다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_EXTENSION_EXCEEDS_LIMIT · 입력 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_EXTENSION_ALREADY_USED · GROUP_BUY_EXTENSION_WINDOW_CLOSED · "
                    + "GROUP_BUY_REQUEST_ALREADY_PENDING · GROUP_BUY_ACTION_NOT_ALLOWED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> requestExtension(@PathVariable Long groupBuyId,
                                                            @Valid @RequestBody GroupBuyExtensionRequestRequest request);

    @Operation(
            summary = "조기 마감 요청",
            description = """
                    C3 — 운영자에게 조기 마감을 요청한다. 진행중에서만.

                    **권한:** SELLER

                    - 요청만으로 공구 상태는 바뀌지 않는다 — 운영자 승인이 있어야 종료된다.
                    - 검토 중인 요청(중단·조기 마감, 요청자 무관)이 있으면 추가로 요청할 수 없다. 요청은 **취소할 수 없다**.
                    - `ETC`면 `memo` 필수. 반려 후 재요청은 가능하다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_REASON_MEMO_REQUIRED · 입력 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_REQUEST_ALREADY_PENDING · GROUP_BUY_ACTION_NOT_ALLOWED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> requestEarlyClose(@PathVariable Long groupBuyId,
                                                             @Valid @RequestBody GroupBuyEarlyCloseRequestRequest request);

    @Operation(
            summary = "공구 중단 요청",
            description = """
                    C2(진행중) · C4(준비완료) — 운영자에게 중단을 요청한다.

                    **권한:** SELLER

                    - 요청만으로 판매는 멈추지 않는다(제16조②) — 승인 전까지 주문·배송은 그대로 진행된다.
                    - 재고 소진은 중단이 아니라 조기 마감 사유다 — 사유 코드에 없다.
                    - 검토 중인 요청이 있으면 추가로 요청할 수 없다. 요청은 **취소할 수 없다**. `ETC`면 `memo` 필수.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_REASON_MEMO_REQUIRED · 입력 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_REQUEST_ALREADY_PENDING · GROUP_BUY_ACTION_NOT_ALLOWED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> requestSuspension(@PathVariable Long groupBuyId,
                                                             @Valid @RequestBody GroupBuySuspensionRequestRequest request);

    @Operation(
            summary = "소명 증빙 업로드 URL 발급",
            description = """
                    C9 — 직권 중단 소명에 붙일 증빙의 S3 업로드 URL을 발급한다. PNG · JPG · PDF · 10MB 이하 · 최대 5개.

                    **권한:** SELLER

                    발급 후 `uploadUrl`로 PUT하고, 소명 제출 시 `attachmentId`를 싣는다. 제출 시점에 실제 업로드된 크기·타입을 다시 검증한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "발급 완료"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_APPEAL_ATTACHMENT_INVALID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_APPEAL_NOT_OPEN · GROUP_BUY_APPEAL_ALREADY_SUBMITTED · GROUP_BUY_APPEAL_DEADLINE_PASSED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyAppealAttachmentPresignResponse> presignAppealAttachment(
            @PathVariable Long groupBuyId, @Valid @RequestBody GroupBuyAppealAttachmentPresignRequest request);

    @Operation(
            summary = "소명 자료 제출",
            description = """
                    C9 — 직권 중단 사전 통지에 대한 소명(제17조④). 중단 예정 상태 · 소명 기한 이내에서만.

                    **권한:** SELLER

                    - **제출 후 수정할 수 없다.** 소명은 운영자가 판단 근거로 읽는 글이다.
                    - 제출해도 상태는 그대로 `SUSPENSION_SCHEDULED`다 — 집행·철회는 운영자가 판단한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제출 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_APPEAL_ATTACHMENT_INVALID · 입력 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_APPEAL_NOT_OPEN · GROUP_BUY_APPEAL_ALREADY_SUBMITTED · GROUP_BUY_APPEAL_DEADLINE_PASSED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> submitAppeal(@PathVariable Long groupBuyId,
                                                        @Valid @RequestBody GroupBuyAppealSubmitRequest request);

    @Operation(
            summary = "이슈 스레드 열기",
            description = """
                    C5 — 종료 후 정산과 무관한 이견을 운영자가 참여하는 스레드로 연다. 열린 이슈는 1건만.

                    **권한:** SELLER

                    - 종료 또는 **브랜드가 요청하지 않은** 중단에서만.
                    - 이슈는 공구 상태를 바꾸지 않고 정산도 보류하지 않는다.
                    - ⚠️ 연결·소통의 스레드 모델 변경 전이라 현재는 **503 `GROUP_BUY_THREAD_UNAVAILABLE`**을 돌려준다(설계서 5-3).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "개설 완료"),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_ISSUE_ALREADY_OPEN · GROUP_BUY_ACTION_NOT_ALLOWED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "GROUP_BUY_THREAD_UNAVAILABLE — 3자 스레드 미지원",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyIssueOpenResponse> openIssue(@PathVariable Long groupBuyId,
                                                        @Valid @RequestBody GroupBuyIssueOpenRequest request);

    @Operation(
            summary = "계약 이행 확인",
            description = """
                    C6(이행) · C7(미이행) — 인플루언서의 콘텐츠 의무 이행을 확인한다. 종료 상태에서 **1회 · 불가역**.

                    **권한:** SELLER

                    - `UNFULFILLED`면 `reason` 필수 — 운영자가 참여하는 3자 스레드의 첫 글이 되고, 합의 종결 전까지 정산이 보류된다.
                    - 기한이 지나도 받는다 — 무응답 자동 이행은 약관 근거 확정 전까지 꺼져 있다.
                    - ⚠️ 연결·소통의 스레드 모델 변경 전이라 `UNFULFILLED`는 현재 **503 `GROUP_BUY_THREAD_UNAVAILABLE`**이다. `FULFILLED`는 정상 동작한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_FULFILLMENT_REASON_REQUIRED · 입력 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_FULFILLMENT_ALREADY_CHECKED · GROUP_BUY_ACTION_NOT_ALLOWED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "GROUP_BUY_THREAD_UNAVAILABLE — 미이행 3자 스레드 미지원",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> checkFulfillment(@PathVariable Long groupBuyId,
                                                            @Valid @RequestBody GroupBuyFulfillmentCheckRequest request);
}
