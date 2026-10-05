package showroomz.api.seller.order.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.order.dto.BatchActionResponse;
import showroomz.api.seller.order.dto.CancelRequestRejectRequest;
import showroomz.api.seller.order.dto.PrepareStartRequest;
import showroomz.api.seller.order.dto.PurchaseOrderRequest;
import showroomz.api.seller.order.dto.PurchaseOrderTemplateDto;
import showroomz.api.seller.order.dto.SellerDirectCancelRequest;
import showroomz.api.seller.order.dto.SellerOrderDetailResponse;
import showroomz.api.seller.order.dto.SellerOrderListItem;
import showroomz.api.seller.order.dto.SellerOrderSummaryResponse;
import showroomz.api.seller.order.dto.ShipmentParseResponse;
import showroomz.api.seller.order.dto.ShipmentRegisterRequest;
import showroomz.api.seller.order.dto.ShipmentUpdateRequest;
import showroomz.domain.order.type.OrderDateBasis;
import showroomz.domain.order.type.OrderSearchType;
import showroomz.domain.order.type.OrderSortType;
import showroomz.domain.order.type.OrderTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDate;

@Tag(name = "Seller - Order", description = "파트너센터 주문 관리 API — 조회용 대시보드가 아니라 매일 여는 작업 큐다(§34-0).")
public interface SellerOrderControllerDocs {

