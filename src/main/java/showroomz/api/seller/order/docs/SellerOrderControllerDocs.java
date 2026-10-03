package showroomz.api.seller.order.docs;

import io.swagger.v3.oas.annotations.Operation;
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
                    - `from` · `to` — 생략 시 탭 기본 기간(작업 큐 7일 · 조회 1개월). **최대 1년** — 초과는 400
                    - `searchType` + `keyword` — `ORDER_NUMBER` · `RECIPIENT_NAME` · `TRACKING_NUMBER` · `PRODUCT_NAME`
                    - `sort` — 생략 시 작업 큐는 `OLDEST_FIRST`(오래된순), 조회 탭은 `LATEST_FIRST`. `SHIP_DUE_ASC`는 발송기한 열 정렬

                    **행 필드 해석**
                    - `statusLabel` · `statusTone`(NEUTRAL/INFO/WARNING/SUCCESS/DANGER)은 서버 배지 값이다 — FE가 매핑하지 않는다.
                    - `overlays` — 이행 상태와 별 축: `cancelRequested`(취소 요청 검토 중) · `trackingAlert`(집화 확인 필요/추적 정지) ·
                      `shipOverdue`(발송기한 경과 · 브랜드 귀책). 한 행에 여럿이 함께 뜰 수 있다.
                    - `recipientName`은 전체 표기(rev.6). 연락처·주소는 목록에 없다 — 상세·발주서에만(§34-11).
                    - `settlementLabel`은 정산 모듈 전이라 `null`이다 — 0이 아니다.
                    - `items[]`는 행 확장(▸) 미리보기다 — 상품·옵션 · 수량 · 공구가 · 금액 · 항목 상태.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 조건에 맞는 주문이 없으면 `content: []`(요약 바 숫자는 유지된다)"),
            @ApiResponse(responseCode = "400", description = "기간 1년 초과(`ORDER_SEARCH_RANGE_EXCEEDED`) 또는 정의되지 않은 enum 값",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<SellerOrderListItem>> getOrders(
            @RequestParam(required = false) OrderTab tab,
            @RequestParam(required = false) OrderDateBasis dateBasis,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) OrderSearchType searchType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderSortType sort,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "처리 대기 요약 + 탭 카운트",
            description = """
                    요약 바 5칸과 탭 카운트 9종을 **한 응답**으로 내린다 — 동시 갱신 요구(§34-2)가 같은 트랜잭션의 같은
                    데이터를 읽는 것으로 충족된다.

                    - `deliveryIssue` = 집화 확인 필요 + 추적 정지 + 반송중 **합산** — 구분은 목록이 한다.
                    - `incomingCheck` · `reshipExchange`는 반품·교환 관리(미제작) 몫이라 **`null`** 이다. 0으로 그리지 말 것.
                    - 「배송완료 처리」 칸은 없다 — 자동 전환이라 상시 대기 항목이 아니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<SellerOrderSummaryResponse> getSummary();

    @Operation(
            summary = "주문 상세",
            description = """
                    상세 모달(§34-9) — 주문 정보 · **마스킹 해제 배송지**(소비자 입력 · 브랜드는 수정 불가) · 항목 표 ·
                    금액 요약 · 우 레일 핵심 시각(배송완료는 출처 병기) · 검토 중 취소 요청 블록(C11) · 처리 이력.

                    `actions`가 버튼 노출 규칙의 정본이다 — 그 탭에서 쓸 수 없는 버튼은 노출하지 않는다(§34-3).
                    배송완료 처리·요청 액션은 **아예 없다** — 돈의 시점을 바꾸는 전이는 자동 아니면 운영자다(§34-0).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "없는 주문 — 타 브랜드 주문도 404(존재 비노출)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerOrderDetailResponse> getOrder(@PathVariable Long deliveryGroupId);

    // ── 준비 시작 · 발주서 ──────────────────────────────────────────────────

    @Operation(
            summary = "준비 시작",
            description = """
                    신규 → 상품준비중(E8). 효과는 하나 — **소비자 단순 취소권 종료**(약관 제17조② · 제18조①).
                    이후 소비자는 취소 요청 → 브랜드 승인·거부 경로만 남는다.

                    **되돌리기는 없다**(§34-4) — 되돌림을 열면 소비자 취소권이 열렸다 닫혔다 한다. 문제는 직권 취소로 푼다.

                    다건 부분 성공 — 취소 요청이 걸렸거나 상태가 변한 행은 `skipped`에 사유와 함께 빠지고 나머지는 진행된다.
                    """)
    @ApiResponse(responseCode = "200", description = "처리 결과 — `succeeded` + `skipped[]`")
    ResponseEntity<BatchActionResponse> prepareStart(@Valid @RequestBody PrepareStartRequest request);

    @Operation(
            summary = "발주서 다운로드",
            description = """
                    xlsx 를 내려받는다(E1 · §34-4). 고정 양식 없음 — `columns` 선택 순서 = 엑셀 좌→우 열 순서.
                    행 단위는 **주문 항목**(SKU)이고 취소 항목은 빠진다.

                    - `deliveryGroupIds` 비면 **현재 탭 전체**(목록과 같은 필터 파라미터를 함께 보낸다 · 상한 2,000건)
                    - `startPreparation` 기본 ON — 발주서를 뽑는 것은 보내겠다는 결정이다. OFF 면 다운로드만(견적·재고 확인용)
                    - `saveAsDefault` — 이 구성을 기본값으로 저장(기본정보 관리에서 수정)
                    - 다운로드는 **반출 이력으로 기록**된다 — 엑셀로 개인정보가 나간다(§34-11)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx 바이너리(Content-Disposition attachment)"),
            @ApiResponse(responseCode = "400", description = "컬럼 미선택 · 대상 없음(`PURCHASE_ORDER_EMPTY`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> downloadPurchaseOrder(@Valid @RequestBody PurchaseOrderRequest request);

    @Operation(summary = "발주서 컬럼 기본값 조회", description = "저장 구성(없으면 기본 8종)과 선택 가능한 전체 컬럼. 기본정보 관리 화면이 같은 API를 쓴다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<PurchaseOrderTemplateDto.Response> getPurchaseOrderTemplate();

    @Operation(summary = "발주서 컬럼 기본값 저장", description = "선택 순서가 곧 열 순서다 — 순서까지 저장된다.")
    @ApiResponse(responseCode = "200", description = "저장 후 구성")
    ResponseEntity<PurchaseOrderTemplateDto.Response> updatePurchaseOrderTemplate(
            @Valid @RequestBody PurchaseOrderTemplateDto.UpdateRequest request);

    // ── 송장 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "송장 등록 확정",
            description = """
                    상품준비중 → 배송중(§34-5). 셀 입력은 클라이언트 임시값이고 **이 호출이 유일한 확정 지점**이다 —
                    배송중 전환은 소비자에게 송장이 전달되는 사건이다. `shipped_at`(발송기한 판정값)이 여기서 박힌다.

                    서버 검사(③): **전역 중복**(겹치는 주문번호를 지목한다) · **형식**(연동 업체 판정 — 자릿수인지
                    체크디지트인지 구분하지 않는다) · **주문 상태 재확인**(그 사이 취소 가능). 하드 차단은 그 행만 제외하고
                    나머지는 등록한다. 빈 값 행은 에러 없이 조용히 제외된다.

                    택배사는 11종 enum 만 — 자유 입력·「미지원 택배사」 없음.
                    """)
    @ApiResponse(responseCode = "200", description = "부분 성공 결과 — 결과 배너(몇 건이 어디로)는 이 응답으로 그린다")
    ResponseEntity<BatchActionResponse> registerShipments(@Valid @RequestBody ShipmentRegisterRequest request);

    @Operation(
            summary = "송장 엑셀 업로드 검증",
            description = """
                    xlsx(최대 1,000행 · 필수 = 주문번호 + 송장번호)를 파싱·분류만 한다(E3) — **상태를 바꾸지 않는다.**
                    「N건 목록에 채우기」는 FE 가 이 응답으로 셀만 채우고, 확정은 수기 입력과 같은 `POST /shipments`다.

                    제외 사유: 이미 배송중 · 송장번호 중복(겹치는 주문 병기) · 주문번호 없음 ·
                    **신규(준비 대기) 주문 — 준비 시작 전이라 등록 불가**(rev.7) · 취소 요청 검토 중 · 미지원 택배사 ·
                    하위주문 여러 건(하위주문번호 안내).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "행별 분류 결과 — 부분 성공 허용"),
            @ApiResponse(responseCode = "400", description = "파일 손상(`SHIPMENT_FILE_INVALID`) · 1,000행 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ShipmentParseResponse> parseShipments(@RequestPart("file") MultipartFile file);

    @Operation(summary = "송장 업로드 양식", description = "주문번호 · 택배사 · 송장번호 3열 xlsx.")
    @ApiResponse(responseCode = "200", description = "xlsx 바이너리")
    ResponseEntity<byte[]> shipmentTemplate();

    @Operation(
            summary = "송장 수정",
            description = """
                    배송중에서만 · 반송중 불가 · **배송완료 전까지**(§34-6). 형식은 맞지만 다른 주문의 송장을 붙여넣은
                    경우를 고치는 경로다. 수정 이력(구 → 신)이 기록되고, `shipped_at`은 유지된다 — 수정으로 발송기한
                    위반이 세탁되면 안 된다. 수정 후 감시 배치가 새 송장 기준으로 다시 판정한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 후 상세"),
            @ApiResponse(responseCode = "409", description = "상태 변경(`ORDER_STATE_CHANGED`) · 전역 중복(`INVOICE_DUPLICATE`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerOrderDetailResponse> updateShipment(@PathVariable Long deliveryGroupId,
                                                             @Valid @RequestBody ShipmentUpdateRequest request);

    // ── 취소 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "직권 취소(판매 취소)",
            description = """
                    품절·하자 대응(E5 · §34-8). 하위주문 전 항목이 취소되고 재고가 돌아간다. **되돌릴 수 없다.**
                    환불은 운영자가 집행한다(환불 큐 적재) · 취소율에 반영된다.

                    `consumerMessage`는 사유와 함께 **소비자에게 그대로 전달**된다(약관 제18조②).

                    허용 단계는 신규·상품준비중이다(§34-13 #1 확정 전 시안 기준 집행 — 취소 당시 상태가 기록된다).
                    검토 중 취소 요청이 걸린 건은 선처리 요구로 `skipped`에 빠진다.
                    """)
    @ApiResponse(responseCode = "200", description = "부분 성공 결과")
    ResponseEntity<BatchActionResponse> directCancel(@Valid @RequestBody SellerDirectCancelRequest request);

    @Operation(
            summary = "취소 요청 승인",
            description = """
                    E6 — **요청 항목만 취소 · 남은 항목은 발송**한다. 환불은 운영자 집행(환불 큐) · 취소율 미반영.
                    전 항목 승인일 때만 하위주문이 취소 탭으로 가고 배송비까지 전액 환불 예정이 된다. 부분 취소의
                    배송비는 재계산하지 않는다(§34-8).

                    처리 후 하위주문은 원래 탭(신규/상품준비중)으로 복귀한다 — 이행 상태는 바뀐 적이 없다.
                    **일괄 승인은 없다** — 건별 근거가 다른 판단을 묶으면 검토가 형식이 된다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 후 상세"),
            @ApiResponse(responseCode = "409", description = "이미 처리된 요청(`CANCEL_REQUEST_ALREADY_DECIDED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerOrderDetailResponse> approveCancelRequest(@PathVariable Long cancelRequestId);

    @Operation(
            summary = "취소 요청 거부",
            description = """
                    E7 — 사유 필수(소비자에게 그대로 전달 · 약관 제18조①). 전 항목 배송이 진행되고 소비자는
                    수령 후 반품으로만 돌릴 수 있다(반품비 발생).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 후 상세"),
            @ApiResponse(responseCode = "409", description = "이미 처리된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerOrderDetailResponse> rejectCancelRequest(@PathVariable Long cancelRequestId,
                                                                  @Valid @RequestBody CancelRequestRejectRequest request);
}
