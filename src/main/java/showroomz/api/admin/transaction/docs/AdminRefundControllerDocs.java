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
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import static showroomz.api.admin.transaction.docs.AdminTransactionDocsExamples.*;

@Tag(name = "Admin - Refund", description = "관리자 거래 관리 · 환불 관리 API (06c)")
public interface AdminRefundControllerDocs {

    @Operation(summary = "환불 목록 (06c A1 ~ A3)", description = """
            환불 큐 행을 탭별로 봅니다. 다이얼로그(M1 집행 재확인 · M2 재시도)에 필요한 값은 상세 API 에 있습니다.

            **권한:** ADMIN

            **탭 `tab`** — 없으면 `PENDING`. 탭 숫자 · 합계는 `GET /v1/admin/refunds/summary`.

            | 값 | 화면 | 들어오는 행 | 「일시」(`displayAt`) |
            |---|---|---|---|
            | `PENDING` | 집행 대기 | **운영자 사유 환불**의 대기 · 처리 중(`PENDING` · `EXECUTING`). PG 자동 대기는 커밋 직후 집행돼 여기 머물지 않는다 | 편입 시각 |
            | `FAILED` | 실패 | PG 환불 실패 — **출처 불문**(자동 재시도 뒤). `statusNote` · `lastError`가 사유 | 마지막 실패 시각 |
            | `DONE` | 완료 | 완료 **전체**(PG 자동 포함 — CS 「환불 언제 들어와요?」). 최근 `days`일(기본 30 · **집행 시각** 기준) | 집행 시각 |

            철회된 행(`VOID`)은 어느 탭에도 없습니다 — 상세 · 주문 상세에서만 보입니다.

            **경로 `route`** — 발생 경로 셀렉트(없으면 전체)

            | 값 | 라벨 | 들어오는 출처(`source`) |
            |---|---|---|
            | `CANCEL` | 취소 | `CANCEL_REQUEST_APPROVED` 취소 요청 승인(자동 승인 포함) · `SELLER_DIRECT_CANCEL` 직권 취소(브랜드 · 운영자 대행) · `USER_CANCEL_BEFORE_PREPARE` 결제완료 소비자 취소 · `LOST_IN_TRANSIT` 배송 분실 처리 |
            | `RETURN` | 반품 | `CLAIM_RETURN_PASSED` 반품 검수 통과(일부 반려 포함) |
            | `RETURN_SHIPMENT` | 반송 | `RETURN_COMPLETED` 반송 완료 |
            | `EXCHANGE` | 교환 | `CLAIM_PAYMENT_CANCELLED` 교환 · 반려 재발송비 결제 취소 |
            | `OPERATOR` | 운영자 사유 | `OPERATOR_REASON` — 반려 이의 인용 · 구매확정 후 하자 · 위해성 리콜 · 검수 무응답 |

            **검색 `keyword`** — 한 입력창, 아래 셋 중 하나와 **정확 일치**합니다.
            - 환불번호 — `RFD-918` 또는 `918`(대소문자 무시). **`RFD-` 뒤가 숫자가 아니면 결과 0건**입니다(전체로 떨어지지 않는다)
            - 주문번호 — `20261003-000123`
            - PG 거래번호 — 포트원 거래 id · paymentId

            **정렬 `sort`** — `LATEST`(「일시」 최신순 · 기본) · `AMOUNT_DESC`(환불액 높은순 · 동률은 최신 id 먼저)

            **페이징** — `page`(1부터) · `size`(기본 20 · **1~100**, 벗어나면 400)

            **행 그리기** — 문장은 서버가 만듭니다. FE 는 그대로 씁니다.

            | 열 | 필드 |
            |---|---|
            | 경로 | `sourceLabel`(예: 「반려 이의 인용」 · 「취소 요청 자동 승인」 · 「운영자 대행 직권 취소」) + 회색 `sourceRef`(`CLM-` · `CRQ-` 번호) |
            | 출처 | `originLabel` — 「PG 자동」(중립) · 「운영자 사유」(경고) |
            | 결제 | `paymentLabel` — 「카드 · 원래 부분」 · 「간편결제 · 추가」. `paymentKind` = `ORIGINAL` 원래 결제 · `ADDITIONAL` 추가 결제(재발송비) |
            | 상태 | `statusLabel` + `statusNote`(「편입 10.09 14:20 · 김운영」 · 실패 사유 · 「집행 김운영 · 재확인」) |
            | 버튼 | `executable` [집행]/[재시도] · `voidable` [철회] · `manuallyCompletable` [수동 완료 기록] |
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "환불 목록과 페이지 정보",
                    content = @Content(schema = @Schema(implementation = PageResponse.class), examples = {
                            @ExampleObject(name = "집행 대기", summary = "반려 이의 인용(철회 불가) · 위해성 리콜(철회 가능)", value = REFUND_LIST_PENDING),
                            @ExampleObject(name = "실패", summary = "PG 자동 반품 환불 · 자동 재시도 뒤 실패", value = REFUND_LIST_FAILED),
                            @ExampleObject(name = "완료", summary = "tab=DONE&days=30 — PG 자동 포함", value = REFUND_LIST_DONE)
                    })),
            @ApiResponse(responseCode = "400", description = "페이지 크기 1~100 밖 · 없는 enum 값 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "페이지 크기", value = ERR_PAGE_SIZE),
                            @ExampleObject(name = "enum", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<PageResponse<AdminTransactionDto.RefundItem>> getRefunds(
            @Parameter(description = "탭 — PENDING(기본) · FAILED · DONE", example = "PENDING") AdminTransactionDto.RefundTab tab,
            @Parameter(description = "경로 — CANCEL · RETURN · RETURN_SHIPMENT · EXCHANGE · OPERATOR. 없으면 전체", example = "OPERATOR")
            AdminTransactionDto.RefundRoute route,
            @Parameter(description = "환불번호(RFD-918 · 918) · 주문번호 · PG 거래번호 정확 일치", example = "RFD-918") String keyword,
            @Parameter(description = "정렬 — LATEST(기본) · AMOUNT_DESC", example = "LATEST") AdminTransactionDto.RefundSort sort,
            @Parameter(description = "[완료 탭] 집행 시각 기준 최근 N일 — 기본 30 · 1 미만은 1. 다른 탭에서는 무시", example = "30") Integer days,
            PagingRequest pagingRequest);

    @Operation(summary = "환불 요약", description = """
            탭별 건수 · 합계와 사이드바 배지를 내립니다. **폴링 대상**입니다(30~60초).

            **권한:** ADMIN

            | 필드 | 내용 |
            |---|---|
            | `tabs.PENDING` | 집행 대기(운영자 사유 · 처리 중 포함) 건수 · 합계 |
            | `tabs.FAILED` | 실패 건수 · 합계 · `oldestWaitingDays` — 실패 건 중 가장 오래 기다린 소비자의 대기 일수(환불이 생긴 시각 기준 · 실패가 없으면 null) |
            | `tabs.DONE` | 최근 `days`일(집행 시각) 완료 건수 · 합계 · `days` · `byOrigin`(`PG_AUTO` · `OPERATOR` 출처별 건수) |
            | `badge` | 사이드바 배지 = 집행 대기 + 실패 |

            목록과 **같은 `days`**를 보내야 완료 탭 숫자 = 목록 건수입니다. 경로 · 검색은 받지 않습니다(탭 숫자는 전체 기준).
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탭별 건수 · 합계 · 배지",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.RefundSummary.class),
                            examples = @ExampleObject(value = REFUND_SUMMARY))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminTransactionDto.RefundSummary> getSummary(
            @Parameter(description = "완료 탭 조회 기간(일) — 목록 `days`와 같은 값 · 기본 30 · 1 미만은 1", example = "30") Integer days);

