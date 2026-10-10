package showroomz.api.admin.transaction.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimSort;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDate;
import java.util.Set;

import static showroomz.api.admin.transaction.docs.AdminTransactionDocsExamples.*;

@Tag(name = "Admin - Transaction · Claims", description = """
        어드민 거래 관리 · 반품·교환(06b). 파트너센터 반품·교환(11)과 **같은 탭 · 같은 건수 · 같은 행**에 어드민 전용 열(브랜드 · 귀책)을 더한 화면이다.

        회수 · 입고 · 검수 · 재발송은 소비자와 브랜드의 일이다. 운영자가 실행하는 일은 두 가지뿐이고, 둘 다 **운영자 사유 환불 편입**으로 끝난다
        (돈은 환불 관리(06c)에서만 나간다) — B2 반려 이의 인용 · 검수 무응답 환불.""")
public interface AdminClaimControllerDocs {

    @Operation(summary = "반품·교환 목록 (06b A1)", description = """
            전 브랜드 반품·교환을 클레임(항목) 단위로 봅니다. 행의 `claim`은 파트너 11 목록 행(`SellerClaimListItem`)과 **같은 값**이고,
            어드민 전용 열이 바깥에 붙습니다.

            **권한:** ADMIN

            **탭 `tab`** — 없으면 `ALL`. 탭 숫자는 `GET /v1/admin/claims/summary`.

            | 값 | 화면 | 클레임 상태 |
            |---|---|---|
            | `ALL` | 전체 | 결제 대기(접수 전) 제외 전부 |
            | `COLLECT_WAIT` | 회수 대기 | `REQUESTED` |
            | `COLLECTING` | 회수 중 | `COLLECTING` |
            | `INSPECTION` | 입고·검수 | `ARRIVED` · `RECEIVED` |
            | `RESHIP` | 재발송 대기 | `RESHIP_READY` |
            | `REJECT_HOLD` | 반려 보류 | `REJECT_HOLD` |
            | `DONE` | 완료 | `REFUND_PENDING` · `RESHIPPING` · `COMPLETED` |

            **조건** — 파트너 목록과 같은 조건에 어드민 전용 조건을 더합니다. 모두 AND.
            - `types` — 유형(반품 · 교환)은 탭이 아니라 이 값으로 거릅니다. 여러 개: `types=RETURN&types=EXCHANGE`(없으면 둘 다)
            - `reason` — 사유 코드 · `from` · `to` — **신청일** 기준(양 끝 포함)
            - `keyword` — 파트너 목록과 같은 검색(접수번호 · 주문번호 · 상품명 등)
            - `marketId` — 그 브랜드만 · `marketName` — 브랜드명 부분 일치 · `consumerName` — 소비자명 부분 일치 *(어드민 전용)*
            - `sort` — `REQUESTED_DESC`(신청 최신순) · `ELAPSED_ASC`(현재 단계 경과 오래된순). 없으면 **탭 기본 정렬** *(어드민 전용)*

            **정렬 고정** — 전체 탭은 **검수 지연(입고 · 기한 경과)을 맨 위에 고정**합니다(페이지를 넘어서도).

            **페이징** — `page`(1부터) · `size`(기본 20 · **1~100**, 벗어나면 400)

            **어드민 전용 열**

            | 필드 | 내용 |
            |---|---|
            | `brandName` | 브랜드명(하위주문 스냅샷) |
            | `feeBearer` · `feeBearerLabel` | 귀책 — `SELLER` 「브랜드 귀책」 · `CONSUMER` 「소비자 귀책」. **항목 단위**라 일부 반려에서 갈린다 |
            | `faultChangedToSeller` | 검수에서 브랜드 귀책으로 인정됐다 |
            | `disputeOpen` · `disputedAt` | 보조줄 「소비자 이의 접수」 — 반려 보류 중이고 앱 「이의 제기」 1:1 문의가 **답변 전**. `disputedAt`은 가장 최근 이의 시각(이의가 없었으면 null) |
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "클레임 목록과 페이지 정보",
                    content = @Content(schema = @Schema(implementation = PageResponse.class), examples = @ExampleObject(
                            name = "전체 탭", summary = "CLM-3021 검수 지연(상단 고정 · 브랜드 귀책) / CLM-3008 반려 보류 · 소비자 이의 접수",
                            value = CLAIM_LIST))),
            @ApiResponse(responseCode = "400", description = "페이지 크기 1~100 밖 · 없는 enum 값 · 날짜 형식 오류 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "페이지 크기", value = ERR_PAGE_SIZE),
                            @ExampleObject(name = "enum · 날짜 형식", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<PageResponse<AdminTransactionDto.ClaimListItem>> getClaims(
            @Parameter(description = "브랜드(마켓) ID — 그 브랜드만", example = "17") Long marketId,
            @Parameter(description = "탭 — ALL(기본) · COLLECT_WAIT · COLLECTING · INSPECTION · RESHIP · REJECT_HOLD · DONE", example = "ALL")
            ClaimTab tab,
            @Parameter(description = "유형 — RETURN · EXCHANGE 중 여러 개(반복 파라미터). 없으면 둘 다",
                    array = @ArraySchema(schema = @Schema(implementation = ClaimType.class)), example = "RETURN")
            Set<ClaimType> types,
            @Parameter(description = "사유 — CHANGE_OF_MIND · ORDER_MISTAKE · SIZE_MISMATCH · DAMAGED_OR_DEFECTIVE · WRONG_OR_LATE_DELIVERY · OTHER",
                    example = "DAMAGED_OR_DEFECTIVE") ClaimReason reason,
            @Parameter(description = "신청일 시작(포함) — yyyy-MM-dd", example = "2026-09-01") LocalDate from,
            @Parameter(description = "신청일 끝(포함) — yyyy-MM-dd", example = "2026-10-10") LocalDate to,
            @Parameter(description = "검색어 — 파트너 목록과 같은 검색", example = "CLM-3021") String keyword,
            @Parameter(description = "[어드민] 브랜드명 부분 일치", example = "데일리") String marketName,
            @Parameter(description = "[어드민] 소비자명 부분 일치", example = "김민지") String consumerName,
            @Parameter(description = "[어드민] 정렬 — REQUESTED_DESC · ELAPSED_ASC. 없으면 탭 기본 정렬", example = "ELAPSED_ASC") ClaimSort sort,
            PagingRequest pagingRequest);

    @Operation(summary = "반품·교환 요약", description = """
            파트너 11 과 같은 KPI · 탭 · 유형 건수에 어드민 「반려 이의 N건」을 더합니다. 전 브랜드 기준이고, `marketId`를 주면 그 브랜드만 셉니다.

            **권한:** ADMIN

            **검색 조건 · 기간과 무관한 전체 기준**입니다. 결제 대기(접수 전) 건은 어디에도 세지 않습니다.

            | 필드 | 내용 |
            |---|---|
            | `kpi.collectWait` | 회수 대기 |
            | `kpi.inspection` | 입고·검수 — 입고 확인 전 + 검수 대기 |
            | `kpi.reship` | 재발송 대기 |
            | `kpi.overdue` | 기한 초과 — 회수 대기 방치 + 검수 기한 경과. 0 이면 경고 톤을 뺀다 |
            | `tabCounts` | 탭 7종 — `ALL`은 여섯 탭의 합 |
            | `typeCounts` | `RETURN` · `EXCHANGE` |
            | `disputeCount` | **반려 이의 미처리** — 반려 보류 중 ∧ 이의 문의 답변 전. 인용하면 반려 보류를 벗어나고, 기각(문의 답변)하면 빠진다. 사이드바 「반품·교환」 배지 후보 |
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPI · 탭 · 유형 건수 · 반려 이의 건수",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.ClaimSummary.class),
                            examples = @ExampleObject(value = CLAIM_SUMMARY))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminTransactionDto.ClaimSummary> getSummary(
            @Parameter(description = "브랜드(마켓) ID — 없으면 전 브랜드", example = "17") Long marketId);

