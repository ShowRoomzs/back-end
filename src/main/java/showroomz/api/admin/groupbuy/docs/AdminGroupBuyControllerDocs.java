package showroomz.api.admin.groupbuy.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

import static showroomz.api.admin.groupbuy.docs.AdminGroupBuyDocsExamples.*;

@Tag(name = "Admin - GroupBuy", description = "관리자 공구 목록·상세 조회와 운영 판정 API.")
public interface AdminGroupBuyControllerDocs {

    // ── 조회 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "공구 목록",
            description = """
                    모든 공구를 탭 · 검색 · 정렬로 조회한다(A1 · A2 · A3).

                    **권한:** ADMIN

                    **탭 `tab`** (기본 `ALL`)

                    | 값 | 포함 상태 |
                    |---|---|
                    | `ALL` | 전체 |
                    | `ACTION_REQUIRED` | 조치 큐 4종 해당 행(요약의 `actionRequiredCount`와 같은 판정식) |
                    | `PREPARING` | 준비중 |
                    | `READY` | 준비완료 |
                    | `IN_PROGRESS` | 진행중 + **중단 예정** |
                    | `ENDED` | 종료 — 정산 지연 감시는 이 탭에서 본다 |
                    | `SETTLED` | 정산완료 |
                    | `SUSPENDED` | 중단 |

                    파트너·스튜디오와 달리 **종료와 정산완료를 가른다.**

                    **정렬 `sort`** (기본 `ACTION_REQUIRED_FIRST`)
                    - `ACTION_REQUIRED_FIRST` — 조치 큐 해당 행이 먼저(「상단 고정」은 별도 목록이 아니라 정렬 키다) → 최근 등록순
                    - `CREATED_DESC` — 최근 등록순
                    - `END_AT_ASC` — 비종결을 종료 임박순으로, 종결은 최근 종결순으로 뒤에 둔다

                    **검색 `keyword`** — 공구명 · 공구번호 · 브랜드명 · 쇼룸명 부분 일치.

                    **페이징** — `page`(1부터, 기본 1) · `size`(기본 20).

                    **응답 행**
                    - `status`는 7종 개별 값이다 — 진행중 탭 안의 `SUSPENSION_SCHEDULED`도 그대로 내린다.
                    - `postStatus`는 게시물 8종 파생값(미작성 · 작성중 · 승인대기 · 반려 · 예약 · 노출중 · 숨김 · 종료).
                    - `actionRequired` — 경고 배경은 FE가 탭으로 끈다(조치 필요 탭에서는 그리지 않는다).
                    - **목록에 실행 액션이 없다** — `permissions`도, 무엇이 걸렸는지(`queueKind`)도 싣지 않는다. 상세가 답한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(examples = {
                            @ExampleObject(name = "전체 탭 · 조치 필요 우선", summary = "조치 필요 3건(오픈 승인 · 조기 마감 요청 · 소명 검토)이 위로", value = LIST),
                            @ExampleObject(name = "결과 없음", value = LIST_EMPTY)
                    })),
            @ApiResponse(responseCode = "400", description = "없는 `tab` · `sort` 값",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_INVALID_ENUM)))
    })
    ResponseEntity<PageResponse<AdminGroupBuyListItem>> getGroupBuys(
            @Parameter(description = "탭 — 기본 ALL", example = "ACTION_REQUIRED") @RequestParam(required = false) AdminGroupBuyTab tab,
            @Parameter(description = "공구명 · 공구번호 · 브랜드명 · 쇼룸명 검색", example = "글로우랩") @RequestParam(required = false) String keyword,
            @Parameter(description = "정렬 — ACTION_REQUIRED_FIRST(기본) / CREATED_DESC / END_AT_ASC", example = "ACTION_REQUIRED_FIRST")
            @RequestParam(required = false) AdminGroupBuySortType sort,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "조치 큐 · 탭 카운트 · 최단 기한 · 정산 지연 감시",
            description = """
                    GNB 배지 · 탭 배지 · 툴바 요약이 이 응답 하나를 쓴다. **파라미터를 받지 않는다** — 검색어가 걸린 카운트는 처리할 건을 가린다.
                    GNB가 공구 화면 밖에서도 폴링하므로 판매 포트를 부르지 않는 가벼운 쿼리다.

                    **권한:** ADMIN

                    **조치 큐 `queues`** = 「내가 눌러야 다음으로 가는 것」 4종. 서로 배타적이라 `actionRequiredCount` = 큐 합 = 조치 필요 탭 행 수다.

                    | 큐 | 조건 |
                    |---|---|
                    | `OPEN_REVIEW` 오픈 승인 | 준비중 ∧ 게시물 승인대기 |
                    | `SUSPEND_REQUEST` 중단 요청 | 검토 중 중단 요청 |
                    | `EARLY_CLOSE_REQUEST` 조기 마감 요청 | 검토 중 조기 마감 요청 |
                    | `APPEAL_REVIEW` 소명 검토 | 직권 중단 통지 중 ∧ (소명 제출됨 ∨ **소명 기한 경과**) |

                    게시물 숨김(다음 차례는 인플루언서) · 소명 대기(브랜드 차례) · 이행 미합의(3자 스레드)는 넣지 않는다.