    @Operation(summary = "환불 상세 (06c M1 · M2)", description = """
            집행 재확인(M1) · 재시도(M2) 다이얼로그가 보는 값 전부입니다. **소멸(`VOID`) 건도 돌려줍니다**(주문 상세에서 링크로 올 수 있다).

            **권한:** ADMIN

            | 블록 | 내용 |
            |---|---|
            | `order` | 주문 · 하위주문 · 브랜드 |
            | `source` | 경로 코드 · 라벨 · 참조번호 + 근거 id(`claimId` · `cancelRequestId` · `collectionId` — 링크용) |
            | `reason` | 운영자 사유 환불만 — 사유 코드 · 근거 · 편입 운영자 · 편입 시각. PG 자동이면 null |
            | `target` | M1 「취소 대상」 — 결제 id · PG 거래번호 · 수단 · 결제액 · 누적 취소액 · **지금 취소 가능 잔액** · 이번 환불액 · 부분 여부. 결제가 없는 주문은 null |
            | `additionalPayments[]` | M1 「추가 결제」 — 클레임 박스의 재발송비 청구와 그 처리(`note`: 「인용 시 결제 취소됨」 · 「재발송비 결제 요청은 인용 시 취소됨」 등). 클레임 경로가 아니면 빈 배열 |
            | `settlement` | 이 환불과 정산 — 아래 |
            | `failure` | 실패 상태일 때만 — 운영자 문장 · PG 응답 코드 · 시도별 기록(`attempts[]` 오래된순) |
            | `execution` | 완료일 때만 — 집행 시각 · 운영자(PG 자동은 null) · PG 취소 거래 id |
            | `history[]` | 이 환불 건의 이력 오래된순(편입 · 실패 · 집행 · 철회 · 수동 완료 기록) |

            **정산 `settlement.state`**
            - `BEFORE_SETTLEMENT` — 그 하위주문의 정산이 아직 없다. 정산 생성 때 배송 예외 · 반품 차감으로 반영된다
            - `SETTLED` — 이미 생성된 정산의 항목. 그 정산 금액은 바뀌지 않고 **`clawback`(차감)으로 다음 정산**에 간다(측별 2행 중 덜 끝난 쪽 상태)

            `autoMaxAttempts` — PG 자동 환불의 자동 시도 상한(최초 포함). 운영자 사유는 자동 재시도하지 않습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "환불 상세",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.RefundDetail.class), examples = {
                            @ExampleObject(name = "집행 대기 · 반려 이의 인용", summary = "M1 — 취소 대상 · 재발송비 청구 소멸 · 정산 전", value = REFUND_DETAIL_DISPUTE),
                            @ExampleObject(name = "실패 · PG 자동", summary = "M2 — 시도 2회 실패 · 정산 후 차감 예정", value = REFUND_DETAIL_FAILED)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 환불 건 (`REFUND_TASK_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_REFUND_TASK_NOT_FOUND)))
    })
    ResponseEntity<AdminTransactionDto.RefundDetail> getRefund(
            @Parameter(description = "환불 큐 ID — 환불번호 RFD-918 의 918", example = "918", required = true) Long refundTaskId);

    @Operation(summary = "편입 철회", description = """
            집행 전 운영자 사유 환불을 **철회**합니다 — 오편입 · 금액 재산정. 돈이 나가지 않았으므로 되돌릴 것이 없습니다.

            **권한:** ADMIN

            **조건** — 행 `voidable = true`: 출처 `OPERATOR` ∧ 상태 `PENDING`(처리 중 · 실패 · 완료는 안 된다).
            다음 둘은 **철회할 수 없습니다** — 편입 때 클레임을 환불로 종결하고 반품 수량 · 재발송비 청구 · 교환 재고를 이미 정리해 되살릴 수 없기 때문입니다.
            - `DISPUTE_ACCEPTED` 반려 이의 인용
            - `INSPECTION_UNANSWERED` 검수 무응답

            **효과** — 큐 행이 `VOID`(「취소됨」)가 되어 어느 탭에도 없게 됩니다(상세 · 주문 상세에서만). 사유는 이력 「운영자 사유 환불 편입 철회」에 남습니다.
            금액을 고치려면 철회한 뒤 06a 에서 다시 편입합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "철회 후 행",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.RefundItem.class),
                            examples = @ExampleObject(summary = "status VOID · 버튼 전부 false", value = REFUND_AFTER_VOID))),
            @ApiResponse(responseCode = "400", description = "사유 누락 · 공백 · 300자 초과 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_NOT_BLANK))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 환불 건 (`REFUND_TASK_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_REFUND_TASK_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "집행 전 운영자 사유가 아님 · 반려 이의 인용 · 검수 무응답 · 그 사이 집행 시작 (`REFUND_TASK_NOT_VOIDABLE`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_REFUND_TASK_NOT_VOIDABLE)))
    })
    ResponseEntity<AdminTransactionDto.RefundItem> voidRefund(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "환불 큐 ID", example = "919", required = true) Long refundTaskId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "철회 사유 — 이력에 남는다",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.RefundVoidRequest.class),
                            examples = @ExampleObject(value = REQ_REFUND_VOID)))
            AdminTransactionDto.RefundVoidRequest request);

    @Operation(summary = "수동 완료 기록", description = """
            PG 콘솔 등 **밖에서 이미 돌려준** 환불을 완료로 적습니다. **PG 를 부르지 않습니다.**
            반복 실패 건을 사람이 포트원 콘솔에서 처리한 뒤, 큐가 `FAILED`로 남아 소비자 앱이 「환불 처리 중」으로 보이는 것을 닫는 경로입니다.

            **권한:** ADMIN

            **조건** — 행 `manuallyCompletable = true`: 대기(`PENDING`) · 실패(`FAILED`). 아래면 409.
            - 처리 중 · 완료 · 철회된 건
            - 같은 결제의 다른 환불이 PG 를 기다리는 중
            - 환불액이 결제의 취소 가능 잔액보다 큼

            **입력** — `pgCancellationId`(PG 취소 거래번호 · 있으면) · `note`(근거 — 어디서 어떻게 돌려줬는지 · 필수)

            **효과** — 결제 누적 취소액 · 부분 취소 기록 · 반품 종결 등 후속은 PG 집행과 **같습니다**. 이력은 「환불 수동 완료 기록」(운영자 · 취소번호 · 근거).
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기록 후 행",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.RefundItem.class),
                            examples = @ExampleObject(summary = "실패 건 → 환불 완료", value = REFUND_AFTER_MANUAL))),
            @ApiResponse(responseCode = "400", description = "근거 누락 · 공백 · 500자 초과 · 취소번호 100자 초과 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_NOT_BLANK))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 환불 건 (`REFUND_TASK_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_REFUND_TASK_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "대기 · 실패가 아님 · 같은 결제의 다른 환불 처리 중 · 잔액 초과 (`REFUND_TASK_NOT_EXECUTABLE`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_REFUND_TASK_NOT_EXECUTABLE)))
    })
    ResponseEntity<AdminTransactionDto.RefundItem> recordManual(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "환불 큐 ID", example = "902", required = true) Long refundTaskId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "PG 취소 거래번호(선택) · 근거(필수)",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.RefundManualCompleteRequest.class),
                            examples = @ExampleObject(value = REQ_REFUND_MANUAL)))
            AdminTransactionDto.RefundManualCompleteRequest request);

    @Operation(summary = "환불 집행 · 재시도 (06c M1 · M2)", description = """
            운영자 사유 환불의 **[집행]**과 실패 건의 **[재시도]**가 같은 호출입니다(`/retry`는 없다). **돈이 나가는 순간은 이 호출 하나**입니다 —
            재확인 다이얼로그 1회 · 2인 승인 없음 · 일괄 없음. 요청 본문은 없습니다.

            **권한:** ADMIN

            **조건** — 행 `executable = true`: 대기 · 실패 ∧ 결제가 있다. 아니면 409.

            포트원 **부분 취소**로 돌려줍니다. 지금 취소 가능 잔액을 함께 보내 이중 환불을 막습니다. PG 호출은 트랜잭션 밖에서 하고, 응답의 `refund`는 집행 뒤 다시 읽은 행입니다.

            **결과 `outcome`** — HTTP 는 모두 200 이고 결과는 이 값으로 가릅니다.

            | 값 | 뜻 | 행 | FE |
            |---|---|---|---|
            | `DONE` | 환불 완료 | 완료 탭으로 | 성공 토스트 |
            | `FAILED` | PG 거절 | 실패 탭(`lastError`) | 사유를 보이고 [재시도] 유지 |
            | `UNKNOWN` | 결과 확인 중(타임아웃 등) | 처리 중 | 정리 배치가 포트원 조회로 닫는다 — 다시 누르지 않게 안내 |
            | `SKIPPED` | 같은 결제의 다른 환불이 처리 중 | 그대로 | 잠시 후 다시 |
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "집행 결과 — outcome 과 집행 뒤 행",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.RefundExecuteResponse.class), examples = {
                            @ExampleObject(name = "완료", summary = "outcome=DONE", value = REFUND_EXECUTE_DONE),
                            @ExampleObject(name = "PG 거절", summary = "outcome=FAILED — 재시도 버튼 유지", value = REFUND_EXECUTE_FAILED),
                            @ExampleObject(name = "다른 환불 처리 중", summary = "outcome=SKIPPED — 행은 그대로", value = REFUND_EXECUTE_SKIPPED)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 환불 건 (`REFUND_TASK_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_REFUND_TASK_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "이미 완료 · 처리 중 · 철회됨 · 결제 없는 주문 (`REFUND_TASK_NOT_EXECUTABLE`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_REFUND_TASK_NOT_EXECUTABLE)))
    })
    ResponseEntity<AdminTransactionDto.RefundExecuteResponse> execute(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "환불 큐 ID", example = "918", required = true) Long refundTaskId);
}
