package showroomz.api.admin.settlement.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
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
                    | `EVIDENCE` | 증빙 문서 | 기한 이른순 | `count` · `operatorActionCount` · `payoutBlockedCount` — **증빙 단계(V181) 전까지 빈 목록** |
                    | `CLAWBACK` | 차감 | 발생일 최신순 | `count` · `unrecoverableCount` · `unrecoverableAmount` — **차감 단계(V182) 전까지 빈 목록** |

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
                    - `gnbBadge` — **운영자 조치만**: 분배 실패 + 차감 미회수 + 증빙 확인 대기 · 발행 대기. 조정 협의 · 주민번호 미등록은 다음 차례가 당사자라 세지 않습니다.

                    **권한:** ADMIN
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    AdminSettlementDto.Summary summary();

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
                    | 차감 반영 | `clawbacksApplied` | 차감 단계(V182) 전까지 [] |
                    | 조정 내역 | `adjustment` | 없으면 null. 제안마다 `preview`(그 금액으로 확정했다면의 실지급 · 브랜드 수취액). `threadId` 로 20b 이슈 스레드를 연다 |
                    | 3자 분배 | `payouts` | 확정 전(`REVIEWING` · `ADJUSTING`) null. 계좌는 **전체 노출**(기본정보 정책) — 지시 뒤 `SNAPSHOT`, 지시 전 `CURRENT_PROFILE`. `check` = 수취자 합 + PG + 원천징수 vs 확정 거래액 + 재발송비 + 소비자 배송비 |
                    | 증빙 | `taxDocuments` | 증빙 단계(V181) 전까지 [] |
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
}
