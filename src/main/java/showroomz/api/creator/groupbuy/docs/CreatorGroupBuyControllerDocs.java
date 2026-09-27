package showroomz.api.creator.groupbuy.docs;

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

import static showroomz.api.creator.groupbuy.docs.CreatorGroupBuyDocsExamples.*;

@Tag(name = "Creator - GroupBuy", description = "쇼룸 스튜디오 공구 조회·게시물 제출·요청 응답 API.")
public interface CreatorGroupBuyControllerDocs {

    // ── 조회 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "공구 목록",
            description = """
                    내 공구를 상태 탭 · 검색 · 정렬로 조회한다(A1 목록 · A2 빈 상태).

                    **권한:** CREATOR (내 공구만 조회된다)

                    **목록은 조회 전용이다.** 행에 실행 버튼·`permissions`가 없고, 비고(`remark`) 열도 없다(§31-1).
                    무엇을 해야 하는지는 상세에서 본다. 대신 행마다 `actionRequired` 하나로 「내 조치가 필요한 공구」를 표시한다.

                    **파라미터**
                    - `tab` — 생략 시 `ALL`. 탭은 **필터일 뿐**이고 응답 `status`는 항상 7종 개별 값이다.
                      - `ALL` 전체 · `PREPARING` 준비중 · `READY` 준비완료
                      - `IN_PROGRESS` 진행중 — `IN_PROGRESS` + **`SUSPENSION_SCHEDULED`(중단 예정)** 포함. 중단 예정 행은 `status`가 그대로 `SUSPENSION_SCHEDULED`(경고 톤)로 내려온다
                      - `ENDED` 종료·정산 — `ENDED` + `SETTLED`
                      - `SUSPENDED` 중단 — 정상 종료와 성격이 달라 탭을 분리한다
                    - `keyword` — **부분 일치**(앞뒤 공백 제거, 빈 문자열은 미적용). 대상: 공구명(계약명) · 브랜드명 · 공구번호
                    - `sort` — 생략 시 `START_AT_ASC`
                      - `START_AT_ASC` — **비종결 먼저(시작일 오름차순) → 종결 3종(시작일 내림차순) → id 내림차순.** 순수 오름차순이 아니다 —
                        지금 챙겨야 할 공구가 위, 끝난 공구는 최근 것부터 아래에 온다
                      - `ACTION_REQUIRED_FIRST` — `actionRequired = true`인 행을 먼저 두고, 그 안팎은 `START_AT_ASC`와 같다
                    - `page`(1부터 · 기본 1) · `size`(기본 20)

                    **행 필드 해석**
                    - `title` · `brandName` · `itemCount`는 계약에서 읽은 값이다(공구는 복사본을 갖지 않는다).
                    - `endAt`은 **현재 종료 예정** — 연장이 수락되면 바뀐 값이 내려온다.
                    - `postStatus` — 「내 게시물」 열. 게시물 8종이며 저장값이 아니라 공구 상태에서 파생한다:
                      `NOT_WRITTEN` 미작성 · `WRITING` 작성중 · `PENDING_APPROVAL` 승인대기 · `REJECTED` 반려 ·
                      `SCHEDULED` 예약(승인됐고 시작 전) · `EXPOSED` 노출중 · `HIDDEN` 숨김 · `CLOSED` 종료(종결 3종 공통)
                    - `*Label` · `*Tone`(`NEUTRAL` · `INFO` · `WARNING` · `SUCCESS` · `DANGER`)은 배지 문구·색이다. FE가 매핑하지 않고 그대로 쓴다.
                    - `actionRequired` — 요약 API의 `actionRequiredCount`와 **같은 판정식**이다(판정 조건은 요약 API 설명 참고).

                    **빈 상태(A2) vs 검색 결과 없음** — 둘 다 `content: []`로 같다. FE가 요약 API의 `tabCounts.ALL == 0`이면 A2(계약 관리로 이동 CTA),
                    아니면 「검색 결과 없음」(탭·검색 조건 유지)으로 가른다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 조건에 맞는 공구가 없으면 `content: []`",
                    content = @Content(examples = {
                            @ExampleObject(name = "목록", summary = "탭 ALL · 기본 정렬", value = LIST),
                            @ExampleObject(name = "빈 결과", summary = "공구가 없거나 검색 결과 없음", value = LIST_EMPTY)
                    })),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `tab` · `sort`에 정의되지 않은 값",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_INVALID_INPUT))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND · CREATOR_NOT_FOUND — 토큰의 회원·크리에이터 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_CREATOR_NOT_FOUND)))
    })
    ResponseEntity<PageResponse<CreatorGroupBuyListItem>> getGroupBuys(
            @Parameter(description = "상태 탭 — ALL(기본) · PREPARING · READY · IN_PROGRESS(중단 예정 포함) · ENDED(정산완료 포함) · SUSPENDED",
                    example = "ALL")
            @RequestParam(required = false) CreatorGroupBuyTab tab,
            @Parameter(description = "검색어(부분 일치) — 공구명 · 브랜드명 · 공구번호", example = "세럼")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "정렬 — START_AT_ASC(기본 · 비종결 먼저 시작일순) / ACTION_REQUIRED_FIRST(내 조치 필요 먼저)",
                    example = "START_AT_ASC")
            @RequestParam(required = false) CreatorGroupBuySortType sort,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "탭 카운트 · 내 조치 필요 수",
            description = """
                    탭별 건수와 「지금 내가 조치해야 하는」 공구 수를 돌려준다(31 설계 3-3).
                    GNB 배지 「공구 관리 N」과 목록 헤더 「내 조치가 필요한 공구 N건」이 **같은 값**을 쓴다.
                    GNB 배지는 공구 화면 밖에서도 폴링되므로 목록과 분리했다 — 연장 응답 기한이 현재 종료 시각이라 배지만 보고 움직이는 경우가 많다.

                    **권한:** CREATOR

                    **파라미터를 받지 않는다** — 검색어·탭과 무관한 전체 기준 건수다.

