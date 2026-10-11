package showroomz.api.creator.settlement.docs;

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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.creator.settlement.dto.CreatorSettlementDto;
import showroomz.domain.settlement.type.SettlementPartySort;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.Set;

import static showroomz.api.creator.settlement.docs.CreatorSettlementDocsExamples.*;

@Tag(name = "Creator - Settlement", description = "쇼룸 스튜디오 정산 관리 API (#12)")
public interface CreatorSettlementControllerDocs {

    String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Operation(summary = "정산 목록 (L1 · L2)",
            description = """
                    내 정산 목록입니다. 조회 전용 — KPI · 상태 칩 숫자는 `GET /v1/creator/settlements/summary`.

                    **권한:** CREATOR

                    **파라미터**
                    - `status` — `REVIEWING` · `ADJUSTING` · `PAYOUT_SCHEDULED` · `PAID` 복수 · 생략 시 전부. `PAID` 를 고르면 분배 실패 건도 함께.
                      **`PAYOUT_FAILED` 는 받지 않습니다**(400)
                    - `keyword` — 공구명 · 브랜드명 부분 일치
                    - `sort` — `CREATED_DESC`(정산 생성일 최신순 · 기본) · `PAYOUT_DESC`(실지급액 높은순)
                    - `page`(1부터) · `size`(기본 20 · 1~100)

                    **행 필드**
                    - `rewardRateLabel` — 「15%」 · 상품마다 다르면 「상품별」
                    - `rewardClawbackAmount` — 이전 회차 환불의 회수(있으면 「차감 −6,150원」 보조줄)
                    - `withholdingAmount`(비사업자) · `creatorVatAmount`(사업자) · `creatorPayoutAmount`(실지급액)
                    - `payoutDate` · `payoutDateKind` — **내 행**의 지급 예정일(`SCHEDULED` 「10.12 예정」) · 지급일(`PAID` 「09.02」).
                      정산 확인 중 · 조정 협의 · 보류 · 지급 확인 중이면 둘 다 null
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정산 목록과 페이지 정보",
                    content = @Content(schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(summary = "size=3 — 정산 확인 중 · 지급 예정 · 지급 완료", value = LIST))),
            @ApiResponse(responseCode = "400", description = "PAYOUT_FAILED 필터 · size 1~100 밖 · 없는 enum 값 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "PAYOUT_FAILED", value = ERR_PAYOUT_FAILED_FILTER),
                            @ExampleObject(name = "페이지 크기", value = ERR_PAGE_SIZE),
                            @ExampleObject(name = "enum", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<PageResponse<CreatorSettlementDto.ListItem>> getSettlements(
            @Parameter(description = "상태 필터(복수) — REVIEWING · ADJUSTING · PAYOUT_SCHEDULED · PAID", example = "REVIEWING")
            Set<SettlementStatus> status,
            @Parameter(description = "공구명 · 브랜드명 부분 일치", example = "크림") String keyword,
            @Parameter(description = "정렬 — CREATED_DESC(기본) · PAYOUT_DESC", example = "CREATED_DESC") SettlementPartySort sort,
            PagingRequest pagingRequest);

    @Operation(summary = "정산 요약 (KPI 4칸 · 상태 칩 · GNB 배지)",
            description = """
                    인플루언서 전체 기준입니다 — 검색어 · 상태 칩을 따라가지 않습니다. **건수 0 이면 `amount = null`**(FE 「—」).

                    **권한:** CREATOR

                    | 필드 | 내용 |
                    |---|---|
                    | `totalPaid` | 누적 수령 — 내 행 지급 완료 · 실지급액(세후) |
                    | `payoutScheduled` | 지급 예정 — 내 행 지급 예정 · 처리 중 · **보류** 합. `nearestDate` 는 보류 건을 빼고 가장 가까운 예정일 |
                    | `reviewing` | 정산 확인 중 건수 · 요청 가능 창이 열린 것 중 가장 임박한 마감(`nearestDueAt`) |
                    | `adjusting` | 조정 협의 건수 |
                    | `statusCounts` | 상태 칩 숫자 — 분배 실패는 `PAID` 에 포함 |
                    | `attentionCount` | GNB 「정산 관리」 배지 = 요청 가능 창이 열린 정산 확인 중 + 세금계산서 입력 대기 · 반려. 조정 협의의 「내 응답 필요」는 연결·소통 배지가 센다 |
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPI · 상태 칩 · 배지",
                    content = @Content(schema = @Schema(implementation = CreatorSettlementDto.Summary.class),
                            examples = @ExampleObject(summary = "누적 수령 3건 · 10.12 지급 예정 · 확인 중 1건 → 배지 1", value = SUMMARY))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<CreatorSettlementDto.Summary> getSummary();

    @Operation(summary = "정산 상세 (D1 ~ D5c)",
            description = """
                    시안 11장이 응답 하나를 씁니다. 블록은 상태에 따라 **null** 입니다 — FE 는 null 인 블록을 그리지 않습니다.

                    **권한:** CREATOR — 남의 정산은 404

                    | 블록 | 규칙 |
                    |---|---|
                    | `settlement` | 헤더 — `taxInvoiceRequired` = 사업자(세금계산서 제출 대상) |
                    | `timeline` | `payoutDueDate` 는 **내 행** 기준 — 사업자 발행 전 · 정산 확인 중 · 조정 협의면 null. `payoutDueNote` 는 아래 |
                    | `review` | 정산 확인 중에서만 — `canRequestAdjustment`(마감 전 ∧ 협의 없음) · `requestDeadlineAt` · `maxRewardAmount`(조정 상한 · 10원 절사). 그 외 null |
                    | `breakdown` | 확정 거래액까지(브랜드 축 상단) + 인플루언서 축. PG · 플랫폼 수수료 · 브랜드 수취액은 내리지 않는다. `rewardRates` — 상품별 리워드율 |
                    | `clawbacks` | 이 정산에서 회수한 이전 회차 환불(인플루언서 측) — 없으면 [] |
                    | `payouts` | 3자 분배(내 행 첫 줄) — 아래 「분배 행」 |
                    | `payment` | 내 계좌(**뒤 6자리**) · 지시 후엔 지급 시점 스냅샷 · 예금주는 실명. 계좌가 없으면(온보딩 미완료) null |
                    | `adjustment` | 조정 내역 — 협의가 없으면 null. 차례(`turn`)는 **나 기준** |
                    | `withholding` | 비사업자만 — `receiptAvailable`(지급 완료 ∧ 원천징수영수증 생성) · `receiptLabel` 「징수 예정액」→「징수액」. 사업자는 null |
                    | `taxInvoice` | 사업자 ∧ 확정 후 카드 — 아래 「세금계산서 카드」. 그 전 null |
                    | `items` | 명세 미리보기 5건(소비자 열 없음) · `downloadAvailable`(확정 후) |

                    **`timeline.payoutDueNote`** — 「자동 확정 10.06 + 3영업일 · 10.09 한글날 제외」 · 「합의 확정 …」 · 「기한 만료 확정 …」 ·
                    사업자 보류 「발행 확인 후 + 3영업일」 · 「확인 후 + 3영업일」 · 「재제출 확인 후 + 3영업일」 · 주민등록번호 미등록 「주민등록번호 등록 후 지급」

                    **분배 행 `payouts.rows[]`**
                    - `shownAfterConfirm = true`(확정 전) — 금액만 보이고 상태는 「확인 기간 후 지급」(`WAITING`) · 「보류 중」(`HELD` — 조정 협의)
                    - 확정 후 — 「10.12 지급 예정」 · 「10.07 지급 완료」 · 0원 행은 「—」
                    - 내 행 보류(`BLOCKED`) — 사업자는 세금계산서 카드 상태(「발행 필요」 · 「확인 중」 · 「반려 · 재제출 필요」), 비사업자는 「주민등록번호 등록 필요」
                    - 내 행 실패만 「지급 확인 중」(`FAILED`) · 브랜드 행 실패는 「지급 예정」으로 접힌다

                    **세금계산서 카드 `taxInvoice.cardStatus`** — `PENDING_INPUT`(발행 필요) · `SUBMITTED`(확인 중) · `REJECTED`(반려 — `rejectReasonLabel` 표시) ·
                    `VERIFIED`(확인 완료). `supplier` 는 공급받는자(SHOWROOMZ — 설정값) · 공급가 = 차감 후 리워드 · 부가세.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정산 상세",
                    content = @Content(schema = @Schema(implementation = CreatorSettlementDto.Detail.class), examples = {
                            @ExampleObject(name = "정산 확인 중 · 비사업자", summary = "조정 요청 가능 · 상한 2,072,270 · 확인 기간 후 지급", value = DETAIL_REVIEWING),
                            @ExampleObject(name = "지급 예정 · 비사업자", summary = "10.12 지급 예정 · 원천징수 18,084(징수 예정액)", value = DETAIL_SCHEDULED),
                            @ExampleObject(name = "조정 협의 · 사업자", summary = "민지의 뷰티룸 시점 — 내 282,000 제안 · 상대 응답 대기 · 전 행 보류 중", value = DETAIL_ADJUSTING),
                            @ExampleObject(name = "세금계산서 확인 중 · 사업자", summary = "지우_스타일 시점 — 내 몫 보류(확인 중) · 브랜드 몫 지급 완료", value = DETAIL_TAX_INVOICE)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 남의 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND)))
    })
    ResponseEntity<CreatorSettlementDto.Detail> getSettlement(
            @Parameter(description = "정산 id", example = "26", required = true) Long settlementId);

    @Operation(summary = "정산 명세 (전체 · 페이지)",
            description = """
                    명세(주문 항목 1행) — 주문번호 내림차순 · **상태 무관**하게 열립니다(확정 전에도). 소비자 열이 없습니다.

                    **권한:** CREATOR

                    - `status` — `CONFIRMED`(구매확정) · `PARTIAL_RETURNED`(부분 반품 — `settledQuantity/quantity`) · `RETURNED` · `CANCELLED` · `DELIVERY_EXCEPTION`
                    - `rewardAmount` = 내 리워드 · `rewardRate` 는 % 값(10.0 = 10%)
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
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 남의 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND)))
    })
    ResponseEntity<PageResponse<CreatorSettlementDto.Item>> getItems(
            @Parameter(description = "정산 id", example = "26", required = true) Long settlementId,
            PagingRequest pagingRequest);

    @Operation(summary = "전체 명세 다운로드 (xlsx)",
            description = """
                    파일명 `정산명세_{정산번호}.xlsx`(`Content-Disposition: attachment; filename*=UTF-8''…`).

                    **권한:** CREATOR

                    **열** — 주문번호 · 상품 · 옵션 · 수량 · 반영 수량 · 결제금액 · 상태 · 정산 반영액 · 리워드율 · 리워드 +
                    합계 행(확정 거래액 · 항목 리워드 합계 · 합의 후 리워드). 소비자 열 없음.

                    **확정 후만** — 정산 확인 중 · 조정 협의면 409 `SETTLEMENT_STATEMENT_NOT_READY`(`items.downloadAvailable` 과 같은 조건 · 세 서피스 공통).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx", content = @Content(mediaType = XLSX)),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 남의 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "확정 전 (`SETTLEMENT_STATEMENT_NOT_READY`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_STATEMENT_NOT_READY)))
    })
    ResponseEntity<byte[]> downloadItems(@Parameter(description = "정산 id", example = "26", required = true) Long settlementId);

    @Operation(summary = "세금계산서 승인번호 제출 (D5 · D5b)",
            description = """
                    사업자 정산이 확정되면 카드(`taxInvoice`)에서 홈택스로 발행한 세금계산서의 국세청 승인번호를 입력합니다.

                    **권한:** CREATOR

                    **multipart/form-data**

                    | 파트 | 규칙 |
                    |---|---|
                    | `approvalNumber` | 국세청 승인번호 `YYYYMMDD-NNNNNNNN-NNNNNNNN`(24자리) — 필수 |
                    | `attachment` | 세금계산서 PDF(10MB) — 선택 |

                    반려 뒤 재제출은 **같은 행**입니다. 첨부는 새로 올리면 바뀌고, 안 올리면 이전 첨부가 유지됩니다.

                    | 검사 | 결과 |
                    |---|---|
                    | 내 정산 아님 | 404 `SETTLEMENT_NOT_FOUND` |
                    | 비사업자 정산 | 409 `SETTLEMENT_TAX_INVOICE_NOT_REQUIRED` |
                    | 확정 전 · 이미 확인 중(`SUBMITTED`) · 확인 완료(`VERIFIED`) | 409 `SETTLEMENT_TAX_INVOICE_NOT_OPEN` |
                    | 형식 불일치 · 빈 값 | 400 `SETTLEMENT_TAX_INVOICE_NUMBER_INVALID` |
                    | 다른 정산에서 이미 확인된 번호 | 400 `SETTLEMENT_TAX_INVOICE_NUMBER_INVALID`(「이미 다른 정산에서 확인된 승인번호입니다.」) |
                    | 첨부가 PDF 아님 · 10MB 초과 | 400 `INVALID_INPUT` |

                    결과 카드는 `cardStatus = SUBMITTED`(「확인 중」)이고, 운영자가 국세청 대조 결과를 고릅니다(확인 · 반려).
                    확인되면 내 몫 지급 예정일 = 확인일 + 3영업일입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제출 — 갱신된 세금계산서 카드",
                    content = @Content(schema = @Schema(implementation = CreatorSettlementDto.TaxInvoiceSubmitResponse.class),
                            examples = @ExampleObject(summary = "SUBMITTED(확인 중) · 승인번호 · 첨부 파일명", value = TAX_INVOICE_SUBMIT_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "승인번호 형식 · 중복 (`SETTLEMENT_TAX_INVOICE_NUMBER_INVALID`) · 첨부 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "형식", value = ERR_TAX_INVOICE_NUMBER_INVALID),
                            @ExampleObject(name = "다른 정산에서 확인된 번호", value = ERR_TAX_INVOICE_NUMBER_DUPLICATED),
                            @ExampleObject(name = "PDF 아님", value = ERR_PDF_ONLY),
                            @ExampleObject(name = "10MB 초과", value = ERR_PDF_SIZE)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 남의 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "비사업자 · 지금 제출할 수 없음 · 그 사이 상태 변경",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "비사업자", value = ERR_TAX_INVOICE_NOT_REQUIRED),
                            @ExampleObject(name = "확정 전 · 확인 중 · 확인 완료", value = ERR_TAX_INVOICE_NOT_OPEN),
                            @ExampleObject(name = "상태 변경", value = ERR_TAX_DOCUMENT_STATE_CHANGED)
                    }))
    })
    ResponseEntity<CreatorSettlementDto.TaxInvoiceSubmitResponse> submitTaxInvoice(
            @Parameter(description = "정산 id", example = "25", required = true) Long settlementId,
            @Parameter(description = "국세청 승인번호 24자리", example = "20261008-41000027-38475920") String approvalNumber,
            @Parameter(description = "세금계산서 PDF(선택 · 10MB)") MultipartFile attachment);

    @Operation(summary = "원천징수영수증 다운로드 (PDF)",
            description = """
                    비사업자 정산의 내 몫 지급 완료 뒤 시스템이 만드는 원천징수영수증입니다 — 상세 `withholding.receiptAvailable = true` 일 때만.
                    바이트 스트림으로 내립니다(링크가 밖으로 새지 않게).

                    **권한:** CREATOR

                    생성 전(지급 전 · 생성 재시도 중) · 사업자 정산이면 409 `SETTLEMENT_RECEIPT_NOT_READY`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 남의 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "영수증 생성 전 (`SETTLEMENT_RECEIPT_NOT_READY`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_RECEIPT_NOT_READY)))
    })
    ResponseEntity<byte[]> downloadWithholdingReceipt(
            @Parameter(description = "정산 id", example = "24", required = true) Long settlementId);

    @Operation(summary = "제출한 세금계산서 PDF 다운로드 (D5c)",
            description = """
                    세금계산서 제출 때 올린 첨부를 내려받습니다.

                    **권한:** CREATOR

                    첨부가 없었으면 404 `SETTLEMENT_NOT_FOUND` — `taxInvoice.attachmentName = null` 이면 버튼을 그리지 않습니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 남의 정산 · 첨부 없음 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND)))
    })
    ResponseEntity<byte[]> downloadTaxInvoiceAttachment(
            @Parameter(description = "정산 id", example = "25", required = true) Long settlementId);

    @Operation(summary = "연간 지급 내역 다운로드 (xlsx)",
            description = """
                    `year`(생략 시 올해)에 **내 몫 지급 완료일**이 속한 정산 전부를 엑셀로 내려받습니다 — 종합소득세 신고용.

                    **권한:** CREATOR

                    **열** — 정산번호 · 공구명 · 브랜드 · 지급일 · 판매 리워드 · 차감 · (비사업자) 소득세 · 지방소득세 / (사업자) 부가세 · 세금계산서 승인번호 ·
                    실지급액 + 합계 행.

                    그 해 지급이 없으면 빈 파일이 아니라 404 `SETTLEMENT_NOT_FOUND` 입니다(빈 엑셀은 「올해 소득 0」으로 읽힌다).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx", content = @Content(mediaType = XLSX)),
            @ApiResponse(responseCode = "400", description = "연도 형식 오류 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_INVALID_INPUT))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "그 해 지급 없음 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND)))
    })
    ResponseEntity<byte[]> downloadAnnualStatement(@Parameter(description = "연도 — 생략 시 올해", example = "2026") Integer year);
}
