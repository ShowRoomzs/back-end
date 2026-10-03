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

@Tag(name = "Seller - GroupBuy", description = "파트너센터 공구 조회·준비 확인·변경 요청 API.")
public interface SellerGroupBuyControllerDocs {

    // ── 조회 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "공구 목록",
            description = """
                    내 브랜드의 공구를 상태 탭 · 검색 · 정렬로 조회한다(A1 목록 · A2 빈 상태 · A3 검색 결과 없음).

                    **권한:** SELLER (내 브랜드 공구만 조회된다)

                    **목록은 조회 전용이다.** 행에 실행 버튼·`permissions`가 없다. 연장·중단 등 모든 실행은 상세에서만 시작한다(§30-1).

                    **파라미터**
                    - `tab` — 생략 시 `ALL`. 탭은 **필터일 뿐**이고 응답 `status`는 항상 7종 개별 값이다.
                      - `ALL` 전체 · `PREPARING` 준비중 · `READY` 준비완료
                      - `IN_PROGRESS` 진행중 — `IN_PROGRESS` + **`SUSPENSION_SCHEDULED`(중단 예정)** 포함. 중단 예정 행은 `status`가 그대로 `SUSPENSION_SCHEDULED`(경고 톤)로 내려온다
                      - `ENDED` 종료·정산 — `ENDED` + `SETTLED`
                      - `SUSPENDED` 중단 — 정상 종료와 성격이 달라 탭을 분리한다
                    - `keyword` — **부분 일치**(앞뒤 공백 제거, 빈 문자열은 미적용). 대상: 공구명(계약명) · 인플루언서 쇼룸명 · 공구번호
                    - `sort` — 생략 시 `START_AT_ASC`
                      - `START_AT_ASC` 시작일 빠른순(동률은 id 오름차순) — 종결 여부와 무관한 순수 오름차순이다
                      - `CREATED_DESC` 생성일(=체결일) 최신순
                    - `page`(1부터) · `size`(기본 20)

                    **행 필드 해석**
                    - `title` · `creatorName` · `itemCount`는 계약에서 읽은 값이다(공구는 복사본을 갖지 않는다).
                    - `endAt`은 **현재 종료 예정** — 연장이 수락되면 바뀐 값이 내려온다.
                    - `postStatus` — 게시물 8종. 저장값이 아니라 공구 상태에서 파생한 값이다:
                      `NOT_WRITTEN` 미작성 · `WRITING` 작성중 · `PENDING_APPROVAL` 승인대기 · `REJECTED` 반려 ·
                      `SCHEDULED` 예약(승인됐고 시작 전) · `EXPOSED` 노출중 · `HIDDEN` 숨김 · `CLOSED` 종료(종결 3종 공통)
                    - `*Label` · `*Tone`(`NEUTRAL` · `INFO` · `WARNING` · `SUCCESS` · `DANGER`)은 배지 문구·색이다. FE가 매핑하지 않고 그대로 쓴다.
                    - `remark`(비고) — 상태만으로 알 수 없는 예외만 **최대 1개** 내린다. 대부분 행은 `null`이다. 문구는 FE가 코드로 고른다.
                      둘 이상 걸리면 아래 **우선순위가 높은 하나만** 내린다(판매가 끊길 가능성이 큰 쪽이 위).
                      1. `ADMIN_SUSPENSION_NOTICED` 직권 중단 예정 — `appealDeadlineAt`(소명 기한)을 함께 내린다
                      2. `SUSPENSION_REQUEST_REVIEWING` 중단 요청 검토 중(요청자 무관)
                      3. `EARLY_CLOSE_REQUEST_REVIEWING` 조기 마감 요청 검토 중
                      4. `EXTENSION_PENDING` 연장 요청 응답 대기
                      5. `SUSPENDED_BY_ADMIN` 운영자 직권 중단으로 종결됨

                    **빈 상태(A2) vs 검색 결과 없음(A3)** — 둘 다 `content: []`로 같다. FE가 요약 API의 `tabCounts.ALL == 0`이면 A2(계약 관리로 이동 CTA),
                    아니면 A3(탭·검색 조건 유지)로 가른다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 조건에 맞는 공구가 없으면 `content: []`"),
            @ApiResponse(responseCode = "400", description = "`tab` · `sort`에 정의되지 않은 값",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND — 판매자 또는 브랜드(마켓)가 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<GroupBuyListItem>> getGroupBuys(
            @Parameter(description = "상태 탭 — ALL(기본) · PREPARING · READY · IN_PROGRESS(중단 예정 포함) · ENDED(정산완료 포함) · SUSPENDED")
            @RequestParam(required = false) GroupBuyTab tab,
            @Parameter(description = "검색어(부분 일치) — 공구명 · 인플루언서 쇼룸명 · 공구번호", example = "글로우")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "정렬 — START_AT_ASC(기본 · 시작일 빠른순) / CREATED_DESC(생성일 최신순)")
            @RequestParam(required = false) GroupBuySortType sort,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "탭 카운트 · GNB 배지",
            description = """
                    탭별 건수와 「지금 브랜드가 조치해야 하는」 건수를 돌려준다(설계서 4-3).
                    GNB 「공구 관리 N」 배지는 다른 화면에서도 폴링되므로 목록과 분리했다.