                    **`tabCounts`** — 6개 탭 코드(`ALL` · `PREPARING` · `READY` · `IN_PROGRESS` · `ENDED` · `SUSPENDED`)가 **항상 모두** 들어 있다(0건도 0으로).
                    탭 ↔ 상태 묶음은 목록 API의 `tab`과 같다(`IN_PROGRESS`에 중단 예정, `ENDED`에 정산완료 포함).

                    **`actionRequiredCount`** — 아래 중 **하나라도** 해당하는 공구 수다(공구 1건이 여러 조건에 걸려도 1로 센다). 공이 나에게 있는 것만 센다.
                    - 게시물 작성 필요 — `PREPARING` ∧ 게시물 없음·작성중(B1)
                    - 게시물 재등록 필요 — `PREPARING` ∧ 반려(B3)
                    - 숨김 게시물 수정 필요 — `READY` · `IN_PROGRESS` ∧ 운영자가 숨김 처리 중(B5a)
                    - 연장 응답 필요 — `IN_PROGRESS` ∧ 연장 요청 대기 ∧ 종료 시각 전(B6). **무응답 = 변경 없이 종결**이라 배지가 중요하다
                    - 이행 확인 필요 — `ENDED` ∧ 내 확인 전(B7). 확인 기한이 지나도 확인 전이면 계속 센다

