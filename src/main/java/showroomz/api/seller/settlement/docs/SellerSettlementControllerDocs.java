package showroomz.api.seller.settlement.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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

@Tag(name = "Seller - Settlement", description = "파트너센터 정산 관리 API (13 · 44 파트너 설계서)")
public interface SellerSettlementControllerDocs {

    String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Operation(summary = "정산 목록 (L1 · L2)",
            description = """
                    내 브랜드의 정산(공구 1건 = 정산 1건). 조회 전용 — 체크박스 · 일괄 액션이 없다.

                    **권한:** SELLER

                    **파라미터**
                    - `status` — `REVIEWING`(정산 확인 중) · `ADJUSTING`(조정 협의) · `PAYOUT_SCHEDULED`(지급 예정) · `PAID`(지급 완료) 복수 ·
                      생략 시 전부. **분배 실패(`PAYOUT_FAILED`)는 받지 않는다**(400) — 수취자 화면은 지급 완료로 접는다(§46 A-4).
                    - `keyword` — 공구명 · 인플루언서 쇼룸명 부분 일치
                    - `sort` — `CREATED_DESC`(정산 생성일 최신순 · 기본) · `PAYOUT_DESC`(브랜드 수취액 높은순)
                    - `page`(1부터) · `size`(1 ~ 100)

                    **행 필드** — 열은 계산 순서 그대로: 확정 거래액 · `feeAmount`(PG + 플랫폼) · 리워드 · 리워드 부가세 · 브랜드 수취액.
                    `schedule` — 「지급(예정)일」 열: `REVIEW_DUE`(확인 마감일) · `PAYOUT_DUE`(브랜드 행 지급 예정일) · `PAID`(브랜드 행 지급일) · `NONE`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — PAYOUT_FAILED 필터 · size 범위 밖",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<SellerSettlementDto.ListItem>> getSettlements(
            @Parameter(description = "상태 필터(복수)") Set<SettlementStatus> status,
            @Parameter(description = "공구명 · 쇼룸명") String keyword,
            @Parameter(description = "정렬") SettlementPartySort sort,
            PagingRequest pagingRequest);

    @Operation(summary = "정산 요약 (KPI · 상태 칩 · GNB 배지)",
            description = """
                    마켓 전체 기준 — 검색어 · 상태 칩을 따라가지 않는다.

                    - `paid` · `scheduled` — 브랜드 행 기준(누적 지급 · 지급 예정). **건수 0 이면 `amount = null`**(FE 「—」)
                    - `attentionCount` — GNB 「정산 관리」 배지 = 요청 가능 창이 열린 정산 확인 중 건수. 조정 협의의 「내 응답 필요」는 연결·소통 배지가 센다
                    - `statusCounts` — 상태 칩 숫자(분배 실패는 `PAID` 에 포함)

                    **권한:** SELLER
                    """)
    ResponseEntity<SellerSettlementDto.Summary> getSummary();

    @Operation(summary = "정산 상세 (D1 ~ D5)",
            description = """
                    블록은 상태에 따라 **null** 이 된다 — FE 는 null 인 블록을 그리지 않는다.

                    - `dates.payoutBasis` — 정산 근거 문장(「자동 확정 10.06 + 3영업일 · 10.09 한글날 제외」) · 확정 전 null
                    - `breakdown` — 브랜드 축 분해. `originalRewardAmount ≠ rewardAmount` 면 조정 합의로 바뀐 것.
                      `consumerDeliveryFee`(소비자 결제 배송비 — 브랜드 가산)는 §46 B-6 미결 행
                    - `payouts` — 3자 분배(브랜드 첫 행) · **확정 전(REVIEWING · ADJUSTING) null**. 브랜드 행 실패만 `FAILED`(「지급 확인 중」)로 보이고
                      남의 행 실패는 `SCHEDULED`(「지급 예정」)로 접힌다. 사업자 인플루언서 행 보류는 「발행 확인 후 지급」
                    - `payment` — 확정 전 null. 지시 후엔 지급 시점 계좌 스냅샷 · 지시 전엔 현재 등록 계좌(뒤 6자리 노출). `pgReference` 는 지급 완료 뒤에만
                    - `taxDocuments` — SHOWROOMZ 발행 세금계산서(확정 시 생긴다) · 확정 전 null
                    - `adjustment` — 조정 내역 · 협의가 없으면 null
                    - `claimShipping` — 반품 · 교환 배송비(분해 밖 · 표시 전용)
                    - `items` — 명세 미리보기 5건 · 전체는 `/items`
                    - `actions` — `canRequestAdjustment`(정산 확인 중 ∧ 마감 전 ∧ 협의 없음) · `canDownloadStatement`(확정 후)

                    **권한:** SELLER — 남의 정산은 404(존재를 알리지 않는다)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND — 없음 · 다른 브랜드의 정산",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerSettlementDto.Detail> getSettlement(@Parameter(description = "정산 id") Long settlementId);

    @Operation(summary = "정산 명세 (전체 · 페이지)",
            description = """
                    명세(주문 항목 1행) — 주문번호 내림차순 · 상태 무관하게 열린다. 부분 반품은 `PARTIAL_RETURNED` + 수량 병기(`settledQuantity/quantity`).

                    **권한:** SELLER
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<SellerSettlementDto.Item>> getItems(@Parameter(description = "정산 id") Long settlementId,
                                                                   PagingRequest pagingRequest);

    @Operation(summary = "전체 명세 다운로드 (xlsx)",
            description = """
                    파일명 `정산명세_{정산번호}.xlsx` · 열 = 정산번호 · 주문번호 · 하위주문번호 · 주문자(마스킹) · 상품 · 옵션 · 수량 · 반영 수량 · 단가 · 결제금액 ·
                    상태 · 정산 반영액 · 리워드율 · 리워드 + 합계 행(확정 거래액 · 항목 리워드 합계 · 합의 후 리워드).
                    **확정 후만** — 정산 확인 중 · 조정 협의면 409 `SETTLEMENT_STATEMENT_NOT_READY`(세 서피스 공통).

                    **권한:** SELLER
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx", content = @Content(mediaType = XLSX)),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_STATEMENT_NOT_READY — 확정 전",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> downloadItems(@Parameter(description = "정산 id") Long settlementId);

    @Operation(summary = "SHOWROOMZ 발행 세금계산서 다운로드 (PDF)",
            description = """
                    상세 `taxDocuments[].downloadable = true` 인 문서(브랜드 세금계산서 · 수정세금계산서 — 운영팀이 발행본을 등록한 뒤)의 PDF 스트림.
                    발행 전이면 409 `SETTLEMENT_TAX_INVOICE_NOT_ISSUED` · 다른 정산의 문서면 404.

                    **권한:** SELLER
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_TAX_INVOICE_NOT_ISSUED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> downloadTaxDocument(@Parameter(description = "정산 id") Long settlementId,
                                               @Parameter(description = "문서 id — taxDocuments[].documentId") Long documentId);
}
