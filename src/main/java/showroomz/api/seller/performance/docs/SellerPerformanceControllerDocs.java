package showroomz.api.seller.performance.docs;

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
import showroomz.api.seller.performance.dto.SellerPerformanceDto;
import showroomz.domain.performance.type.GroupBuyPerformanceSort;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.global.dto.PagingRequest;

import java.util.Set;

import static showroomz.api.seller.performance.docs.SellerPerformanceDocsExamples.*;

@Tag(name = "Seller - Performance", description = "파트너센터 성과 관리 API")
public interface SellerPerformanceControllerDocs {

    String FIGURE = """
            **지표 값 `PerformanceFigure`** — `{ value, state, original, basis }`

            | `state` | 뜻 | 화면 |
            |---|---|---|
            | `FINAL` | 확정 | 값 그대로 |
            | `PROJECTED` | 예정 — 미처리 반품 결과에 따라 줄어들 수 있다(집계 중) | 「예정」 태그 · 회색 |
            | `PENDING_SETTLEMENT` | 정산 후 확정 — 숫자 없음 | 「정산 후 확정」 |
            | `NEGOTIATING` | 조정 협의 중 — `original` 에 원래 값 | 「협의 중 · 원래 N」 |
            | `NOT_AVAILABLE` | 원천 로그 없음 | 「—」 · 집계 준비 중 |

            `value = null` 은 「—」이고 0 은 측정된 0 이다. 합의가 반영된 지급 완료(D4)는 `FINAL` + `original`(원래 값) — 상태는 늘리지 않는다.
            비율은 % 값(소수 1자리) · ROAS 는 % 정수 · 금액은 원.
            """;

    @Operation(summary = "성과 목록 (L1 · L2)",
            description = """
                    종료 공구 단위 비교표입니다. 모집단 = 내 브랜드 · 종료(`ENDED` · `SETTLED`) · 결제 완료 하위주문 1건 이상 — 진행 중 · 중단 · 판매 0건 공구는 없습니다.

                    **권한:** SELLER

                    **파라미터**
                    - `status` — `AGGREGATING`(집계 중) · `REVIEWING`(정산 확인 중) · `ADJUSTING`(조정 협의) · `PAYOUT_SCHEDULED`(지급 예정) · `PAID`(지급 완료) 복수 · 생략 시 전부
                    - `keyword` — 공구명 · 인플루언서 쇼룸명 부분 일치
                    - `sort` — `ENDED_DESC`(종료일 최신순 · 기본) · `SALES_DESC`(확정 거래액 높은순 · 집계 중 행은 맨 뒤)
                    - `page`(1부터) · `size`(기본 20 · 1~100)

                    **행 값** — 집계 중 행은 여섯 지표 전부 `value = null`(「—」 · 목록은 예정치를 섞지 않는다) · 조정 협의 행은 `payout` 만 `NEGOTIATING`. 집계 중 행도 상세(D2)가 열린다(`detailAvailable = true`).
                    `listPeriod` 는 필터를 통과한 공구들의 시작 ~ 종료(「조회기간」 표기).

                    """ + FIGURE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성과 목록",
                    content = @Content(schema = @Schema(implementation = SellerPerformanceDto.ListPage.class),
                            examples = @ExampleObject(summary = "집계 중 · 조정 협의 · 지급 완료", value = LIST))),
            @ApiResponse(responseCode = "400", description = "size 1~100 밖 · 없는 enum 값 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_INVALID_INPUT))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<SellerPerformanceDto.ListPage> getList(
            @Parameter(description = "상태 필터(복수)", example = "PAID") Set<PerformanceStatus> status,
            @Parameter(description = "공구명 · 인플루언서 쇼룸명 부분 일치", example = "앰플") String keyword,
            @Parameter(description = "정렬 — ENDED_DESC(기본) · SALES_DESC", example = "ENDED_DESC") GroupBuyPerformanceSort sort,
            PagingRequest pagingRequest);

