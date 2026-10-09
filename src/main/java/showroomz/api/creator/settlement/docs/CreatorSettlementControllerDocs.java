package showroomz.api.creator.settlement.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.creator.settlement.dto.CreatorSettlementDto;
import showroomz.domain.settlement.type.SettlementPartySort;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.Set;

@Tag(name = "Creator - Settlement", description = "쇼룸 스튜디오 정산 관리 API (12 · 44 스튜디오 설계서)")
public interface CreatorSettlementControllerDocs {

    String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Operation(summary = "정산 목록 (L1 · L2)",
            description = """
                    내 정산(공구 1건 = 정산 1건). 조회 전용.

                    **권한:** CREATOR

                    - `status` — `REVIEWING` · `ADJUSTING` · `PAYOUT_SCHEDULED` · `PAID` 복수 · 생략 시 전부. `PAYOUT_FAILED`는 받지 않는다(400 · `PAID`에 접힌다)
                    - `keyword` — 공구명 · 브랜드명 부분 일치
                    - `sort` — `CREATED_DESC`(정산 생성일 최신순 · 기본) · `PAYOUT_DESC`(실지급액 높은순)
                    - `page`(1부터) · `size`(1 ~ 100)

                    **행 필드** — `rewardRateLabel` 「15%」 · 상품마다 다르면 「상품별」. `payoutDate`는 **내 행**(인플루언서 몫)의 지급 예정일 · 지급일이다 —
                    사업자는 브랜드와 날짜가 다르다. 정산 확인 중 · 조정 협의 · 보류 · 지급 확인 중이면 null.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — PAYOUT_FAILED 필터 · size 범위 밖",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<CreatorSettlementDto.ListItem>> getSettlements(
            @Parameter(description = "상태 필터(복수)") Set<SettlementStatus> status,
            @Parameter(description = "공구명 · 브랜드명") String keyword,
            @Parameter(description = "정렬") SettlementPartySort sort,
            PagingRequest pagingRequest);

    @Operation(summary = "정산 요약 (KPI 4칸 · 상태 칩 · GNB 배지)",
            description = """
                    인플루언서 전체 기준 — 검색어 · 상태 칩을 따라가지 않는다. 건수 0 이면 `amount = null`(FE 「—」).

                    - `totalPaid` — 내 행 지급 완료(실지급액 · 세후) · `payoutScheduled` — 내 행 지급 예정 + 보류(`nearestDate`는 보류 제외)
                    - `attentionCount` — GNB 「정산 관리」 배지 = 요청 가능 창이 열린 정산 확인 중 + 세금계산서 입력 대기 · 반려.
                      조정 협의의 「내 응답 필요」는 연결·소통 배지가 센다

                    **권한:** CREATOR
                    """)
    ResponseEntity<CreatorSettlementDto.Summary> getSummary();

    @Operation(summary = "정산 상세 (D1 ~ D5c)",
            description = """
                    시안 11장이 응답 하나를 쓴다. 블록은 상태에 따라 **null** 이다 — FE 는 null 인 블록을 그리지 않는다.

                    - `timeline.payoutDueDate` — **내 행** 기준. 사업자 발행 전 · 정산 확인 중 · 조정 협의면 null.
                      `payoutDueNote` — 「자동 확정 08.28 + 3영업일」 · 「합의 확정 …」 · 「기한 만료 확정 …」 · 사업자 「발행 확인 후 + 3영업일」 ·
                      주민등록번호 미등록 「주민등록번호 등록 후 지급」
                    - `review` — 정산 확인 중에서만: `canRequestAdjustment`(마감 전 ∧ 협의 없음) · `maxRewardAmount`(조정 상한 · 10원 절사) · 그 외 null
                    - `breakdown` — 확정 거래액까지(브랜드 축 상단) + 인플루언서 축. PG · 플랫폼 수수료 · 브랜드 수취액은 내리지 않는다
                    - `payouts` — 내 행 첫 줄 · `shownAfterConfirm = true`(확정 전)면 상태는 「확인 기간 후 지급」 · 「보류 중」. 내 행 실패는 「지급 확인 중」 ·
                      브랜드 행 실패는 「지급 예정」으로 접힌다
                    - `payment` — 내 계좌(뒤 6자리) · 계좌가 없으면 null
                    - `withholding` — 비사업자만 · `receiptAvailable`(지급 완료 ∧ 영수증 생성) · 사업자는 null
                    - `taxInvoice` — 사업자 ∧ 확정 후 카드 · 그 전 null
                    - `items` — 명세 미리보기 5건(소비자 열 없음) · `downloadAvailable`(확정 후)

                    **권한:** CREATOR — 남의 정산은 404
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND — 없음 · 남의 정산",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorSettlementDto.Detail> getSettlement(@Parameter(description = "정산 id") Long settlementId);

    @Operation(summary = "정산 명세 (전체 · 페이지)",
            description = """
                    명세(주문 항목 1행) — 주문번호 내림차순 · 상태 무관. 소비자 열 없음.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<CreatorSettlementDto.Item>> getItems(@Parameter(description = "정산 id") Long settlementId,
                                                                    PagingRequest pagingRequest);

    @Operation(summary = "전체 명세 다운로드 (xlsx)",
            description = """
                    파일명 `정산명세_{정산번호}.xlsx` · 열 = 주문번호 · 상품 · 옵션 · 수량 · 반영 수량 · 결제금액 · 상태 · 정산 반영액 · 리워드율 · 리워드 +
                    합계 행(확정 거래액 · 항목 리워드 합계 · 합의 후 리워드). 소비자 열 없음. **확정 후만** — 아니면 409 `SETTLEMENT_STATEMENT_NOT_READY`.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx", content = @Content(mediaType = XLSX)),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_STATEMENT_NOT_READY — 확정 전",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> downloadItems(@Parameter(description = "정산 id") Long settlementId);
}