                    **권한:** SELLER

                    **파라미터를 받지 않는다** — 검색어와 무관한 전체 기준 건수다.

                    **`tabCounts`** — 6개 탭 코드(`ALL` · `PREPARING` · `READY` · `IN_PROGRESS` · `ENDED` · `SUSPENDED`)가 **항상 모두** 들어 있다(0건도 0으로).
                    탭 ↔ 상태 묶음은 목록 API의 `tab`과 같다(`IN_PROGRESS`에 중단 예정, `ENDED`에 정산완료 포함).

                    **`actionRequiredCount`** — 아래 3종의 합이다. 공이 브랜드에게 있는 것만 센다.
                    - 최소 물량 확인 대기 — `PREPARING` ∧ 아직 확보 확인 전(B1)
                    - 소명 가능 — 직권 중단 통지 중 ∧ 소명 미제출 ∧ 소명 기한 이내(B4i)
                    - 이행 확인 대기 — `ENDED` ∧ 브랜드 측 이행 확인 전(B5)

                    **넣지 않는 것** — 게시물 반려·숨김(고칠 권한이 인플루언서·운영자에게 있다), 브랜드가 낸 요청의 검토·응답 대기.
                    브랜드가 끌 수 없는 배지가 켜지면 안 되기 때문이다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuySummaryResponse> getSummary();