                    **넣지 않는 것** — 승인대기(운영자 차례) · 준비완료 · 브랜드 요청 검토 중 · 직권 중단 예고 · 내가 낸 요청의 검토 대기.
                    내가 끌 수 없는 배지가 켜지면 안 되기 때문이다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuySummaryResponse.class), examples = {
                            @ExampleObject(name = "공구 있음", value = SUMMARY),
                            @ExampleObject(name = "공구 없음(A2 빈 상태)", value = SUMMARY_EMPTY)
                    })),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND · CREATOR_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_CREATOR_NOT_FOUND)))
    })
    ResponseEntity<CreatorGroupBuySummaryResponse> getSummary();

    @Operation(
            summary = "공구 상세",
            description = """
                    상세 화면(B1~B13)과 모달(C1~C7)의 배경이 **이 응답 하나**를 쓴다(31 설계 4-1). 화면 분기 값(`viewPhase` 등)은 없다 —
                    FE가 `groupBuy.status` × `post.status` × `extension` × `activeRequest` 조합으로 고른다. 실행 API 7종도 성공 시 이 응답을 돌려준다.

                    **권한:** CREATOR (내 공구만 — 남의 공구는 **404**다)

                    **블록별 내려오는 조건** — 조건 밖이면 `null`이다.

                    | 블록 | 언제 채워지나 |
                    |---|---|
                    | `groupBuy` · `timeline` · `brand` · `contract` · `items` · `fixedFee` · `post` · `permissions` · `history` · `navigation` | 항상 |
                    | `payout` | 비종결 4종(`PREPARING` · `READY` · `IN_PROGRESS` · `SUSPENSION_SCHEDULED`) |
                    | `readiness` | `PREPARING` · `READY` |
                    | `sales` | `IN_PROGRESS` · `SUSPENSION_SCHEDULED`(LIVE) · `ENDED`(PROVISIONAL) · `SETTLED` · `SUSPENDED`(AT_SUSPENSION) — **현재 항상 null** |
                    | `orderClosure` | `IN_PROGRESS` · `SUSPENSION_SCHEDULED` · `ENDED` · `SUSPENDED` — **현재 항상 null** |
                    | `extension` | 브랜드가 연장을 요청한 적이 있을 때(대기·수락·거절·만료 모두) |
                    | `activeRequest` | 검토 중(PENDING)인 중단·조기 마감 요청이 있을 때(요청자 무관) |
                    | `adminSuspension` | `SUSPENSION_SCHEDULED`(진행 중 직권 중단 통지)에서만 |
                    | `closure` | 종결 3종(`ENDED` · `SETTLED` · `SUSPENDED`) |
                    | `settlement` | `SETTLED` |
                    | `afterEnd` | `ENDED` · `SETTLED` (`SUSPENDED`는 null) |

                    **상태별 화면 가이드**
                    - `PREPARING` — `readiness.gates` 3개를 **내 관점으로** 판정한다: 내 차례(`MY_TURN`)인 줄만 경고 톤이다.
                      게시물이 없음·작성중·반려면 ②가 `MY_TURN`, 제출하면 ②`DONE` · ③`IN_REVIEW`. `permissions.canWritePost`면 `[임시저장]` · `[등록하고 검토 요청]`.
                      `readiness.registrationDeadline`(등록 마감일)이 지났고 ②가 아직이면 `registrationOverdue = true`.
                      `timeline.startOverdue = true`면 시작 시각이 지났는데 게이트가 덜 채워진 상태다.
                      반려(B3)면 `post.rejection`(코드 · 운영자 설명 · 시각)을 보여주고 다시 제출하게 한다.
                    - `READY` — 시작 대기(`timeline.daysUntilStart`). 게시물은 `SCHEDULED`이고 `[게시물 수정]` 가능. **스튜디오는 준비완료에서 중단 요청을 낼 수 없다**(브랜드와 비대칭).
                    - `IN_PROGRESS` — 판매 중. `post.status`(노출중/숨김) · `extension.status = PENDING`(B6 연장 응답) · `activeRequest`(검토 중 요청 — `mine`으로 내 요청/브랜드 요청 구분)로 가른다.
                      숨김(B5a)이면 `post.hidden`(사유 코드 · 운영자 설명 · 숨김 N일차)을 보여주고 `[게시물 수정]`을 유도한다.
                    - `SUSPENSION_SCHEDULED` — B13. `adminSuspension`(통지 종류 · 제17조① 호수 · 통지 본문 · 집행 예정 · 집행까지 영업일).
                      **할 수 있는 액션이 없다** — 게시물 수정·연장 응답·중단 요청이 모두 잠긴다. 소명은 브랜드↔운영자 절차라 기한·내용을 내리지 않는다.
                    - `ENDED` — B7. `afterEnd.fulfillment`: `mine`(내가 브랜드 의무를 확인한 결과) · `theirs`(브랜드가 내 의무를 확인한 결과) ·
                      `myTarget`(확인할 **브랜드 의무**: 주문 배송 · 고정 지급비 지급) · `theirTarget`(내 콘텐츠 의무와 건수) · `dueAt`.
                      `permissions.canCheckFulfillment`면 `[이행 확인]`.
                    - `SETTLED` — `settlement`(정산 시각 · 고정 지급비 · 확정 리워드 · **공제 전** 합계).
                    - `SUSPENDED` — `closure.source`로 사유를 가른다: `REQUEST`(요청 승인 — `closure.requester.mine`으로 내 요청/브랜드 요청) ·
                      `ADMIN_NOTICE`(사전 통지 후 집행) · `ADMIN_EMERGENCY`(긴급 직권 중단 — `closure.adminBasis.emergencyReason`).

                    **값 해석 주의**
                    - `payout` — 「내가 받는 금액」. **공제 전 금액**이고 공제·실지급액은 정산 관리 소관이다. `platformGuaranteed`는 항상 `false` —
                      FE는 이 값으로 「플랫폼이 지급을 보증하지 않음」 고지(§29-9)를 붙이고, 분쟁 경로로 `disputeChannel.threadId`(브랜드와의 스레드)를 건다.
                    - `items[].unitReward` = ⌊공구가 × 내 리워드율 ÷ 100⌋ — 계약·파트너·정산과 같은 계산이다. 정가·최소 준비 물량은 싣지 않는다.
                    - `fixedFee.displayText` — 3서피스 문자 단위 동일 표기를 서버가 짓는다. FE가 조립하지 않는다. 지급 여부(브랜드 신고)는 싣지 않는다.
                    - `sales` — 종료(`ENDED`)에서도 `basis: PROVISIONAL`(잠정 · 확정 시 변동)로 내린다(파트너와 반대 결정). 판매 모듈 연동 전이라 현재는 항상 `null`이다.
                    - `post.disclosureText` — 대가관계 표시 문구. 저장하지 않고 서버가 브랜드명으로 조립한다. 게시물이 없어도 미리보기용으로 내린다.
                    - `post.expectedReviewDate` — 승인대기일 때 예상 승인일 = 제출일 + SLA 영업일(`readiness.reviewSlaBusinessDays`, 제출일은 세지 않음).
                    - `readiness.registrationDeadline` — 승인 SLA를 역산한 등록 마감일(이날까지 제출하면 시작일 전날까지 승인이 난다). FE가 날짜를 계산하지 않는다.
                    - `timeline`의 일수는 서버 now(Asia/Seoul) 기준 · 양끝 포함 일자 계산이다. FE 시계로 다시 계산하지 않는다.
                    - `activeRequest.memo` — **내가 낸 요청일 때만** 내린다. 브랜드의 요청 메모는 운영자에게 쓴 글이라 `null`이다. 조기 마감 요청(브랜드 발의)도 남은 판매일에 영향이 있어 내린다.
                    - `extension.rejectMemo` — 내가 거절하며 쓴 메모다. **브랜드에게도 보인다.**
                    - `afterEnd.fulfillment.autoConfirmOnTimeout` — 현재 `false`. false면 「기한까지 답하지 않으면 이행으로 처리」 문구를 **쓰면 안 된다.**
                    - `afterEnd.fulfillment.onHold` — 미이행이 있고 양측 합의 종결 전이면 정산 보류(`true`).
                    - `history` — **최신순**(발생 시각 내림차순 · 동률은 id 내림차순). 스튜디오용 화이트리스트로 거른다:
                      브랜드의 소명 제출(`APPEAL_SUBMITTED`)은 내리지 않고, 물량 확인(`STOCK_CONFIRMED`)의 수량 스냅샷은 `detail`을 비운다.
                      중단·조기 마감 요청 이력의 `detail`은 메모가 아닌 **사유 라벨**이다. `actorDisplayName`은 브랜드명·쇼룸명 스냅샷이고 운영자(`ADMIN`)는 `null`이다.
                    - `navigation` — 쿼리에 `tab` · `keyword` · `sort` 중 하나라도 오면 **목록과 같은 조건·정렬**로 이전·다음 공구 id를 채운다.
                      없으면 둘 다 `null`, 현재 공구가 그 목록 조건에 없으면(그 사이 탭이 바뀐 경우) 둘 다 `null`이다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 예제는 한 공구(41)의 상태별 스냅샷이다",
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyDetailResponse.class), examples = {
                            @ExampleObject(name = "B1 준비중 · 게시물 작성 필요", summary = "게이트 ② MY_TURN · 작성중",
                                    value = DETAIL_PREPARING_WRITING),
                            @ExampleObject(name = "B2 준비중 · 승인대기", summary = "제출 완료 · 운영자 검토 중(수정 불가)",
                                    value = DETAIL_PREPARING_PENDING),
                            @ExampleObject(name = "B3 준비중 · 반려", summary = "post.rejection · 게이트 ② 다시 MY_TURN",
                                    value = DETAIL_PREPARING_REJECTED),
                            @ExampleObject(name = "B6 진행중 · 연장 응답 대기", summary = "extension.status = PENDING · canRespondExtension",
                                    value = DETAIL_EXTENSION_PENDING),
                            @ExampleObject(name = "B5a 진행중 · 게시물 숨김", summary = "post.hidden · 중단 요청 불가",
                                    value = DETAIL_POST_HIDDEN),
                            @ExampleObject(name = "B13 중단 예정", summary = "adminSuspension · 모든 액션 잠김",
                                    value = DETAIL_SUSPENSION_SCHEDULED),
                            @ExampleObject(name = "B7 종료 · 이행 확인 대기", summary = "afterEnd.fulfillment · canCheckFulfillment",
                                    value = DETAIL_ENDED_CHECK_PENDING),
                            @ExampleObject(name = "정산완료", summary = "settlement · 확정 리워드 미연동(null)",
                                    value = DETAIL_SETTLED),
                            @ExampleObject(name = "중단 · 내 요청 승인", summary = "closure.source = REQUEST · requester.mine",
                                    value = DETAIL_SUSPENDED_BY_MY_REQUEST)
                    })),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `tab` · `sort`에 정의되지 않은 값",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_INVALID_INPUT))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND — 없는 공구 · GROUP_BUY_NOT_OWNED_BY_CREATOR — 남의 공구(문구 동일) · "
                    + "USER_NOT_FOUND · CREATOR_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "없는 공구", value = ERR_NOT_FOUND),
                            @ExampleObject(name = "남의 공구", value = ERR_NOT_OWNED)
                    }))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> getGroupBuy(
            @Parameter(description = "공구 id", example = "41") @PathVariable Long groupBuyId,
            @Parameter(description = "목록 탭 — 이전·다음 공구 계산용(목록에서 들어올 때 목록 조건을 그대로 넘긴다)", example = "ALL")
            @RequestParam(required = false) CreatorGroupBuyTab tab,
            @Parameter(description = "목록 검색어 — 이전·다음 공구 계산용")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "목록 정렬 — 이전·다음 공구 계산용", example = "START_AT_ASC")
            @RequestParam(required = false) CreatorGroupBuySortType sort);

    // ── 게시물 ──────────────────────────────────────────────────────────────

    @Operation(
            summary = "공구 게시물 임시저장",
            description = """
                    C3 · C4 `[임시저장]` — 공구 게시물의 제목·본문을 저장만 한다. **최초 호출이 게시물을 만들고 이후 호출은 덮어쓴다.**

                    **권한:** CREATOR · **버튼 노출:** `permissions.canWritePost` (`PREPARING` ∧ 게시물 없음·작성중·반려)

                    **입력 규칙**
                    - 제목 40자 · 본문 2,000자(UTF-16 `length` 기준). **둘 중 하나는 입력**해야 한다 — 빈 임시저장은 막는다.
                    - 제목은 앞뒤 공백을 제거해 저장하고, 공백뿐인 본문은 빈 값으로 본다. 필수(둘 다) 검증은 제출 시점의 일이다.
                    - 사진 필드는 없다(§31-2). 본문에 대가관계 문구를 넣지 않는다 — 서버가 렌더링 시점에 붙인다(`post.disclosureText`).

                    **처리 결과**
                    - 게시물이 없었으면 `post.status`가 `NOT_WRITTEN → WRITING`이 된다.
                    - **반려 게시물을 임시저장해도 반려 그대로다** — 반려 사유 카드(`post.rejection`)가 고치는 동안 사라지지 않는다.
                    - 이력·알림·리비전을 남기지 않는다. 공구 상태도 바뀌지 않는다.

                    **동시성** — 두 탭에서 동시에 최초 저장하면 하나는 409 `GROUP_BUY_STATUS_CONFLICT`다. FE는 상세를 다시 조회해 수정 모드로 연다.

                    **에러 판정 순서** — 작성 불가 상태(409 — 승인대기면 `POST_UNDER_REVIEW`, 그 밖은 `POST_NOT_WRITABLE`)
                    → 길이 초과(400 `POST_TOO_LONG`) → 둘 다 비어 있음(400 `POST_EMPTY_DRAFT`) → 동시 최초 저장(409 `STATUS_CONFLICT`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 완료 — 갱신된 상세(`post.status = WRITING` 또는 `REJECTED` 유지)",
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyDetailResponse.class),
                            examples = @ExampleObject(name = "작성중으로 저장", value = SAVE_DRAFT_RESULT))),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_POST_EMPTY_DRAFT — 제목·본문 모두 비어 있음 · "
                    + "GROUP_BUY_POST_TOO_LONG — 제목 40자 · 본문 2,000자 초과 · INVALID_INPUT — 요청 바디 없음·JSON 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "둘 다 비어 있음", value = ERR_POST_EMPTY_DRAFT),
                            @ExampleObject(name = "길이 초과", value = ERR_POST_TOO_LONG),
                            @ExampleObject(name = "바디 없음", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND · GROUP_BUY_NOT_OWNED_BY_CREATOR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_POST_UNDER_REVIEW — 승인대기(제출 후 검토 중) · "
                    + "GROUP_BUY_POST_NOT_WRITABLE — 준비중이 아니거나 이미 승인됨 · "
                    + "GROUP_BUY_STATUS_CONFLICT — 동시 최초 저장(다시 조회해 수정 모드로)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "승인대기", value = ERR_POST_UNDER_REVIEW),
                            @ExampleObject(name = "작성 불가 상태", value = ERR_POST_NOT_WRITABLE),
                            @ExampleObject(name = "동시 최초 저장", value = ERR_STATUS_CONFLICT)
                    }))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> saveDraft(
            @Parameter(description = "공구 id", example = "41") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyPostRequest.class), examples = {
                            @ExampleObject(name = "제목·본문", value = """
                                    {"title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이…"}
                                    """),
                            @ExampleObject(name = "제목만", summary = "임시저장은 둘 중 하나만 있어도 된다", value = """
                                    {"title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": null}
                                    """)
                    }))
            @RequestBody CreatorGroupBuyPostRequest request);

    @Operation(
            summary = "공구 게시물 등록하고 검토 요청",
            description = """
                    C4 `[등록하고 검토 요청]` — 준비 게이트 ②. **저장과 제출을 한 요청으로 받는다** — 둘로 나누면 사이에 실패가 끼어
                    「저장은 됐는데 제출은 안 된」 상태가 생기고 사용자는 제출했다고 믿는다.

                    **권한:** CREATOR · **버튼 노출:** `permissions.canWritePost` (`PREPARING` ∧ 게시물 없음·작성중·반려)

                    **입력 규칙** — 제목(40자)·본문(2,000자) **모두 필수**. 공백만 입력하면 빈 값으로 본다. 게시물이 없으면 이 요청이 만들고 바로 제출한다.

                    **처리 결과**
                    - `post.status = PENDING_APPROVAL`, `post.submittedAt` · `post.expectedReviewDate`(예상 승인일)가 채워진다.
                    - `readiness.gates`: ② `DONE`, ③ `IN_REVIEW`.
                    - 제출 원문이 리비전으로 보관되고, 이력 `POST_SUBMITTED`(반려 후 재제출이면 `detail = "재등록"`) · 운영자에게 알림.
                    - **공구 상태는 바뀌지 않는다** — 운영자 오픈 승인(게이트 ③)이 남아 있다. 시작 시각이 지난 준비중에서도 받는다.
                    - 반려 후 재제출해도 이전 반려 기록은 운영자가 재심사 때 보도록 남는다.

                    **이후 흐름(운영자 검토)**
                    - 승인 → ③ `DONE`. 브랜드 물량 확인(①)까지 끝나 있으면 공구가 `READY`, 게시물은 `SCHEDULED`(예약)
                    - 반려 → `post.status = REJECTED`, `post.rejection` 채워짐. 다시 임시저장·제출할 수 있다
                    - **검토 중(승인대기)에는 수정·취소·재제출이 모두 불가다**(B2) — 제출 취소 API가 없다.

                    **에러 판정 순서** — 작성 불가 상태(409 `POST_UNDER_REVIEW` / `POST_NOT_WRITABLE`) → 길이 초과(400 `POST_TOO_LONG`)
                    → 제목·본문 중 빈 값(400 `POST_REQUIRED_FIELD`) → 동시 최초 저장(409 `STATUS_CONFLICT`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제출 완료 — 갱신된 상세(`post.status = PENDING_APPROVAL`)",
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyDetailResponse.class),
                            examples = @ExampleObject(name = "승인대기", value = SUBMIT_POST_RESULT))),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_POST_REQUIRED_FIELD — 제목·본문 중 빈 값 · "
                    + "GROUP_BUY_POST_TOO_LONG — 길이 초과 · INVALID_INPUT — 요청 바디 없음·JSON 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "필수 누락", value = ERR_POST_REQUIRED_FIELD),
                            @ExampleObject(name = "길이 초과", value = ERR_POST_TOO_LONG)
                    })),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND · GROUP_BUY_NOT_OWNED_BY_CREATOR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_POST_UNDER_REVIEW — 이미 제출해 검토 중 · "
                    + "GROUP_BUY_POST_NOT_WRITABLE — 준비중이 아니거나 이미 승인됨 · GROUP_BUY_STATUS_CONFLICT — 동시 최초 저장",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "검토 중", value = ERR_POST_UNDER_REVIEW),
                            @ExampleObject(name = "작성 불가 상태", value = ERR_POST_NOT_WRITABLE)
                    }))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> submitPost(
            @Parameter(description = "공구 id", example = "41") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyPostRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "여름 수분 세럼, 제가 쓰던 그 조합",
                                      "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요."
                                    }
                                    """)))
            @RequestBody CreatorGroupBuyPostRequest request);

    @Operation(
            summary = "승인 후 게시물 수정",
            description = """
                    B4 · B5 · B5a · B10 `[게시물 수정]` — 운영자 승인을 받은 게시물을 고친다. **즉시 반영 · 재승인 없음**(인플 제14조⑤).

                    **권한:** CREATOR · **버튼 노출:** `permissions.canEditPost` (게시물 승인됨 ∧ 공구 `READY` · `IN_PROGRESS` — 숨김 중 포함)

                    **잠기는 경우**
                    - 승인대기(`PENDING_APPROVAL`) — 409 `POST_UNDER_REVIEW`
                    - 미승인(없음·작성중·반려) — 준비중에는 임시저장·제출 API를 쓴다
                    - **중단 예정(`SUSPENSION_SCHEDULED`)** — 직권 중단은 통지 시점의 게시물을 근거로 판정하므로 도중에 근거가 바뀌면 안 된다
                    - 종결 3종

                    **입력 규칙** — 제목(40자)·본문(2,000자) **모두 필수**. 노출 중인 게시물의 제목·본문이 사라지면 안 된다.

                    **처리 결과**
                    - 제목·본문이 바로 바뀌고 `post.lastEditedAt`이 갱신된다. 수정 전후 원문은 서버가 리비전으로 보관한다(이력 `history`에는 남기지 않는다).
                    - **숨김 중 수정(B5a)은 운영자에게 통지되지만 숨김이 자동으로 풀리지 않는다** — 운영자가 읽고 해제한다.
                      응답의 `post.status`는 계속 `HIDDEN`이다.

                    **에러 판정 순서** — 수정 불가 상태(409 — 승인대기면 `POST_UNDER_REVIEW`, 그 밖은 `POST_NOT_EDITABLE`)
                    → 길이 초과(400 `POST_TOO_LONG`) → 제목·본문 중 빈 값(400 `POST_REQUIRED_FIELD`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 완료 — 갱신된 상세(`post.lastEditedAt` 갱신)",
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyDetailResponse.class),
                            examples = @ExampleObject(name = "숨김 중 수정(B5a) — 숨김 유지", value = EDIT_POST_RESULT))),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_POST_REQUIRED_FIELD — 제목·본문 중 빈 값 · "
                    + "GROUP_BUY_POST_TOO_LONG — 길이 초과 · INVALID_INPUT — 요청 바디 없음·JSON 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "필수 누락", value = ERR_POST_REQUIRED_FIELD),
                            @ExampleObject(name = "길이 초과", value = ERR_POST_TOO_LONG)
                    })),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND · GROUP_BUY_NOT_OWNED_BY_CREATOR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_POST_UNDER_REVIEW — 승인대기 · "
                    + "GROUP_BUY_POST_NOT_EDITABLE — 미승인 게시물, 중단 예정, 종결",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "승인대기", value = ERR_POST_UNDER_REVIEW),
                            @ExampleObject(name = "수정 불가 상태", value = ERR_POST_NOT_EDITABLE)
                    }))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> editPost(
            @Parameter(description = "공구 id", example = "41") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyPostRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "여름 수분 세럼, 제가 쓰던 그 조합",
                                      "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 제 피부 기준으로 오후까지 촉촉함이 유지됐어요."
                                    }
                                    """)))
            @RequestBody CreatorGroupBuyPostRequest request);

    // ── 기간 연장 응답 ─────────────────────────────────────────────────────────

    @Operation(
            summary = "기간 연장 수락",
            description = """
                    C1 `[수락]` — 브랜드의 기간 연장 요청을 받아들인다. **바디 없음 · 되돌릴 수 없다.**

                    **권한:** CREATOR · **버튼 노출:** `permissions.canRespondExtension`
                    (`extension.status = PENDING` ∧ 공구 `IN_PROGRESS` ∧ 현재 시각 < 현재 종료 시각)

                    **응답 기한** — `extension.respondDeadlineAt` = **현재 종료 시각**이다. 종료 시각까지 무응답이면 연장 없이 종료되고 `EXPIRED`가 된다.
                    중단 예정(`SUSPENSION_SCHEDULED`) 동안에는 응답할 수 없다(철회되면 다시 열린다). 게시물 숨김·검토 중 요청은 응답을 막지 않는다.

                    **처리 결과**
                    - `extension.status = ACCEPTED`(`responseActorType = CREATOR` · `respondedAt`)
                    - `timeline.endAt`이 `extension.afterEndAt`으로 바뀌고 `totalDays` · `daysUntilEnd`가 다시 계산된다. `originalEndAt`은 계약 원래 값 그대로다.
                    - **리워드율·고정 지급비는 그대로다**(계약 값). 30일 상한은 브랜드 요청 시점에 이미 검사했다.
                    - 이력 `EXTENSION_ACCEPTED`(「종료일 MM.dd HH:mm로 변경」) · 브랜드에게 알림.

                    **동시성** — 종료 스케줄러와 동시에 들어오면 **하나만** 통과한다. 수락이 지면 409 `EXTENSION_RESPONSE_CLOSED`(종료 시각 경과) 또는 `STATUS_CONFLICT`다.

                    **에러 판정 순서** — 대기 중 연장 요청 없음(409 `EXTENSION_NOT_PENDING`) → 종료 시각 경과(409 `EXTENSION_RESPONSE_CLOSED`)
                    → 진행중 아님·중단 예정(409 `ACTION_NOT_ALLOWED`) → 동시 처리에 짐(409 `EXTENSION_NOT_PENDING` / `STATUS_CONFLICT` / `EXTENSION_RESPONSE_CLOSED`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수락 완료 — 갱신된 상세(`extension.status = ACCEPTED` · `timeline.endAt` 변경)",
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyDetailResponse.class),
                            examples = @ExampleObject(name = "종료일 08.20 → 08.27", value = ACCEPT_EXTENSION_RESULT))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND · GROUP_BUY_NOT_OWNED_BY_CREATOR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_EXTENSION_NOT_PENDING — 응답할 연장 요청 없음(이미 응답·만료) · "
                    + "GROUP_BUY_EXTENSION_RESPONSE_CLOSED — 종료 시각 경과 · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED — 진행중 아님(중단 예정 등) · GROUP_BUY_STATUS_CONFLICT — 동시 처리 충돌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "응답할 요청 없음", value = ERR_EXTENSION_NOT_PENDING),
                            @ExampleObject(name = "종료 시각 경과", value = ERR_EXTENSION_RESPONSE_CLOSED),
                            @ExampleObject(name = "중단 예정 등", value = ERR_ACTION_NOT_ALLOWED),
                            @ExampleObject(name = "동시 처리 충돌", value = ERR_STATUS_CONFLICT)
                    }))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> acceptExtension(
            @Parameter(description = "공구 id", example = "41") @PathVariable Long groupBuyId);

    @Operation(
            summary = "기간 연장 거절",
            description = """
                    C2 `[거절]` — 브랜드의 기간 연장 요청을 거절한다. 수락과 **같은 기한·같은 조건**이다. 되돌릴 수 없다.

                    **권한:** CREATOR · **버튼 노출:** `permissions.canRespondExtension`

                    **입력**
                    - `reasonCode` — **선택**: `NEXT_SCHEDULE_BOOKED`(다음 일정이 잡혀 있음) · `CONTENT_PLAN_MISMATCH`(콘텐츠 계획과 맞지 않음) ·
                      `TERMS_RENEGOTIATION`(조건 재협의 필요) · `ETC`(기타 — `memo` 필수)
                    - `memo` — 선택 · 1,000자. 공백만 입력하면 빈 값으로 본다. **브랜드에게 보인다**(브랜드 상세의 `extension.rejectMemo`).
                    - 사유 없이 거절하려면 빈 객체 `{}`를 보낸다(바디 자체는 필요하다).

                    **처리 결과**
                    - `extension.status = REJECTED`(`responseActorType = CREATOR` · `rejectReasonCode` · `rejectReasonLabel` · `rejectMemo`). 종료일은 그대로다.
                    - 이력 `EXTENSION_REJECTED`(`detail` = 사유 라벨 — 메모는 이력에 남기지 않는다) · 브랜드에게 알림.
                    - 연장 요청은 **공구당 1회**다 — 거절하면 브랜드가 다시 요청할 수 없다.

                    **에러 판정 순서** — 입력 형식(400 `INVALID_INPUT` — 미정의 `reasonCode`, `memo` 1,000자 초과) → 대기 중 연장 요청 없음(409 `EXTENSION_NOT_PENDING`)
                    → 종료 시각 경과(409 `EXTENSION_RESPONSE_CLOSED`) → 진행중 아님(409 `ACTION_NOT_ALLOWED`) → `ETC`인데 메모 없음(400 `REASON_MEMO_REQUIRED`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거절 완료 — 갱신된 상세(`extension.status = REJECTED`)",
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyDetailResponse.class),
                            examples = @ExampleObject(name = "사유·메모와 함께 거절", value = REJECT_EXTENSION_RESULT))),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_REASON_MEMO_REQUIRED — ETC인데 메모 없음 · "
                    + "INVALID_INPUT — 미정의 `reasonCode`, `memo` 1,000자 초과, 바디 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "ETC 메모 누락", value = ERR_REASON_MEMO_REQUIRED),
                            @ExampleObject(name = "미정의 reasonCode", value = ERR_INVALID_INPUT),
                            @ExampleObject(name = "memo 1,000자 초과", value = """
                                    {"code": "INVALID_INPUT", "message": "크기가 0에서 1000 사이여야 합니다"}
                                    """)
                    })),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND · GROUP_BUY_NOT_OWNED_BY_CREATOR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_EXTENSION_NOT_PENDING · GROUP_BUY_EXTENSION_RESPONSE_CLOSED · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED — 수락과 같다",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "응답할 요청 없음", value = ERR_EXTENSION_NOT_PENDING),
                            @ExampleObject(name = "종료 시각 경과", value = ERR_EXTENSION_RESPONSE_CLOSED),
                            @ExampleObject(name = "중단 예정 등", value = ERR_ACTION_NOT_ALLOWED)
                    }))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> rejectExtension(
            @Parameter(description = "공구 id", example = "41") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = CreatorExtensionRejectRequest.class), examples = {
                            @ExampleObject(name = "사유 + 메모", value = """
                                    {"reasonCode": "NEXT_SCHEDULE_BOOKED", "memo": "8월 넷째 주에 다른 공구가 잡혀 있어요."}
                                    """),
                            @ExampleObject(name = "기타(메모 필수)", value = """
                                    {"reasonCode": "ETC", "memo": "재고 품질이 확인될 때까지는 기간을 늘리기 어렵습니다."}
                                    """),
                            @ExampleObject(name = "사유 없이 거절", value = "{}")
                    }))
            @Valid @RequestBody CreatorExtensionRejectRequest request);

    // ── 중단 요청 ────────────────────────────────────────────────────────────

    @Operation(
            summary = "공구 중단 요청",
            description = """
                    C7 — 운영자에게 공구 중단을 요청한다. 운영자가 판정한다. **요청만으로 판매가 멈추지 않는다**(제16조②).

                    **권한:** CREATOR · **버튼 노출:** `permissions.canRequestSuspension`

                    **요청 가능 조건** — 모두 충족해야 한다.
                    - 상태 **`IN_PROGRESS`만** — 준비완료(`READY`)는 불가다(시작 전에는 멈출 판매가 없다 · 브랜드와 비대칭). 중단 예정·종결도 불가
                    - 검토 중인 중단·조기 마감 요청 없음(요청자 무관 — 브랜드 요청이 검토 중이어도 막힌다)
                    - 게시물 숨김 중 아님 — 숨김 중 상품 하자는 스레드 → 운영자 직권 중단 경로로 간다
                    - 대기 중인 연장 요청은 막지 않는다(B6 「연장 응답과 별개로 공구 중단은 언제든 요청할 수 있습니다」)

                    **입력**
                    - `reasonCode` — 필수: `PRODUCT_DEFECT`(상품에 문제가 있어 추천을 이어갈 수 없음) · `DELIVERY_FAILURE`(배송 지연 · 미발송이 계속됨) ·
                      `CONSUMER_COMPLAINTS`(소비자 불만이 반복적으로 접수됨) · `BRAND_UNREACHABLE`(브랜드와 연락이 되지 않음) ·
                      `PERSONAL_REASON`(개인 사정으로 진행이 어려움) · `ETC`(기타)
                    - `memo` — **항상 필수** · 1,000자. 운영자에게 전달할 내용이다 — 운영자가 사실을 확인할 재료가 필요하다.
                      앞뒤 공백은 제거해 저장한다. **브랜드에게는 사유 라벨만 보이고 메모는 보이지 않는다.**

                    **처리 결과**
                    - `activeRequest`(`type = SUSPEND` · `requesterType = CREATOR` · `mine = true` · 내 메모 포함)가 채워지고 `permissions.canRequestSuspension`이 `false`가 된다.
                    - **공구 상태는 `IN_PROGRESS` 그대로** — 주문·배송·게시물 노출이 계속된다.
                    - 운영자·브랜드에게 알림 · 이력 `SUSPENSION_REQUESTED`(`detail` = 사유 라벨).
                    - **요청을 취소하는 API가 없다.** 검토 중에는 다른 중단·조기 마감 요청이 모두 막힌다.

                    **이후 흐름(운영자 판정)**
                    - 승인 → `SUSPENDED`(재개 불가), `closure.source = REQUEST` · `closure.requester.mine = true` · `closure.decisionReason`
                    - 반려 → 상태 유지, `activeRequest`가 사라지고 이력 `SUSPENSION_REJECTED`. 조건이 맞으면 다시 요청할 수 있다
                    - 판정 전에 종료 시각 도달 → 기간 종료(`ENDED`)로 끝나고 요청은 소멸한다

                    **에러 판정 순서** — 입력 형식(400 `INVALID_INPUT` — `reasonCode` 누락·미정의, `memo` 누락·공백·1,000자 초과)
                    → 검토 중 요청 있음(409 `REQUEST_ALREADY_PENDING`) → 진행중 아님·게시물 숨김 중(409 `ACTION_NOT_ALLOWED`)
                    → 브랜드 요청과 동시 접수(409 `REQUEST_ALREADY_PENDING`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 완료 — 갱신된 상세(`activeRequest.mine = true`)",
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyDetailResponse.class),
                            examples = @ExampleObject(name = "배송 문제로 중단 요청", value = REQUEST_SUSPENSION_RESULT))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `reasonCode` 누락·미정의 값, `memo` 누락·공백·1,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "reasonCode 누락", value = """
                                    {"code": "INVALID_INPUT", "message": "널이어서는 안됩니다"}
                                    """),
                            @ExampleObject(name = "memo 누락·공백", value = """
                                    {"code": "INVALID_INPUT", "message": "공백일 수 없습니다"}
                                    """),
                            @ExampleObject(name = "memo 1,000자 초과", value = """
                                    {"code": "INVALID_INPUT", "message": "크기가 0에서 1000 사이여야 합니다"}
                                    """),
                            @ExampleObject(name = "미정의 reasonCode", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND · GROUP_BUY_NOT_OWNED_BY_CREATOR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_REQUEST_ALREADY_PENDING — 검토 중인 중단·조기 마감 요청 있음(요청자 무관) · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED — 진행중 아님(준비완료·중단 예정·종결) 또는 게시물 숨김 중",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "검토 중 요청 있음", value = ERR_REQUEST_ALREADY_PENDING),
                            @ExampleObject(name = "요청 불가 상태", value = ERR_ACTION_NOT_ALLOWED)
                    }))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> requestSuspension(
            @Parameter(description = "공구 id", example = "41") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = CreatorSuspensionRequestRequest.class), examples = {
                            @ExampleObject(name = "배송 문제", value = """
                                    {"reasonCode": "DELIVERY_FAILURE", "memo": "시작 4일째인데 첫날 주문 18건이 아직 발송되지 않았고 배송 문의가 계속 들어옵니다."}
                                    """),
                            @ExampleObject(name = "개인 사정", value = """
                                    {"reasonCode": "PERSONAL_REASON", "memo": "건강 문제로 남은 기간 콘텐츠 운영이 어렵습니다."}
                                    """)
                    }))
            @Valid @RequestBody CreatorSuspensionRequestRequest request);

    // ── 종료 후 ──────────────────────────────────────────────────────────────

    @Operation(
            summary = "계약 이행 확인",
            description = """
                    C5(이행) · C6(미이행) — 종료 후 **브랜드의 의무**를 이행했는지 인플루언서가 확인한다.
                    확인 대상은 `afterEnd.fulfillment.myTarget.duties` — `ORDER_DELIVERY`(주문 배송) · `FIXED_FEE_PAYMENT`(고정 지급비 지급 — 계약에 있을 때만).

                    **권한:** CREATOR · **버튼 노출:** `permissions.canCheckFulfillment` (`ENDED` ∧ 내 확인 전)

                    **측별 1회 · 불가역**(제20조②) — 수정·삭제 API가 없다. 제출 전 확인 모달로 고지한다.
                    확인 기한(`afterEnd.fulfillment.dueAt` = 종결 시각 + 3일)이 지나도 받는다 — 무응답 자동 이행은 약관 근거 확정 전까지 꺼져 있다(`autoConfirmOnTimeout = false`).
                    정산완료(`SETTLED`) · 중단(`SUSPENDED`)에서는 확인할 수 없다.

                    **입력**
                    - `result`: `FULFILLED`(이행) / `UNFULFILLED`(미이행)
                    - `reason`: `UNFULFILLED`면 **필수**(2,000자) — 운영자가 참여하는 3자 스레드의 첫 글이 된다. `FULFILLED`면 무시되고 저장되지 않는다.

                    **처리 결과**
                    - `afterEnd.fulfillment.mine`에 내 확인(결과 · 사유 · 시각 · `auto = false`)이 채워진다.
                    - 브랜드에게 알림 · 이력 `FULFILLMENT_CONFIRMED`(이행) / `FULFILLMENT_DISPUTED`(미이행 — `detail` = 사유).
                    - `UNFULFILLED`면 3자 스레드가 열리고(`afterEnd.fulfillment.threadId`) 운영자에게도 알린다.
                      **양측 합의로 종결될 때까지 정산이 보류된다**(`afterEnd.fulfillment.onHold = true`).

                    ⚠️ **현재 미지원** — 연결·소통의 스레드 모델 변경 전이라 `UNFULFILLED`는 **503 `GROUP_BUY_THREAD_UNAVAILABLE`**이고 아무것도 저장되지 않는다.
                    `FULFILLED`는 정상 동작한다.

                    **에러 판정 순서** — 입력 형식(400 `INVALID_INPUT` — `result` 누락·미정의, `reason` 2,000자 초과) → 이미 확인함(409 `FULFILLMENT_ALREADY_CHECKED`)
                    → 종료(ENDED) 아님(409 `ACTION_NOT_ALLOWED`) → 미이행인데 사유 없음(400 `FULFILLMENT_REASON_REQUIRED`) → 미이행 스레드 미지원(503)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 완료 — 갱신된 상세(`afterEnd.fulfillment.mine` 채워짐)",
                    content = @Content(schema = @Schema(implementation = CreatorGroupBuyDetailResponse.class),
                            examples = @ExampleObject(name = "이행 확인", value = CHECK_FULFILLMENT_RESULT))),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_FULFILLMENT_REASON_REQUIRED — 미이행인데 사유 없음 · "
                    + "INVALID_INPUT — `result` 누락·미정의 값, `reason` 2,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "미이행 사유 누락", value = ERR_FULFILLMENT_REASON_REQUIRED),
                            @ExampleObject(name = "result 누락", value = """
                                    {"code": "INVALID_INPUT", "message": "널이어서는 안됩니다"}
                                    """)
                    })),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND · GROUP_BUY_NOT_OWNED_BY_CREATOR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_FULFILLMENT_ALREADY_CHECKED — 이미 확인함 · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED — 종료(ENDED) 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "이미 확인함", value = ERR_FULFILLMENT_ALREADY_CHECKED),
                            @ExampleObject(name = "종료 상태 아님", value = ERR_ACTION_NOT_ALLOWED)
                    })),
            @ApiResponse(responseCode = "503", description = "GROUP_BUY_THREAD_UNAVAILABLE — 미이행 3자 스레드 미지원(UNFULFILLED만 · 현재 항상)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_THREAD_UNAVAILABLE)))
    })
    ResponseEntity<CreatorGroupBuyDetailResponse> checkFulfillment(
            @Parameter(description = "공구 id", example = "41") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = CreatorFulfillmentCheckRequest.class), examples = {
                            @ExampleObject(name = "이행", value = """
                                    {"result": "FULFILLED"}
                                    """),
                            @ExampleObject(name = "미이행(현재 503)", value = """
                                    {"result": "UNFULFILLED", "reason": "종료 후 3일이 지났는데 18건이 아직 발송되지 않았고, 고정 지급비도 받지 못했습니다."}
                                    """)
                    }))
            @Valid @RequestBody CreatorFulfillmentCheckRequest request);
}
