package showroomz.api.admin.transaction.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.order.type.AdminOrderSearchType;
import showroomz.domain.order.type.AdminOrderSort;
import showroomz.domain.order.type.AdminOrderTab;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.domain.payment.type.PaymentMethod;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDate;

import static showroomz.api.admin.transaction.docs.AdminTransactionDocsExamples.*;

@Tag(name = "Admin - Transaction · Orders", description = """
        어드민 거래 관리 · 주문 조회(06a). 전 브랜드 · 전 공구 주문을 **주문(결제 1건) 단위**로 보고, 하위주문(브랜드)별로 조치한다.

        운영자는 이해당사자의 일을 대신하지 않는다 — 조치는 **조건이 찼을 때만** 열리고(`actions`), 돈은 **편입만** 하고 집행은 환불 관리(06c)에서 한다.
        조치 API 의 경로 변수는 전부 `deliveryGroupId`(하위주문)이고, 응답은 처리 후 **주문 상세 전체**다(모달을 그대로 다시 그린다).""")
public interface AdminOrderControllerDocs {

    // ── 조회 ─────────────────────────────────────────────────────────────────

    @Operation(summary = "주문 목록 (06a A1 ~ A5)", description = """
            전 브랜드 · 전 공구 주문의 조회 입구입니다. **행 단위는 주문(결제 1건)**이고 행 확장(A2)의 `groups[]`가 하위주문(브랜드)입니다.

            **권한:** ADMIN

            **탭 `tab`** — 없으면 `ALL`. 탭 숫자는 `GET /v1/admin/orders/summary`.

            | 값 | 화면 | 들어오는 주문 |
            |---|---|---|
            | `ALL` | 전체 | 결제된 주문 전부 |
            | `DELIVERY_ISSUE` | 배송 이상 | 하위주문 하나라도 집화 확인 필요 · 추적 정지 · 반송 중. **반송 완료가 감지된 건은 빠진다**(PG 자동 환불로 넘어감) |
            | `CANCEL` | 취소 | 취소된 하위주문 · 검토 중 취소 요청이 있는 주문 |

            **조건** — 모두 AND 이고, 하위주문 조건은 **하위주문 하나라도 맞으면** 그 주문이 나옵니다.
            - `status` — 상태 셀렉트. `PENDING`(결제 대기)은 목록에 오지 않으므로 고르지 않습니다.
            - `marketId` — 브랜드 상세의 「이 브랜드 진행 주문 보기」 진입 · `groupBuyId` · `creatorId`(공구를 연 인플루언서) · `paymentMethod` — 상세 조건
            - `trackingAlert` — 배송 이상 탭의 「이상 유형」(`PICKUP_UNCONFIRMED` 집화 확인 필요 · `STALLED` 추적 정지)
            - `from` · `to` — **결제일** 기준(양 끝 포함, `yyyy-MM-dd`)

            **검색 `searchType` + `keyword`**

            | `searchType` | 매칭 |
            |---|---|
            | `ALL`(기본) | 아래 전부 OR |
            | `ORDER_NUMBER` · `SUB_ORDER_NUMBER` | 부분 일치 |
            | `RECIPIENT` | 수취인명 부분 일치 |
            | `BRAND` | 브랜드명 부분 일치 |
            | `TRACKING_NUMBER` | 송장번호 — **숫자만** 비교(하이픈 무시) |
            | `PG_TX_ID` | PG 거래번호 **정확 일치** |

            **정렬 `sort`** — `PAID_DESC`(결제 최신순 · 기본) · `PAID_ASC` · `AMOUNT_DESC`(결제 금액 높은순) ·
            `ISSUE_OLDEST`(이상 지속 오래된순 — 마지막 추적 · 반송 감지 시각이 오래된 것 먼저. 배송 이상 탭용)

            **페이징** — `page`(1부터, 기본 1) · `size`(기본 20 · **1~100**, 벗어나면 400)

            **행 그리기**
            - `attentionCount` — 운영자가 볼 것이 있는 하위주문 수(배송 이상 · 발송 기한 경과 · 검토 중 취소 요청). 0 이면 표시를 뺍니다.
            - `groups[].shipOverdue` — 발송 전인데 발송 기한이 지났다(브랜드 귀책). `shipDueAt`이 null 이면 공구 진행 중이라 기한이 아직 없습니다.
            - `groups[].statusTone` — 배지 색(`NEUTRAL` · `INFO` · `WARNING` · `SUCCESS` · `DANGER`). 상태 문구는 `statusLabel`을 그대로 씁니다.
            - 툴바 「처리 지연 N건」은 이 응답에 없습니다 — `GET /v1/admin/order-exceptions/summary`의 `tabCounts.DELAY`.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "주문 목록과 페이지 정보",
                    content = @Content(schema = @Schema(implementation = PageResponse.class), examples = {
                            @ExampleObject(name = "전체 탭", summary = "하위주문 2개 중 1개가 발송 기한 경과(attentionCount 1)", value = ORDER_LIST),
                            @ExampleObject(name = "배송 이상 탭", summary = "tab=DELIVERY_ISSUE&trackingAlert=STALLED&sort=ISSUE_OLDEST", value = ORDER_LIST_DELIVERY_ISSUE),
                            @ExampleObject(name = "결과 없음", value = ORDER_LIST_EMPTY)
                    })),
            @ApiResponse(responseCode = "400", description = "페이지 크기 1~100 밖(`INVALID_INPUT`) · 없는 enum 값 · 날짜 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "페이지 크기", value = ERR_PAGE_SIZE),
                            @ExampleObject(name = "enum · 날짜 형식", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<PageResponse<AdminOrderDto.ListItem>> getOrders(
            @Parameter(description = "탭 — ALL(기본) · DELIVERY_ISSUE · CANCEL", example = "ALL") AdminOrderTab tab,
            @Parameter(description = "상태 셀렉트 — NEW · PREPARING · SHIPPING · RETURNING · DELIVERED · CONFIRMED · CANCELLED. 하위주문 하나라도 맞으면 나온다",
                    example = "PREPARING") FulfillmentStatus status,
            @Parameter(description = "브랜드(마켓) ID", example = "17") Long marketId,
            @Parameter(description = "공구 ID", example = "312") Long groupBuyId,
            @Parameter(description = "공구를 연 인플루언서(크리에이터) ID", example = "3021") Long creatorId,
            @Parameter(description = "결제수단 — CARD · EASY_PAY", example = "CARD") PaymentMethod paymentMethod,
            @Parameter(description = "[배송 이상 탭] 이상 유형 — PICKUP_UNCONFIRMED · STALLED", example = "STALLED") TrackingAlert trackingAlert,
            @Parameter(description = "검색 대상 — ALL(기본) · ORDER_NUMBER · SUB_ORDER_NUMBER · RECIPIENT · BRAND · TRACKING_NUMBER · PG_TX_ID",
                    example = "ALL") AdminOrderSearchType searchType,
            @Parameter(description = "검색어 — 앞뒤 공백 무시", example = "20261003-000123") String keyword,
            @Parameter(description = "정렬 — PAID_DESC(기본) · PAID_ASC · AMOUNT_DESC · ISSUE_OLDEST", example = "PAID_DESC") AdminOrderSort sort,
            @Parameter(description = "결제일 시작(포함) — yyyy-MM-dd", example = "2026-10-01") LocalDate from,
            @Parameter(description = "결제일 끝(포함) — yyyy-MM-dd", example = "2026-10-10") LocalDate to,
            PagingRequest pagingRequest);

    @Operation(summary = "주문 탭 건수", description = """
            목록과 **같은 조건**(브랜드 · 공구 · 인플루언서 · 결제수단 · 기간 · 검색)에서 탭별 주문 수를 셉니다.
            탭 · 상태 셀렉트 · 이상 유형 · 정렬은 받지 않습니다 — 탭을 바꿔도 숫자가 그대로여야 하기 때문입니다.

            **권한:** ADMIN

            - `tabCounts` — `ALL` · `DELIVERY_ISSUE` · `CANCEL` 세 키가 항상 있습니다(0건 포함).
            - 목록 조건을 바꿀 때마다 같은 값으로 함께 호출합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탭별 주문 수",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.SummaryResponse.class),
                            examples = @ExampleObject(value = ORDER_SUMMARY))),
            @ApiResponse(responseCode = "400", description = "없는 enum 값 · 날짜 형식 오류 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_INVALID_INPUT))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminOrderDto.SummaryResponse> getSummary(
            @Parameter(description = "브랜드(마켓) ID", example = "17") Long marketId,
            @Parameter(description = "공구 ID", example = "312") Long groupBuyId,
            @Parameter(description = "인플루언서(크리에이터) ID", example = "3021") Long creatorId,
            @Parameter(description = "결제수단 — CARD · EASY_PAY", example = "CARD") PaymentMethod paymentMethod,
            @Parameter(description = "검색 대상 — 목록과 같은 값", example = "ALL") AdminOrderSearchType searchType,
            @Parameter(description = "검색어 — 목록과 같은 값", example = "20261003-000123") String keyword,
            @Parameter(description = "결제일 시작(포함)", example = "2026-10-01") LocalDate from,
            @Parameter(description = "결제일 끝(포함)", example = "2026-10-10") LocalDate to);

    @Operation(summary = "주문 상세 (06a B1 ~ B8)", description = """
            주문 모달 전체입니다. 주문 · 결제 · 소비자 · 수취인 머리에 **하위주문(브랜드)별 블록 `groups[]`**가 붙습니다.

            **권한:** ADMIN · 열람 시 운영자 ID · 주문 ID 가 서버 로그에 남습니다(개인정보 열람 기록 — 수취인 연락처 · 주소를 가리지 않고 내리는 대신).

            **머리**
            - `payment` — 결제가 없으면 null. `cancelledAmount`는 PG 부분 취소 누적액(환불 큐 집행 · 수동 완료 기록이 올린다)
            - `inquiryCount` — 헤더 「문의 N건」. 이 주문을 가리키는 1:1 문의 수

            **하위주문 블록 `groups[]`**

            | 필드 | 내용 |
            |---|---|
            | `shipping.shipDueAt` | 발송 기한 = 공구 마감 + `shipDueBusinessDays`영업일(**주문 시점 N** · 주말 · 공휴일 제외). 공구 진행 중이면 null |
            | `shipping.shipOverdue` · `overdueNoticeCount` | 발송 기한 경과 · **시스템 자동 알림** 횟수(독촉 버튼은 없다) |
            | `shipping.trackingAlert` | `PICKUP_UNCONFIRMED` 집화 확인 필요 · `STALLED` 추적 정지 · null |
            | `shipping.deliveredSourceLabel` | 배송완료 출처 — 「자동 확인」 · 「운영자 정정」 · 「운영자 처리 · 추적 정지」 |
            | `purchaseConfirm` | 배송완료 뒤에만. 반품·교환 진행 중이면 `paused = true` · `dueAt = null` |
            | `cancel` | 취소된 하위주문만 — 유형(`cancelTypeLabel`) · 사유 · 소비자 전달 문구 |
            | `cancelRequest` | 검토 중 취소 요청(B7). `respondDueAt`이 지나면 **자동 승인** · PG 즉시 환불. 운영자 조치는 없다 |
            | `items[]` | 항목 — `status`(PENDING · PAID · PURCHASE_CONFIRMED · CANCELLED · RETURNED) · 반품 수량 |
            | `refunds[]` | 이 하위주문의 환불 큐 전부 — 출처(`PG_AUTO` · `OPERATOR`) · 상태(`PENDING` · `EXECUTING` · `DONE` · `FAILED` · `VOID`). `refundNo`로 06c 검색 |
            | `activeClaims[]` | 진행 중 반품·교환(B8) — `claimId`로 06b 상세(`GET /v1/admin/claims/{claimId}`) |
            | `history[]` | 처리 이력 **오래된순** — 준비 시작 사유(발주서 다운로드 · 개별 · 일괄) · 자동 승인 · 운영자 대행 · 정정 · 환불 |
            | `settlement` | ④ 정산 반영 — 정산 번호 · 상태 · 반영액 · 리워드(항목 기준) · 정산 후 환불로 생긴 차감(`clawbacks[]`). **정산 생성 전이면 null**(공구 주문이 전부 종결된 뒤 10분 안에 생긴다) |

            **우 레일 조치 `groups[].actions`** — 버튼 노출의 정본은 서버입니다. 거짓이면 버튼을 그리지 않습니다.

            | 필드 | 버튼 | 조건 |
            |---|---|---|
            | `canCorrectDeliveredAt` | B3 배송완료일 정정 | 배송완료 |
            | `canRegisterShipment` | B4 대행 송장 등록 | 상품준비중 ∧ 발송 기한 경과 ∧ 자동 알림 3회 무응답 ∧ 검토 중 취소 요청 없음 |
            | `canCancel` | B4 · B5 대행 직권 취소 | 신규 · 상품준비중 ∧ 검토 중 취소 요청 없음. **사유가 `DEFECT`가 아니면 대행 조건을 서버가 다시 본다**(미충족 409) |
            | `canEnqueueRefund` | B5 운영자 사유 환불 편입 | 배송중 · 배송완료 · 구매확정(반송 중 제외) |
            | `canOpenDefectClaim` | B6 하자 반품 대신 열기 | 구매확정(또는 배송완료) ∧ 배송완료 3개월 안 |
            | `canMarkLost` · `canMarkDelivered` | 추적 정지 종결 — 분실 / 배송완료 | 배송중 ∧ 추적 정지 ∧ 마지막 추적(없으면 발송) + 28일 경과 |
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "주문 상세",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.DetailResponse.class), examples = {
                            @ExampleObject(name = "발송 기한 경과 · 대행 가능", summary = "-01 상품준비중 · 알림 3회 → canRegisterShipment · canCancel / -02 정상 배송중",
                                    value = ORDER_DETAIL_OVERDUE),
                            @ExampleObject(name = "추적 정지 28일 경과", summary = "canMarkLost · canMarkDelivered", value = ORDER_DETAIL_STALLED),
                            @ExampleObject(name = "구매확정 · 정산 반영", summary = "settlement 블록 · 운영자 사유 환불 편입(PENDING)", value = ORDER_DETAIL_CONFIRMED)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 주문 · **결제 전 주문** (`ORDER_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ORDER_NOT_FOUND)))
    })
    ResponseEntity<AdminOrderDto.DetailResponse> getOrder(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "주문 ID", example = "5012", required = true) Long orderId);

    // ── 추적 정지 종결 ───────────────────────────────────────────────────────

    @Operation(summary = "추적 정지 종결 — 분실 처리", description = """
            배송중 하위주문의 추적이 오래 끊긴 건을 운영자가 **분실로 판정**합니다. 자동 처리는 없습니다 — 플랫폼이 단정하지 않습니다.

            **권한:** ADMIN

            **조건** — 상세 `actions.canMarkLost = true`: 배송중 ∧ 추적 정지(`STALLED`) ∧ 마지막 추적(없으면 발송) + **28일**
            (`order.exception.stalled-resolve-days`) 경과. 아니면 409.

            **효과**(한 트랜잭션)
            - 하위주문 → 취소(`cancel.cancelTypeLabel` 「배송 분실 · 운영자 처리」) · 결제완료 항목 전부 취소
            - **재고는 돌아오지 않습니다**(상품이 없다)
            - 환불 = 항목 금액 + 배송비 **전액**, **PG 자동**(`source = LOST_IN_TRANSIT` · 06c 경로 「취소」). 운영자 집행이 필요 없습니다
            - 이력 「배송 분실 처리 · PG 자동 환불」에 `reason`이 남습니다
            - 분실 보상은 택배사와 브랜드 사이의 일이라 여기 없습니다. 법무 확정 전이라 근거(`reason`)는 운영 지침에 따라 적습니다
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 후 주문 상세",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.DetailResponse.class),
                            examples = @ExampleObject(name = "분실 처리 후", summary = "취소(LOST) · PG 자동 환불 완료(RFD-921)", value = ORDER_AFTER_LOST))),
            @ApiResponse(responseCode = "400", description = "`reason` 누락 · 공백 · 300자 초과 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_NOT_BLANK))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 하위주문 (`ORDER_GROUP_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ORDER_GROUP_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "조건 미충족 · 그 사이 상태 변경 (`ORDER_STATE_CHANGED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "조건 미충족", value = ERR_STALLED_NOT_RESOLVABLE_LOST),
                            @ExampleObject(name = "동시 변경", value = ERR_ORDER_STATE_CHANGED)
                    }))
    })
    ResponseEntity<AdminOrderDto.DetailResponse> markLost(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "하위주문 ID", example = "1024", required = true) Long deliveryGroupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "판정 근거",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.MarkLostRequest.class), examples = @ExampleObject(value = REQ_MARK_LOST)))
            AdminOrderDto.MarkLostRequest request);

    @Operation(summary = "추적 정지 종결 — 배송완료 처리", description = """
            분실 처리와 **같은 조건**에서 운영자가 **배송완료로 판정**합니다(소비자가 받았다고 확인한 경우). 둘 중 하나를 고릅니다.

            **권한:** ADMIN

            - 조건 — 상세 `actions.canMarkDelivered = true`(배송중 ∧ 추적 정지 ∧ 마지막 추적 + 28일 경과). 아니면 409
            - `deliveredAt` — 실제 수령 시각. **발송 이후 · 지금 이전**이어야 합니다(아니면 400)
            - 출처는 「운영자 처리 · 추적 정지」로 남고, **수령 시각부터 구매확정 타이머**가 갑니다
            - 추적이 끊긴 송장에만 열리는 유일한 직권 배송완료입니다(그 밖의 「직권 배송완료」는 없다 — 수령일 이의는 B3 정정)
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 후 주문 상세",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.DetailResponse.class),
                            examples = @ExampleObject(name = "배송완료 처리 후", summary = "deliveredSourceLabel 「운영자 처리 · 추적 정지」 · 구매확정 D-5",
                                    value = ORDER_AFTER_MARK_DELIVERED))),
            @ApiResponse(responseCode = "400", description = "수령 시각이 발송 전 · 미래 · 필드 누락 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "수령 시각 범위", value = ERR_DELIVERED_AT_RANGE),
                            @ExampleObject(name = "필드 누락", value = ERR_NOT_BLANK)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 하위주문 (`ORDER_GROUP_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ORDER_GROUP_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "조건 미충족 · 그 사이 상태 변경 (`ORDER_STATE_CHANGED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "조건 미충족", value = ERR_STALLED_NOT_RESOLVABLE_DELIVERED),
                            @ExampleObject(name = "동시 변경", value = ERR_ORDER_STATE_CHANGED)
                    }))
    })
    ResponseEntity<AdminOrderDto.DetailResponse> markDelivered(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "하위주문 ID", example = "1024", required = true) Long deliveryGroupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "수령 시각 · 판정 근거",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.MarkDeliveredRequest.class), examples = @ExampleObject(value = REQ_MARK_DELIVERED)))
            AdminOrderDto.MarkDeliveredRequest request);

    // ── B3 ~ B6 조치 ─────────────────────────────────────────────────────────

    @Operation(summary = "B3 배송완료일 정정", description = """
            소비자가 수령일에 이의를 제기했을 때 배송완료일을 고칩니다. **배송완료 상태에서만** 됩니다 — 「직권 배송완료」는 없습니다.

            **권한:** ADMIN

            - `deliveredAt` — 실제 수령일시. **발송 이후 · 지금 이전**(아니면 400)
            - 구매확정 예정도 **같은 만큼** 옮깁니다(정지 뒤 재개 기산점이 있으면 그것도)
            - 출처는 「운영자 정정」, 이력 「배송완료일 정정」에 `구 → 신 · 사유`가 남습니다
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정정 후 주문 상세",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.DetailResponse.class),
                            examples = @ExampleObject(name = "정정 후", summary = "10-07 → 10-08 · 구매확정 예정도 하루 뒤로", value = ORDER_AFTER_CORRECT))),
            @ApiResponse(responseCode = "400", description = "수령일이 발송 전 · 미래 · 필드 누락 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "수령일 범위", value = ERR_DELIVERED_AT_RANGE),
                            @ExampleObject(name = "필드 누락", value = ERR_NOT_BLANK)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 하위주문 (`ORDER_GROUP_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ORDER_GROUP_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "배송완료가 아님 · 그 사이 상태 변경 (`ORDER_STATE_CHANGED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "배송완료 아님", value = ERR_NOT_DELIVERED),
                            @ExampleObject(name = "동시 변경", value = ERR_ORDER_STATE_CHANGED)
                    }))
    })
    ResponseEntity<AdminOrderDto.DetailResponse> correctDeliveredAt(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "하위주문 ID", example = "1024", required = true) Long deliveryGroupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "실제 수령일시 · 정정 사유",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.CorrectDeliveredAtRequest.class), examples = @ExampleObject(value = REQ_CORRECT_DELIVERED_AT)))
            AdminOrderDto.CorrectDeliveredAtRequest request);

    @Operation(summary = "B4 운영자 대행 송장 등록", description = """
            브랜드가 발송 기한을 넘기고 자동 알림에도 응답하지 않을 때, 운영자가 브랜드에게 받은 송장을 대신 등록합니다.

            **권한:** ADMIN

            **대행 조건** — 상세 `actions.canRegisterShipment = true`: 상품준비중 ∧ 발송 기한 경과 ∧ 자동 알림 **3회**
            (`order.act-on-behalf-notice-threshold`) 무응답. 06d 예외 관리 「대행 가능」과 같은 판정입니다. 신규(준비 시작 전)는 대상이 아닙니다.

            **입력**
            - `carrier` — 추적 연동 택배사만(목록 밖이면 400 `INVOICE_FORMAT_INVALID`)
            - `trackingNumber` — **숫자만 남겨** 저장합니다(`6400-1234-5690` → `640012345690`). 택배사 형식 검증에 걸리면 400
            - `note` — **대행 사유(필수)**. 이력 「송장 등록 · 운영자 대행 · {택배사} {송장} · {사유}」로 남습니다

            **효과** — 브랜드 송장 등록과 같은 전이(→ 배송중 · 발송 기한 판정값 = 지금). 이후 추적 · 배송완료는 일반 주문과 같습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 후 주문 상세",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.DetailResponse.class),
                            examples = @ExampleObject(name = "대행 등록 후", summary = "배송중 · 이력 actorType ADMIN", value = ORDER_AFTER_SHIPMENT))),
            @ApiResponse(responseCode = "400", description = "송장 형식 · 지원하지 않는 택배사 (`INVOICE_FORMAT_INVALID`) · 필드 누락 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "송장 형식", value = ERR_INVOICE_FORMAT_INVALID),
                            @ExampleObject(name = "대행 사유 누락", value = ERR_NOT_BLANK)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 하위주문 (`ORDER_GROUP_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ORDER_GROUP_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "대행 조건 미충족 · 다른 주문에 쓰인 송장 · 검토 중 취소 요청 · 그 사이 상태 변경",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "대행 조건 미충족", value = ERR_ACT_ON_BEHALF_NOT_ALLOWED),
                            @ExampleObject(name = "송장 중복", value = ERR_INVOICE_DUPLICATE),
                            @ExampleObject(name = "검토 중 취소 요청", value = ERR_CANCEL_REQUEST_PENDING_EXISTS),
                            @ExampleObject(name = "동시 변경", value = ERR_ORDER_STATE_CHANGED)
                    }))
    })
    ResponseEntity<AdminOrderDto.DetailResponse> registerShipment(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "하위주문 ID", example = "1024", required = true) Long deliveryGroupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "택배사 · 송장번호 · 대행 사유",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.ShipmentRequest.class), examples = @ExampleObject(value = REQ_SHIPMENT)))
            AdminOrderDto.ShipmentRequest request);

    @Operation(summary = "B4 · B5 운영자 대행 직권 취소(미발송분)", description = """
            발송 전 하위주문을 운영자가 대신 취소합니다. 하위주문 **전체**가 취소되고 재고가 돌아오며, **PG 즉시 자동 환불**(항목 + 배송비 전액)됩니다.

            **권한:** ADMIN

            **조건** — 신규 · 상품준비중 ∧ 검토 중 취소 요청 없음(상세 `actions.canCancel`), 그리고 아래 둘 중 하나.

            | `reasonCode` | 추가 조건 |
            |---|---|
            | `DEFECT`(상품 하자 · 위해성) | 없음 — 대행 조건 없이 바로 취소할 수 있다(B5) |
            | `SOLD_OUT` · `UNDELIVERABLE_AREA` · `ETC` | 대행 조건(발송 기한 경과 ∧ 자동 알림 3회 무응답) 충족(B4). 아니면 409 |

            - `consumerMessage` — **소비자에게 그대로 전달**됩니다(최대 300자). 이력에도 남습니다
            - 환불은 06c 를 거치지 않습니다(`source = SELLER_DIRECT_CANCEL` · 경로 「운영자 대행 직권 취소」 · PG 자동)
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 후 주문 상세",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.DetailResponse.class),
                            examples = @ExampleObject(name = "대행 직권 취소 후", summary = "취소 · 재고 원복 · PG 자동 환불 완료(RFD-922)", value = ORDER_AFTER_CANCEL))),
            @ApiResponse(responseCode = "400", description = "필드 누락 · 없는 사유 코드 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_NOT_BLANK))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 하위주문 (`ORDER_GROUP_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ORDER_GROUP_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "대행 조건 미충족(DEFECT 외) · 검토 중 취소 요청 · 발송됨 등 상태 변경",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "대행 조건 미충족", value = ERR_ACT_ON_BEHALF_NOT_ALLOWED),
                            @ExampleObject(name = "검토 중 취소 요청", value = ERR_CANCEL_REQUEST_PENDING_EXISTS),
                            @ExampleObject(name = "상태 변경", value = ERR_ORDER_STATE_CHANGED)
                    }))
    })
    ResponseEntity<AdminOrderDto.DetailResponse> cancel(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "하위주문 ID", example = "1024", required = true) Long deliveryGroupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "취소 사유 · 소비자 전달 문구",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.CancelCommand.class), examples = {
                            @ExampleObject(name = "대행 조건 충족 · 품절", value = REQ_CANCEL_SOLD_OUT),
                            @ExampleObject(name = "위해성 · 상품 하자(조건 없음)", value = REQ_CANCEL_DEFECT)
                    }))
            AdminOrderDto.CancelCommand request);

    @Operation(summary = "B5 운영자 사유 환불 편입", description = """
            발송 이후 하위주문의 운영자 사유 환불을 **편입만** 합니다. 돈은 환불 관리(06c)의 재확인 다이얼로그(`POST /v1/admin/refunds/{id}/execute`)에서만 나갑니다.

            **권한:** ADMIN

            **사유 `reason`**

            | 값 | 여기서 받나 |
            |---|---|
            | `RECALL` 위해성 리콜 | 받는다 |
            | `POST_CONFIRM_DEFECT` 구매확정 후 하자 | 받는다 |
            | `DISPUTE_ACCEPTED` 반려 이의 인용 | **400** — 06b `POST /v1/admin/claims/{id}/dispute-acceptance`(서버 계산 금액) |
            | `INSPECTION_UNANSWERED` 검수 무응답 | **400** — 06b `POST /v1/admin/claims/{id}/refund-tasks`(서버 계산 금액) |

            **상태** — 배송중 · 배송완료 · 구매확정만(상세 `actions.canEnqueueRefund`).
            - 발송 전(신규 · 상품준비중) · 취소 → 409 「직권 취소로 환불」
            - 반송 중 → 409 — 반송 완료 감지 때 PG 자동 환불되므로 두 길로 돈이 나가지 않게 막습니다

            **금액 `amount`** — 운영자가 입력합니다(1 이상). **결제의 취소 가능 잔액 − 아직 나가지 않은 환불**을 넘으면 409 이고, 메시지에 남은 잔액이 들어갑니다.
            결제가 없는 주문은 상한 검사를 건너뜁니다(밖에서 처리 후 06c 수동 완료 기록).

            **결과** — `groups[].refunds[]`에 `OPERATOR · PENDING` 행이 생기고, 06c 집행 대기 탭에 나타납니다. 집행 전이면 06c 에서 철회할 수 있습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "편입 후 주문 상세 — refunds[] 에 OPERATOR · PENDING 행",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.DetailResponse.class),
                            examples = @ExampleObject(name = "편입 후", summary = "RFD-919 위해성 리콜 27,200원 · 집행 대기", value = ORDER_AFTER_ENQUEUE))),
            @ApiResponse(responseCode = "400", description = "06b 전용 사유 · 필드 누락 · 금액 1 미만 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "06b 전용 사유", value = ERR_CLAIM_BOUND_REASON),
                            @ExampleObject(name = "필드 누락", value = ERR_NOT_BLANK)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 하위주문 (`ORDER_GROUP_NOT_FOUND`) · 결제 기록 없음 (`PAYMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "하위주문 없음", value = ERR_ORDER_GROUP_NOT_FOUND),
                            @ExampleObject(name = "결제 없음", value = ERR_PAYMENT_NOT_FOUND)
                    })),
            @ApiResponse(responseCode = "409", description = "취소 가능 잔액 초과 · 발송 전 · 반송 중",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "잔액 초과", value = ERR_REFUND_AMOUNT_EXCEEDED),
                            @ExampleObject(name = "발송 전", value = ERR_REFUND_BEFORE_SHIPMENT),
                            @ExampleObject(name = "반송 중", value = ERR_REFUND_RETURNING)
                    }))
    })
    ResponseEntity<AdminOrderDto.DetailResponse> enqueueRefund(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "하위주문 ID", example = "1024", required = true) Long deliveryGroupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "사유 · 금액 · 근거",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.OperatorRefundRequest.class), examples = @ExampleObject(value = REQ_OPERATOR_REFUND)))
            AdminOrderDto.OperatorRefundRequest request);

    @Operation(summary = "B6 구매확정 후 하자 · 반품 대신 열기", description = """
            구매확정 뒤 하자는 소비자가 직접 반품을 신청할 수 없습니다. 1:1 문의를 받은 운영자가 **반품을 대신 엽니다**.

            **권한:** ADMIN

            **조건**
            - 하위주문이 구매확정(또는 배송완료) ∧ **배송완료 3개월 안**(상세 `actions.canOpenDefectClaim`). 「안 날부터 30일」은 운영자가 문의로 판단합니다
            - 항목은 구매확정 상태여야 하고, 수량은 1 이상 · 남은 수량 이하. 같은 `orderProductId`를 두 번 넣으면 400
            - `reasonCode` — **브랜드 귀책만**: `DAMAGED_OR_DEFECTIVE`(파손 · 불량) · `WRONG_OR_LATE_DELIVERY`(오배송). 단순 변심 등은 400
            - `detail`(하자 내용 · 최대 1000자)과 `evidenceImageUrls`(증빙 1장 이상 — 문의에 첨부된 사진 URL) **필수**

            **연 뒤** — 일반 반품과 같습니다. 회수 송장은 소비자 · 검수는 브랜드 · 통과 시 PG 자동 환불.
            파트너 목록에는 「운영자 개설 · 구매확정 후 하자 · 증빙 N장」(`openedByOperator = true`)으로 보입니다.
            응답의 `claimIds`로 06b 상세(`GET /v1/admin/claims/{claimId}`)에 들어갑니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "개설 — 요청(박스) id · 클레임 id 목록(항목마다 1건)",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.DefectClaimResponse.class),
                            examples = @ExampleObject(value = DEFECT_CLAIM_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "브랜드 귀책 사유가 아님 · 같은 상품 중복 · 하자 내용 · 증빙 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "사유", value = ERR_DEFECT_REASON),
                            @ExampleObject(name = "같은 상품 중복", value = ERR_DEFECT_DUPLICATE_ITEM),
                            @ExampleObject(name = "내용 · 증빙 누락", value = ERR_CLAIM_REASON_DETAIL_REQUIRED)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 하위주문 · 이 하위주문의 상품이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "하위주문 없음", value = ERR_ORDER_GROUP_NOT_FOUND),
                            @ExampleObject(name = "상품 없음", value = ERR_ORDER_PRODUCT_NOT_FOUND)
                    })),
            @ApiResponse(responseCode = "409", description = "구매확정 · 배송완료가 아님 · 3개월 경과 · 수량 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "대상 아님", value = ERR_CLAIM_NOT_ELIGIBLE),
                            @ExampleObject(name = "3개월 경과", value = ERR_CLAIM_NOT_ELIGIBLE_3M),
                            @ExampleObject(name = "수량 초과", value = ERR_CLAIM_QUANTITY_EXCEEDED)
                    }))
    })
    ResponseEntity<AdminOrderDto.DefectClaimResponse> openDefectClaim(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "하위주문 ID", example = "1024", required = true) Long deliveryGroupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "항목 · 사유 · 하자 내용 · 증빙",
                    content = @Content(schema = @Schema(implementation = AdminOrderDto.DefectClaimRequest.class), examples = @ExampleObject(value = REQ_DEFECT_CLAIM)))
            AdminOrderDto.DefectClaimRequest request);
}