    String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // ── 조회 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "주문 목록",
            description = """
                    내 브랜드의 하위주문(공구·배송 단위)을 탭·기간·검색으로 조회한다. 목록 기본 단위는 **하위주문**이고
                    타 브랜드 몫은 보이지 않는다(§34-0 단위 3층).

                    **권한:** SELLER

                    **파라미터**
                    - `tab` — 생략 시 `ALL`. 작업 큐 3(`NEW` 신규 · `PREPARING` 상품준비중 · `CANCEL_REQUESTED` 취소 요청) ·
                      조회 6(`ALL` · `SHIPPING` · `RETURNING` · `DELIVERED` · `CONFIRMED` · `CANCELLED`).
                      취소 요청은 이행 상태가 아니라 **오버레이**다 — 항목 하나만 요청돼도 하위주문 전체가 취소 요청 탭으로 가고,
                      신규·상품준비중 탭에서는 빠진다. 처리 후에는 원래 탭으로 복귀한다(§34-8).
                    - `dateBasis` — 조회 기준 5종: `PAID`(결제일 · 기본) · `PREPARE_STARTED`(발주확인일) · `SHIPPED`(발송처리일) ·
                      `DELIVERED`(배송완료일) · `CONFIRMED`(구매확정일)
                    - `from` · `to` — `yyyy-MM-dd` · 양끝 포함. `to` 생략 시 오늘, `from` 생략 시 `to`에서 탭 기본 기간
                      (작업 큐 7일 · 조회 30일)을 뺀 날. **최대 1년** — 초과는 400 · 시작일이 종료일보다 늦으면 400
                    - `searchType` + `keyword` — `ORDER_NUMBER` · `RECIPIENT_NAME` · `TRACKING_NUMBER` · `PRODUCT_NAME`
                    - `sort` — 생략 시 작업 큐는 `OLDEST_FIRST`(오래된순), 조회 탭은 `LATEST_FIRST`. `SHIP_DUE_ASC`는 발송기한 열 정렬
                    - `page`(1부터 · 1 미만은 첫 페이지) · `size`(기본 20 · **1~100** — 밖이면 400)

                    **행 필드 해석**
                    - `statusLabel` · `statusTone`(NEUTRAL/INFO/WARNING/SUCCESS/DANGER)은 서버 배지 값이다 — FE가 매핑하지 않는다.
                    - `overlays` — 이행 상태와 별 축: `cancelRequested`(취소 요청 검토 중) · `trackingAlert`(집화 확인 필요/추적 정지) ·
                      `shipOverdue`(발송기한 경과 · 브랜드 귀책) · `openClaimCount`(진행 중인 반품·교환 건수). 한 행에 여럿이 함께 뜰 수 있다.
                    - `confirmRemainingDays`는 거절되지 않은 진행 중 반품·교환이 있으면 `null`이다 — 그 건이 끝날 때까지 구매확정이 선다.
                    - `recipientName`은 전체 표기(rev.6). 연락처·주소는 목록에 없다 — 상세·발주서에만(§34-11).
                    - `settlementLabel`은 정산 모듈 전이라 `null`이다 — 0이 아니다.
                    - `items[]`는 행 확장(▸) 미리보기다 — 상품·옵션 · 수량 · 공구가 · 금액 · 항목 상태.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 조건에 맞는 주문이 없으면 `content: []`(요약 바 숫자는 유지된다)"),
            @ApiResponse(responseCode = "400", description = "ORDER_SEARCH_RANGE_EXCEEDED — 기간 1년 초과 · "
                    + "INVALID_INPUT — 정의되지 않은 enum 값 · 날짜 형식 오류 · 시작일 > 종료일 · `size` 1~100 밖",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "기간 1년 초과", value = """
                                    {
                                      "code": "ORDER_SEARCH_RANGE_EXCEEDED",
                                      "message": "조회 기간은 최대 1년까지 설정할 수 있습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND — 판매자 또는 브랜드(마켓)가 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<SellerOrderListItem>> getOrders(
            @Parameter(description = "탭 — ALL(기본) · NEW · PREPARING · CANCEL_REQUESTED · SHIPPING · RETURNING · DELIVERED · CONFIRMED · CANCELLED",
                    example = "NEW")
            @RequestParam(required = false) OrderTab tab,
            @Parameter(description = "조회 기준일 — PAID(기본) · PREPARE_STARTED · SHIPPED · DELIVERED · CONFIRMED", example = "PAID")
            @RequestParam(required = false) OrderDateBasis dateBasis,
            @Parameter(description = "조회 시작일(yyyy-MM-dd · 포함) — 생략 시 탭 기본 기간", example = "2026-09-26")
            @RequestParam(required = false) LocalDate from,
            @Parameter(description = "조회 종료일(yyyy-MM-dd · 포함) — 생략 시 오늘", example = "2026-10-03")
            @RequestParam(required = false) LocalDate to,
            @Parameter(description = "검색 대상 — ORDER_NUMBER · RECIPIENT_NAME · TRACKING_NUMBER · PRODUCT_NAME", example = "RECIPIENT_NAME")
            @RequestParam(required = false) OrderSearchType searchType,
            @Parameter(description = "검색어 — `searchType`과 함께 보낸다", example = "김민지")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "정렬 — OLDEST_FIRST(작업 큐 기본) · LATEST_FIRST(조회 탭 기본) · SHIP_DUE_ASC(발송기한 빠른순)",
                    example = "OLDEST_FIRST")
            @RequestParam(required = false) OrderSortType sort,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "처리 대기 요약 + 탭 카운트",
            description = """
                    요약 바 5칸과 탭 카운트 9종을 **한 응답**으로 내린다 — 동시 갱신 요구(§34-2)가 같은 트랜잭션의 같은
                    데이터를 읽는 것으로 충족된다.

                    **권한:** SELLER · **파라미터를 받지 않는다** — 목록의 검색·기간과 무관한 전체 기준 건수다.

                    - `tabCounts` — 탭 코드 9종이 **항상 모두** 들어 있다(0건도 0으로).
                      `NEW` · `PREPARING`은 검토 중 취소 요청이 걸린 건을 뺀 수이고, 그 건들은 `CANCEL_REQUESTED`로 센다.
                    - `actionBar.prepareStart` = `tabCounts.NEW`, `actionBar.invoiceRegister` = `tabCounts.PREPARING`
                    - `deliveryIssue` = 집화 확인 필요 + 추적 정지 + 반송중 **합산** — 구분은 목록이 한다.
                    - `incomingCheck` · `reshipExchange`는 반품·교환 관리(미제작) 몫이라 **`null`** 이다. 0으로 그리지 말 것.
                    - 「배송완료 처리」 칸은 없다 — 자동 전환이라 상시 대기 항목이 아니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerOrderSummaryResponse> getSummary();

    @Operation(
            summary = "주문 상세",
            description = """
                    상세 모달(§34-9) — 주문 정보 · **마스킹 해제 배송지**(소비자 입력 · 브랜드는 수정 불가) · 항목 표 ·
                    금액 요약 · 우 레일 핵심 시각(배송완료는 출처 병기) · 검토 중 취소 요청 블록(C11) · 처리 이력.

