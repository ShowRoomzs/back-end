package showroomz.api.creator.groupbuy.docs;

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
import showroomz.api.creator.groupbuy.dto.CreatorExtensionRejectRequest;
import showroomz.api.creator.groupbuy.dto.CreatorFulfillmentCheckRequest;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyDetailResponse;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyListItem;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyPostRequest;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuySummaryResponse;
import showroomz.api.creator.groupbuy.dto.CreatorSuspensionRequestRequest;
import showroomz.domain.groupbuy.type.CreatorGroupBuySortType;
import showroomz.domain.groupbuy.type.CreatorGroupBuyTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Creator - GroupBuy", description = "쇼룸 스튜디오 공구 관리 API (§29·§31)")
public interface CreatorGroupBuyControllerDocs {

    @Operation(
            summary = "공구 목록",
            description = """
                    내 공구를 상태 탭 · 검색 · 정렬로 조회한다(A1 · A2).

                    **권한:** CREATOR

                    - 공구는 계약 체결 시 자동으로 생긴다 — 생성·수정·삭제 API가 없다. 빈 상태(A2)의 CTA는 계약 관리로 가는 FE 링크다.
                    - `tab`은 6종이다(§29-2) — 중단은 「종료·정산」과 별도 탭이다. 응답의 `status`는 항상 7종 개별 값이다.
                    - `sort` 기본 `START_AT_ASC`는 **비종결 먼저(시작일 오름차순) → 종결(시작일 내림차순)**이다. 순수 오름차순이 아니다.
                    - `ACTION_REQUIRED_FIRST`는 `actionRequired`인 행을 먼저 둔다. 판정식은 요약의 `actionRequiredCount`와 같다.
                    - 비고(`remark`) 열이 없다(§31-1). 무엇을 해야 하는지는 상세에서 본다.
                    - 검색 대상: 공구명 · 브랜드명 · 공구번호.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<PageResponse<CreatorGroupBuyListItem>> getGroupBuys(
            @Parameter(description = "상태 탭 — 기본 ALL") @RequestParam(required = false) CreatorGroupBuyTab tab,
            @Parameter(description = "공구명 · 브랜드명 · 공구번호 검색") @RequestParam(required = false) String keyword,
            @Parameter(description = "정렬 — START_AT_ASC(기본) / ACTION_REQUIRED_FIRST(내 조치 필요 먼저)")
            @RequestParam(required = false) CreatorGroupBuySortType sort,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "탭 카운트 · 내 조치 필요 수",
            description = """
                    GNB 배지 「공구 관리 N」과 목록 헤더 「내 조치가 필요한 공구 N건」이 같은 값을 쓴다(31 설계 1-3).

                    **권한:** CREATOR

                    `actionRequiredCount`에 들어가는 것
                    - 게시물 작성 필요 — 준비중 ∧ 게시물 없음·작성중(B1)
                    - 게시물 재등록 필요 — 준비중 ∧ 반려(B3)
                    - 숨김 게시물 수정 필요 — 준비완료·진행중 ∧ 숨김 중(B5a)
                    - 연장 응답 필요 — 진행중 ∧ 연장 대기 ∧ 종료 전(B6). **무응답 = 변경 없이 종결**이라 배지가 중요하다
                    - 이행 확인 필요 — 종료 ∧ 내 확인 전(B7)

                    승인대기·준비완료·브랜드 요청 검토 중·직권 중단 예고·내가 낸 요청 대기는 넣지 않는다 — 내가 할 조치가 없다.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<CreatorGroupBuySummaryResponse> getSummary();

    @Operation(
            summary = "공구 상세",
            description = """
                    상세 화면(B1~B13)과 모달(C1~C7)의 배경이 **이 응답 하나**를 쓴다. 화면 분기는 FE가 값으로 고른다.

                    **권한:** CREATOR (내 공구만 — 남의 공구는 **404**다. 403이 아니다)

                    - **파트너 응답과 필드 집합이 다르다.** 최소 준비 물량 · 비고 · 직권 중단 소명 · 브랜드가 운영자에게 쓴 요청 메모 ·
                      고정 지급비 지급 신고는 내리지 않는다.
                    - `payout` — 「내가 받는 금액」. 비종결에서만 내리고 `platformGuaranteed: false`로 미보증 고지를 붙이게 한다.
                    - `sales` — 종료(ENDED)에서도 `basis: PROVISIONAL`(잠정)로 내린다. 판매 모듈 연동 전이라 현재는 항상 `null`이다(0이 아니다).
                    - `readiness.registrationDeadline` — 승인 SLA(영업일)를 역산한 등록 마감일. FE가 날짜를 계산하지 않는다.
                    - `post.disclosureText` — 대가관계 표시. 저장하지 않고 서버가 조립한다. 게시물이 없어도 미리보기용으로 내린다.
                    - `afterEnd.fulfillment.autoConfirmOnTimeout` — false면 「기한까지 답하지 않으면 이행으로 처리」 문구를 쓰면 안 된다.
                    - `tab` · `keyword` · `sort`를 넘기면 `navigation`에 목록 기준 이전·다음 공구를 채운다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND · GROUP_BUY_NOT_OWNED_BY_CREATOR(문구 동일)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> getGroupBuy(
            @PathVariable Long groupBuyId,
            @Parameter(description = "목록 탭 — 이웃 계산용") @RequestParam(required = false) CreatorGroupBuyTab tab,
            @Parameter(description = "목록 검색어 — 이웃 계산용") @RequestParam(required = false) String keyword,
            @Parameter(description = "목록 정렬 — 이웃 계산용") @RequestParam(required = false) CreatorGroupBuySortType sort);

    @Operation(
            summary = "공구 게시물 임시저장",
            description = """
                    C3 · C4 `[임시저장]`. 최초 호출이 게시물을 만들고 이후 호출은 덮어쓴다.

                    **권한:** CREATOR

                    - 허용: 준비중 ∧ 게시물 없음·작성중·반려(`permissions.canWritePost`).
                    - 제목 40자 · 본문 2,000자 · **둘 중 하나는 입력**. 필수 검증은 제출 시점의 일이다.
                    - **반려 게시물을 임시저장해도 반려 그대로다** — 반려 사유 카드가 고치는 동안 사라지지 않는다.
                    - 이력·알림·리비전을 남기지 않는다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_POST_EMPTY_DRAFT · GROUP_BUY_POST_TOO_LONG",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_POST_UNDER_REVIEW(승인대기) · GROUP_BUY_POST_NOT_WRITABLE · "
                    + "GROUP_BUY_STATUS_CONFLICT(동시 최초 저장 — 다시 조회해 수정 모드로)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> saveDraft(
            @PathVariable Long groupBuyId, @RequestBody CreatorGroupBuyPostRequest request);

    @Operation(
            summary = "공구 게시물 등록하고 검토 요청",
            description = """
                    C4 `[등록하고 검토 요청]` — 준비 게이트 ②. **저장과 제출을 한 요청으로 받는다.**

                    **권한:** CREATOR

                    - 허용 조건은 임시저장과 같다. 제목·본문 **모두 필수**.
                    - 제출 후 운영자 검토(승인대기) 동안은 수정·취소·재제출이 모두 불가다(B2).
                    - 공구 상태는 바뀌지 않는다 — 운영자 오픈 승인(게이트 ③)이 남아 있다.
                    - 본문에 대가관계 문구를 넣지 않는다 — 서버가 렌더링 시점에 붙인다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제출 완료 — 갱신된 상세(post.status = PENDING_APPROVAL)"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_POST_REQUIRED_FIELD · GROUP_BUY_POST_TOO_LONG",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_POST_UNDER_REVIEW · GROUP_BUY_POST_NOT_WRITABLE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> submitPost(
            @PathVariable Long groupBuyId, @RequestBody CreatorGroupBuyPostRequest request);

    @Operation(
            summary = "승인 후 게시물 수정",
            description = """
                    B4 · B5 · B5a · B10 `[게시물 수정]` — **즉시 반영 · 재승인 없음**(인플 제14조⑤).

                    **권한:** CREATOR

                    - 허용: 승인 ∧ 준비완료·진행중(숨김 중 포함 — `permissions.canEditPost`).
                    - **중단 예정(직권 중단 통지 중)에는 잠긴다** — 통지 시점의 게시물이 집행 판정의 근거다.
                    - 제목·본문 모두 필수. 수정 전후 원문은 서버가 리비전으로 보관한다.
                    - 숨김 중 수정은 운영자에게 통지되지만 **숨김이 자동으로 풀리지 않는다**.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_POST_REQUIRED_FIELD · GROUP_BUY_POST_TOO_LONG",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_POST_UNDER_REVIEW · GROUP_BUY_POST_NOT_EDITABLE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> editPost(
            @PathVariable Long groupBuyId, @RequestBody CreatorGroupBuyPostRequest request);

    @Operation(
            summary = "기간 연장 수락",
            description = """
                    C1 `[수락]` — 바디 없음. **되돌릴 수 없다.**

                    **권한:** CREATOR

                    - 응답 기한은 **현재 종료 시각**이다. 무응답이면 변경 없이 종결된다.
                    - 수락하면 종료일이 요청의 새 종료일로 바뀐다. 리워드율·고정 지급비는 그대로다.
                    - 종료 스케줄러와 동시에 들어오면 **하나만** 통과한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수락 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_EXTENSION_NOT_PENDING · GROUP_BUY_EXTENSION_RESPONSE_CLOSED · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED(중단 예정 등) · GROUP_BUY_STATUS_CONFLICT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> acceptExtension(@PathVariable Long groupBuyId);

    @Operation(
            summary = "기간 연장 거절",
            description = """
                    C2 `[거절]` — 수락과 같은 기한이다.

                    **권한:** CREATOR

                    - 사유는 선택이다. `ETC`(기타 · 직접 입력)면 메모 필수.
                    - 메모는 **브랜드에게** 보인다.
                    - 연장 요청은 공구당 1회다 — 거절해도 브랜드가 다시 요청할 수 없다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거절 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_REASON_MEMO_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_EXTENSION_NOT_PENDING · GROUP_BUY_EXTENSION_RESPONSE_CLOSED · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> rejectExtension(
            @PathVariable Long groupBuyId, @Valid @RequestBody CreatorExtensionRejectRequest request);

    @Operation(
            summary = "공구 중단 요청",
            description = """
                    C7 — 운영자가 판정한다. **요청만으로 판매가 멈추지 않는다**(제16조②).

                    **권한:** CREATOR

                    - 허용: **진행중만**(준비완료 불가 — 시작 전에는 중단할 판매가 없다) ∧ 검토 중 요청 없음 ∧ 게시물 숨김 아님.
                    - 연장 응답 대기 중에도 요청할 수 있다.
                    - 메모(운영자에게 전달할 내용)는 **항상 필수**다. 브랜드에게는 사유 라벨만 보인다.
                    - 요청을 취소하는 API가 없다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 완료 — 갱신된 상세(activeRequest.mine = true)"),
            @ApiResponse(responseCode = "400", description = "사유·메모 누락 · 메모 1,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_REQUEST_ALREADY_PENDING · GROUP_BUY_ACTION_NOT_ALLOWED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> requestSuspension(
            @PathVariable Long groupBuyId, @Valid @RequestBody CreatorSuspensionRequestRequest request);

    @Operation(
            summary = "계약 이행 확인",
            description = """
                    C5 · C6 — 확인 대상은 **브랜드의 의무**(주문 배송 · 고정 지급비 지급)다. **되돌릴 수 없다.**

                    **권한:** CREATOR

                    - 허용: 종료 ∧ 내 확인 전. 기한이 지나도 받는다.
                    - `UNFULFILLED`면 사유 필수 — 3자 스레드의 첫 글이 되고 정산이 보류된다.
                      스레드 연동 전에는 **503 GROUP_BUY_THREAD_UNAVAILABLE**로 막힌다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 완료 — 갱신된 상세(afterEnd.fulfillment.mine)"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_FULFILLMENT_REASON_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_FULFILLMENT_ALREADY_CHECKED · GROUP_BUY_ACTION_NOT_ALLOWED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "GROUP_BUY_THREAD_UNAVAILABLE — 미이행 3자 스레드를 열 수 없다",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> checkFulfillment(
            @PathVariable Long groupBuyId, @Valid @RequestBody CreatorFulfillmentCheckRequest request);
}