    @Operation(summary = "성과 요약 (KPI 8 · 상태 칩)",
            description = """
                    **금액이 확정된 정산 — 지급 예정 + 지급 완료 공구를 합산**합니다(55 계획서 P1 · D3 — 집계 중은 금액이 없고, 정산 확인 중 · 조정 협의는 조정 요청으로 값이 바뀔 수 있다). 검색어 · 상태 칩을 따라가지 않습니다.
                    근거 문장은 「정산 N건 합산(지급 예정 a · 지급 완료 b)」이고 한쪽이 0건이어도 둘 다 적습니다 · `population.breakdown` 이 같은 내역입니다.

                    **권한:** SELLER

                    평균은 공구 평균이 아니라 **합산 비율**(Σ분자 ÷ Σ분모)입니다 — 평균 전환율 = Σ구매확정 ÷ Σ방문 · 평균 ROAS = Σ확정 거래액 ÷ Σ총 집행비(집행비 0원 공구 제외).
                    평균 AOV = Σ확정 거래액 ÷ Σ확정 주문 · 평균 구매확정률 = Σ구매확정 ÷ Σ주문 · 평균 재구매율 = Σ재구매 ÷ Σ구매확정 소비자.
                    지급 완료에는 분배 실패 건도 포함됩니다.

                    모집단 공구가 없으면(`population.count = 0`) 지표는 전부 `value = null`(「—」 · 0원은 실적처럼 읽힌다) · `statusCounts` 는 실제 수(다섯 키 항상 있음).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPI · 상태 칩",
                    content = @Content(schema = @Schema(implementation = SellerPerformanceDto.Summary.class), examples = {
                            @ExampleObject(name = "L1", summary = "정산 2건 합산(지급 예정 1 · 지급 완료 1)", value = SUMMARY),
                            @ExampleObject(name = "L2", summary = "지급 예정 · 지급 완료 공구 없음", value = SUMMARY_EMPTY)})),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<SellerPerformanceDto.Summary> getSummary();

    @Operation(summary = "공구 성과 상세 (D1 · D2 · D3 · D4 · D1-C · D1-D)",
            description = """
                    공구 1건 리포트입니다. 블록 순서 = 계약 조건 바 → KPI 8 → 유입 → 판매 → 배송 → 소비자 → 원가(브랜드 전용) → 정산 연결.

                    **권한:** SELLER — 내 브랜드 공구가 아니거나 모집단 밖(진행 중 · 중단 · 판매 0건)이면 404(존재를 알리지 않는다)

                    | 상태 | 화면 |
                    |---|---|
                    | `AGGREGATING`(D2) | `aggregating` 배너(미처리 반품 N건) · 판매 · 소비자 값 `PROJECTED` · 돈 값 `PENDING_SETTLEMENT` · `cost.editable = false` · `settlement = null` |
                    | `REVIEWING` · `PAYOUT_SCHEDULED` · `PAID`(D1) | 전부 `FINAL` |
                    | `ADJUSTING`(D3) | 판매 · 유입 · 소비자는 `FINAL` · 리워드에 걸린 값(정산액 · ROAS · 획득 단가 · 수익 · 수익률 · 총 집행비)만 `NEGOTIATING` + `original` · `settlement.adjustment` 에 협의 이력 |
                    | `PAID` + 합의 반영(D4) | 배지는 D1 과 같다 · 리워드에 걸린 값이 `FINAL` + `original`(원래 값) |
                    | 원가 미입력(D1-C) | `cost.items[].unitCost = null` · `totalCost · profit · profitRate` 의 `value = null` + basis 「개당 원가를 입력하면 수익이 계산됩니다」 |

                    - 방문 · 유입 경로 · 게시물 · 좋아요 · 주문 · 발송 · 팔로워 비중은 줄지 않는 값이라 **어느 상태에서나 `FINAL`** 입니다.
                    - 게시물 공유(`traffic.post.shareCount`)만 원천 로그가 없어 항상 `NOT_AVAILABLE`(「집계 준비 중」)입니다.
                    - `traffic.limits[]` 는 커버리지 한계 고지 문장입니다(쇼룸 단위 방문 · SNS 출처 미상 · 공유 미집계) — 그대로 표시합니다.
                    - 재구매는 **이 브랜드**의 이전 공구 기준입니다(스튜디오 성과는 같은 인플루언서 기준이라 같은 공구라도 값이 다를 수 있다).

                    정산 연결 `settlement.breakdown` 은 정산 관리(13) 분해 그대로입니다 — 이 화면은 돈을 계산하지 않습니다.
                    응답 버튼(동의 · 제안 · 반대)은 없습니다 — 조정 협의 응답은 이슈 스레드(`settlement.adjustment.threadId`)에서만 합니다.
                    원가(`cost`)는 이 응답에만 있고 인플루언서 · 운영자 API 에는 없습니다.

                    """ + FIGURE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공구 성과",
                    content = @Content(schema = @Schema(implementation = SellerPerformanceDto.Detail.class), examples = {
                            @ExampleObject(name = "D1", summary = "지급 완료 · 원가 입력 완료(일부 생략)", value = DETAIL),
                            @ExampleObject(name = "D2", summary = "집계 중 — 예정 · 정산 후 확정 · 원가 잠금(일부 블록 생략)", value = DETAIL_AGGREGATING),
                            @ExampleObject(name = "D3", summary = "조정 협의 — 리워드에 걸린 값만 협의 중(일부 블록 생략)", value = DETAIL_ADJUSTING)})),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "남의 공구 · 없음 · 모집단 밖 (`GROUP_BUY_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_NOT_FOUND)))
    })
    ResponseEntity<SellerPerformanceDto.Detail> getDetail(@Parameter(description = "공구 id", example = "55") Long groupBuyId);

    @Operation(summary = "원가 저장 (D1-C → D1-D)",
            description = """
                    원가 · 수익 섹션의 저장 — 성과 화면의 **유일한 쓰기**입니다. **전체 교체**(부분 수정 없음)이고 응답은 저장 뒤 상세 전체입니다.

                    **권한:** SELLER

                    - `items` — 계약 상품 **전부**를 정확히 한 번씩(`contractItemId` · `unitCost`). 키는 상세 `cost.items[].contractItemId`. 미입력은 `unitCost: null`.
                      빠지거나 · 중복이거나 · 다른 공구의 항목이면 400 `PERFORMANCE_COST_ITEM_MISMATCH`.
                    - 공구 전체 비용 4종(`packagingCost` 부자재비 · `shippingCost` 배송비 · `returnDisposalCost` 반품 폐기 손실 · `etcCost` 기타) — 각 `null` 허용.
                    - 금액은 0 이상 10억 이하 정수(원) — 밖이면 400 `INVALID_INPUT`(메시지에 항목 이름). 전부 `null` 이면 「입력 안 함」으로 되돌린다(행은 지우지 않는다).
                    - **집계 중(정산 전)에는 저장할 수 없다** — 409 `PERFORMANCE_COST_LOCKED`(확정 수량이 바뀌면 소계도 달라진다). 정산 확인 중 · 조정 협의 · 지급 예정 · 지급 완료는 저장 가능.
                    - 인플루언서 리워드 · PG · 플랫폼 수수료는 정산액에서 이미 빠졌고 고정 지급비는 수익 계산에 자동 반영된다 — 다시 넣지 않는다.
                    - 저장하는 값은 개당 원가 · 4종뿐입니다. 소계 · 총 원가 · 수익 · 수익률은 조회 때 계산합니다(응답 `cost`). 이력은 남기지 않습니다.

                    **검사 순서:** 소유 · 모집단(404) → 집계 중(409) → `items` 항목 · 개당 원가 범위(400 · 한 줄씩) → 4종 금액 범위(400)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 뒤 상세 — `cost.updatedAt` 갱신 · `totalCost` · `profit` · `profitRate` 재계산",
                    content = @Content(schema = @Schema(implementation = SellerPerformanceDto.Detail.class),
                            examples = @ExampleObject(summary = "D1-D 원가 입력 완료(일부 생략)", value = DETAIL))),
            @ApiResponse(responseCode = "400", description = "항목 불일치(`PERFORMANCE_COST_ITEM_MISMATCH`) · 금액 범위(`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "항목 불일치", value = ERR_COST_ITEM_MISMATCH),
                            @ExampleObject(name = "금액 범위", value = ERR_COST_AMOUNT)})),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "남의 공구 · 없음 · 모집단 밖 (`GROUP_BUY_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "집계 중 (`PERFORMANCE_COST_LOCKED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_COST_LOCKED)))
    })
    ResponseEntity<SellerPerformanceDto.Detail> saveCost(
            @Parameter(description = "공구 id", example = "55") Long groupBuyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    schema = @Schema(implementation = SellerPerformanceDto.CostRequest.class),
                    examples = @ExampleObject(value = COST_REQUEST))) SellerPerformanceDto.CostRequest request);
}