                    **권한:** SELLER (내 브랜드 하위주문만)

                    `actions`가 버튼 노출 규칙의 정본이다 — 그 탭에서 쓸 수 없는 버튼은 노출하지 않는다(§34-3).
                    배송완료 처리·요청 액션은 **아예 없다** — 돈의 시점을 바꾸는 전이는 자동 아니면 운영자다(§34-0).

                    | 버튼 | 노출 조건 |
                    |---|---|
                    | `canPrepareStart` 준비 시작 | `NEW` ∧ 검토 중 취소 요청 없음 |
                    | `canRegisterInvoice` 송장 등록 | `PREPARING` ∧ 검토 중 취소 요청 없음 |
                    | `canUpdateInvoice` 송장 수정 | `SHIPPING` |
                    | `canCancelDirectly` 직권 취소 | `NEW` · `PREPARING` ∧ 검토 중 취소 요청 없음 |
                    | `canDecideCancelRequest` 승인·거부 | 검토 중 취소 요청 있음(`cancelRequest` 블록이 채워진다) |

                    - `timeline` — 아직 일어나지 않은 시각은 `null`이다.
                    - `history` — 최신순. `detail`에 송장(「CJ대한통운 640012345678」) · 수정 전후(「구 → 신」) · 취소 사유 등이 남는다.
                    - 실행 API(송장 수정 · 취소 요청 승인/거부)도 성공 시 이 응답을 그대로 돌려준다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "ORDER_GROUP_NOT_FOUND — 없는 주문. 타 브랜드 주문도 404(존재 비노출) · "
                    + "SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "없는 주문", value = """
                                    {
                                      "code": "ORDER_GROUP_NOT_FOUND",
                                      "message": "존재하지 않는 주문입니다."
                                    }
                                    """)))
    })
    ResponseEntity<SellerOrderDetailResponse> getOrder(
            @Parameter(description = "하위주문 id(목록의 `deliveryGroupId`)", example = "1024") @PathVariable Long deliveryGroupId);

    // ── 준비 시작 · 발주서 ──────────────────────────────────────────────────

    @Operation(
            summary = "준비 시작",
            description = """
                    신규 → 상품준비중(E8). 효과는 하나 — **소비자 단순 취소권 종료**(약관 제17조② · 제18조①).
                    이후 소비자는 취소 요청 → 브랜드 승인·거부 경로만 남는다.

                    **권한:** SELLER · **버튼 노출:** `actions.canPrepareStart`

                    **되돌리기는 없다**(§34-4) — 되돌림을 열면 소비자 취소권이 열렸다 닫혔다 한다. 문제는 직권 취소로 푼다.

