package showroomz.api.admin.settlement.docs;

import java.time.LocalDate;
import java.time.YearMonth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

import static showroomz.api.admin.settlement.docs.AdminSettlementDocsExamples.*;

@Tag(name = "Admin - Settlement", description = "관리자 정산 관리 API (07a · 07b)")
public interface AdminSettlementControllerDocs {

    String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Operation(summary = "정산 목록 (07a)",
            description = """
                    탭별 정산 목록 · 합계 행 · 탭 툴바를 한 번에 내립니다. 탭 숫자 · GNB 배지는 `GET /v1/admin/settlements/summary`.

                    **권한:** ADMIN

                    **탭 `tab`** (기본 `ALL`) — **증빙 · 차감 탭은 행의 모양이 다릅니다**(`content` 원소 타입이 갈린다).

                    | 탭 | 행(`content[]`) | 정렬 | 툴바(`toolbar`) |
                    |---|---|---|---|
                    | `ALL` | 정산(`AdminSettlementListItem`) | `sort` — `SCHEDULE_DESC`(일정 최신순 · 기본) · `SALES_DESC`(확정 거래액순) | 없음 — 대신 합계 행 `footer` |
                    | `REVIEWING` | 정산 | 확인 마감 이른순(고정) | 없음 |
                    | `ADJUSTING` | 정산 | 합의 기한 이른순(고정) | `heldAmount`(보류 합계 = 확정 거래액 합) · `earliestDeadlineAt` |
                    | `PAYOUT_FAILED` | 정산 | 실패 경과 오래된순(고정) | `undeliveredAmount` · `failedPayeeLabel`(「인플루언서 2 · 브랜드 1」) · `elapsedDays`(최장) |
                    | `EVIDENCE` | 증빙 문서 1건(`AdminSettlementEvidenceItem`) — 정산 1건에 2건일 수 있다 | 기한 이른순(기한 없는 행은 뒤) | `count` · `operatorActionCount`(확인 대기 + 발행 대기) · `payoutBlockedCount` |
                    | `CLAWBACK` | 차감 번호 1건(`AdminSettlementClawbackItem`) — 측별 행을 한 줄로 | 발생일 최신순 | `count` · `unrecoverableCount` · `unrecoverableAmount` |

                    `toolbar` 는 **값이 있는 칸만** 내려갑니다(탭마다 칸이 다르다). 툴바가 없는 탭은 `toolbar = null`, 전체 탭이 아니면 `footer = null`.

                    **정산 행**
                    - **어드민은 분배 실패(`PAYOUT_FAILED`)를 접지 않습니다**(파트너 · 스튜디오는 지급 완료로 접는다).
                    - `scheduleAt` · `scheduleKind` — 「일정」 열. 상태별로 다음 날짜 하나:

                    | `scheduleKind` | 상태 | 값 |
                    |---|---|---|
                    | `REVIEW_DUE` | 정산 확인 중 | 확인 마감(23:59:59) |
                    | `AGREEMENT_DUE` | 조정 협의 | 합의 기한(23:59:59) |
                    | `PAYOUT_DUE` | 지급 예정 | 지급 예정일 00:00 |
                    | `PAID_AT` | 지급 완료 | 지급 완료 시각 |
                    | `FAILED_AT` | 분배 실패 | 가장 최근 실패 시각 |

                    **증빙 행** — 처리 끝(확인 · 발행 · 생성 완료)은 빠집니다. 비사업자 **주민번호 미등록**은 문서가 아니라 보류 사유지만
                    `type = RESIDENT_NUMBER_MISSING` · `documentId = null` · `status = WAITING_CREATOR` 가상 행으로 함께 내립니다.
                    `payoutImpact = BLOCKING` 이면 인플루언서 몫 지급을 막는 행입니다.

                    **차감 행** — 측별 상태(`brandStatus` · `creatorStatus`)는 `PENDING`(차감 예정 · 이월 중 포함) · `APPLIED`(차감 반영) · `UNRECOVERABLE`(미회수).
                    `appliedSettlementNumber` 는 마지막으로 반영된 정산입니다.

                    **검색 `keyword`**
                    - 정산 탭 — `STL-`로 시작하면 정산번호 **전방 일치**(대소문자 무시), 아니면 공구명 · 브랜드명 · 쇼룸명 부분 일치
                    - 차감 탭 — `CLW-` 번호 · 원 정산번호 · 주문번호 · 브랜드명 · 쇼룸명 부분 일치
                    - 증빙 탭은 검색을 받지 않습니다

                    **합계 행 `footer`(ALL 탭)** — 지급 예정 · 지급 완료 · 분배 실패만 합산합니다. 정산 확인 중 · 조정 협의는 금액이 확정 전이라 뺍니다.
                    검색어가 있으면 같은 조건으로 합산합니다(페이지와 무관하게 조건 전체).

                    기간 필터는 없습니다(07a 「기간 프리셋 삭제」). 페이징 `page`(1부터) · `size`(기본 20 · **1~100**, 벗어나면 400).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록 · 페이지 정보 · 합계 행 · 툴바",
                    content = @Content(schema = @Schema(implementation = AdminSettlementDto.ListResponse.class), examples = {
                            @ExampleObject(name = "전체", summary = "tab=ALL&size=5 — 일정 최신순 · 합계 행", value = LIST_ALL),
                            @ExampleObject(name = "조정 협의", summary = "tab=ADJUSTING — 보류 합계 · 가장 이른 합의 기한", value = LIST_ADJUSTING),
                            @ExampleObject(name = "분배 실패", summary = "tab=PAYOUT_FAILED — 미지급 합계 · 실패 수취자 · 경과일", value = LIST_PAYOUT_FAILED),
                            @ExampleObject(name = "증빙", summary = "tab=EVIDENCE — 문서 1건 = 1행 · 주민번호 미등록 가상 행", value = LIST_EVIDENCE),
                            @ExampleObject(name = "차감", summary = "tab=CLAWBACK — 차감 번호 1건 = 1행 · 인플루언서 측 미회수", value = LIST_CLAWBACK)
                    })),
            @ApiResponse(responseCode = "400", description = "없는 tab · sort 값 · size 1~100 밖 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "페이지 크기", value = ERR_PAGE_SIZE),
                            @ExampleObject(name = "enum", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    AdminSettlementDto.ListResponse<?> list(
            @Parameter(description = "탭 — ALL(기본) · REVIEWING · ADJUSTING · PAYOUT_FAILED · EVIDENCE · CLAWBACK", example = "ALL")
            AdminSettlementTab tab,
            @Parameter(description = "STL- 정산번호 전방 일치 · 공구명 · 브랜드명 · 쇼룸명. [차감 탭] CLW- · 주문번호도", example = "STL-2609")
            String keyword,
            @Parameter(description = "[ALL 탭] SCHEDULE_DESC(기본) · SALES_DESC — 다른 탭은 고정 정렬이라 무시", example = "SCHEDULE_DESC")
            AdminSettlementSort sort,
            PagingRequest paging);

    @Operation(summary = "탭 숫자 · GNB 배지 (07a)",
            description = """
                    목록의 탭 숫자와 사이드바 「정산 관리」 배지를 내립니다. 검색어를 받지 않습니다(탭 숫자는 전체 기준).

                    **권한:** ADMIN

                    | 필드 | 내용 |
                    |---|---|
                    | `tabCounts` | 탭별 건수 — `ALL` · `REVIEWING` · `ADJUSTING` · `PAYOUT_FAILED` · `EVIDENCE`(열린 증빙 + 주민번호 미등록) · `CLAWBACK`(차감 번호 수) |
                    | `gnbBadge` | **운영자 조치만** — 분배 실패 + 차감 미회수(번호 수) + 인플루언서 세금계산서 확인 대기 + 브랜드 세금계산서(수정 포함) 발행 대기 |

                    조정 협의 · 주민번호 미등록은 다음 차례가 당사자라 배지에 세지 않습니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탭 숫자 · 배지",
                    content = @Content(schema = @Schema(implementation = AdminSettlementDto.Summary.class),
                            examples = @ExampleObject(summary = "배지 5 = 분배 실패 1 + 증빙 조치 3 + 미회수 1", value = SUMMARY))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    AdminSettlementDto.Summary summary();

    @Operation(summary = "원천세 신고 자료 다운로드 (07a 툴바)",
            description = """
                    `month`(YYYY-MM)에 **인플루언서 몫 지급 완료**가 속한 비사업자 정산을 엑셀로 내려받습니다.

                    **권한:** ADMIN

                    **열** — 정산번호 · 공구 · 인플루언서 실명 · **주민등록번호(복호화)** · 지급액(차감 후 리워드) · 소득세 · 지방소득세 · 지급일 + 합계 행.

                    - 주민등록번호는 이 파일에만 씁니다. 내려받을 때마다 **반출 기록**(운영자 · 월 · 행 수)을 남깁니다.
                    - 지급이 없는 달은 헤더만 있는 파일입니다(404 가 아니다).
                    - 사업자 정산은 들어가지 않습니다(원천징수 없음 — 세금계산서).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx", content = @Content(mediaType = XLSX)),
            @ApiResponse(responseCode = "400", description = "month 누락 · 형식 오류(YYYY-MM) (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "누락", value = ERR_MONTH_REQUIRED),
                            @ExampleObject(name = "형식", summary = "month=2026-9", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<byte[]> withholdingReport(
            @Parameter(description = "지급 월 YYYY-MM — 필수", example = "2026-09", required = true) YearMonth month,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "이슈 스레드의 정산 영향 (20b 이슈 패널)",
            description = """
                    20b 이슈 스레드 우측 패널의 「정산 영향 · 진행 단계」 보강 — 상세(07b)의 `rail` + `adjustment` 와 **같은 모양**입니다.
                    상세로 가는 링크는 `settlementId`.

                    **권한:** ADMIN

                    정산 조정 스레드가 아니면(일반 이슈 스레드 · 없는 스레드) 404 `SETTLEMENT_ADJUSTMENT_NOT_FOUND`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정산 레일 · 조정 내역",
                    content = @Content(schema = @Schema(implementation = AdminSettlementDto.ByThread.class),
                            examples = @ExampleObject(summary = "조정 협의 중 · 인플루언서 다른 금액 제안 응답 대기", value = BY_THREAD))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "정산 조정 스레드가 아님 (`SETTLEMENT_ADJUSTMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ADJUSTMENT_NOT_FOUND)))
    })
    AdminSettlementDto.ByThread byThread(@Parameter(description = "이슈 스레드 id", example = "812", required = true) Long threadId);