    @Operation(summary = "반품·교환 상세 (06b B1)", description = """
            클레임 상세 모달입니다. `claim`은 파트너 상세(`SellerClaimDetailResponse`)와 같은 값이고(반려 6항목 · 구매확정 타이머 「정지 · 남은 N일」 ·
            운영자 개설 표시 포함), 바깥에 브랜드 · 귀책과 **운영자 조치 판단값**이 붙습니다. 소비자 연락처는 마스킹돼 있습니다.

            **권한:** ADMIN

            **운영자 조치 — 둘 중 하나만 참일 수 있습니다**

            | 판단값 | 버튼 | 조건 | 금액 |
            |---|---|---|---|
            | `canAcceptDispute` | B2 반려 이의 인용 | 반품 ∧ 반려됨 ∧ 아직 반송 전(반려 보류 · 재발송 대기) | `disputeRefundAmount` |
            | `canRefundUnanswered` | 검수 무응답 환불 | 검수 대기 ∧ 기한 경과 ∧ 검수 기한 자동 알림 3회 | `unansweredRefundAmount` |

            - 금액은 **서버 계산 · 수정 불가**입니다. FE 는 비활성 입력으로 보이고, 조치 API 는 금액을 받지 않습니다. 조치를 할 수 없으면 금액은 null
            - `disputeRefundAmount` = 단가 × 수량 + 통과분 환불에서 차감했던 반려 재발송비(환원)
            - `unansweredRefundAmount` = 단가 × 수량(차감 없음)

            **레일 정보**
            - `dispute` — ④ 소비자 이의. 앱 「이의 제기」로 걸린 **가장 최근** 1:1 문의의 원문 · 사진 · 접수 시각 · 답변 여부. 이의가 없었으면 null.
              기각은 버튼이 아니라 **그 문의의 답변**입니다(`inquiryId`로 1:1 문의 상세)
            - `inspectNotice` — B1 「자동 알림 N회」. 검수 기한 경과 자동 알림 횟수 · 마지막 시각(영업일 10시 · 15시 중 하루 1회). 06d `noticeCount`와 같은 값
            - `inspectOverdueBusinessDays` — 「검수 기한 N영업일 초과」. 검수 대기이고 기한이 지났을 때만, 아니면 null
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "클레임 상세",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.ClaimDetail.class), examples = {
                            @ExampleObject(name = "반려 이의 접수 · 인용 가능", summary = "canAcceptDispute · 인용액 24,600원 · dispute 블록", value = CLAIM_DETAIL_DISPUTE),
                            @ExampleObject(name = "검수 무응답 · 환불 가능", summary = "기한 3영업일 초과 · 알림 3회 → canRefundUnanswered", value = CLAIM_DETAIL_UNANSWERED)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 클레임 (`CLAIM_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_CLAIM_NOT_FOUND)))
    })
    ResponseEntity<AdminTransactionDto.ClaimDetail> getClaim(
            @Parameter(description = "클레임 ID", example = "3008", required = true) Long claimId);

    @Operation(summary = "검수 무응답 · 운영자 사유 환불 편입", description = """
            브랜드가 입고 뒤 검수를 끝내 하지 않아 **검수 기한 자동 알림이 대행 조건 횟수(기본 3회)에 닿은** 건의 출구입니다.
            운영자는 검수를 대신하지 않습니다 — 대신 소비자에게 돈을 돌려줍니다.

            **권한:** ADMIN

            **조건** — 상세 `canRefundUnanswered = true`(검수 대기 ∧ 기한 경과 ∧ 알림 3회). 06d 검수 지연 행의 「운영자 환불 가능」과 같은 판정입니다.

            **효과**(한 트랜잭션)
            - 클레임을 **환불로 종결** · 상품은 브랜드 창고에 있으므로 반품 수량에 반영
            - 환불액 = **서버 계산**(단가 × 수량 · 차감 없음). 요청에 금액이 없습니다
            - 교환이면 잡아 둔 새 옵션 재고를 되돌립니다
            - 귀책은 바꾸지 않습니다
            - 운영자 사유 환불(`reason = INSPECTION_UNANSWERED`)로 **편입** — 06c 집행 대기에 나타나고, 돈은 그 화면의 [집행]에서 나갑니다
            - **편입 철회 불가** — 클레임을 이미 종결했기 때문입니다(06c `voidable = false`)
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "편입 — 환불 큐 id · 환불번호 · 서버 계산 금액",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.DisputeAcceptResponse.class),
                            examples = @ExampleObject(value = CLAIM_REFUND_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "근거(`detail`) 누락 · 공백 · 500자 초과 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_NOT_BLANK))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 클레임 (`CLAIM_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_CLAIM_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "검수 대기가 아니거나 알림 횟수 미달 · 그 사이 상태 변경 (`CLAIM_STATE_CHANGED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "조건 미충족", value = ERR_UNANSWERED_NOT_REFUNDABLE),
                            @ExampleObject(name = "동시 변경", value = ERR_CLAIM_STATE_CHANGED)
                    }))
    })
    ResponseEntity<AdminTransactionDto.DisputeAcceptResponse> refundUnanswered(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "클레임 ID", example = "3021", required = true) Long claimId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "근거 — 이력 · 환불 관리에 남는다",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.ClaimRefundRequest.class),
                            examples = @ExampleObject(value = REQ_CLAIM_REFUND)))
            AdminTransactionDto.ClaimRefundRequest request);

    @Operation(summary = "B2 반려 이의 인용", description = """
            검수 반려에 대한 소비자 이의(앱 「이의 제기」 1:1 문의)를 **받아들입니다**. 반려를 환불로 뒤집는 운영자 조치입니다.

            **권한:** ADMIN

            **조건** — 상세 `canAcceptDispute = true`: **반품** ∧ 반려됨 ∧ 아직 반송 전(반려 보류 · 재발송 대기). 재발송 송장이 나간 뒤 · 종결 뒤는 409.
            교환 반려 이의는 기획 확정 전이라 받지 않습니다. 이의 문의가 없어도(전화 등) 운영자 판단으로 인용할 수 있습니다.

            **효과**(한 번에)
            1. 반려를 **환불로 닫습니다**(재발송 없음) · 귀책을 브랜드로 돌립니다(`faultChangedToSeller = true`) · 반품 수량에 반영합니다
            2. 환불액 = **서버 계산** — 단가 × 수량 + 통과분 환불에서 차감했던 재발송비(환원). 요청에 금액이 없습니다(상세 `disputeRefundAmount`)
            3. 반려 재발송비 청구 — 같은 박스에 재발송 대상 반려가 **이 건뿐**이면:

               | 청구 상태 | 처리 |
               |---|---|
               | 결제 대기 | 요청 소멸 |
               | 결제됨 | **결제 자동 취소**(인용 커밋 뒤 포트원 취소 · 실패하면 정리 배치가 재시도) |
               | 환불액에서 차감됨 | 소멸하고 환불액에 가산 |

               같은 박스에 다른 반려가 남아 있으면 그 건의 청구라 건드리지 않습니다.
            4. 운영자 사유 환불(`reason = DISPUTE_ACCEPTED`)로 **편입** — 돈은 06c 재확인 다이얼로그에서만 나갑니다
            5. **편입 철회 불가** — 위 정리가 되돌릴 수 없어서입니다(06c `voidable = false`)

            **기각**은 버튼이 아니라 이의 문의의 **답변**입니다 — 반려는 그대로 유지되고 `disputeOpen`이 꺼집니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인용 — 편입된 환불 큐 id · 환불번호 · 서버 계산 금액",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.DisputeAcceptResponse.class),
                            examples = @ExampleObject(summary = "단가 × 수량 24,600 · 결제 대기였던 반려 재발송비 3,000 은 소멸", value = DISPUTE_ACCEPT_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "근거(`detail`) 누락 · 공백 · 500자 초과 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_NOT_BLANK))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 클레임 (`CLAIM_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_CLAIM_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "반려된 반품이 아님 · 이미 반송 중 · 종결 · 그 사이 상태 변경 (`CLAIM_STATE_CHANGED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "조건 미충족", value = ERR_DISPUTE_NOT_ACCEPTABLE),
                            @ExampleObject(name = "동시 변경", value = ERR_CLAIM_STATE_CHANGED)
                    }))
    })
    ResponseEntity<AdminTransactionDto.DisputeAcceptResponse> acceptDispute(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "클레임 ID", example = "3008", required = true) Long claimId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "인용 근거 — 이력 · 환불 관리에 남는다",
                    content = @Content(schema = @Schema(implementation = AdminTransactionDto.DisputeAcceptRequest.class),
                            examples = @ExampleObject(value = REQ_DISPUTE_ACCEPT)))
            AdminTransactionDto.DisputeAcceptRequest request);
}
