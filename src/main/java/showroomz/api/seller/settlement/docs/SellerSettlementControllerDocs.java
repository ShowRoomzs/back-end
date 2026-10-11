package showroomz.api.seller.settlement.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.settlement.dto.SellerSettlementDto;
import showroomz.domain.settlement.type.SettlementPartySort;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.Set;

import static showroomz.api.seller.settlement.docs.SellerSettlementDocsExamples.*;

@Tag(name = "Seller - Settlement", description = "파트너센터 정산 관리 API (§13)")
public interface SellerSettlementControllerDocs {

    String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Operation(summary = "정산 목록 (L1 · L2)",
            description = """
                    내 브랜드의 정산 목록입니다. 상태 칩 숫자 · KPI 는 `GET /v1/seller/settlements/summary`.

                    **권한:** SELLER

                    **파라미터**
                    - `status` — `REVIEWING` · `ADJUSTING` · `PAYOUT_SCHEDULED` · `PAID` 복수(`?status=REVIEWING&status=ADJUSTING`) · 생략 시 전부.
                      `PAID` 를 고르면 분배 실패 건도 함께 나옵니다. **`PAYOUT_FAILED` 는 받지 않습니다**(400).
                    - `keyword` — 공구명 · 인플루언서 쇼룸명 부분 일치
                    - `sort` — `CREATED_DESC`(정산 생성일 최신순 · 기본) · `PAYOUT_DESC`(브랜드 수취액 높은순)
                    - `page`(1부터) · `size`(기본 20 · 1~100)

                    **행 필드** — 열은 계산 순서 그대로: 확정 거래액 · 수수료(`feeAmount` = PG + 플랫폼) · 리워드 · 리워드 부가세 · 브랜드 수취액(차감 후).

                    **「지급(예정)일」 열 `schedule`** — 브랜드 행 기준

                    | `kind` | 시안 | `date` |
                    |---|---|---|
                    | `REVIEW_DUE` | 「09.28까지 확인」 | 확인 마감일 |
                    | `PAYOUT_DUE` | 「10.12 예정」 | 브랜드 행 지급 예정일 |
                    | `PAID` | 「10.07」 | 브랜드 행 지급일 — 정산이 아직 지급 예정이어도(인플루언서 행 보류 등) 브랜드 몫이 나갔으면 이 값 |
                    | `NONE` | 「—」 | null — 조정 협의 · 지급 확인 중 |
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정산 목록과 페이지 정보",
                    content = @Content(schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(summary = "size=3 — 조정 협의 · 지급 예정 · 브랜드 몫만 지급 완료", value = LIST))),
            @ApiResponse(responseCode = "400", description = "PAYOUT_FAILED 필터 · size 1~100 밖 · 없는 enum 값 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "PAYOUT_FAILED", value = ERR_PAYOUT_FAILED_FILTER),
                            @ExampleObject(name = "페이지 크기", value = ERR_PAGE_SIZE),
                            @ExampleObject(name = "enum", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<PageResponse<SellerSettlementDto.ListItem>> getSettlements(
            @Parameter(description = "상태 필터(복수) — REVIEWING · ADJUSTING · PAYOUT_SCHEDULED · PAID", example = "PAYOUT_SCHEDULED")
            Set<SettlementStatus> status,
            @Parameter(description = "공구명 · 인플루언서 쇼룸명 부분 일치", example = "세럼") String keyword,
            @Parameter(description = "정렬 — CREATED_DESC(기본) · PAYOUT_DESC", example = "CREATED_DESC") SettlementPartySort sort,
            PagingRequest pagingRequest);

    @Operation(summary = "정산 요약 (KPI · 상태 칩 · GNB 배지)",
            description = """
                    마켓 전체 기준입니다 — 검색어 · 상태 칩을 따라가지 않습니다.

                    **권한:** SELLER

                    | 필드 | 내용 |
                    |---|---|
                    | `paid` | 누적 지급 — 브랜드 행 지급 완료 합 · 건수. **건수 0 이면 `amount = null`**(FE 「—」 — 0원은 실적처럼 읽힌다) |
                    | `scheduled` | 지급 예정 — 브랜드 행 지급 예정 · 처리 중 합 · 건수(0 이면 `amount = null`) |
                    | `nextPayoutDate` | 가장 가까운 브랜드 행 지급 예정일 |
                    | `scheduledHasClawback` | 지급 예정 중 차감(이전 회차 환불 회수)이 반영된 건이 있는가 — KPI 보조 문구 |
                    | `reviewingCount` · `reviewingDueAt` | 요청 가능 창이 열린 정산 확인 중 건수 · 가장 임박한 확인 마감 |
                    | `adjustingCount` | 조정 협의 건수 |
                    | `statusCounts` | 상태 칩 숫자 — 분배 실패는 `PAID` 에 포함 |
                    | `attentionCount` | GNB 「정산 관리」 배지 = 요청 가능 창이 열린 정산 확인 중 건수. 조정 협의의 「내 응답 필요」는 연결·소통 배지가 센다 |

                    `paid.count` 는 **브랜드 행** 건수라 `statusCounts.PAID` 와 다를 수 있습니다(인플루언서 행이 보류 중인 정산은 지급 예정이지만 브랜드 몫은 지급 완료).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPI · 상태 칩 · 배지",
                    content = @Content(schema = @Schema(implementation = SellerSettlementDto.Summary.class), examples = {
                            @ExampleObject(name = "운영 중", summary = "누적 지급 4건 · 지급 예정 1건 · 조정 협의 1건", value = SUMMARY),
                            @ExampleObject(name = "첫 정산", summary = "지급 실적 없음(amount null) · 정산 확인 중 1건 → 배지 1", value = SUMMARY_FIRST)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<SellerSettlementDto.Summary> getSummary();

    @Operation(summary = "정산 상세 (D1 ~ D5)",
            description = """
                    블록은 상태에 따라 **null** 이 됩니다 — FE 는 null 인 블록을 그리지 않습니다.

                    **권한:** SELLER — 남의 정산은 404(존재를 알리지 않는다)

                    | 블록 | 규칙 |
                    |---|---|
                    | `dates` | `payoutDueDate` · `paidAt` 은 **브랜드 행** 기준. `payoutBasis` — 정산 근거 문장(「자동 확정 10.06 + 3영업일 · 10.09 한글날 제외」) · 확정 전 null |
                    | `adjustment` | 조정 내역 — 협의가 없으면 null. 차례(`turn`) · 「내 · 상대 최신 제안」은 **나(브랜드) 기준**. 종결 뒤에도 이력으로 남는다 |
                    | `breakdown` | 브랜드 축 분해(계산 순서). `originalRewardAmount ≠ rewardAmount` 면 조정 합의로 바뀐 것. `platformFee.normalRate` — 베타 종료 후 정상 요율. `consumerDeliveryFee`(소비자 결제 배송비 — 브랜드 가산)는 §46 B-6 미결 행. `clawbacks` — 이전 회차 환불의 회수(없으면 []) |
                    | `payouts` | 3자 분배(브랜드 첫 행 · `label` 「우리」) · **확정 전(REVIEWING · ADJUSTING) null**. 아래 「분배 행 상태」 |
                    | `payment` | 지급 정보 — 확정 전 null. 지시 후엔 지급 시점 계좌 스냅샷 · 지시 전엔 현재 등록 계좌(**뒤 6자리 노출**). `pgReference` 는 브랜드 행 지급 완료 뒤에만 |
                    | `taxDocuments` | SHOWROOMZ 발행 세금계산서(브랜드 세금계산서 · 수정세금계산서) — 확정 시 생긴다 · 확정 전 null. `downloadable = true` 면 `[다운로드]` |
                    | `influencerTax` | 인플루언서 세무 표시값 — 「브랜드가 챙길 인플루언서 증빙은 없습니다」 |
                    | `claimShipping` | 반품 · 교환 배송비 — 분해 밖 · 표시 전용(소비자 부담 · 브랜드 부담) |
                    | `items` · `itemTotalCount` | 명세 미리보기 5건(주문번호 내림차순) · 전체는 `/items` |
                    | `actions` | 아래 |

                    **분배 행 상태(`payouts[].status` · `statusLabel`)**
                    - 브랜드 행 실패만 `FAILED`(「지급 확인 중」). 남의 행 실패는 `SCHEDULED`(「지급 예정」)로 접힌다
                    - 지급 처리 중(`REQUESTED`)도 「지급 예정」으로 보인다
                    - 인플루언서 행 보류(`BLOCKED`) — 사업자는 「발행 확인 후 지급」, 비사업자는 「지급 예정」

                    **버튼 `actions`**

                    | 필드 | 조건 |
                    |---|---|
                    | `canRequestAdjustment` | 정산 확인 중 ∧ 마감 전 ∧ 협의 없음 — [정산 조정 요청] |
                    | `canRespondAdjustment` | 조정 협의의 내 차례(`MY_TURN` · `OPEN_FLOOR`) — [연결·소통에서 응답] |
                    | `canDownloadStatement` | 확정 후 — [전체 명세 다운로드] |
                    | `canDownloadTaxInvoice` | 브랜드 세금계산서 발행본 등록 후 |
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정산 상세",
                    content = @Content(schema = @Schema(implementation = SellerSettlementDto.Detail.class), examples = {
                            @ExampleObject(name = "지급 예정", summary = "자동 확정 → 10.12 지급 예정 · 세금계산서 발행 대기", value = DETAIL_SCHEDULED),
                            @ExampleObject(name = "조정 협의", summary = "인플루언서 다른 금액 제안 → 내 응답 필요 · 분배 · 지급 정보 null", value = DETAIL_ADJUSTING)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 다른 브랜드의 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND)))
    })
    ResponseEntity<SellerSettlementDto.Detail> getSettlement(
            @Parameter(description = "정산 id", example = "26", required = true) Long settlementId);

    @Operation(summary = "정산 명세 (전체 · 페이지)",
            description = """
                    명세(주문 항목 1행) — 주문번호 내림차순 · **상태 무관**하게 열립니다(확정 전에도). 소비자 이름은 마스킹입니다.

                    **권한:** SELLER

                    - `status` — `CONFIRMED`(구매확정) · `PARTIAL_RETURNED`(부분 반품 — 「확정 1/2」처럼 `settledQuantity/quantity` 병기) · `RETURNED` · `CANCELLED` · `DELIVERY_EXCEPTION`
                    - `settledAmount` = 단가 × 반영 수량 · `rewardRate` 는 % 값(10.0 = 10%)
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
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 다른 브랜드의 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND)))
    })
    ResponseEntity<PageResponse<SellerSettlementDto.Item>> getItems(
            @Parameter(description = "정산 id", example = "26", required = true) Long settlementId,
            PagingRequest pagingRequest);

    @Operation(summary = "전체 명세 다운로드 (xlsx)",
            description = """
                    파일명 `정산명세_{정산번호}.xlsx`(`Content-Disposition: attachment; filename*=UTF-8''…`).

                    **권한:** SELLER

                    **열** — 정산번호 · 주문번호 · 하위주문번호 · 주문자(마스킹) · 상품 · 옵션 · 수량 · 반영 수량 · 단가 · 결제금액 · 상태 · 정산 반영액 ·
                    리워드율 · 리워드 + 합계 행(확정 거래액 · 항목 리워드 합계 · 합의 후 리워드).

                    **확정 후만** — 정산 확인 중 · 조정 협의면 409 `SETTLEMENT_STATEMENT_NOT_READY`(`actions.canDownloadStatement` 와 같은 조건 · 세 서피스 공통).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx", content = @Content(mediaType = XLSX)),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 다른 브랜드의 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "확정 전 (`SETTLEMENT_STATEMENT_NOT_READY`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_STATEMENT_NOT_READY)))
    })
    ResponseEntity<byte[]> downloadItems(@Parameter(description = "정산 id", example = "26", required = true) Long settlementId);

    @Operation(summary = "SHOWROOMZ 발행 세금계산서 다운로드 (PDF)",
            description = """
                    상세 `taxDocuments[].downloadable = true` 인 문서(브랜드 세금계산서 · 수정세금계산서 — 운영팀이 발행본을 등록한 뒤)의 PDF 바이트 스트림입니다.

                    **권한:** SELLER

                    - 발행 전(`PENDING_ISSUE`)이면 409 `SETTLEMENT_TAX_INVOICE_NOT_ISSUED`
                    - 다른 정산의 문서 · 브랜드 세금계산서가 아닌 문서면 404
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 다른 브랜드 · 다른 정산의 문서 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "발행 전 (`SETTLEMENT_TAX_INVOICE_NOT_ISSUED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_TAX_INVOICE_NOT_ISSUED)))
    })
    ResponseEntity<byte[]> downloadTaxDocument(
            @Parameter(description = "정산 id", example = "25", required = true) Long settlementId,
            @Parameter(description = "문서 id — taxDocuments[].documentId", example = "62", required = true) Long documentId);
}