                    - `tabCounts` — 목록 탭 8종과 같은 키. `ACTION_REQUIRED` = `actionRequiredCount`.
                    - `nearestDeadlines` — 큐마다 가장 이른 1건. `OPEN_REVIEW`는 제출일 + SLA 3영업일 23:59:59, `APPEAL_REVIEW`는 집행 예정 일시.
                      요청 2종은 검토 SLA 미정이라 행이 없다(기한을 지어내지 않는다). `daysLeft`는 지났으면 음수.
                    - `settlementWatch` — 조치와 다른 축. 정산이 끝나지 않은 종료(ENDED) 공구를 센다. 기한(종료 +30일)을 넘겨도
                      `actionRequiredCount`에 더하지 않는다(누를 버튼이 없다). 톤은 `nearest.reached`로 고른다. 중단 공구는 세지 않는다.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = AdminGroupBuySummaryResponse.class), examples = {
                    @ExampleObject(name = "조치 3건", summary = "오픈 승인 1 · 조기 마감 요청 1 · 소명 검토 1 / 정산 감시 1", value = SUMMARY),
                    @ExampleObject(name = "처리할 것 없음", summary = "FE는 GNB 배지를 그리지 않는다", value = SUMMARY_EMPTY)
            })))
    ResponseEntity<AdminGroupBuySummaryResponse> getSummary();

    @Operation(
            summary = "공구 상세",
            description = """
                    상세 13종(B1~B6)과 모달(M1~M8)의 배경이 **이 응답 하나**를 쓴다. 서버는 `viewPhase` 같은 값을 만들지 않는다 —
                    FE가 상태 × 사실 블록 조합으로 화면을 고른다.

                    **권한:** ADMIN

                    **블록이 채워지는 조건** (없으면 null)

                    | 블록 | 조건 |
                    |---|---|
                    | `readiness` | 준비중 · 준비완료 — 게이트 3개(물량 확인 · 게시물 제출 · 오픈 승인). 운영자 차례만 `MY_TURN`/`WARNING` |
                    | `openReview` | 준비중 ∧ 게시물 승인대기 — SLA 기한 · 시작일. `overdue`는 표시만 한다(자동 처리 없음) |
                    | `sales` | 진행중·중단 예정 = `LIVE`, 정산완료 = `SETTLED`. 판매 포트가 비면 항상 null |
                    | `activeRequest` | 검토 중 중단·조기 마감 요청 — `decisionBasis`는 유형마다 채우는 칸이 다르다 |
                    | `extension` | 기간 연장 요청이 있었으면 — 조회 전용(어드민은 판정하지 않는다) |
                    | `adminSuspension` | 진행 중(NOTICED) 직권 중단 통지 — 소명 · 첨부 목록 포함 |
                    | `afterEnd` | 종결(종료 · 정산완료 · 중단). `fulfillment` · `settlement`는 종료 · 정산완료만 |
                    | `closure` | 중단 · 조기 마감 종결 — 요청 승인이면 `requester`, 직권이면 `adminBasis` |

                    **판단 근거 숫자는 포트에서 오고, 없으면 null이다** — 거짓 0은 오판으로 직행한다.
                    판매 모듈 연동 전이라 `sales` · `decisionBasis`의 주문 수 · `inquiries.total` · `hidden.ordersSinceHidden` ·
                    `salesSinceNotice` · `closure.acceptedOrderCount` · `settlement.preview.provisionalSalesAmount`는 현재 null이다.
                    `decisionBasis.inquiries.defectRelated`는 항상 null(문의 유형에 하자 분류가 없다). 조기 마감의 `preparedQuantity`(계약 최소 물량 합)와
                    `soldOutInquiriesSinceRequest`(재입고 문의)는 판매 포트 없이도 나온다.

                    **필드별 주의**
                    - `post` — 원문 전체 · 판본(`latestRevisionNo`) · 승인 후 수정 횟수(`editCount`). 숨김 해제 요청에 `latestRevisionNo`를 그대로 돌려준다.
                      `disclosureText`는 저장값이 아니라 렌더링 시점에 붙는 대가관계 표시다(심사 기준 「대가관계 표시 훼손」 판정용).
                    - `adminSuspension.noticeRevisionNo` ≠ `post.latestRevisionNo`면 통지 후 게시물이 바뀌었다 — 판본 목록 API로 대조한다.
                    - 소명 첨부 URL은 싣지 않는다 — 첨부 다운로드 API로 클릭 시 발급한다.
                    - `afterEnd.settlement.blockers` — 「왜 아직 정산이 안 되나」를 서버가 판정한다. 사유가 둘이면 카드도 둘.
                      `UNCLOSED_ORDERS`(미종결 주문) · `FULFILLMENT_PENDING`(한쪽이라도 이행 확인 전) · `FULFILLMENT_DISPUTE`(정산 보류) · `CLOSURE_UNKNOWN`(판매 포트 없음 — 판정 불가).
                      `stageSource = DERIVED`면 공구 상태에서 파생한 값이다 — 중간 단계를 확정값처럼 그리지 않는다. `preview.rewardAmount`는 항상 null(확정 대기).
                    - `afterEnd.fulfillment` — 방향 이름(`brandToCreator` · `creatorToBrand`). 미이행 행은 합의 후에도 `UNFULFILLED` 그대로다 —
                      「합의로 해소」는 `UNFULFILLED + agreedAt` 조합으로 FE가 만든다. `autoConfirmOnTimeout = false`면 「무응답은 이행으로 처리됩니다」를 쓰지 않는다.
                    - `fixedFee.displayText` — 3서피스 동일 표준 표기. 지급 여부는 내리지 않는다. 고정 지급비 없는 계약이면 네 칸 모두 null.
                    - `permissions` — 실행 API가 같은 판정으로 409를 낸다. `noticeUnavailableReason`으로 통지 버튼이 왜 잠겼는지 보인다
                      (`STATUS` 진행중 아님 · `REQUEST_PENDING` 검토 중 요청 먼저 · `NO_WINDOW_BEFORE_END` 종료 전 집행 창 없음 — 긴급 중단만 남는다).
                      집행 버튼처럼 시각이 여는 버튼은 서버 now로 판정한다.
                    - `history` — 최신순 · 이벤트 전량 · 운영자 실명. `synthetic: true`는 게시물 리비전에서 합성한 `POST_EDITED` 행이다(`revisionNo` 동반).
                    - `tab` · `keyword` · `sort` 중 하나라도 넘기면 `navigation`에 목록 기준 이전·다음 공구를 채운다.
                      넘기지 않았거나 그 사이 판정으로 현재 공구가 목록 조건에서 빠졌으면 둘 다 null.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDetailResponse.class), examples = {
                            @ExampleObject(name = "B1 오픈 승인 대기", summary = "준비중 · 게시물 승인대기 — 게이트 ③이 운영자 차례", value = DETAIL_OPEN_REVIEW),
                            @ExampleObject(name = "B2b 게시물 숨김 중", summary = "숨김 판본 2 → 인플루언서가 3으로 수정. 해제 시 expectedRevisionNo=3", value = DETAIL_POST_HIDDEN),
                            @ExampleObject(name = "B2c 소명 검토", summary = "중단 예정 · 소명 제출 · 기한 경과 · 집행 예정 도래 — 집행·철회 가능", value = DETAIL_APPEAL_REVIEW),
                            @ExampleObject(name = "B3 중단 요청 검토", summary = "인플루언서의 중단 요청 · 거절된 연장 카드 동반", value = DETAIL_SUSPEND_REQUEST),
                            @ExampleObject(name = "B4 조기 마감 요청 검토", summary = "브랜드의 재고 소진 요청 — 준비 물량 · 재입고 문의", value = DETAIL_EARLY_CLOSE_REQUEST),
                            @ExampleObject(name = "B5 종료 · 이행 이견", summary = "이행 한쪽 UNFULFILLED → 정산 보류 · 판매 포트 없음", value = DETAIL_ENDED),
                            @ExampleObject(name = "B6 직권 중단 종결", summary = "사전 통지 집행으로 중단 — closure.adminBasis", value = DETAIL_SUSPENDED_BY_NOTICE)
                    })),
            @ApiResponse(responseCode = "400", description = "없는 `tab` · `sort` 값",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_INVALID_ENUM))),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND)))
    })
    ResponseEntity<AdminGroupBuyDetailResponse> getGroupBuy(
            @Parameter(description = "공구 ID", example = "41") @PathVariable Long groupBuyId,
            @Parameter(description = "목록 탭 — 이전·다음 계산용", example = "ACTION_REQUIRED") @RequestParam(required = false) AdminGroupBuyTab tab,
            @Parameter(description = "목록 검색어 — 이전·다음 계산용") @RequestParam(required = false) String keyword,
            @Parameter(description = "목록 정렬 — 이전·다음 계산용", example = "ACTION_REQUIRED_FIRST") @RequestParam(required = false) AdminGroupBuySortType sort);

    @Operation(
            summary = "게시물 판본 목록",
            description = """
                    「수정 전후 본문 대조 ↗」 — 원문 판본을 판본 번호 오름차순으로 전부 내린다.
                    **차분은 서버가 계산하지 않는다**(표시 규칙 미정) — FE가 두 판본을 나란히 놓는다.

                    **권한:** ADMIN

                    **판본 표지** — 여러 개가 한 판본에 겹칠 수 있다.

                    | 필드 | 의미 |
                    |---|---|
                    | `approved` | 운영자가 승인한 판(승인 시각 이전 마지막 `SUBMITTED`) |
                    | `hiddenBasis` | 현재·마지막 숨김의 기준 판 |
                    | `unhiddenBasis` | 숨김 해제 판단에 쓴 판 |
                    | `noticeBasis` | 최근 직권 중단 통지의 기준 판 |
                    | `latest` | 최신 판 |

                    `kind` — `SUBMITTED`(등록·재등록) / `EDITED`(승인 후 수정 · 재승인 없음).
                    게시물이 없으면 빈 배열이다. 페이징하지 않는다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminGroupBuyDto.PostRevisionItem.class)),
                            examples = {
                                    @ExampleObject(name = "승인 → 숨김 기준 → 숨김 뒤 수정", value = POST_REVISIONS),
                                    @ExampleObject(name = "게시물 없음", value = "[]")
                            })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND)))
    })
    ResponseEntity<List<AdminGroupBuyDto.PostRevisionItem>> getPostRevisions(
            @Parameter(description = "공구 ID", example = "41") @PathVariable Long groupBuyId);

    @Operation(
            summary = "직권 중단 통지 날짜 선택지(M6)",
            description = """
                    M6의 소명 기한 · 집행 예정 칩을 **서버가 만든다** — 서버 시각(Asia/Seoul) · 공휴일 설정 기준. FE가 달력 규칙을 다시 계산하지 않는다.

                    **권한:** ADMIN

                    - `appealDeadline.min` — 소명 기한 하한 = 통지일 다음 날부터 3영업일째 23:59:59(제17조④ · 통지일 불산입). `default`는 현재 하한과 같다.
                    - `executionDates` — 통지 다음 날부터 종료일까지 **영업일만** 나열한다(주말이 빠지는 것이 칩에 그대로 보인다).
                      집행 예정 = 통지일 +3영업일 이후(제17조②) ∧ **소명 기한보다 엄격히 뒤** ∧ 공구 종료 전 — 어긋나는 날은 `selectable: false`(회색 취소선).
                      칩은 하루 단위라 보수적으로 판정한다.
                    - `latestExecutionBefore` — 집행 예정은 이 시각(= 공구 종료 예정)보다 앞이어야 한다. 종료일 칩은 이 시각 전만 유효하고,
                      종료가 자정 정각이면 전날까지만 나열한다.
                    - `available: false`면 M6을 열 수 없다 — `unavailableReason`(`STATUS` · `REQUEST_PENDING` · `NO_WINDOW_BEFORE_END`).
                      상세의 `permissions.noticeUnavailableReason`과 같은 값이다.
                    - 「직접 입력」도 통지 API가 같은 규칙으로 검증한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.NoticeOptionsResponse.class), examples = {
                            @ExampleObject(name = "통지 가능", summary = "09-07(월) 통지 · 09-11부터 선택 가능 · 주말 제외", value = NOTICE_OPTIONS),
                            @ExampleObject(name = "종료 전 집행 창 없음", summary = "소명 기한 하한이 종료 뒤 — 긴급 중단만 남는다", value = NOTICE_OPTIONS_NO_WINDOW)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND)))
    })
    ResponseEntity<AdminGroupBuyDto.NoticeOptionsResponse> getNoticeOptions(
            @Parameter(description = "공구 ID", example = "45") @PathVariable Long groupBuyId);

    @Operation(
            summary = "소명 첨부 다운로드 URL",
            description = """
                    B2c 「첨부 N건」 — 클릭 시 발급한다(유효 5분 · `expiresInSeconds = 300`). 상세를 오래 열어 둔 채 만료된 링크를 누르지 않도록
                    상세에는 URL을 싣지 않는다.

                    **권한:** ADMIN

                    - 첨부 ID는 상세의 `adminSuspension.appeal.attachments[].attachmentId`
                    - 업로드가 확인된 첨부만 — 업로드 중·실패 첨부나 다른 공구의 첨부는 404
                    - URL은 `attachment` 다운로드로 원본 파일명을 붙여 발급한다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.AttachmentUrlResponse.class),
                            examples = @ExampleObject(value = ATTACHMENT_URL))),
            @ApiResponse(responseCode = "404", description = "첨부 없음 · 다른 공구의 첨부 · 업로드 미완료",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_ATTACHMENT_NOT_FOUND)))
    })
    ResponseEntity<AdminGroupBuyDto.AttachmentUrlResponse> getAppealAttachmentUrl(
            @Parameter(description = "공구 ID", example = "45") @PathVariable Long groupBuyId,
            @Parameter(description = "소명 첨부 ID", example = "71") @PathVariable Long attachmentId);

    // ── 오픈 승인 · 게시물 ────────────────────────────────────────────────────

    @Operation(
            summary = "오픈 승인(B1)",
            description = """
                    게이트 ③. **바디 없음.** 브랜드 물량 확인(게이트 ①)이 이미 끝났으면 **이 요청이 준비완료를 만든다**.

                    **권한:** ADMIN · **조건:** 준비중 ∧ 게시물 승인대기(`permissions.canApproveOpen`)

                    - 응답 `status`로 완료 문구를 고른다
                      - `READY` — 준비완료. `openAt` = 시작 일시(「08.14 10:00에 공구가 열립니다」)
                      - `PREPARING` — 브랜드 물량 확인 대기(「브랜드 물량 확인을 기다립니다」). `openAt = null`
                    - 두 경우 모두 `postStatus = SCHEDULED`(예약) — 게시물 노출은 이 API가 하지 않고 시작 시각에 스케줄러가 연다.
                      시작 시각이 이미 지났으면(`timeline.startOverdue`) 다음 tick에 단축된 기간으로 바로 열린다.
                    - 브랜드 · 인플루언서 양측에 통지한다. 응답·통지·이력 어디에도 고정 지급비를 언급하지 않는다(§29-9)
                    - 운영자 둘이 동시에 승인·반려하면 늦은 쪽이 409
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ActionResponse.class), examples = {
                            @ExampleObject(name = "준비완료가 됨", summary = "물량 확인이 먼저 끝나 있었다", value = ACTION_APPROVE_READY),
                            @ExampleObject(name = "물량 확인 대기", summary = "게이트 ①이 남아 준비중 유지", value = ACTION_APPROVE_WAITING_STOCK)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "승인대기 게시물이 아님 — 이미 다른 운영자가 판정했거나 준비중이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_OPEN_REVIEW_NOT_PENDING)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> approveOpen(
            @Parameter(description = "공구 ID", example = "41") @PathVariable Long groupBuyId,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "오픈 반려(M1)",
            description = """
                    게시물을 반려한다. 공구는 준비중에서 멈추고, 인플루언서가 고쳐 다시 제출하면 승인대기로 돌아온다. 반려 횟수는 세지 않는다.

                    **권한:** ADMIN · **조건:** 준비중 ∧ 게시물 승인대기(`permissions.canRejectOpen`)

                    **`reasonCode`** — 사유 7종(심사 기준과 같은 축)

                    | 코드 | 라벨 | 축 |
                    |---|---|---|
                    | `AD_EFFECT_ASSERTION` | 표시광고법 위반 문구 — 효과 단정 | 표시광고법 |
                    | `AD_MEDICAL_CLAIM` | 표시광고법 위반 문구 — 의료적 효능 표현 | 표시광고법 |
                    | `AD_SUPERLATIVE` | 표시광고법 위반 문구 — 최저가·최상급 표현 | 표시광고법 |
                    | `CONTRACT_PRODUCT_MISMATCH` | 계약과 다른 상품 구성 | 계약 불일치 |
                    | `CONTRACT_PRICE_MISMATCH` | 계약과 다른 가격 표기 | 계약 불일치 |
                    | `DISCLOSURE_DAMAGED` | 대가관계 표시 훼손 | 대가관계 |
                    | `ETC` | 기타(직접 입력) | 기타 |

                    **`detail`** — 인플루언서에게 그대로 전달된다. **사유와 무관하게 필수**(1,000자) — 고칠 문장을 지목하지 않으면 재등록이 반복된다.
                    이력에는 사유 라벨만 남고 설명 원문은 게시물 행에 저장된다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ActionResponse.class),
                            examples = @ExampleObject(value = ACTION_REJECT_OPEN))),
            @ApiResponse(responseCode = "400", description = "설명 누락 · 사유 누락 · 없는 사유 코드 · 1,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "detail 공백", value = ERR_REJECT_DETAIL_REQUIRED),
                            @ExampleObject(name = "reasonCode 누락", value = ERR_NOT_NULL),
                            @ExampleObject(name = "없는 reasonCode", value = ERR_INVALID_ENUM),
                            @ExampleObject(name = "detail 1,000자 초과", value = ERR_SIZE_1000)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "승인대기 게시물이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_OPEN_REVIEW_NOT_PENDING)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> rejectOpen(
            @Parameter(description = "공구 ID", example = "41") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.OpenRejectRequest.class), examples = {
                            @ExampleObject(name = "효과 단정", value = REQ_OPEN_REJECT),
                            @ExampleObject(name = "계약과 다른 가격", value = REQ_OPEN_REJECT_PRICE)
                    }))
            @Valid @RequestBody AdminGroupBuyDto.OpenRejectRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "게시물 숨김(M5)",
            description = """
                    게시물만 소비자 화면에서 내린다. **공구는 멈추지 않는다** — 판매는 계속된다.
                    숨김 중에는 양측의 연장·중단·조기 마감 요청이 막히고, 인플루언서는 게시물을 고칠 수 있다(재승인 없음).

                    **권한:** ADMIN · **조건:** 승인된 게시물 ∧ 숨김 아님 ∧ 준비완료 · 진행중 · 중단 예정(`permissions.canHidePost`)

                    - 준비완료에서 숨기면 **숨긴 채로 열린다**
                    - `reasonCode` 7종 — `AD_EFFECT_ASSERTION` · `AD_MEDICAL_CLAIM` · `AD_SUPERLATIVE` · `DISCLOSURE_DAMAGED` ·
                      `CONTRACT_MISMATCH`(계약과 다른 상품·가격 기재) · `FALSE_INFORMATION`(사실과 다른 정보) · `ETC`
                    - `detail` 필수(1,000자) — 인플루언서에게 전달된다
                    - `observedRevisionNo` — 운영자가 본 판본(상세의 `post.latestRevisionNo`). 검증하지 않고 대조에만 쓴다.
                      숨김 판본은 서버가 잠금 안에서 읽은 최신 판본을 박는다. 둘이 다르면 숨김은 그대로 유효하고 응답 `revisionAdvanced: true`
                      — 「숨기는 사이 게시물이 수정됐습니다 — 최신 본문을 확인하세요」를 띄운다.
                    - 양측에 통지한다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "숨김 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ActionResponse.class), examples = {
                            @ExampleObject(name = "진행중 숨김", value = ACTION_HIDE),
                            @ExampleObject(name = "숨기는 사이 수정됨", summary = "observedRevisionNo와 최신 판본이 다르다", value = ACTION_HIDE_REVISION_ADVANCED),
                            @ExampleObject(name = "준비완료에서 숨김", summary = "숨긴 채로 시작 시각에 열린다", value = ACTION_HIDE_READY)
                    })),
            @ApiResponse(responseCode = "400", description = "설명 누락 · 사유 누락 · 없는 사유 코드 · 1,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "detail 공백", value = ERR_REJECT_DETAIL_REQUIRED),
                            @ExampleObject(name = "reasonCode 누락", value = ERR_NOT_NULL),
                            @ExampleObject(name = "없는 reasonCode", value = ERR_INVALID_ENUM)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "이미 숨김 · 숨길 수 없는 상태(미승인 게시물 · 준비중 · 종결)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "이미 숨김", value = ERR_POST_ALREADY_HIDDEN),
                            @ExampleObject(name = "숨길 수 없는 상태", value = ERR_POST_NOT_HIDEABLE)
                    }))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> hidePost(
            @Parameter(description = "공구 ID", example = "41") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.PostHideRequest.class),
                            examples = @ExampleObject(value = REQ_POST_HIDE)))
            @Valid @RequestBody AdminGroupBuyDto.PostHideRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "게시물 숨김 해제(B2b)",
            description = """
                    게시물을 다시 소비자 화면에 올린다(진행중이면 노출중, 준비완료면 예약).

                    **권한:** ADMIN · **조건:** 숨김 중 ∧ 종결 전(`permissions.canUnhidePost`)

                    **읽은 글 = 여는 글** — 상세에서 받은 `post.latestRevisionNo`를 `expectedRevisionNo`로 돌려준다.
                    그사이 인플루언서가 고쳤으면 409 `GROUP_BUY_POST_CHANGED_SINCE_VIEW` — 운영자에게 보이는 게 정상이다. 상세를 다시 읽고 판단한다.

                    - 「고쳤는가」를 서버가 판정하지 않는다 — 숨김이 잘못이었다고 판단할 수 있어야 한다
                    - 해제 사유를 받지 않는다 — 판본 번호(`unhiddenBasis`)가 근거 기록이다
                    - 양측에 통지한다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ActionResponse.class),
                            examples = @ExampleObject(value = ACTION_UNHIDE))),
            @ApiResponse(responseCode = "400", description = "`expectedRevisionNo` 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_NULL))),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "숨김 상태가 아님 · 확인 뒤 게시물이 수정됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "숨김 아님", value = ERR_POST_NOT_HIDDEN),
                            @ExampleObject(name = "읽은 뒤 수정됨", value = ERR_POST_CHANGED_SINCE_VIEW)
                    }))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> unhidePost(
            @Parameter(description = "공구 ID", example = "41") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.PostUnhideRequest.class),
                            examples = @ExampleObject(value = REQ_POST_UNHIDE)))
            @Valid @RequestBody AdminGroupBuyDto.PostUnhideRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    // ── 직권 중단 ────────────────────────────────────────────────────────────

    @Operation(
            summary = "직권 중단 사전 통지(M6)",
            description = """
                    진행중 → 중단 예정. **판매는 멈추지 않는다.** 브랜드는 소명 기한까지 소명할 수 있고, 집행 예정 일시가 되면 운영자가 집행하거나 철회한다.

                    **권한:** ADMIN · **조건:** 진행중 ∧ 검토 중 요청 없음 ∧ 종료 전 집행 창 있음(`permissions.canNoticeSuspension`)

                    **`clause`** — 제17조① 1~4호 고정(기타 없음)

                    | 코드 | 라벨 |
                    |---|---|
                    | `ART17_1_LAW` | 제17조① 1호 법령 위반 |
                    | `ART17_2_IP_DEFECT` | 제17조① 2호 지식재산권 침해·중대 하자 |
                    | `ART17_3_BREACH` | 제17조① 3호 중대 의무 불이행 |
                    | `ART17_4_DISPUTE` | 제17조① 4호 분쟁 심화·신용 훼손 |

                    - `noticeBody` 필수(2,000자) — 브랜드에 그대로 노출된다
                    - `appealDeadlineAt` · `executeScheduledAt` — 통지 날짜 선택지 API와 같은 규칙. 어기면 400 `GROUP_BUY_SUSPENSION_SCHEDULE_INVALID`
                      - 소명 기한 ≥ 통지일 +3영업일 23:59:59
                      - 집행 예정일 ≥ 통지일 +3영업일 ∧ 집행 예정 > 소명 기한 ∧ 집행 예정 < 공구 종료 예정
                    - **검토 중 중단·조기 마감 요청이 있으면 409 `GROUP_BUY_REQUEST_DECIDE_FIRST`** — 요청을 먼저 판정한다
                    - 3호 선택 시 응답 `clauseCaution: "C2_POST_ALTERATION"` — 게시물 변경을 이유로 삼으면 브랜드가 소명할 수 없는 사유라는 주의 문구를 띄운다
                    - 숨김 중에도 받는다 · 대기 중인 연장은 그대로 둔다
                    - 통지 시점의 게시물 판본(`noticeRevisionNo`)과 판매 스냅샷을 박는다. 이력 detail 예: 「제17조① 1호 법령 위반 · 집행 예정 09.11 10:00 · 소명 기한 09.10」
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "통지 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ActionResponse.class), examples = {
                            @ExampleObject(name = "1호 법령 위반", value = ACTION_NOTICE),
                            @ExampleObject(name = "3호 중대 의무 불이행", summary = "clauseCaution 동반", value = ACTION_NOTICE_CLAUSE3)
                    })),
            @ApiResponse(responseCode = "400", description = "본문 누락 · 날짜 규칙 위반 · 필수값 누락 · 2,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "noticeBody 공백", value = ERR_DECISION_REASON_REQUIRED),
                            @ExampleObject(name = "날짜 규칙 위반", summary = "소명 기한 하한 전 · 3영업일 전 집행 · 소명 기한 이전 집행 · 종료 후 집행", value = ERR_SUSPENSION_SCHEDULE_INVALID),
                            @ExampleObject(name = "clause · 일시 누락", value = ERR_NOT_NULL),
                            @ExampleObject(name = "noticeBody 2,000자 초과", value = ERR_SIZE_2000)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "진행중이 아님 · 이미 통지 중 · 검토 중 요청 먼저",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "진행중이 아님", value = ERR_STATUS_CONFLICT),
                            @ExampleObject(name = "이미 통지 중", value = ERR_SUSPENSION_ALREADY_NOTICED),
                            @ExampleObject(name = "검토 중 요청 있음", value = ERR_REQUEST_DECIDE_FIRST)
                    }))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> noticeSuspension(
            @Parameter(description = "공구 ID", example = "45") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.SuspensionNoticeRequest.class),
                            examples = @ExampleObject(value = REQ_NOTICE)))
            @Valid @RequestBody AdminGroupBuyDto.SuspensionNoticeRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "직권 중단 집행(B2c)",
            description = """
                    중단 예정 → 중단(**재개 불가**). **소명 기한이 지나고 통지한 집행 예정 일시가 되어야** 열린다 — 통지 내용보다 먼저 판매를 끊지 않는다.
                    자동 집행하지 않는다. 소명이 없어도(기한 경과) 운영자가 최종 판정한다.

                    **권한:** ADMIN (판매를 멈추는 판정 — 최고관리자 한정 정책 확정 시 검사 지점) · **조건:** `permissions.canExecuteSuspension`

                    - `executionNote` 필수(1,000자) — 브랜드에 전달된다. 소명을 낸 브랜드는 그 소명이 왜 받아들여지지 않았는지를 들어야 한다
                    - 종결 부수 효과(대기 연장 만료 · 게시물 내림 · 상품 판매 차단 재계산 · 양측 통지)는 종결 경로 공용 처리로 한다
                    - 집행 후 상세는 `closure.source = ADMIN_NOTICE` · `closure.adminBasis.executionNote`로 보인다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "집행 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ActionResponse.class),
                            examples = @ExampleObject(value = ACTION_SUSPENDED_45))),
            @ApiResponse(responseCode = "400", description = "집행 사유 누락 · 1,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "executionNote 공백", value = ERR_DECISION_REASON_REQUIRED),
                            @ExampleObject(name = "1,000자 초과", value = ERR_SIZE_1000)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "진행 중 통지 없음 · 집행 예정 일시 전",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "통지 없음 · 이미 처리됨", value = ERR_SUSPENSION_NOT_NOTICED),
                            @ExampleObject(name = "집행 예정 전", value = ERR_SUSPENSION_EXECUTION_NOT_DUE)
                    }))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> executeSuspension(
            @Parameter(description = "공구 ID", example = "45") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.SuspensionExecuteRequest.class),
                            examples = @ExampleObject(value = REQ_EXECUTE)))
            @Valid @RequestBody AdminGroupBuyDto.SuspensionExecuteRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "직권 중단 철회(M7)",
            description = """
                    중단 예정 → 진행중. 공구는 원래 일정대로(종료 예정 불변). **소명 기한 전에도 철회할 수 있다** — 운영자가 스스로 시정을 확인했거나 오판을 깨달았을 수 있다.

                    **권한:** ADMIN · **조건:** 중단 예정 ∧ 진행 중 통지(`permissions.canWithdrawSuspension`)

                    **`reasonCode`** — 사유 코드로 제재 이력 연동을 가른다(「귀책 없음」은 세지 않는다)

                    | 코드 | 라벨 |
                    |---|---|
                    | `RECTIFIED` | 지적 사항이 시정 완료됨 |
                    | `NOT_A_VIOLATION` | 사실관계 오인 — 위반에 해당하지 않음 |
                    | `NOT_BRAND_FAULT` | 귀책이 브랜드에 없음 |
                    | `ETC` | 기타(직접 입력) |

                    - `detail` 필수(1,000자) — 브랜드에 전달된다
                    - 같은 사유로 다시 통지하면 절차가 처음부터다(3영업일이 다시 흐른다)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "철회 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ActionResponse.class),
                            examples = @ExampleObject(value = ACTION_WITHDRAW))),
            @ApiResponse(responseCode = "400", description = "설명 누락 · 사유 누락 · 없는 사유 코드",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "detail 공백", value = ERR_DECISION_REASON_REQUIRED),
                            @ExampleObject(name = "reasonCode 누락", value = ERR_NOT_NULL),
                            @ExampleObject(name = "없는 reasonCode", value = ERR_INVALID_ENUM)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "진행 중 통지 없음(이미 집행·철회됨)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_SUSPENSION_NOT_NOTICED)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> withdrawSuspension(
            @Parameter(description = "공구 ID", example = "45") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.SuspensionWithdrawRequest.class),
                            examples = @ExampleObject(value = REQ_WITHDRAW)))
            @Valid @RequestBody AdminGroupBuyDto.SuspensionWithdrawRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "긴급 직권 중단(M4)",
            description = """
                    진행중·중단 예정 → 중단(**즉시 · 재개 불가**). 사전 통지 없이 집행하고 사후 통지한다.

                    **권한:** ADMIN (판매를 멈추는 판정) · **조건:** 진행중 · 중단 예정(`permissions.canEmergencySuspend`)

                    **`emergencyReason`** — 3종 고정 · 기타 없음(제17조③)

                    | 코드 | 라벨 |
                    |---|---|
                    | `CONSUMER_HARM` | 소비자 위해 방지 |
                    | `AUTHORITY_ORDER` | 행정·사법기관의 명령 |
                    | `DAMAGE_SURGE` | 피해 급증 우려 |

                    - `body` 필수(2,000자) — 브랜드에 전달된다. 이력 detail에는 서버가 「긴급 · {사유 라벨}」을 박는다
                    - 진행 중 사전 통지가 있으면 SUPERSEDED(긴급 집행으로 대체)로 닫는다
                    - 준비완료에서는 받지 않는다 — 판매가 없어 피해 급증이 성립하지 않는다(브랜드 중단 요청 경로가 있다)
                    - 긴급 집행 후 소명 API는 없다 — 사후 이의는 이슈 스레드가 받는다
                    - 집행 후 상세는 `closure.source = ADMIN_EMERGENCY` · `closure.adminBasis.emergencyReason`으로 보인다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "중단 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ActionResponse.class),
                            examples = @ExampleObject(value = ACTION_SUSPENDED_45))),
            @ApiResponse(responseCode = "400", description = "본문 누락 · 사유 누락 · 없는 사유 코드 · 2,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "body 공백", value = ERR_DECISION_REASON_REQUIRED),
                            @ExampleObject(name = "emergencyReason 누락", value = ERR_NOT_NULL),
                            @ExampleObject(name = "없는 emergencyReason", value = ERR_INVALID_ENUM),
                            @ExampleObject(name = "body 2,000자 초과", value = ERR_SIZE_2000)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "진행중·중단 예정이 아님(준비중 · 준비완료 · 종결)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_STATUS_CONFLICT)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> emergencySuspend(
            @Parameter(description = "공구 ID", example = "45") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.EmergencySuspensionRequest.class),
                            examples = @ExampleObject(value = REQ_EMERGENCY)))
            @Valid @RequestBody AdminGroupBuyDto.EmergencySuspensionRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    // ── 요청 판정 ────────────────────────────────────────────────────────────

    @Operation(
            summary = "중단·조기 마감 요청 승인(M2 · B4)",
            description = """
                    **경로의 요청 id를 판정한다** — 운영자가 읽은 그 요청(상세의 `activeRequest.requestId`)이다. 「지금 걸린 요청」을 판정하지 않는다.

                    **권한:** ADMIN (중단 승인은 판매를 멈추는 판정) · **조건:** `permissions.canApproveRequest`

                    | 요청 유형 | 출발 상태 | 결과 |
                    |---|---|---|
                    | `SUSPEND` 공구 중단 | 준비완료 · 진행중 | 중단(재개 불가) |
                    | `EARLY_CLOSE` 조기 마감 | 진행중 | 종료(조기 마감) — **승인 시각이 종료 시각** |

                    - `decisionReason` 필수(1,000자) — **요청자와 상대 모두**에게 간다. 조기 마감도 필수 — 인플루언서에게는 처음 듣는 종료다
                    - 종결 부수 효과(대기 연장 만료 · 게시물 내림 · 상품 판매 차단 재계산 · 양측 통지)는 종결 경로 공용 처리로 한다
                    - 운영자 둘이 동시에 판정하거나 스케줄러 종료가 먼저면 늦은 쪽이 409
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ActionResponse.class), examples = {
                            @ExampleObject(name = "중단 요청 승인", value = ACTION_APPROVE_SUSPEND),
                            @ExampleObject(name = "조기 마감 요청 승인", value = ACTION_APPROVE_EARLY_CLOSE)
                    })),
            @ApiResponse(responseCode = "400", description = "판정 사유 누락 · 1,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "decisionReason 공백", value = ERR_DECISION_REASON_REQUIRED),
                            @ExampleObject(name = "1,000자 초과", value = ERR_SIZE_1000)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "검토 중 요청이 아님(다른 공구의 요청 · 이미 판정 · 만료) · 승인하면 출발할 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "검토 중 요청 아님", value = ERR_CHANGE_REQUEST_NOT_PENDING),
                            @ExampleObject(name = "출발 상태 아님", value = ERR_STATUS_CONFLICT)
                    }))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> approveRequest(
            @Parameter(description = "공구 ID", example = "41") @PathVariable Long groupBuyId,
            @Parameter(description = "중단·조기 마감 요청 ID — 상세의 activeRequest.requestId", example = "57") @PathVariable Long requestId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ChangeRequestDecisionRequest.class), examples = {
                            @ExampleObject(name = "중단 요청 승인", value = REQ_APPROVE_SUSPEND),
                            @ExampleObject(name = "조기 마감 요청 승인", value = REQ_APPROVE_EARLY_CLOSE)
                    }))
            @Valid @RequestBody AdminGroupBuyDto.ChangeRequestDecisionRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "중단·조기 마감 요청 반려(M3 · B4)",
            description = """
                    요청만 기각되고 공구는 일정대로 간다.

                    **권한:** ADMIN · **조건:** `permissions.canRejectRequest`

                    - `decisionReason` 필수(1,000자) — 재요청 조건을 함께 적도록 유도한다(서버가 구조화하지 않는다)
                    - 요청자와 상대 모두에게 통지한다 — 시작을 알렸으면 끝도 알려야 한다
                    - 반려 후 재요청은 막지 않는다
                    - 이력: `SUSPENSION_REJECTED` / `EARLY_CLOSE_REJECTED`(detail = 판정 사유)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ActionResponse.class),
                            examples = @ExampleObject(value = ACTION_REJECT_REQUEST))),
            @ApiResponse(responseCode = "400", description = "판정 사유 누락 · 1,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "decisionReason 공백", value = ERR_DECISION_REASON_REQUIRED),
                            @ExampleObject(name = "1,000자 초과", value = ERR_SIZE_1000)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "검토 중 요청이 아님(다른 공구의 요청 · 이미 판정 · 만료)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_CHANGE_REQUEST_NOT_PENDING)))
    })
    ResponseEntity<AdminGroupBuyDto.ActionResponse> rejectRequest(
            @Parameter(description = "공구 ID", example = "41") @PathVariable Long groupBuyId,
            @Parameter(description = "중단·조기 마감 요청 ID — 상세의 activeRequest.requestId", example = "57") @PathVariable Long requestId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.ChangeRequestDecisionRequest.class),
                            examples = @ExampleObject(value = REQ_REJECT_REQUEST)))
            @Valid @RequestBody AdminGroupBuyDto.ChangeRequestDecisionRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    // ── 이슈 · 정산 ──────────────────────────────────────────────────────────

    @Operation(
            summary = "이슈 스레드 개설(B5 · B5b)",
            description = """
                    종결 공구에 운영자가 이슈 스레드를 연다(`openerType = ADMIN`). 공구당 열린 이슈는 1건이다.
                    **정산을 보류하지 않는다** — 이슈는 정산과 무관한 이견이다. 중단 공구에서는 긴급 건의 사후 이의 창구다.

                    **권한:** ADMIN · **조건:** 종료 · 정산완료 · 중단 ∧ 열린 이슈 없음(`permissions.canOpenIssue`)

                    **`issueType`** — `CONTENT_FULFILLMENT`(콘텐츠 이행 문제) · `TERMS_INTERPRETATION`(계약 조건 해석 이견) ·
                    `SETTLEMENT_AMOUNT`(정산 금액 이견) · `ETC`(기타)

                    - `content` 필수(2,000자) — 스레드 첫 글이 된다
                    - 응답 `threadId`로 연결·소통 스레드를 연다(스레드가 아직 없으면 null)

                    🚧 스레드 포트 선행 — 연결·소통의 스레드 모델 변경 전에는 503 `GROUP_BUY_THREAD_UNAVAILABLE`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "개설 완료",
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.IssueOpenResponse.class),
                            examples = @ExampleObject(value = ISSUE_OPENED))),
            @ApiResponse(responseCode = "400", description = "본문 누락 · 유형 누락 · 없는 유형 · 2,000자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "content 공백", value = ERR_DECISION_REASON_REQUIRED),
                            @ExampleObject(name = "issueType 누락", value = ERR_NOT_NULL),
                            @ExampleObject(name = "없는 issueType", value = ERR_INVALID_ENUM),
                            @ExampleObject(name = "content 2,000자 초과", value = ERR_SIZE_2000)
                    })),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "열린 이슈 있음 · 종결 전",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "열린 이슈 있음", value = ERR_ISSUE_ALREADY_OPEN),
                            @ExampleObject(name = "종결 전", value = ERR_ACTION_NOT_ALLOWED)
                    })),
            @ApiResponse(responseCode = "503", description = "스레드 포트 미연동",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_THREAD_UNAVAILABLE)))
    })
    ResponseEntity<AdminGroupBuyDto.IssueOpenResponse> openIssue(
            @Parameter(description = "공구 ID", example = "41") @PathVariable Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AdminGroupBuyDto.IssueOpenRequest.class),
                            examples = @ExampleObject(value = REQ_ISSUE)))
            @Valid @RequestBody AdminGroupBuyDto.IssueOpenRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "정산 확인(B5 계열)",
            description = """
                    정산대기 → 운영자 확인. **바디 없음 · 응답 바디 없음(204).** 정산 모듈에 위임하고 공구 상태는 바꾸지 않는다
                    (정산완료는 이체 완료 통보가 만든다).

                    **권한:** ADMIN · **조건:** 종료 ∧ `afterEnd.settlement.blockers` 없음 ∧ 정산 모듈 단계 `WAITING`(`permissions.canConfirmSettlement`)

                    🚧 착수 게이트 — 정산 모듈 연동 전에는 `permissions.canConfirmSettlement`가 항상 false이고 이 API는 409다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "확인 완료"),
            @ApiResponse(responseCode = "404", description = "공구 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "정산 확인 조건 미충족 — 종료 아님 · 차단 사유 있음 · 정산 모듈 미연동",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_READY)))
    })
    ResponseEntity<Void> confirmSettlement(
            @Parameter(description = "공구 ID", example = "41") @PathVariable Long groupBuyId,
            @Parameter(hidden = true) UserPrincipal principal);
}
