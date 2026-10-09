package showroomz.api.admin.transaction.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Transaction · Refunds", description = "어드민 거래 관리 · 환불 관리(06c)")
public interface AdminRefundControllerDocs {

    @Operation(summary = "환불 목록 (06c A1 ~ A3)",
            description = """
                    **환불은 자동이 기본이고 이 화면은 예외만** 다룬다(결제완료 취소 · 취소 요청 승인 · 직권 취소 · 반품 검수 통과 · 반송 완료는 전부 PG 즉시 자동).

                    - `tab=PENDING`(기본) — **운영자 사유 환불**의 집행 대기(06a B5 · 06b B2 에서 편입 · 처리 중 포함). PG 자동 대기는 커밋 직후 집행돼 여기 머물지 않는다
                    - `tab=FAILED` — PG 환불 실패(출처 불문 · 자동 재시도 뒤). `statusNote` · `lastError` 가 사유다
                    - `tab=DONE` — 완료 **전체**(PG 자동 포함). 기간은 **집행 시각** 기준 최근 `days`일(기본 30)
                    - `route` — 경로 셀렉트: `CANCEL`(취소 요청 승인 · 직권 취소 · 결제완료 소비자 취소) · `RETURN`(반품 검수 통과) ·
                      `RETURN_SHIPMENT`(반송 완료) · `EXCHANGE`(교환 재발송비 환불) · `OPERATOR`(운영자 사유)
                    - `keyword` — 한 입력창: 환불번호(`RFD-918` 또는 `918`) · 주문번호 · PG 거래번호(포트원 거래 id · paymentId) 정확 일치.
                      `RFD-` 뒤가 숫자가 아니면 결과 0건이다(전체로 떨어지지 않는다)
                    - `sort` — `LATEST`(「일시」 최신순 · 기본) · `AMOUNT_DESC`(환불액 높은순)
                    - 「일시」(`displayAt`)는 탭마다 뜻이 다르다 — 집행 대기 = 편입 · 실패 = 마지막 실패 · 완료 = 집행
                    - 경로 · 결제 · 상태 열의 문장(`sourceLabel` · `sourceRef` · `paymentLabel` · `statusNote`)은 서버가 만든다

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "ORDER_PAGE_SIZE_INVALID · INVALID_INPUT — 페이지 크기 상한 초과")
    })
    ResponseEntity<PageResponse<AdminTransactionDto.RefundItem>> getRefunds(AdminTransactionDto.RefundTab tab,
                                                                           AdminTransactionDto.RefundRoute route,
                                                                           String keyword,
                                                                           AdminTransactionDto.RefundSort sort,
                                                                           Integer days, PagingRequest pagingRequest);

    @Operation(summary = "환불 요약",
            description = """
                    탭별 건수 · 합계(`tabs.PENDING` · `FAILED` · `DONE`)와 사이드바 배지(`badge` = 집행 대기 + 실패).
                    - `FAILED.oldestWaitingDays` — 실패 건 중 가장 오래 기다린 소비자의 대기 일수(환불이 생긴 시각 기준)
                    - `DONE` — 최근 30일(집행 시각) · `byOrigin` 출처별 건수
                    폴링 대상이다(30~60초).

                    **권한:** ADMIN
                    """)
    ResponseEntity<AdminTransactionDto.RefundSummary> getSummary();

    @Operation(summary = "환불 상세 (06c M1 · M2)",
            description = """
                    집행 재확인(M1) · 재시도(M2) 다이얼로그가 보는 값 전부 — 경로 · 운영자 사유와 근거 · **취소 대상 결제**(수단 · PG 거래번호 ·
                    취소 가능 잔액) · 추가 결제(재발송비 청구와 그 처리) · 정산(정산 모듈 전이라 「정산 전」 고정) · 실패 기록 · 이 건의 이력.
                    소멸(VOID) 건도 돌려준다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "REFUND_TASK_NOT_FOUND")
    })
    ResponseEntity<AdminTransactionDto.RefundDetail> getRefund(Long refundTaskId);

    @Operation(summary = "편입 철회",
            description = """
                    집행 전 운영자 사유 환불(`PENDING · OPERATOR`)을 철회한다 — 오편입 · 금액 재산정. 돈은 나가지 않았으므로 되돌릴 것이 없고,
                    큐 행은 `VOID`(어느 탭에도 없음 · 상세 · 주문 상세에서만)가 된다. 사유는 이력 「운영자 사유 환불 편입 철회」에 남는다.
                    **반려 이의 인용 건은 철회할 수 없다** — 편입 때 재발송비 청구를 이미 소멸 · 취소해 되살릴 수 없다(§39-6 A-1 후단).

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "철회 후 행"),
            @ApiResponse(responseCode = "404", description = "REFUND_TASK_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "REFUND_TASK_NOT_VOIDABLE — 집행 전 운영자 사유가 아니거나 반려 이의 인용")
    })
    ResponseEntity<AdminTransactionDto.RefundItem> voidRefund(@Parameter(hidden = true) UserPrincipal principal,
                                                             Long refundTaskId, AdminTransactionDto.RefundVoidRequest request);

    @Operation(summary = "수동 완료 기록",
            description = """
                    PG 콘솔 등 **밖에서 이미 돌려준** 환불을 완료로 적는다 — PG 를 부르지 않는다. 반복 실패 건을 사람이 PG 콘솔에서 처리한 뒤
                    큐가 `FAILED` 로 남아 소비자 앱이 「환불 처리 중」으로 보이는 것을 닫는 경로다(41 보고 2번).
                    결제의 누적 취소액 · 부분 취소 기록 · 반품 종결 등 후속은 PG 집행과 같고, 이력은 「환불 수동 완료 기록」(운영자 · 취소번호 · 근거)이다.
                    대기 · 실패 건만. 같은 결제의 다른 환불이 PG 를 기다리는 중이거나 환불액이 취소 가능 잔액보다 크면 409.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기록 후 행"),
            @ApiResponse(responseCode = "404", description = "REFUND_TASK_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "REFUND_TASK_NOT_EXECUTABLE")
    })
    ResponseEntity<AdminTransactionDto.RefundItem> recordManual(@Parameter(hidden = true) UserPrincipal principal,
                                                               Long refundTaskId,
                                                               AdminTransactionDto.RefundManualCompleteRequest request);

    @Operation(summary = "환불 집행 · 재시도 (06c M1 · M2)",
            description = """
                    운영자 사유 환불의 [집행]과 실패 건의 [재시도]가 같은 호출이다(`/retry` 는 없다) — **돈이 나가는 순간은 이 호출 하나**다(재확인 다이얼로그 1회 ·
                    2인 승인 없음 · 일괄 없음). 포트원 **부분 취소**로 돌려준다(지금 취소 가능 잔액을 함께 보내 이중 환불을 막는다).

                    - `outcome=DONE` — 환불 완료 · `FAILED` — PG 거절(`refund.lastError`) · `UNKNOWN` — 결과 확인 중(정리 배치가 포트원 조회로 닫는다) ·
                      `SKIPPED` — 같은 결제의 다른 환불이 처리 중(잠시 후 다시)

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "집행 결과"),
            @ApiResponse(responseCode = "404", description = "REFUND_TASK_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "REFUND_TASK_NOT_EXECUTABLE — 이미 완료 · 처리 중 · 결제 없는 주문")
    })
    ResponseEntity<AdminTransactionDto.RefundExecuteResponse> execute(@Parameter(hidden = true) UserPrincipal principal,
                                                                     Long refundTaskId);
}
