package showroomz.api.admin.settlement.docs;

import java.time.LocalDate;
import java.time.YearMonth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.admin.settlement.dto.AdminSettlementDto;
import showroomz.api.admin.settlement.type.AdminSettlementSort;
import showroomz.api.admin.settlement.type.AdminSettlementTab;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Settlement", description = "어드민 정산 관리 API (07a 목록 · 07b 상세 · 44 어드민 정산관리 설계서 7절)")
public interface AdminSettlementControllerDocs {

    @Operation(summary = "정산 목록 (07a)",
            description = """
                    탭별 정산 목록 · 합계 행 · 탭 툴바를 한 번에 내립니다.

                    **권한:** ADMIN

                    **탭 `tab`** (기본 `ALL`)

                    | 탭 | 행 | 정렬 | 툴바 |
                    |---|---|---|---|
                    | `ALL` | 정산 | `sort` — `SCHEDULE_DESC`(일정 최신순 · 기본) · `SALES_DESC`(확정 거래액순) | 합계 행 `footer` |
                    | `REVIEWING` | 정산 | 확인 마감 이른순(고정) | — |
                    | `ADJUSTING` | 정산 | 합의 기한 이른순(고정) | `heldAmount`(보류 합계) · `earliestDeadlineAt` |
                    | `PAYOUT_FAILED` | 정산 | 실패 경과 오래된순(고정) | `undeliveredAmount` · `failedPayeeLabel` · `elapsedDays` |
                    | `EVIDENCE` | 증빙 문서(`AdminSettlementEvidenceItem`) — 처리 끝 제외 · 주민번호 미등록 가상 행 포함 | 기한 이른순 | `count` · `operatorActionCount`(확인 대기 + 발행 대기) · `payoutBlockedCount` |
                    | `CLAWBACK` | 차감 번호(`AdminSettlementClawbackItem` — 측별 행을 한 줄로) | 발생일 최신순 | `count` · `unrecoverableCount` · `unrecoverableAmount` — 검색은 `CLW-` · 원 정산번호 · 주문번호 · 브랜드명 · 쇼룸명 |

                    - **어드민은 분배 실패(`PAYOUT_FAILED`)를 접지 않습니다**(파트너 · 스튜디오는 지급 완료로 접는다).
                    - `scheduleAt` · `scheduleKind` — 「일정」 열. `REVIEW_DUE`(확인 마감) · `AGREEMENT_DUE`(합의 기한) · `PAYOUT_DUE`(지급 예정일 00:00) ·
                      `PAID_AT`(지급 완료) · `FAILED_AT`(분배 실패 시각).
                    - **검색 `keyword`** — `STL-`로 시작하면 정산번호 전방 일치(대소문자 무시), 아니면 공구명 · 브랜드명 · 쇼룸명 부분 일치.
                    - **합계 행 `footer`(ALL 탭)** — 지급 예정 · 지급 완료 · 분배 실패만 합산합니다. 정산 확인 중 · 조정 협의는 금액이 확정 전이라 뺍니다.
                      검색어가 있으면 같은 조건으로 합산합니다.
                    - 기간 필터는 없습니다(07a 「기간 프리셋 삭제」).
                    - 페이징 `page`(1부터) · `size`(기본 20 · 최대 100).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "없는 tab · sort 값 · size 범위 밖 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminSettlementDto.ListResponse<?> list(
            @Parameter(description = "ALL · REVIEWING · ADJUSTING · PAYOUT_FAILED · EVIDENCE · CLAWBACK", example = "ALL")
            AdminSettlementTab tab,
            @Parameter(description = "STL- 정산번호 전방 일치 · 공구명 · 브랜드명 · 쇼룸명", example = "STL-2609") String keyword,
            @Parameter(description = "[ALL 탭] SCHEDULE_DESC(기본) · SALES_DESC", example = "SCHEDULE_DESC") AdminSettlementSort sort,
            PagingRequest paging);

    @Operation(summary = "탭 숫자 · GNB 배지 (07a)",
            description = """
                    - `tabCounts` — 탭별 건수(`ALL` · `REVIEWING` · `ADJUSTING` · `PAYOUT_FAILED` · `EVIDENCE` · `CLAWBACK`). 증빙 · 차감은 해당 단계 전까지 0.
                    - `gnbBadge` — **운영자 조치만**: 분배 실패 + 차감 미회수(번호 수) + 인플루언서 세금계산서 확인 대기 + 브랜드 세금계산서(수정 포함) 발행 대기.
                      조정 협의 · 주민번호 미등록은 다음 차례가 당사자라 세지 않습니다.

                    **권한:** ADMIN
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    AdminSettlementDto.Summary summary();

    @Operation(summary = "원천세 신고 자료 다운로드 (07a 툴바)",
            description = """
                    `month`(YYYY-MM)에 **인플루언서 몫 지급 완료**가 속한 비사업자 정산 — 정산번호 · 공구 · 인플루언서 실명 · **주민등록번호(복호화)** ·
                    지급액(차감 후 리워드) · 소득세 · 지방소득세 · 지급일 + 합계 행. 주민등록번호는 이 파일에만 쓰고, 내려받을 때마다 반출 기록
                    (운영자 · 월 · 행 수)을 남깁니다. 지급이 없는 달은 헤더만 있는 파일입니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            @ApiResponse(responseCode = "400", description = "month 누락 · 형식 오류(YYYY-MM) (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> withholdingReport(@Parameter(description = "지급 월 YYYY-MM", example = "2026-09") YearMonth month,
                                             @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "이슈 스레드의 정산 영향 (20b 이슈 패널)",
            description = """
                    20b 이슈 스레드 우측 패널의 「정산 영향 · 진행 단계」 보강 — 상세(07b)의 `rail` + `adjustment` 입니다.
                    정산 조정 스레드가 아니면 404 `SETTLEMENT_ADJUSTMENT_NOT_FOUND`.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_ADJUSTMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminSettlementDto.ByThread byThread(@Parameter(description = "이슈 스레드 id") Long threadId);

    @Operation(summary = "정산 상세 (07b)",
            description = """
                    | 블록 | 필드 | 규칙 |
                    |---|---|---|
                    | 개요 · 단계 줄 | `overview` · `stage` | `stage.current` — `REVIEW` · `ADJUSTMENT` · `CONFIRMED`(지급 예정 · 분배 실패) · `PAID`. `adjustmentSkipped` = 조정 없이 확정 |
                    | 금액 분해 | `breakdown.brand` · `breakdown.creator` · `platformShareAmount` | 요율은 정산 행의 스냅샷. `originalRewardAmount ≠ rewardAmount` 면 합의로 바뀐 것. 사업자는 `creator.withholding = null` |
                    | 차감 반영 | `clawbacksApplied` | 이 정산에서 회수한 이전 회차 환불 — 번호별 브랜드 · 인플루언서 측 금액. 분해의 `brand.clawbackAmount` · `creator.clawbackAmount` 와 합이 같다 |
                    | 조정 내역 | `adjustment` | 없으면 null. 제안마다 `preview`(그 금액으로 확정했다면의 실지급 · 브랜드 수취액). `threadId` 로 20b 이슈 스레드를 연다 |
                    | 3자 분배 | `payouts` | 확정 전(`REVIEWING` · `ADJUSTING`) null. 계좌는 **전체 노출**(기본정보 정책) — 지시 뒤 `SNAPSHOT`, 지시 전 `CURRENT_PROFILE`. `check` = 수취자 합 + PG + 원천징수 vs 확정 거래액 + 재발송비 + 소비자 배송비 |
                    | 증빙 | `taxDocuments` | 확정 시 생긴다 — 브랜드 세금계산서(기한 다음 달 10일) · 사업자면 인플루언서 세금계산서 · 지급 완료 뒤 원천징수영수증. 승인번호는 앞 8 · 뒤 4. `actions.canVerify / canRegister` 가 M4 · M5 버튼 |
                    | 고정 지급비 | `fixedFee` | 계약 읽기 전용 · 정산 대상 아님 |
                    | 명세 | `items` | 최근 5건 + 합계(`itemRewardTotal` vs 정산 `rewardAmount`) — 전체는 `GET …/items` |
                    | 우 레일 | `rail` · `actions` | `blockReasons` — 인플루언서 행 보류 사유. `failedPayout` — 분배 실패 행 · 재분배 횟수/상한 |
                    | 처리 이력 | `history` | 최신순 · `actorLabel` = 시스템 · PG · 운영자 이름 · 브랜드명 · 쇼룸명 |

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminSettlementDto.Detail detail(@Parameter(description = "정산 id") Long settlementId);

    @Operation(summary = "정산 명세 (07b)",
            description = """
                    명세 행 전부 — 주문번호 내림차순 · 상태 무관. 소비자 이름은 마스킹입니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    PageResponse<AdminSettlementDto.Item> items(@Parameter(description = "정산 id") Long settlementId,
                                                PagingRequest paging);

    @Operation(summary = "정산 명세 엑셀 (07b)",
            description = """
                    명세 행 + 분해 요약 시트. 파일명 `정산명세_{settlementNumber}.xlsx`.
                    확정 전(`REVIEWING` · `ADJUSTING`)이면 409 `SETTLEMENT_STATEMENT_NOT_READY` — 레일 버튼 비활성과 같은 조건(세 서피스 공통).

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_STATEMENT_NOT_READY",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> statement(@Parameter(description = "정산 id") Long settlementId);

    @Operation(summary = "재분배 (07b M3)",
            description = """
                    분배 실패 행을 **같은 금액**으로 다시 지시합니다 — 행을 오늘 예정으로 되돌리고(재분배 횟수 + 1) 정산은 지급 예정으로 재파생되며,
                    지시는 **커밋 뒤 바로** 나갑니다(배치를 기다리지 않는다). 다시 실패하면 정산은 분배 실패로 돌아갑니다. 응답은 갱신된 상세입니다.

                    - `accountSource` — `CURRENT_PROFILE`(회원 정보의 현재 계좌로 다시 스냅샷 — 회원이 계좌를 고친 뒤) · `PREVIOUS`(지난 지시 계좌 그대로).
                      **운영자는 계좌를 입력하지 않습니다.**
                    - 재분배 상한(`settlement.payout-retry-limit` · 기본 3회)을 넘으면 409 — 수동 이체 절차로 갑니다.
                    - 이력 `PAYOUT_RETRIED`(운영자 · 회차 · 계좌 출처).

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재분배 지시 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "SETTLEMENT_ACCOUNT_MISSING(현재 회원 정보에 계좌 없음 · 지난 지시 계좌 없음) · INVALID_INPUT(accountSource 누락)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_STATE_CHANGED(분배 실패 행이 아님) · SETTLEMENT_PAYOUT_RETRY_EXCEEDED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminSettlementDto.Detail redistribute(@Parameter(description = "정산 id") Long settlementId,
                                           @Parameter(description = "수취자 행 id — 상세 payouts[].payoutId") Long payoutId,
                                           AdminSettlementDto.RedistributeRequest request,
                                           @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "인플루언서 세금계산서 승인번호 대조 (07b M4)",
            description = """
                    인플루언서가 입력한 승인번호를 국세청에서 대조한 **결과만** 고릅니다 — 운영자는 번호를 입력하지 않습니다.

                    - `MATCH` → 확인 완료 · 인플루언서 몫 지급 보류 해제(지급 예정일 = 확인일 + 3영업일) · 인플루언서 알림
                    - `AMOUNT_MISMATCH` · `RECIPIENT_MISMATCH` · `NOT_FOUND` → 반려(사유가 스튜디오 카드에 뜬다) · 인플루언서가 같은 행에 다시 입력
                    - 인플루언서 세금계산서가 확인 대기(`SUBMITTED`)가 아니면 409

                    응답은 갱신된 상세입니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대조 결과 반영 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "result 누락 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminSettlementDto.Detail verifyTaxInvoice(@Parameter(description = "정산 id") Long settlementId,
                                               @Parameter(description = "문서 id — taxDocuments[].documentId") Long documentId,
                                               AdminSettlementDto.VerifyRequest request,
                                               @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "브랜드 세금계산서 발행본 등록 (07b M5)",
            description = """
                    multipart — `file`(PDF · 10MB) · `approvalNumber`(국세청 승인번호 24자리) · `issuedDate`(YYYY-MM-DD).
                    브랜드 세금계산서(수정 포함)가 발행 대기(`PENDING_ISSUE`)일 때만 — **정상 등록 뒤 버튼이 사라지고 다시 호출되지 않습니다**
                    (정정은 수정세금계산서 새 행). 등록하면 파트너센터 `[다운로드]`가 열립니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "SETTLEMENT_TAX_INVOICE_NUMBER_INVALID · INVALID_INPUT(파일 · 발행일)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminSettlementDto.Detail issueBrandInvoice(@Parameter(description = "정산 id") Long settlementId,
                                                @Parameter(description = "문서 id") Long documentId,
                                                @Parameter(description = "발행본 PDF") MultipartFile file,
                                                @Parameter(description = "국세청 승인번호", example = "20261010-41000012-38475920")
                                                String approvalNumber,
                                                @Parameter(description = "발행일", example = "2026-10-10") LocalDate issuedDate,
                                                @Parameter(hidden = true) UserPrincipal principal);
}