    @Operation(summary = "정산 상세 (07b)",
            description = """
                    07b 한 화면이 이 응답 하나를 씁니다. 블록은 상태에 따라 `null` · `[]` 입니다 — FE 는 비어 있는 블록을 그리지 않습니다.

                    **권한:** ADMIN

                    | 블록 | 필드 | 규칙 |
                    |---|---|---|
                    | 개요 · 단계 줄 | `overview` · `stage` | `stage.current` — `REVIEW` · `ADJUSTMENT` · `CONFIRMED`(지급 예정 · 분배 실패) · `PAID`. `adjustmentSkipped` = 조정 없이 확정 |
                    | 금액 분해 | `breakdown.brand` · `breakdown.creator` · `platformShareAmount` | 요율은 정산 행의 스냅샷. `originalRewardAmount ≠ rewardAmount` 면 합의로 바뀐 것. 사업자는 `creator.withholding = null` · `creator.vatAmount` 가 있다 |
                    | 차감 반영 | `clawbacksApplied` | 이 정산에서 회수한 이전 회차 환불 — 번호별 브랜드 · 인플루언서 측 금액. 분해의 `brand.clawbackAmount` · `creator.clawbackAmount` 와 합이 같다 |
                    | 조정 내역 | `adjustment` | 없으면 null. 제안마다 `preview`(그 금액으로 확정했다면의 실지급 · 브랜드 수취액). `threadId` 로 20b 이슈 스레드를 연다 |
                    | 3자 분배 | `payouts` | 확정 전(`REVIEWING` · `ADJUSTING`) null(「확정 후 표시」). 아래 「분배 행」 |
                    | 증빙 | `taxDocuments` | 확정 시 생긴다 — 아래 「증빙」 |
                    | 고정 지급비 | `fixedFee` | 계약 읽기 전용 · 정산 대상 아님. 계약에 없으면 `amount` · `trigger` 가 null |
                    | 명세 | `items` | 최근 5건(주문번호 내림차순) + 합계(`itemRewardTotal` vs 정산 `rewardAmount`) — 전체는 `GET …/items` |
                    | 우 레일 | `rail` · `actions` | `blockReasons` — 인플루언서 행 보류 사유(`TAX_INVOICE_UNVERIFIED` · `RESIDENT_NUMBER_MISSING`). `failedPayout` — 분배 실패일 때 첫 실패 행 · 재분배 횟수/상한 |
                    | 처리 이력 | `history` | 최신순 · `actorLabel` = 시스템 · PG · 운영자 이름 · 브랜드명 · 쇼룸명 |

                    **분배 행 `payouts.rows[]`** — 브랜드 · 인플루언서 · 플랫폼 순.
                    - 계좌는 **전체 노출**(기본정보 정책). `accountSource` — `SNAPSHOT`(지급 지시 때 찍은 값) · `CURRENT_PROFILE`(지시 전 — 회원 정보의 현재 계좌) · `NONE`(플랫폼 · 계좌 없음)
                    - `status` — `SCHEDULED`(지급 예정) · `REQUESTED`(지급 처리 중) · `PAID` · `FAILED`(분배 실패) · `BLOCKED`(증빙 · 주민번호 보류) · `NOT_APPLICABLE`(0원 행 「—」)
                    - 재분배 뒤에도 지난 실패 기록(`failedAt` · `failCode` · `failReason`)은 남고 `attempt` 가 늘어납니다
                    - `check` — tfoot 검산: 수취자 합 + PG 수수료 + 원천징수(`total`) = 확정 거래액 + 재발송비 + 소비자 배송비(`inflowAmount`) → `balanced`

                    **증빙 `taxDocuments[]`** — 브랜드 세금계산서(기한 = 확정 다음 달 10일) · 사업자면 인플루언서 세금계산서 · 지급 완료 뒤 원천징수영수증.
                    승인번호는 앞 8 · 뒤 4 만 보입니다. 문서별 `actions.canVerify`(M4) · `canRegister`(M5)가 버튼입니다.

                    **버튼 `actions`**

                    | 필드 | 조건 |
                    |---|---|
                    | `canRedistribute` | 분배 실패 ∧ 실패 행 재분배 횟수 < 상한 |
                    | `canVerifyInvoice` | 인플루언서 세금계산서 확인 대기(`SUBMITTED`) |
                    | `canRegisterBrandInvoice` | 브랜드 세금계산서(수정 포함) 발행 대기(`PENDING_ISSUE`) |
                    | `canDownloadStatement` | 확정 후(정산 확인 중 · 조정 협의 아님) |
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정산 상세",
                    content = @Content(schema = @Schema(implementation = AdminSettlementDto.Detail.class), examples = {
                            @ExampleObject(name = "지급 예정 · 비사업자", summary = "자동 확정 → 10.12 지급 예정 · 브랜드 세금계산서 발행 대기", value = DETAIL_SCHEDULED),
                            @ExampleObject(name = "조정 협의 · 사업자", summary = "브랜드 요청 270,000 → 인플루언서 282,000 응답 대기 · 분배 null", value = DETAIL_ADJUSTING),
                            @ExampleObject(name = "분배 실패", summary = "브랜드 행 예금주 불일치 · 재분배 가능 · 원천징수영수증 생성", value = DETAIL_PAYOUT_FAILED),
                            @ExampleObject(name = "세금계산서 확인 대기 · 사업자", summary = "인플루언서 몫 지급 보류 · 대조(M4) 가능", value = DETAIL_INVOICE_SUBMITTED)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없는 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND)))
    })
    AdminSettlementDto.Detail detail(@Parameter(description = "정산 id", example = "26", required = true) Long settlementId);

    @Operation(summary = "정산 명세 (07b)",
            description = """
                    명세 행(주문 항목 1행) 전부 — 주문번호 내림차순 · **상태 무관**하게 열립니다(확정 전에도). 소비자 이름은 마스킹입니다.

                    **권한:** ADMIN

                    - `status` — `CONFIRMED`(구매확정) · `PARTIAL_RETURNED`(부분 반품 — `returnedQuantity` · `settledQuantity` 병기) · `RETURNED` · `CANCELLED` · `DELIVERY_EXCEPTION`(반송 · 분실 · 정산 전 운영자 사유 환불)
                    - `settledAmount` = 단가 × 반영 수량 · `rewardAmount` = 1개당 리워드(1원 절사) × 반영 수량 · `rewardRate` 는 % 값(10.0 = 10%)
                    - 페이징 `page`(1부터) · `size`(기본 20 · 1~100)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "명세 페이지",
                    content = @Content(schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(summary = "size=5 — 부분 반품 · 취소 행 포함", value = ITEMS))),
            @ApiResponse(responseCode = "400", description = "size 1~100 밖 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_PAGE_SIZE))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없는 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND)))
    })
    PageResponse<AdminSettlementDto.Item> items(@Parameter(description = "정산 id", example = "26", required = true) Long settlementId,
                                                PagingRequest paging);

    @Operation(summary = "정산 명세 엑셀 (07b)",
            description = """
                    명세 행 + 분해 요약 시트. 파일명 `정산명세_{settlementNumber}.xlsx`(`Content-Disposition: attachment; filename*=UTF-8''…`).

                    **권한:** ADMIN

                    **확정 후만** — 정산 확인 중 · 조정 협의면 409 `SETTLEMENT_STATEMENT_NOT_READY`.
                    레일 버튼(`actions.canDownloadStatement`) 비활성과 같은 조건이고 세 서피스가 같습니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx", content = @Content(mediaType = XLSX)),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없는 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "확정 전 (`SETTLEMENT_STATEMENT_NOT_READY`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_STATEMENT_NOT_READY)))
    })
    ResponseEntity<byte[]> statement(@Parameter(description = "정산 id", example = "26", required = true) Long settlementId);

    @Operation(summary = "재분배 (07b M3)",
            description = """
                    분배 실패 행을 **같은 금액**으로 다시 지시합니다. 운영자는 계좌를 입력하지 않고 출처만 고릅니다.

                    **권한:** ADMIN

                    **요청 `accountSource`**
                    - `CURRENT_PROFILE` — 회원 정보(브랜드 · 인플루언서)의 **현재 계좌로 다시 스냅샷**. 회원이 계좌를 고친 뒤 · 예금주 불일치
                    - `PREVIOUS` — 지난 지시 계좌 그대로. PG 일시 장애 등 계좌와 무관한 실패

                    **처리** — 행을 오늘 예정으로 되돌리고(`attempt + 1`) 정산은 지급 예정으로 재파생되며, 지시는 **커밋 뒤 바로** 나갑니다(배치를 기다리지 않는다).
                    응답은 그 뒤의 상세라 보통 그 행이 `REQUESTED`(지급 처리 중)로 보입니다. 다시 실패하면 정산은 분배 실패로 돌아갑니다.
                    지난 실패 기록(`failedAt` · `failCode`)은 지우지 않습니다. 이력 `PAYOUT_RETRIED`(「브랜드 · 1회차 · 회원 정보 현재 계좌 · 확정 금액 그대로」).

                    | 검사 | 실패 |
                    |---|---|
                    | 없는 정산 · 다른 정산의 행 | 404 `SETTLEMENT_NOT_FOUND` |
                    | 정산이 분배 실패가 아님 · 행이 `FAILED` 가 아님(그 사이 처리됨) | 409 `SETTLEMENT_STATE_CHANGED` |
                    | 재분배 상한(`settlement.payout-retry-limit` · 기본 3회) 도달 | 409 `SETTLEMENT_PAYOUT_RETRY_EXCEEDED` — 수동 이체 절차로 |
                    | `CURRENT_PROFILE` 인데 회원 정보에 계좌 없음 · `PREVIOUS` 인데 지난 지시 계좌 없음 | 400 `SETTLEMENT_ACCOUNT_MISSING` |
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재분배 지시 — 갱신된 상세",
                    content = @Content(schema = @Schema(implementation = AdminSettlementDto.Detail.class),
                            examples = @ExampleObject(summary = "브랜드 행 REQUESTED · attempt 1 · 정산 지급 예정 · 이력 PAYOUT_RETRIED", value = DETAIL_REDISTRIBUTED))),
            @ApiResponse(responseCode = "400", description = "계좌 없음 (`SETTLEMENT_ACCOUNT_MISSING`) · accountSource 누락 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "계좌 없음", value = ERR_ACCOUNT_MISSING),
                            @ExampleObject(name = "accountSource 누락", value = ERR_NOT_NULL)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없는 정산 · 다른 정산의 행 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "분배 실패 행이 아님 (`SETTLEMENT_STATE_CHANGED`) · 상한 도달 (`SETTLEMENT_PAYOUT_RETRY_EXCEEDED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "상태 변경", value = ERR_STATE_CHANGED),
                            @ExampleObject(name = "상한 도달", value = ERR_PAYOUT_RETRY_EXCEEDED)
                    }))
    })
    AdminSettlementDto.Detail redistribute(
            @Parameter(description = "정산 id", example = "24", required = true) Long settlementId,
            @Parameter(description = "수취자 행 id — 상세 payouts.rows[].payoutId · rail.failedPayout.payoutId", example = "70", required = true)
            Long payoutId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "계좌 출처 — 운영자는 계좌를 입력하지 않는다",
                    content = @Content(schema = @Schema(implementation = AdminSettlementDto.RedistributeRequest.class), examples = {
                            @ExampleObject(name = "회원 정보 현재 계좌", summary = "회원이 계좌를 고친 뒤 · 예금주 불일치", value = REQ_REDISTRIBUTE_CURRENT),
                            @ExampleObject(name = "지난 지시 계좌", summary = "PG 일시 장애", value = REQ_REDISTRIBUTE_PREVIOUS)
                    }))
            AdminSettlementDto.RedistributeRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "인플루언서 세금계산서 승인번호 대조 (07b M4)",
            description = """
                    사업자 인플루언서가 입력한 승인번호를 국세청에서 대조한 **결과만** 고릅니다 — 운영자는 번호를 입력하지 않습니다(요청에 번호 필드가 없다).

                    **권한:** ADMIN

                    | `result` | 결과 |
                    |---|---|
                    | `MATCH` | 확인 완료(`VERIFIED`) · 인플루언서 몫 **보류 해제** — 지급 예정일 = 확인일 + 3영업일 · 인플루언서 알림 · 이력 「승인번호 확인 · 인플루언서 몫 지급 예정 10.14」 |
                    | `AMOUNT_MISMATCH` | 반려 「금액 불일치」 |
                    | `RECIPIENT_MISMATCH` | 반려 「공급받는자 불일치」 |
                    | `NOT_FOUND` | 반려 「국세청 조회 불가」 |

                    반려는 사유가 스튜디오 세금계산서 카드에 뜨고, 인플루언서가 **같은 행**에 다시 입력합니다(보류 유지).
                    인플루언서 세금계산서가 아니거나 확인 대기(`SUBMITTED`)가 아니면 409. 응답은 갱신된 상세입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대조 결과 반영 — 갱신된 상세",
                    content = @Content(schema = @Schema(implementation = AdminSettlementDto.Detail.class), examples = {
                            @ExampleObject(name = "확인", summary = "MATCH — VERIFIED · 인플루언서 행 SCHEDULED(10.14) · 보류 사유 없음", value = DETAIL_INVOICE_VERIFIED),
                            @ExampleObject(name = "반려", summary = "AMOUNT_MISMATCH — REJECTED · 보류 유지", value = DETAIL_INVOICE_REJECTED)
                    })),
            @ApiResponse(responseCode = "400", description = "result 누락 · 없는 값 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "누락", value = ERR_NOT_NULL),
                            @ExampleObject(name = "없는 값", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없는 정산 · 다른 정산의 문서 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "확인 대기가 아님 — 이미 처리 · 인플루언서 세금계산서 아님 (`SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_TAX_DOCUMENT_STATE_CHANGED)))
    })
    AdminSettlementDto.Detail verifyTaxInvoice(
            @Parameter(description = "정산 id", example = "25", required = true) Long settlementId,
            @Parameter(description = "문서 id — taxDocuments[].documentId", example = "63", required = true) Long documentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "대조 결과",
                    content = @Content(schema = @Schema(implementation = AdminSettlementDto.VerifyRequest.class), examples = {
                            @ExampleObject(name = "확인", value = REQ_VERIFY_MATCH),
                            @ExampleObject(name = "반려 — 금액 불일치", value = REQ_VERIFY_AMOUNT_MISMATCH)
                    }))
            AdminSettlementDto.VerifyRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "브랜드 세금계산서 발행본 등록 (07b M5)",
            description = """
                    운영팀이 홈택스에서 발행한 브랜드 세금계산서(수정 포함)의 발행본을 등록합니다. 등록하면 파트너센터 `[다운로드]`가 열립니다.

                    **권한:** ADMIN

                    **multipart/form-data**

                    | 파트 | 규칙 |
                    |---|---|
                    | `file` | PDF · 10MB — 필수 |
                    | `approvalNumber` | 국세청 승인번호 `YYYYMMDD-NNNNNNNN-NNNNNNNN`(24자리) |
                    | `issuedDate` | 발행일 `YYYY-MM-DD` — 필수 |

                    문서가 발행 대기(`PENDING_ISSUE`)일 때만 — **정상 등록 뒤 버튼이 사라지고 다시 호출되지 않습니다**(덮어쓰기 방지).
                    정정은 수정세금계산서(새 행)로 합니다. 이력 `BRAND_INVOICE_ISSUED`. 응답은 갱신된 상세입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 — 갱신된 상세",
                    content = @Content(schema = @Schema(implementation = AdminSettlementDto.Detail.class),
                            examples = @ExampleObject(summary = "문서 ISSUED · 승인번호 마스킹 · 등록 버튼 사라짐", value = DETAIL_BRAND_INVOICE_ISSUED))),
            @ApiResponse(responseCode = "400", description = "승인번호 형식 (`SETTLEMENT_TAX_INVOICE_NUMBER_INVALID`) · 파일 · 발행일 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "승인번호", value = ERR_TAX_INVOICE_NUMBER_INVALID),
                            @ExampleObject(name = "파일 · 발행일 누락", value = ERR_ISSUE_FILE_REQUIRED),
                            @ExampleObject(name = "PDF 아님", value = ERR_PDF_ONLY),
                            @ExampleObject(name = "10MB 초과", value = ERR_PDF_SIZE)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없는 정산 · 다른 정산의 문서 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "발행 대기가 아님 — 이미 등록 · 브랜드 세금계산서 아님 (`SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_TAX_DOCUMENT_STATE_CHANGED)))
    })
    AdminSettlementDto.Detail issueBrandInvoice(
            @Parameter(description = "정산 id", example = "26", required = true) Long settlementId,
            @Parameter(description = "문서 id — taxDocuments[].documentId", example = "64", required = true) Long documentId,
            @Parameter(description = "발행본 PDF(10MB)") MultipartFile file,
            @Parameter(description = "국세청 승인번호 24자리", example = "20261010-41000012-38475920") String approvalNumber,
            @Parameter(description = "발행일 YYYY-MM-DD", example = "2026-10-10") LocalDate issuedDate,
            @Parameter(hidden = true) UserPrincipal principal);
}
