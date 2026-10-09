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

                    - `tab=PENDING`(기본) — **운영자 사유 환불**의 집행 대기(06a B5 · 06b B2 에서 편입). PG 자동 대기는 커밋 직후 집행돼 여기 머물지 않는다
                    - `tab=FAILED` — PG 환불 실패(자동 재시도 뒤). `lastError` 가 사유다
                    - `tab=DONE` — 완료 **전체**(PG 자동 포함 · 기본 최근 30일 — `days` 로 바꾼다). `originLabel` 이 출처 열(PG 자동 · 운영자 사유)
                    - 원래 결제와 추가 결제(교환 · 반려 재발송비)는 PG 거래가 따로라 취소도 따로다 — 여기는 원래 결제의 **부분 취소**만

                    **권한:** ADMIN
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<PageResponse<AdminTransactionDto.RefundItem>> getRefunds(AdminTransactionDto.RefundTab tab, Integer days,
                                                                           PagingRequest pagingRequest);

    @Operation(summary = "환불 요약", description = "탭 건수와 사이드바 배지(집행 대기 + 실패).\n\n**권한:** ADMIN")
    ResponseEntity<AdminTransactionDto.RefundSummary> getSummary();

    @Operation(summary = "환불 집행 · 재시도 (06c M1 · M2)",
            description = """
                    운영자 사유 환불의 [집행]과 실패 건의 [재시도]가 같은 호출이다 — **돈이 나가는 순간은 이 호출 하나**다(재확인 다이얼로그 1회 ·
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