                    다건 부분 성공 — 취소 요청이 걸렸거나 상태가 변한 행은 `skipped`에 사유와 함께 빠지고 나머지는 진행된다.
                    - `CANCEL_REQUEST_PENDING_EXISTS` — 검토 중 취소 요청이 있다(먼저 승인·거부)
                    - `ORDER_STATE_CHANGED` — 이미 신규가 아니다(그 사이 준비 시작·취소됨)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 결과 — `succeeded` + `skipped[]`",
                    content = @Content(schema = @Schema(implementation = BatchActionResponse.class),
                            examples = @ExampleObject(name = "부분 성공", value = """
                                    {
                                      "succeeded": 2,
                                      "skipped": [
                                        {
                                          "deliveryGroupId": 1031,
                                          "code": "CANCEL_REQUEST_PENDING_EXISTS",
                                          "message": "검토 중인 취소 요청이 있습니다. 요청을 먼저 처리해 주세요."
                                        }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `deliveryGroupIds` 비었음 · 500건 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BatchActionResponse> prepareStart(@Valid @RequestBody PrepareStartRequest request);

    @Operation(
            summary = "발주서 다운로드",
            description = """
                    xlsx 를 내려받는다(E1 · §34-4). 고정 양식 없음 — `columns` 선택 순서 = 엑셀 좌→우 열 순서.
                    행 단위는 **주문 항목**(SKU)이고 취소 항목은 빠진다.

                    **권한:** SELLER

                    **대상**
                    - `deliveryGroupIds`를 보내면 **선택 건**. 비우면 **현재 탭 전체** — 목록과 같은 필터(`tab` · `dateBasis` ·
                      `from` · `to` · `searchType` · `keyword`)를 함께 보낸다. 이때 **`tab` 생략 시 `NEW`** 다(목록 API 기본값
                      `ALL`과 다르다). 발주서는 신규·상품준비중 탭의 액션이라 **`NEW` · `PREPARING` 탭만** 대상이 있다.
                    - 어느 경로든 **결제된 신규·상품준비중만** 싣는다 — 선택 건에 배송중·배송완료·취소·결제 전이 섞이면 조용히 빠진다
                      (개인정보 재반출 방지 · §34-11).
                    - 검토 중 취소 요청이 걸린 하위주문은 **자동으로 빠진다**(작업 큐 밖). 다 빠져서 남는 게 없으면 400.
                    - 소비자 취소가 결제 쪽에서 처리 중이라 **준비 시작이 되지 않은 신규 주문도 빠진다** — 발주서에 실린 주문은
                      전부 상품준비중이다.
                    - 대상 상한 2,000건 — 넘으면 잘라 내려보내지 않고 400 `PURCHASE_ORDER_TOO_MANY`(기간·검색으로 나눠 받는다).

                    **발주서 = 준비 시작**
                    - 발주서를 내려받으면 대상 중 **신규 주문이 항상 준비 시작**된다 — 발주서를 뽑는 것은 보내겠다는 결정이다.
                      이력 `detail`에 「발주서 다운로드」가 남는다. **소비자 단순 취소권과 배송지 변경이 이 순간 닫힌다**
                      (되돌리기 없음) — 그래서 발주서에 실린 주소는 그 뒤 바뀌지 않는다.
                    - 「다운로드만」은 없다. `startPreparation` 은 보내지 않는다 — `false` 를 보내면 400 `INVALID_INPUT`.
                      견적·재고 확인은 목록의 행 확장(상품 · 옵션 · 수량)으로 본다.

                    **옵션**
                    - `saveAsDefault` — 이 구성을 기본값으로 저장(기본정보 관리에서 수정)

                    **응답** — 파일명 `발주서_yyyyMMdd_HHmmss.xlsx`(Content-Disposition `filename*=UTF-8''…`).
                    다운로드는 **반출 이력으로 기록**된다 — 엑셀로 개인정보가 나간다(§34-11)
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PurchaseOrderRequest.class),
                    examples = {
                            @ExampleObject(name = "선택 건", value = """
                                    {
                                      "deliveryGroupIds": [1024, 1025],
                                      "columns": ["ORDER_NUMBER", "RECIPIENT", "PHONE", "ZIP_CODE", "ADDRESS", "PRODUCT_NAME", "OPTION", "QUANTITY"]
                                    }
                                    """),
                            @ExampleObject(name = "현재 탭 전체 · 구성 기본값 저장", value = """
                                    {
                                      "deliveryGroupIds": [],
                                      "columns": ["ORDER_NUMBER", "RECIPIENT", "PHONE", "ADDRESS", "PRODUCT_NAME", "OPTION", "QUANTITY", "DELIVERY_MEMO"],
                                      "saveAsDefault": true,
                                      "tab": "NEW",
                                      "dateBasis": "PAID",
                                      "from": "2026-09-26",
                                      "to": "2026-10-03"
                                    }
                                    """)
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx 바이너리(Content-Disposition attachment)",
                    content = @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "PURCHASE_ORDER_EMPTY — 내려받을 대상 없음(취소 요청 건 · 준비 시작이 안 된 건 제외 후 0건 포함) · "
                    + "PURCHASE_ORDER_TOO_MANY — 대상 2,000건 초과 · "
                    + "ORDER_SEARCH_RANGE_EXCEEDED — 필터 기간 1년 초과 · INVALID_INPUT — `columns` 비었음 · 정의되지 않은 enum 값 · `startPreparation: false` · "
                    + "시작일 > 종료일",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "대상 없음", value = """
                                    {
                                      "code": "PURCHASE_ORDER_EMPTY",
                                      "message": "발주서로 내려받을 주문이 없습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> downloadPurchaseOrder(@Valid @RequestBody PurchaseOrderRequest request);

    @Operation(
            summary = "발주서 컬럼 기본값 조회",
            description = """
                    저장 구성(없으면 기본 8종)과 선택 가능한 전체 컬럼 13종. 기본정보 관리 화면이 같은 API를 쓴다.

                    **권한:** SELLER

                    | 코드 | 헤더 | 기본 8종 |
                    |---|---|---|
                    | `ORDER_NUMBER` | 주문번호 | ✔ |
                    | `RECIPIENT` | 수취인 | ✔ |
                    | `PHONE` | 연락처 | ✔ |
                    | `ZIP_CODE` | 우편번호 | ✔ |
                    | `ADDRESS` | 주소(주소 + 상세 주소) | ✔ |
                    | `PRODUCT_NAME` | 상품명 | ✔ |
                    | `OPTION` | 옵션 | ✔ |
                    | `QUANTITY` | 수량 | ✔ |
                    | `DELIVERY_MEMO` | 배송 요청사항 | |
                    | `GROUP_BUY_NAME` | 공구명 | |
                    | `ORDERED_AT` | 주문일시 | |
                    | `PAID_AMOUNT` | 결제금액(항목 금액) | |
                    | `SUB_ORDER_NUMBER` | 하위주문번호 | |
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PurchaseOrderTemplateDto.Response> getPurchaseOrderTemplate();

    @Operation(
            summary = "발주서 컬럼 기본값 저장",
            description = """
                    선택 순서가 곧 열 순서다 — 순서까지 저장된다. 중복 코드는 첫 위치만 남긴다.
                    발주서 다운로드의 `saveAsDefault: true`와 같은 저장소를 쓴다.

                    **권한:** SELLER
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 후 구성(조회 API와 같은 응답)"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `columns` 비었음 · 정의되지 않은 컬럼 코드",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PurchaseOrderTemplateDto.Response> updatePurchaseOrderTemplate(
            @Valid @RequestBody PurchaseOrderTemplateDto.UpdateRequest request);

    // ── 송장 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "송장 등록 확정",
            description = """
                    상품준비중 → 배송중(§34-5). 셀 입력은 클라이언트 임시값이고 **이 호출이 유일한 확정 지점이다** —
                    배송중 전환은 소비자에게 송장이 전달되는 사건이다. `shipped_at`(발송기한 판정값)이 여기서 박힌다.

                    **권한:** SELLER · **버튼 노출:** `actions.canRegisterInvoice`

                    서버 검사(③): **전역 중복**(겹치는 주문번호를 지목한다) · **형식**(연동 업체 판정 — 자릿수인지
                    체크디지트인지 구분하지 않는다) · **주문 상태 재확인**(그 사이 취소 가능). 하드 차단은 그 행만 제외하고
                    나머지는 등록한다. 빈 값 행은 에러 없이 조용히 제외된다(`succeeded`·`skipped` 어디에도 세지 않는다).

                    송장번호는 숫자만 남기고 판정한다(`6400-1234-5678` → `640012345678`).
                    택배사는 11종 enum 만 — 자유 입력·「미지원 택배사」 없음:
                    `CJ` CJ대한통운 · `LOTTE` 롯데택배 · `HANJIN` 한진택배 · `EPOST` 우체국택배 · `KYUNGDONG` 경동택배 ·
                    `DAESIN` 대신택배 · `LOGEN` 로젠택배 · `HAPDONG` 합동택배 · `COUPANG` 쿠팡택배 · `WOORI` 우리택배 · `CU` CU편의점택배

                    **`skipped[].code`**
                    - `INVOICE_DUPLICATE` — 같은 요청 안 중복, 또는 다른 진행 중 주문에 이미 등록(`message`에 그 주문번호 —
                      **내 브랜드 주문일 때만**. 다른 브랜드 주문이면 「다른 주문에 이미 등록된 번호입니다.」)
                    - `INVOICE_FORMAT_INVALID` — 택배사 규칙에 맞지 않는 번호
                    - `CANCEL_REQUEST_PENDING_EXISTS` — 검토 중 취소 요청이 있다
                    - `ORDER_STATE_CHANGED` — 상품준비중이 아니다(신규 · 이미 배송중 · 취소됨)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "부분 성공 결과 — 결과 배너(몇 건이 어디로)는 이 응답으로 그린다",
                    content = @Content(schema = @Schema(implementation = BatchActionResponse.class),
                            examples = @ExampleObject(name = "부분 성공", value = """
                                    {
                                      "succeeded": 1,
                                      "skipped": [
                                        {
                                          "deliveryGroupId": 1025,
                                          "code": "INVOICE_DUPLICATE",
                                          "message": "20260930-000087에 이미 등록된 번호입니다."
                                        },
                                        {
                                          "deliveryGroupId": 1031,
                                          "code": "INVOICE_FORMAT_INVALID",
                                          "message": "송장번호 형식이 올바르지 않습니다. 다시 확인해 주세요."
                                        }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `rows` 비었음 · 500행 초과 · 필수 값 누락 · 11종 외 택배사",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BatchActionResponse> registerShipments(@Valid @RequestBody ShipmentRegisterRequest request);

    @Operation(
            summary = "송장 엑셀 업로드 검증",
            description = """
                    xlsx(최대 1,000행 · 필수 = 주문번호 + 송장번호)를 파싱·분류만 한다(E3) — **상태를 바꾸지 않는다.**
                    「N건 목록에 채우기」는 FE 가 이 응답으로 셀만 채우고, 확정은 수기 입력과 같은 `POST /shipments`다.

                    **권한:** SELLER

                    **양식** — 첫 시트 · 1행 헤더 · A열 주문번호 · B열 택배사 · C열 송장번호(`GET /shipments/template`).
                    - 주문번호 칸에는 **하위주문번호**(`20261003-000123-01`)도 넣을 수 있다. 한 주문에 내 하위주문이 여럿이면
                      주문번호로는 고를 수 없어 `AMBIGUOUS_ORDER`다.
                    - 택배사 칸은 한글명(「CJ대한통운」) · 코드(`CJ`) 모두 인식한다. 비워 두면 `carrier: null`로 통과하고
                      FE 셀에서 고른 뒤 확정한다.
                    - 세 칸이 모두 빈 행은 건너뛴다(`totalRows`에 세지 않는다).

                    **`rows[].errorCode`**
                    | 코드 | 사유 |
                    |---|---|
                    | `ORDER_NOT_FOUND` | 주문번호 없음 · 내 브랜드 주문이 아님 |
                    | `AMBIGUOUS_ORDER` | 하위주문 여러 건 — 하위주문번호로 입력 안내 |
                    | `TRACKING_REQUIRED` | 송장번호 없음 |
                    | `CARRIER_INVALID` | 11종 외 택배사 |
                    | `NEW_NOT_ALLOWED` | 신규(준비 대기) — 준비 시작 전이라 등록 불가(rev.7) |
                    | `ALREADY_SHIPPED` | 이미 배송중 이후 단계 |
                    | `STATE_INVALID` | 취소 등 등록할 수 없는 상태 |
                    | `CANCEL_REQUEST_PENDING` | 취소 요청 검토 중 |
                    | `INVOICE_DUPLICATE` | 다른 진행 중 주문에 등록된 번호(내 브랜드 주문이면 주문번호 병기) · 파일 안 중복 |
                    | `ORDER_DUPLICATE_IN_FILE` | 같은 하위주문이 파일에 두 번 — 첫 정상 행만 남는다 |
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "행별 분류 결과 — 부분 성공 허용",
                    content = @Content(schema = @Schema(implementation = ShipmentParseResponse.class),
                            examples = @ExampleObject(name = "정상 1 · 오류 2", value = """
                                    {
                                      "totalRows": 3,
                                      "validRows": 1,
                                      "rows": [
                                        {
                                          "rowNumber": 2,
                                          "orderNumber": "20261003-000123",
                                          "subOrderNumber": "20261003-000123-01",
                                          "deliveryGroupId": 1024,
                                          "carrier": "CJ",
                                          "trackingNumber": "640012345678",
                                          "valid": true,
                                          "errorCode": null,
                                          "message": null
                                        },
                                        {
                                          "rowNumber": 3,
                                          "orderNumber": "20261003-000131",
                                          "subOrderNumber": null,
                                          "deliveryGroupId": null,
                                          "carrier": "HANJIN",
                                          "trackingNumber": "512345678901",
                                          "valid": false,
                                          "errorCode": "NEW_NOT_ALLOWED",
                                          "message": "신규(준비 대기) 주문 · 준비 시작 전이라 송장을 등록할 수 없습니다."
                                        },
                                        {
                                          "rowNumber": 4,
                                          "orderNumber": "20261002-000045",
                                          "subOrderNumber": null,
                                          "deliveryGroupId": null,
                                          "carrier": "CJ",
                                          "trackingNumber": "640099998888",
                                          "valid": false,
                                          "errorCode": "INVOICE_DUPLICATE",
                                          "message": "20260930-000087에 이미 등록된 번호입니다."
                                        }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "SHIPMENT_FILE_INVALID — xlsx 가 아니거나 손상 · "
                    + "SHIPMENT_FILE_TOO_MANY_ROWS — 1,000행 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "1,000행 초과", value = """
                                    {
                                      "code": "SHIPMENT_FILE_TOO_MANY_ROWS",
                                      "message": "엑셀 업로드는 최대 1,000행까지 가능합니다."
                                    }
                                    """)))
    })
    ResponseEntity<ShipmentParseResponse> parseShipments(
            @Parameter(description = "송장 업로드 xlsx — 업로드 양식(주문번호 · 택배사 · 송장번호) 기준", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("file") MultipartFile file);

    @Operation(
            summary = "송장 업로드 양식",
            description = """
                    주문번호 · 택배사 · 송장번호 3열 xlsx(헤더만 있는 빈 양식). 파일명 `송장_업로드_양식.xlsx`.

                    **권한:** SELLER
                    """)
    @ApiResponse(responseCode = "200", description = "xlsx 바이너리(Content-Disposition attachment)",
            content = @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary")))
    ResponseEntity<byte[]> shipmentTemplate();

    @Operation(
            summary = "송장 수정",
            description = """
                    배송중에서만 · 반송중 불가 · **배송완료 전까지**(§34-6). 형식은 맞지만 다른 주문의 송장을 붙여넣은
                    경우를 고치는 경로다. 수정 이력(구 → 신)이 기록되고, `shipped_at`은 유지된다 — 수정으로 발송기한
                    위반이 세탁되면 안 된다. 수정 후 감시 배치가 새 송장 기준으로 다시 판정한다.

                    **권한:** SELLER · **버튼 노출:** `actions.canUpdateInvoice`

                    검사 순서: 형식(400) → 전역 중복(409) → 상태(409). 이력 `detail` 예: 「CJ대한통운 640012345678 → 한진택배 512345678901」
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 후 상세"),
            @ApiResponse(responseCode = "400", description = "INVOICE_FORMAT_INVALID — 송장번호가 비었거나 택배사 규칙에 맞지 않음 · "
                    + "INVALID_INPUT — 필수 값 누락 · 11종 외 택배사",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ORDER_GROUP_NOT_FOUND — 없는 주문 · 타 브랜드 주문",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ORDER_STATE_CHANGED — 배송중이 아님(반송중 · 배송완료 등) · "
                    + "INVOICE_DUPLICATE — 다른 진행 중 주문에 이미 등록된 번호",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "상태 변경", value = """
                                    {
                                      "code": "ORDER_STATE_CHANGED",
                                      "message": "주문 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요."
                                    }
                                    """)))
    })
    ResponseEntity<SellerOrderDetailResponse> updateShipment(
            @Parameter(description = "하위주문 id", example = "1024") @PathVariable Long deliveryGroupId,
            @Valid @RequestBody ShipmentUpdateRequest request);

    // ── 취소 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "직권 취소(판매 취소)",
            description = """
                    품절·하자 대응(E5 · §34-8). 하위주문 전 항목이 취소되고 재고가 돌아간다. **되돌릴 수 없다.**
                    환불은 운영자가 집행한다(환불 큐 적재 · 배송비 포함 전액) · 취소율에 반영된다.

                    **권한:** SELLER · **버튼 노출:** `actions.canCancelDirectly`

                    `consumerMessage`는 사유와 함께 **소비자에게 그대로 전달**된다(약관 제18조②).

                    허용 단계는 신규·상품준비중이다(§34-13 #1 확정 전 시안 기준 집행 — 취소 당시 상태가 기록된다).

                    **`skipped[].code`**
                    - `CANCEL_REQUEST_PENDING_EXISTS` — 검토 중 취소 요청이 걸린 건(선처리 요구)
                    - `ORDER_STATE_CHANGED` — 신규·상품준비중이 아니다(이미 배송중 · 취소됨)
                    - `ORDER_GROUP_NOT_FOUND` — 없는 id · 타 브랜드 주문
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "부분 성공 결과",
                    content = @Content(schema = @Schema(implementation = BatchActionResponse.class),
                            examples = @ExampleObject(name = "부분 성공", value = """
                                    {
                                      "succeeded": 1,
                                      "skipped": [
                                        {
                                          "deliveryGroupId": 1040,
                                          "code": "ORDER_STATE_CHANGED",
                                          "message": "주문 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요."
                                        }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `deliveryGroupIds` 비었음 · 200건 초과 · "
                    + "`reasonCode` 누락 · `consumerMessage` 공백 · 300자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BatchActionResponse> directCancel(@Valid @RequestBody SellerDirectCancelRequest request);

    @Operation(
            summary = "취소 요청 승인",
            description = """
                    E6 — **요청 항목만 취소 · 남은 항목은 발송**한다. 환불은 운영자 집행(환불 큐) · 취소율 미반영. **바디 없음.**

                    **권한:** SELLER · **버튼 노출:** `actions.canDecideCancelRequest`

                    전 항목 승인일 때만 하위주문이 취소 탭으로 가고 배송비까지 전액 환불 예정이 된다. 부분 취소의
                    배송비는 재계산하지 않는다(§34-8).

                    처리 후 하위주문은 원래 탭(신규/상품준비중)으로 복귀한다 — 이행 상태는 바뀐 적이 없다.
                    **일괄 승인은 없다** — 건별 근거가 다른 판단을 묶으면 검토가 형식이 된다.

                    이력 `detail` 예: 「요청 1건 취소 · 환불 예정 27000원」
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 후 상세"),
            @ApiResponse(responseCode = "404", description = "ORDER_GROUP_NOT_FOUND — 없는 취소 요청 · 타 브랜드 주문의 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CANCEL_REQUEST_ALREADY_DECIDED — 이미 승인·거부된 요청 · "
                    + "ORDER_STATE_CHANGED — 하위주문이 신규·상품준비중이 아님(발송 뒤 취소는 반품 경로)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "이미 처리됨", value = """
                                    {
                                      "code": "CANCEL_REQUEST_ALREADY_DECIDED",
                                      "message": "이미 처리된 취소 요청입니다."
                                    }
                                    """)))
    })
    ResponseEntity<SellerOrderDetailResponse> approveCancelRequest(
            @Parameter(description = "취소 요청 id(목록·상세의 `cancelRequest.cancelRequestId`)", example = "77")
            @PathVariable Long cancelRequestId);

    @Operation(
            summary = "취소 요청 거부",
            description = """
                    E7 — 사유 필수(소비자에게 그대로 전달 · 약관 제18조①). 전 항목 배송이 진행되고 소비자는
                    수령 후 반품으로만 돌릴 수 있다(반품비 발생).

                    **권한:** SELLER · **버튼 노출:** `actions.canDecideCancelRequest`

                    처리 후 하위주문은 원래 탭(신규/상품준비중)으로 복귀한다. 이력 `detail`에 거부 사유가 그대로 남는다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 후 상세"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `reason` 공백 · 500자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ORDER_GROUP_NOT_FOUND — 없는 취소 요청 · 타 브랜드 주문의 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CANCEL_REQUEST_ALREADY_DECIDED — 이미 처리된 요청 · "
                    + "ORDER_STATE_CHANGED — 하위주문이 신규·상품준비중이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerOrderDetailResponse> rejectCancelRequest(
            @Parameter(description = "취소 요청 id", example = "77") @PathVariable Long cancelRequestId,
            @Valid @RequestBody CancelRequestRejectRequest request);
}
