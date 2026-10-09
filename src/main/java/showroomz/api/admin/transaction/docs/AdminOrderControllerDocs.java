package showroomz.api.admin.transaction.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
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

@Tag(name = "Admin - Transaction · Orders", description = "어드민 거래 관리 · 주문 조회(06a)")
public interface AdminOrderControllerDocs {

    @Operation(summary = "주문 목록 (06a A1 ~ A5)",
            description = """
                    전 브랜드 · 전 공구 주문의 조회 입구. **단위는 주문(결제 1건)**이고 행 확장(A2)은 하위주문(브랜드)이다.

                    - `tab` — `ALL`(전체 · 기본) · `DELIVERY_ISSUE`(배송 이상 — 집화 확인 필요 · 추적 정지 · 반송 중. 반송 완료가 감지된 건은 빠진다) · `CANCEL`(취소 · 검토 중 취소 요청)
                    - `status` — 상태 셀렉트(NEW · PREPARING · SHIPPING · RETURNING · DELIVERED · CONFIRMED · CANCELLED). 하위주문 하나라도 맞으면 나온다
                    - `marketId` — 브랜드 상세의 「이 브랜드 진행 주문 보기」 · `groupBuyId` · `creatorId`(공구를 연 인플루언서) · `paymentMethod`(CARD · EASY_PAY) — 상세 조건
                    - `trackingAlert` — 배송 이상 탭의 「이상 유형」(PICKUP_UNCONFIRMED · STALLED)
                    - `searchType` — 검색 대상(ALL 기본 · ORDER_NUMBER · SUB_ORDER_NUMBER · RECIPIENT · BRAND · TRACKING_NUMBER · PG_TX_ID). 전체는 OR 매치, 송장은 숫자만, PG 거래번호는 정확 일치
                    - `keyword` — 검색어
                    - `sort` — PAID_DESC(기본) · PAID_ASC · AMOUNT_DESC · ISSUE_OLDEST(이상 지속 오래된순 — 마지막 추적 · 반송 감지 시각)
                    - `from` · `to` — 결제일 기준
                    - 행의 `attentionCount` — 배송 이상 · 발송 기한 경과 · 검토 중 취소 요청이 걸린 하위주문 수

                    **권한:** ADMIN
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<PageResponse<AdminOrderDto.ListItem>> getOrders(AdminOrderTab tab, FulfillmentStatus status,
                                                                   Long marketId, Long groupBuyId, Long creatorId,
                                                                   PaymentMethod paymentMethod, TrackingAlert trackingAlert,
                                                                   AdminOrderSearchType searchType, String keyword,
                                                                   AdminOrderSort sort, LocalDate from, LocalDate to,
                                                                   PagingRequest pagingRequest);

    @Operation(summary = "주문 탭 건수", description = "같은 브랜드 · 공구 · 인플루언서 · 결제수단 · 기간 · 검색 조건에서 탭별 주문 수. 상태 셀렉트 · 정렬은 무시한다.\n\n**권한:** ADMIN")
    ResponseEntity<AdminOrderDto.SummaryResponse> getSummary(Long marketId, Long groupBuyId, Long creatorId,
                                                             PaymentMethod paymentMethod, AdminOrderSearchType searchType,
                                                             String keyword, LocalDate from, LocalDate to);

    @Operation(summary = "주문 상세 (06a B1 ~ B8)",
            description = """
                    주문 · 결제(부분 취소 누적액 포함) · 하위주문별 배송 블록 · 취소 요청 · 항목 · 환불 큐 · 처리 이력 · 운영자 조치 가능 여부.

                    - `shipping.shipDueAt` = 공구 마감 + `shipDueBusinessDays`영업일(주문 시점 N · 주말·공휴일 제외). 공구 진행 중이면 null
                    - `shipping.overdueNoticeCount` — 발송 기한 경과 **시스템 자동 알림** 횟수(독촉 버튼 없음)
                    - `cancelRequest.respondDueAt` — 지나면 **자동 승인** · PG 즉시 환불. 운영자 조치는 없다(B7)
                    - `refunds[]` — 출처(`PG_AUTO` · `OPERATOR`)와 상태(`PENDING` · `EXECUTING` · `DONE` · `FAILED`)
                    - `history[]` — 준비 시작 사유(발주서 다운로드 · 개별 · 일괄) · 자동 승인 · 대행 · 정정이 남는다
                    - `actions` — 우 레일 조치 버튼(B3 정정 · B4 대행 송장 · 대행 직권 취소 · B5 사유 환불 편입 · B6 하자 반품 열기)
                    - `inquiryCount` — 모달 헤더 「문의 N건」(이 주문을 가리키는 1:1 문의)
                    - `groups[].activeClaims[]` — 진행 중 반품·교환(B8) · 06b 상세 링크

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — 없거나 결제 전 주문")
    })
    ResponseEntity<AdminOrderDto.DetailResponse> getOrder(@Parameter(hidden = true) UserPrincipal principal, Long orderId);

    @Operation(summary = "추적 정지 종결 — 분실 처리",
            description = """
                    배송중 ∧ 추적 정지 ∧ 마지막 추적(없으면 발송) + N일(기본 28 · `order.exception.stalled-resolve-days`) 경과한 건을 운영자가
                    **분실로 판정**한다(41 보고 3번 · 권고 3 — 자동 처리 없음 · 플랫폼이 단정하지 않는다). 하위주문은 취소(`cancelType = LOST`)로
                    닫히고 **재고는 돌아오지 않는다**. 환불은 항목 + 배송비 전액 · PG 자동(반송 완료와 같은 길). 분실 보상은 택배사와 브랜드의 일이다.
                    상세 `actions.canMarkLost` 가 참일 때만. 법무 확정 대기 — 그 전엔 운영 지침으로 근거를 적는다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 후 상세"),
            @ApiResponse(responseCode = "409", description = "ORDER_STATE_CHANGED — 조건 미충족 · 상태 변경")
    })
    ResponseEntity<AdminOrderDto.DetailResponse> markLost(@Parameter(hidden = true) UserPrincipal principal,
                                                          Long deliveryGroupId, AdminOrderDto.MarkLostRequest request);

    @Operation(summary = "추적 정지 종결 — 배송완료 처리",
            description = """
                    분실 처리와 같은 조건에서 운영자가 **배송완료로 판정**한다(소비자가 받았다고 확인한 경우). 출처 「운영자 처리 · 추적 정지」 ·
                    수령 시각부터 구매확정 타이머가 간다. 추적이 끊긴 송장에만 열리는 유일한 직권 배송완료다(그 밖의 「직권 배송완료」는 없다).

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 후 상세"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE — 발송 전 · 미래 시각"),
            @ApiResponse(responseCode = "409", description = "ORDER_STATE_CHANGED — 조건 미충족 · 상태 변경")
    })
    ResponseEntity<AdminOrderDto.DetailResponse> markDelivered(@Parameter(hidden = true) UserPrincipal principal,
                                                               Long deliveryGroupId,
                                                               AdminOrderDto.MarkDeliveredRequest request);

    @Operation(summary = "B3 배송완료일 정정",
            description = "소비자 수령일 이의 — 배송완료 상태에서만. 「직권 배송완료」는 없다. 구매확정 예정도 같은 만큼 옮긴다. 이력에 구 → 신과 사유가 남는다.\n\n**권한:** ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정정 후 상세"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — 발송 전 · 미래 시각"),
            @ApiResponse(responseCode = "409", description = "ORDER_STATE_CHANGED — 배송완료가 아님")
    })
    ResponseEntity<AdminOrderDto.DetailResponse> correctDeliveredAt(@Parameter(hidden = true) UserPrincipal principal,
                                                                    Long deliveryGroupId,
                                                                    AdminOrderDto.CorrectDeliveredAtRequest request);

    @Operation(summary = "B4 운영자 대행 송장 등록",
            description = """
                    상품준비중 ∧ **대행 조건** — 발송 기한 경과 ∧ 자동 알림 N회(기본 3) 무응답. 브랜드 송장 등록과 같은 전이(배송중 · 발송기한 판정값 =
                    지금)다. 택배사는 추적 연동 목록만. **대행 사유(`note`)는 필수** — 이력 「운영자 대행 · 사유」로 남는다.
                    대행 조건은 06d 예외 관리 「대행 가능」과 같은 판정이다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 후 상세"),
            @ApiResponse(responseCode = "400", description = "INVOICE_FORMAT_INVALID · INVALID_INPUT_VALUE(대행 사유 누락)"),
            @ApiResponse(responseCode = "409", description = "ORDER_ACT_ON_BEHALF_NOT_ALLOWED — 대행 조건 미충족 · INVOICE_DUPLICATE · "
                    + "CANCEL_REQUEST_PENDING_EXISTS · ORDER_STATE_CHANGED")
    })
    ResponseEntity<AdminOrderDto.DetailResponse> registerShipment(@Parameter(hidden = true) UserPrincipal principal,
                                                                  Long deliveryGroupId,
                                                                  AdminOrderDto.ShipmentRequest request);

    @Operation(summary = "B4 · B5 운영자 대행 직권 취소(미발송분)",
            description = """
                    신규 · 상품준비중 하위주문 전체 취소 · 재고 원복 · **PG 즉시 자동 환불**(배송비 포함 전액). 대행 조건(발송 기한 경과 · 자동 알림 무응답)을
                    채웠거나 `reasonCode = DEFECT`(위해성 · 상품 하자)일 때만. `consumerMessage` 는 소비자에게 그대로 전달된다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 후 상세"),
            @ApiResponse(responseCode = "409", description = "ORDER_ACT_ON_BEHALF_NOT_ALLOWED · CANCEL_REQUEST_PENDING_EXISTS · ORDER_STATE_CHANGED")
    })
    ResponseEntity<AdminOrderDto.DetailResponse> cancel(@Parameter(hidden = true) UserPrincipal principal,
                                                        Long deliveryGroupId, AdminOrderDto.CancelCommand request);

    @Operation(summary = "B5 운영자 사유 환불 편입",
            description = """
                    발송 이후 하위주문의 운영자 사유 환불(위해성 리콜 · 구매확정 후 하자)을 **편입만** 한다 — 돈은 환불 관리(06c)의
                    재확인 다이얼로그에서만 나간다(편입과 집행을 나눈다). 결제의 취소 가능 잔액(아직 나가지 않은 환불 포함)을 넘지 못한다.
                    발송 전 주문은 직권 취소로 환불한다. 반송 중 주문은 반송 완료 시 PG 자동 환불되므로 받지 않는다.
                    반려 이의 인용(`DISPUTE_ACCEPTED`) · 검수 무응답(`INSPECTION_UNANSWERED`)은 반품·교환 상세(06b)에서 서버 계산 금액으로 편입한다 — 400.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "편입 후 상세 — refunds[] 에 OPERATOR · PENDING 행"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — 06b 전용 사유(반려 이의 인용 · 검수 무응답)"),
            @ApiResponse(responseCode = "409", description = "REFUND_AMOUNT_EXCEEDED · ORDER_STATE_CHANGED(발송 전 · 반송 중)")
    })
    ResponseEntity<AdminOrderDto.DetailResponse> enqueueRefund(@Parameter(hidden = true) UserPrincipal principal,
                                                               Long deliveryGroupId,
                                                               AdminOrderDto.OperatorRefundRequest request);

    @Operation(summary = "B6 구매확정 후 하자 · 반품 대신 열기",
            description = """
                    구매확정 뒤 하자는 소비자가 직접 반품을 신청하지 못한다 — 1:1 문의를 받은 운영자가 대신 연다. 브랜드 귀책 사유(파손·불량 ·
                    오배송)만 · 하자 내용과 증빙 필수 · 단순 변심 불가 · 배송완료 3개월 안(안 날부터 30일은 운영자가 문의로 판단).
                    연 뒤에는 일반 반품과 같다 — 회수 송장은 소비자 · 검수는 브랜드 · 통과 시 PG 자동 환불. 파트너 목록에 「운영자 개설 · 구매확정 후
                    하자 · 증빙 N장」으로 보인다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "개설 — 요청 id · 클레임 id"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — 사유가 브랜드 귀책이 아님 · CLAIM_REASON_DETAIL_REQUIRED — 내용 · 증빙 누락"),
            @ApiResponse(responseCode = "409", description = "CLAIM_NOT_ELIGIBLE — 구매확정 · 배송완료가 아니거나 3개월 경과 · CLAIM_QUANTITY_EXCEEDED")
    })
    ResponseEntity<AdminOrderDto.DefectClaimResponse> openDefectClaim(@Parameter(hidden = true) UserPrincipal principal,
                                                                      Long deliveryGroupId,
                                                                      AdminOrderDto.DefectClaimRequest request);
}