    @Operation(
            summary = "공구 상세",
            description = """
                    상세 화면 22종(B1~B7a)이 **이 응답 하나**를 쓴다. 화면 분기는 FE가 `groupBuy.status` × 사실 블록 조합으로 고른다(설계서 4-4).
                    실행 API 7종도 성공 시 이 응답을 그대로 돌려준다.

                    **권한:** SELLER (본인 브랜드 공구만)

                    **블록별 내려오는 조건** — 조건 밖이면 `null`이다.

                    | 블록 | 언제 채워지나 |
                    |---|---|
                    | `groupBuy` · `timeline` · `counterparty` · `contract` · `items` · `fixedFee` · `contentDuty` · `post` · `extension` · `permissions` · `history` | 항상 |
                    | `readiness` | `PREPARING` · `READY` |
                    | `sales` | `IN_PROGRESS` · `SUSPENSION_SCHEDULED`(LIVE) · `SETTLED` · `SUSPENDED`(AT_SUSPENSION). **`ENDED`는 항상 null** |
                    | `orderClosure` | `IN_PROGRESS` · `SUSPENSION_SCHEDULED` · `ENDED` · `SUSPENDED` |
                    | `activeRequest` | 검토 중(PENDING)인 중단·조기 마감 요청이 있을 때(요청자 무관) |
                    | `lastDecision` | 승인·반려된 요청이 1건 이상일 때 — 가장 최근 1건 |
                    | `adminSuspension` | 직권 중단 통지 이력이 있을 때 |
                    | `closure` · `afterEnd` | 종결 3종(`ENDED` · `SETTLED` · `SUSPENDED`) |

                    **상태별 화면 가이드**
                    - `PREPARING` — `readiness.gates` 3개(① 물량 확인 · ② 게시물 제출 · ③ 오픈 승인) 체크리스트. `permissions.canConfirmStock`이면 `[확보 완료]`.
                      `timeline.startOverdue = true`면 시작 시각이 지났는데 게이트가 덜 채워진 상태 — 다 채워지면 다음 스케줄러 tick에 바로 열린다.
                    - `READY` — 시작 대기(`timeline.daysUntilStart`). 게시물은 `SCHEDULED`. 중단 요청만 가능(C4).
                    - `IN_PROGRESS` — 판매 중. 연장(`extension`) · 요청(`activeRequest` · `lastDecision`) · 게시물 숨김(`post.status = HIDDEN`)으로 B4a~B4j를 가른다.
                    - `SUSPENSION_SCHEDULED` — `adminSuspension`(통지 사유 · 집행 예정 · 소명 기한 · 제출한 소명). 가능한 액션은 소명뿐이다.
                    - `ENDED` — `afterEnd.fulfillment`(이행 확인) · `afterEnd.openIssue` · `closure.closeType`(기간 종료/조기 마감).
                    - `SETTLED` — 확정 실적(`sales.basis = SETTLED`) · `afterEnd.settledAt`.
                    - `SUSPENDED` — `closure.source`로 사유를 가른다: `REQUEST`(요청 승인 — `closure.requester`) · `ADMIN_NOTICE`(사전 통지 후 집행) · `ADMIN_EMERGENCY`(긴급 직권 중단).

                    **값 해석 주의**
                    - 계약 조건(공구명 · 상품 · 고정 지급비 · 콘텐츠 의무)은 계약에서 그대로 읽는다 — 공구에서 수정할 수 없다.
                    - `sales` · `orderClosure`는 판매 모듈(주문 관리 34 설계서)이 실값으로 내린다 — 종결 = 구매확정·취소, 반송중은 환불 집행 전까지 미종결이다. `null`이면 판정 불가이지 0이 아니다 — **0으로 그리지 않는다**(미종결 0은 「정산해도 된다」는 뜻이 된다).
                      종료(`ENDED`) 화면에 KPI를 두지 않는 것은 의미 규칙이다 — 잠정치가 지급액으로 오해된다(§30-4).
                    - `fixedFee.displayText` — 3서피스 문자 단위 동일 표기를 서버가 짓는다. FE가 조립하지 않는다. 지급 여부는 싣지 않는다.
                    - `timeline`의 일수는 서버 now(Asia/Seoul) 기준 · 양끝 포함 일자 계산이다. FE 시계로 다시 계산하지 않는다.
                    - `activeRequest.memo` · `closure.requester.memo`는 **브랜드가 쓴 메모만** 내려온다. 인플루언서의 요청 메모는 운영자에게 쓴 글이라 `null`이다.
                    - `history` — **최신순**(발생 시각 내림차순 · 동률은 id 내림차순). 3서피스 공통이다. `actorDisplayName`은 브랜드명·쇼룸명 스냅샷이고, 운영자(`ADMIN`)·시스템(`SYSTEM`)은 `null`이다 — 호칭은 FE가 고른다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "GROUP_BUY_NOT_OWNED_BY_SELLER — 다른 브랜드의 공구",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND — 존재하지 않는 공구 · SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> getGroupBuy(
            @Parameter(description = "공구 id", example = "18") @PathVariable Long groupBuyId);

    // ── 준비 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "최소 물량 확보 확인",
            description = """
                    B1 `[확보 완료]` — 준비 게이트 ①(브랜드 몫). **바디 없음.**

                    **권한:** SELLER · **버튼 노출:** `permissions.canConfirmStock` (`PREPARING` ∧ 아직 확인 전)

                    **처리 결과**
                    - 확인 시각이 기록되고 `readiness.gates[STOCK_CONFIRMED]`가 `DONE`이 된다.
                    - 이력 `STOCK_CONFIRMED`의 `detail`에 확인 당시 최소 물량 스냅샷(예: 「크림 300개 · 세럼 200개」)이 남는다 — 제25조 제재 판정의 증거다.
                    - 운영자 오픈 승인(게이트 ③)이 **이미 끝났으면** 이 요청으로 공구가 즉시 `READY`(준비완료)가 된다.
                      아니면 `PREPARING` 그대로다. FE는 응답의 `groupBuy.status`로 완료 문구를 고른다.

                    **주의**
                    - 서버는 재고를 판정하지 않는다 — 브랜드의 자기 확인이다.
                    - **되돌리는 API가 없다**(§33-1 #10 미정). 확인 모달에서 충분히 고지한다.

                    **409 판정 순서** — 이미 확인함 → `GROUP_BUY_STOCK_ALREADY_CONFIRMED` / 준비중이 아님 → `GROUP_BUY_STATUS_CONFLICT`
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 완료 — 갱신된 상세"),
            @ApiResponse(responseCode = "403", description = "GROUP_BUY_NOT_OWNED_BY_SELLER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_STOCK_ALREADY_CONFIRMED — 이미 확인함 · "
                    + "GROUP_BUY_STATUS_CONFLICT — 준비중(PREPARING)이 아님(새로고침 필요)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> confirmStock(
            @Parameter(description = "공구 id", example = "18") @PathVariable Long groupBuyId);

    // ── 진행 중 요청 ─────────────────────────────────────────────────────────

    @Operation(
            summary = "기간 연장 요청",
            description = """
                    C1 — 인플루언서에게 공구 기간 연장을 요청한다. **공구당 1회**이며 수락·거절·만료와 무관하게 기회가 소진된다.

                    **권한:** SELLER · **버튼 노출:** `permissions.canRequestExtension`

                    **요청 가능 조건** — 모두 충족해야 한다.
                    - 상태 `IN_PROGRESS`(진행중)
                    - 연장 요청 이력 없음(`extension.status == null`)
                    - 현재 시각 < `extension.requestCutoffAt`(= 종료 12시간 전)
                    - 검토 중인 중단·조기 마감 요청 없음 · 게시물 숨김 중 아님

                    **연장 일수 계산** — FE는 상세의 `extension.maxDays`로 입력 즉시 검증한다.
                    - 새 종료 = 현재 종료(`timeline.endAt`) + `extensionDays`일, **시각은 그대로**다(예: 08.21 23:55 → 08.28 23:55). 시작일은 바꿀 수 없다.
                    - 연장 후 총 기간(시작일 ~ 새 종료일, 양끝 포함)은 **30일 이하** — `extensionDays ≤ maxDays`(= 30 − 현재 총 일수).

                    **처리 결과**
                    - `extension.status = PENDING`, `beforeEndAt` · `afterEndAt`이 채워진다. **`timeline.endAt`은 아직 바뀌지 않는다.**
                    - 인플루언서에게 알림 · 이력 `EXTENSION_REQUESTED`(「7일 · 사유」) · 목록 비고 `EXTENSION_PENDING`.

                    **이후 흐름(인플루언서 응답)**
                    - 수락 → `ACCEPTED`, `timeline.endAt`이 `afterEndAt`으로 바뀐다(`originalEndAt`은 계약 원래 값 유지)
                    - 거절 → `REJECTED`(`responseActorType = CREATOR` · `rejectReasonCode` · `rejectMemo`)
                    - 종료 시각까지 무응답 → 변경 없이 종료되고 `EXPIRED`(`responseActorType = SYSTEM`)

                    대기 중인 연장은 조기 마감·중단 요청을 막지 않는다.

                    **에러 판정 순서** — 진행중 아님(409 `ACTION_NOT_ALLOWED`) → 검토 중 요청 있음(409 `REQUEST_ALREADY_PENDING`) → 게시물 숨김 중(409 `ACTION_NOT_ALLOWED`)
                    → 이미 연장 요청함(409 `EXTENSION_ALREADY_USED`) → 컷오프 경과(409 `EXTENSION_WINDOW_CLOSED`) → 30일 초과(400 `EXTENSION_EXCEEDS_LIMIT`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 완료 — 갱신된 상세(`extension.status = PENDING`)"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_EXTENSION_EXCEEDS_LIMIT — 연장 후 총 기간 30일 초과 · "
                    + "입력 형식 오류(`extensionDays` 누락·1 미만, `reason` 300자 초과)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "GROUP_BUY_NOT_OWNED_BY_SELLER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_EXTENSION_ALREADY_USED — 공구당 1회 소진 · "
                    + "GROUP_BUY_EXTENSION_WINDOW_CLOSED — 종료 12시간 전 경과 · "
                    + "GROUP_BUY_REQUEST_ALREADY_PENDING — 검토 중인 중단·조기 마감 요청 있음 · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED — 진행중 아님 또는 게시물 숨김 중",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> requestExtension(
            @Parameter(description = "공구 id", example = "18") @PathVariable Long groupBuyId,
            @Valid @RequestBody GroupBuyExtensionRequestRequest request);

    @Operation(
            summary = "조기 마감 요청",
            description = """
                    C3 — 운영자에게 조기 마감을 요청한다(재고 소진 · 판매 목표 달성 등).

                    **권한:** SELLER · **버튼 노출:** `permissions.canRequestEarlyClose`

                    **요청 가능 조건** — 상태 `IN_PROGRESS` ∧ 검토 중인 중단·조기 마감 요청 없음(요청자 무관) ∧ 게시물 숨김 중 아님.
                    대기 중인 연장 요청은 막지 않는다.

                    **입력** — `reasonCode`: `STOCK_OUT`(재고 소진) · `TARGET_REACHED`(판매 목표 달성) · `ETC`(기타 — `memo` 필수).
                    `memo`는 공백만 입력하면 빈 값으로 본다.

                    **처리 결과**
                    - `activeRequest`(type `EARLY_CLOSE`)가 채워진다. **공구 상태는 `IN_PROGRESS` 그대로** — 판매는 계속된다.
                    - 운영자·인플루언서에게 알림 · 이력 `EARLY_CLOSE_REQUESTED` · 목록 비고 `EARLY_CLOSE_REQUEST_REVIEWING`.
                    - 요청은 **취소할 수 없다.** 검토 중에는 연장·조기 마감·중단 요청이 모두 막힌다.

                    **이후 흐름(운영자 판정)**
                    - 승인 → `ENDED`(`closeType = EARLY_CLOSED`), 게시물 노출 종료
                    - 반려 → 상태 유지, `lastDecision`(`result = REJECTED` · `decisionReason`). **재요청 가능**
                    - 판정 전에 종료 시각 도달 → 기간 종료로 끝나고 요청은 소멸(LAPSED)

                    **에러 판정 순서** — 진행중 아님(409 `ACTION_NOT_ALLOWED`) → 검토 중 요청 있음(409 `REQUEST_ALREADY_PENDING`) → 게시물 숨김 중(409 `ACTION_NOT_ALLOWED`)
                    → `ETC`인데 메모 없음(400 `REASON_MEMO_REQUIRED`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 완료 — 갱신된 상세(`activeRequest` 채워짐)"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_REASON_MEMO_REQUIRED — ETC인데 메모 없음 · "
                    + "입력 형식 오류(`reasonCode` 누락·미정의 값, `memo` 1,000자 초과)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "GROUP_BUY_NOT_OWNED_BY_SELLER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_REQUEST_ALREADY_PENDING — 검토 중인 요청 있음 · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED — 진행중 아님 또는 게시물 숨김 중",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> requestEarlyClose(
            @Parameter(description = "공구 id", example = "18") @PathVariable Long groupBuyId,
            @Valid @RequestBody GroupBuyEarlyCloseRequestRequest request);

    @Operation(
            summary = "공구 중단 요청",
            description = """
                    C2(진행중) · C4(준비완료 · 시작 전) — 운영자에게 공구 중단을 요청한다.

                    **권한:** SELLER · **버튼 노출:** `permissions.canRequestSuspension`

                    **요청 가능 조건** — 상태 `READY` 또는 `IN_PROGRESS` ∧ 검토 중인 중단·조기 마감 요청 없음(요청자 무관) ∧ 게시물 숨김 중 아님.
                    대기 중인 연장 요청은 막지 않는다. `PREPARING`에서는 요청할 수 없다.

                    **입력** — `reasonCode`: `QUALITY_ISSUE`(상품 품질 이슈) · `PRICE_TERMS_ERROR`(가격·조건 오기) ·
                    `NEGOTIATION_BROKEN`(인플루언서와 협의 결렬) · `ETC`(기타 — `memo` 필수).
                    **재고 소진은 중단 사유가 아니라 조기 마감 사유다** — 코드에 없다.

                    **처리 결과**
                    - `activeRequest`(type `SUSPEND`)가 채워진다. `activeRequest.statusAtRequest`(`READY`/`IN_PROGRESS`)로 C4/C2 문구를 가른다.
                    - **요청만으로 판매는 멈추지 않는다**(제16조②) — 승인 전까지 공구 상태·주문·배송은 그대로다.
                    - 운영자·인플루언서에게 알림 · 이력 `SUSPENSION_REQUESTED` · 목록 비고 `SUSPENSION_REQUEST_REVIEWING`.
                    - 요청은 **취소할 수 없다.** 검토 중에는 연장·조기 마감·중단 요청이 모두 막힌다.

                    **이후 흐름(운영자 판정)**
                    - 승인 → `SUSPENDED`(재개 불가), `closure.source = REQUEST`
                    - 반려 → 상태 유지, `lastDecision`(`result = REJECTED` · `decisionReason`). 재요청 가능
                    - 인플루언서도 중단을 요청할 수 있다 — 그때 `activeRequest.requesterType = CREATOR`이고 `memo`는 `null`이다.

                    **에러 판정 순서** — 준비완료·진행중 아님(409 `ACTION_NOT_ALLOWED`) → 검토 중 요청 있음(409 `REQUEST_ALREADY_PENDING`)
                    → 중단 예정·게시물 숨김 중(409 `ACTION_NOT_ALLOWED`) → `ETC`인데 메모 없음(400 `REASON_MEMO_REQUIRED`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 완료 — 갱신된 상세(`activeRequest` 채워짐)"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_REASON_MEMO_REQUIRED — ETC인데 메모 없음 · "
                    + "입력 형식 오류(`reasonCode` 누락·미정의 값, `memo` 1,000자 초과)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "GROUP_BUY_NOT_OWNED_BY_SELLER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_REQUEST_ALREADY_PENDING — 검토 중인 요청 있음 · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED — 준비완료·진행중 아님, 중단 예정, 게시물 숨김 중",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> requestSuspension(
            @Parameter(description = "공구 id", example = "18") @PathVariable Long groupBuyId,
            @Valid @RequestBody GroupBuySuspensionRequestRequest request);

    // ── 직권 중단 소명 ────────────────────────────────────────────────────────

    @Operation(
            summary = "소명 증빙 업로드 URL 발급",
            description = """
                    C9 1단계 — 직권 중단 소명에 붙일 증빙 파일의 S3 업로드(PUT) URL을 발급한다.

                    **권한:** SELLER · **호출 가능:** `permissions.canSubmitAppeal`과 같은 조건
                    (`SUSPENSION_SCHEDULED` ∧ 진행 중 통지 있음 ∧ 소명 미제출 ∧ 소명 기한(`adminSuspension.appealDeadlineAt`) 이내)

                    **파일 규칙** — `image/png` · `image/jpeg` · `application/pdf`, 10MB 이하.
                    **발급 횟수 제한은 없다** — 개수 상한(5개)은 소명 제출 때 `attachmentIds`에만 적용된다.
                    파일을 바꾸거나 업로드에 실패하면 새로 발급받으면 된다. 제출에 싣지 않은 첨부는 버려진다.

                    **업로드 흐름**
                    1. 이 API로 `attachmentId` · `uploadUrl` 발급(유효 15분 · `expiresAt`)
                    2. `uploadUrl`로 파일을 **PUT** — `Content-Type` 헤더를 발급 요청의 `contentType`과 **똑같이** 보내야 한다
                    3. 소명 제출 API의 `attachmentIds`에 `attachmentId`를 싣는다 — 제출 시점에 S3의 실제 크기·타입을 다시 검증한다

                    **에러 판정 순서** — 통지 없음(409 `APPEAL_NOT_OPEN`) → 이미 제출(409 `APPEAL_ALREADY_SUBMITTED`) → 기한 경과(409 `APPEAL_DEADLINE_PASSED`)
                    → 타입·크기 위반(400 `APPEAL_ATTACHMENT_INVALID`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "발급 완료"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_APPEAL_ATTACHMENT_INVALID — 허용되지 않는 타입, 10MB 초과 · "
                    + "입력 형식 오류(필드 누락, `fileName` 255자 초과, `sizeBytes` 0 이하)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "GROUP_BUY_NOT_OWNED_BY_SELLER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_APPEAL_NOT_OPEN — 소명할 통지 없음 · "
                    + "GROUP_BUY_APPEAL_ALREADY_SUBMITTED — 이미 제출 · GROUP_BUY_APPEAL_DEADLINE_PASSED — 소명 기한 경과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyAppealAttachmentPresignResponse> presignAppealAttachment(
            @Parameter(description = "공구 id", example = "18") @PathVariable Long groupBuyId,
            @Valid @RequestBody GroupBuyAppealAttachmentPresignRequest request);

    @Operation(
            summary = "소명 자료 제출",
            description = """
                    C9 2단계 — 운영자의 직권 중단 사전 통지에 대한 소명을 제출한다(제17조④).

                    **권한:** SELLER · **버튼 노출:** `permissions.canSubmitAppeal`
                    (`SUSPENSION_SCHEDULED` ∧ 진행 중 통지 있음 ∧ 소명 미제출 ∧ 현재 시각 ≤ `adminSuspension.appealDeadlineAt`)

                    **입력**
                    - `content` — 필수 · 2,000자. 앞뒤 공백은 제거해 저장한다.
                    - `attachmentIds` — 선택 · **최대 5개**(개수 상한은 여기서만 본다). presign으로 발급받아 **실제로 업로드를 마친** 이 통지의 첨부만 허용한다(중복 id는 한 번만 센다).
                      S3에 객체가 없거나, 10MB를 넘거나, 올린 `Content-Type`이 발급 요청과 다르면 400이다.

                    **처리 결과**
                    - `adminSuspension.appeal`(내용 · 제출 시각 · 첨부 목록)이 채워지고 `permissions.canSubmitAppeal`이 `false`가 된다.
                    - **상태는 그대로 `SUSPENSION_SCHEDULED`** — 판매도 계속된다.
                    - 운영자에게 알림(조치 큐 「소명 검토」) · 이력 `APPEAL_SUBMITTED`(「증빙 N건」).
                    - **제출 후 수정·삭제할 수 없다.** 소명은 운영자가 판단 근거로 읽는 글이다. 제출 전 확인 모달로 고지한다.

                    **이후 흐름(운영자 판단)**
                    - 철회 → `IN_PROGRESS` 복귀, `adminSuspension.status = WITHDRAWN` · `withdrawReason`
                    - 집행 → `SUSPENDED`, `closure.source = ADMIN_NOTICE`
                    - 집행 전에 종료 시각 도달 → `ENDED`(기간 종료), 통지는 소멸(LAPSED)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제출 완료 — 갱신된 상세(`adminSuspension.appeal` 채워짐)"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_APPEAL_ATTACHMENT_INVALID — 5개 초과, 다른 통지의 첨부, 업로드 안 됨, 크기·타입 불일치 · "
                    + "입력 형식 오류(`content` 누락·2,000자 초과)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "GROUP_BUY_NOT_OWNED_BY_SELLER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_APPEAL_NOT_OPEN — 소명할 통지 없음 · "
                    + "GROUP_BUY_APPEAL_ALREADY_SUBMITTED — 이미 제출 · GROUP_BUY_APPEAL_DEADLINE_PASSED — 소명 기한 경과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> submitAppeal(
            @Parameter(description = "공구 id", example = "18") @PathVariable Long groupBuyId,
            @Valid @RequestBody GroupBuyAppealSubmitRequest request);

    // ── 종료 후 ──────────────────────────────────────────────────────────────

    @Operation(
            summary = "이슈 스레드 열기",
            description = """
                    C5 — 종료 후 **정산과 무관한 이견**을 운영자가 참여하는 3자 스레드로 연다.

                    **권한:** SELLER · **버튼 노출:** `permissions.canOpenIssue`

                    **개설 가능 조건**
                    - 상태 `ENDED`, 또는 **브랜드가 요청하지 않은** `SUSPENDED`(인플루언서 요청 승인 · 운영자 직권 중단). `SETTLED`에서는 열 수 없다.
                    - 열린 이슈가 없을 것(`afterEnd.openIssue == null`) — 공구당 열린 이슈는 1건만. 종결된 뒤 새 이견은 새로 연다.

                    **입력** — `issueType`: `CONTENT_FULFILLMENT`(콘텐츠 이행 문제) · `TERMS_INTERPRETATION`(계약 조건 해석 이견) ·
                    `SETTLEMENT_AMOUNT`(정산 금액 이견) · `ETC`(기타). `content`(필수 · 2,000자)는 스레드의 첫 글이 된다.

                    **처리 결과** — 응답 `{issueId, threadId}`로 FE가 「스레드로 이동 ↗」한다.
                    이슈는 공구 상태를 바꾸지 않고 **정산도 보류하지 않는다.**

                    ⚠️ **현재 미지원** — 연결·소통의 스레드 모델 변경(같은 쌍의 두 번째 스레드) 전이라 조건을 통과해도
                    **503 `GROUP_BUY_THREAD_UNAVAILABLE`**을 돌려준다(설계서 5-3). 이슈는 만들어지지 않는다.

                    **에러 판정 순서** — 열린 이슈 있음(409 `ISSUE_ALREADY_OPEN`) → 개설 불가 상태(409 `ACTION_NOT_ALLOWED`) → 스레드 미지원(503)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "개설 완료 — `{issueId, threadId}`"),
            @ApiResponse(responseCode = "400", description = "입력 형식 오류(`issueType` 누락·미정의 값, `content` 누락·2,000자 초과)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "GROUP_BUY_NOT_OWNED_BY_SELLER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_ISSUE_ALREADY_OPEN — 열린 이슈 있음 · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED — 종료 아님 또는 브랜드 요청으로 중단된 공구",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "GROUP_BUY_THREAD_UNAVAILABLE — 3자 스레드 미지원(현재 항상)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyIssueOpenResponse> openIssue(
            @Parameter(description = "공구 id", example = "18") @PathVariable Long groupBuyId,
            @Valid @RequestBody GroupBuyIssueOpenRequest request);

    @Operation(
            summary = "계약 이행 확인",
            description = """
                    C6(이행) · C7(미이행) — **인플루언서의 콘텐츠 의무**(`contentDuty`: 피드 · 릴스 · 스토리 건수 · 기한)를 이행했는지 브랜드가 확인한다.

                    **권한:** SELLER · **버튼 노출:** `permissions.canCheckFulfillment` (`ENDED` ∧ 브랜드 측 확인 전)

                    **측별 1회 · 불가역**(제20조②) — 수정·삭제 API가 없다. 제출 전 확인 모달로 고지한다.

                    **입력**
                    - `result`: `FULFILLED`(이행) / `UNFULFILLED`(미이행)
                    - `reason`: `UNFULFILLED`면 **필수**(2,000자) — 운영자가 참여하는 3자 스레드의 첫 글이 된다. `FULFILLED`면 무시된다.

                    **처리 결과**
                    - `afterEnd.fulfillment.mine`에 브랜드의 확인(결과 · 사유 · 시각)이 채워진다. `theirs`는 인플루언서가 브랜드 의무를 확인한 결과다.
                    - 인플루언서에게 알림 · 이력 `FULFILLMENT_CONFIRMED` / `FULFILLMENT_DISPUTED`.
                    - `UNFULFILLED`면 3자 스레드가 열리고 **양측 합의로 종결될 때까지 정산이 보류된다**(`afterEnd.fulfillment.onHold = true`).
                    - 확인 기한(`afterEnd.fulfillment.dueAt`)이 지나도 받는다 — 무응답 자동 이행은 약관 근거 확정 전까지 꺼져 있다.

                    ⚠️ **현재 미지원** — 연결·소통의 스레드 모델 변경 전이라 `UNFULFILLED`는 **503 `GROUP_BUY_THREAD_UNAVAILABLE`**이고 아무것도 저장되지 않는다.
                    `FULFILLED`는 정상 동작한다.

                    **에러 판정 순서** — 이미 확인함(409 `FULFILLMENT_ALREADY_CHECKED`) → 종료(ENDED) 아님(409 `ACTION_NOT_ALLOWED`)
                    → 미이행 사유 없음(400 `FULFILLMENT_REASON_REQUIRED`) → 미이행 스레드 미지원(503)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 완료 — 갱신된 상세(`afterEnd.fulfillment.mine` 채워짐)"),
            @ApiResponse(responseCode = "400", description = "GROUP_BUY_FULFILLMENT_REASON_REQUIRED — 미이행인데 사유 없음 · "
                    + "입력 형식 오류(`result` 누락·미정의 값, `reason` 2,000자 초과)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "GROUP_BUY_NOT_OWNED_BY_SELLER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "GROUP_BUY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "GROUP_BUY_FULFILLMENT_ALREADY_CHECKED — 이미 확인함 · "
                    + "GROUP_BUY_ACTION_NOT_ALLOWED — 종료(ENDED) 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "GROUP_BUY_THREAD_UNAVAILABLE — 미이행 3자 스레드 미지원(UNFULFILLED만)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<GroupBuyDetailResponse> checkFulfillment(
            @Parameter(description = "공구 id", example = "18") @PathVariable Long groupBuyId,
            @Valid @RequestBody GroupBuyFulfillmentCheckRequest request);
}
