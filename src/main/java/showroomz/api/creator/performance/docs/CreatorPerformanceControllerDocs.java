package showroomz.api.creator.performance.docs;

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
import showroomz.api.creator.performance.dto.CreatorPerformanceDto;
import showroomz.api.creator.performance.type.CreatorPerformanceSort;
import showroomz.api.creator.performance.type.CreatorPerformanceStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.Set;

import static showroomz.api.creator.performance.docs.CreatorPerformanceDocsExamples.*;

@Tag(name = "Creator - Performance", description = "쇼룸 스튜디오 성과 관리 API (§49 · 49 스튜디오 성과 설계서) — 종료 공구의 성과 · 조회 전용")
public interface CreatorPerformanceControllerDocs {

    String STATUS = """
            **상태 3종** — 공통 성과 상태 5종(파트너 12)을 인플루언서 관점으로 접은 값입니다. 저장하지 않고 정산 행에서 파생합니다.

            | `status` | 라벨 · 톤 | 포함하는 정산 상태 | `rewardStage` |
            |---|---|---|---|
            | `COLLECTING` | 집계 중 · `NEUTRAL` | 정산 전(주문 종결 대기) · 정산 확인 중 · 지급 예정 | `AFTER_SETTLEMENT`(「정산 후 확정」) |
            | `ADJUSTING` | 조정 협의 · `WARNING` | 조정 협의 | `AFTER_AGREEMENT`(「합의 후 확정」) |
            | `SETTLED` | 정산 완료 · `SUCCESS` | 지급 완료(분배 실패 포함) | `CONFIRMED`(금액 표시) |

            돈이 들어오기 전은 전부 「집계 중」입니다. 조정 협의만 따로 두는 이유는 판매 지표는 확정이고 리워드만 합의를 기다리기 때문입니다.
            """;

    String VALUES = """
            **값 규칙** — 값이 없으면 `null`(FE 「—」)이고 `0` 은 측정된 0 입니다. 비율은 % 값(소수 1자리 · `5.8` = 5.8%) · 금액은 원 · 리워드는 세전입니다.
            """;

    @Operation(summary = "성과 목록 (L1 · L2)",
            description = """
                    종료 공구 단위 비교표입니다. 모집단 = 내 공구 · 종료(`ENDED` · `SETTLED`) · 결제 완료 하위주문 1건 이상 —
                    진행 중 · 중단 · 판매 0건 공구는 없습니다(진행 중 공구는 공구 관리에서 봅니다).

                    **권한:** CREATOR

                    **파라미터**
                    - `months` — `3` · `6`(기본) · `12`. 종료일이 오늘 − N개월 이후인 공구. 그 밖의 값은 400
                    - `status` — `COLLECTING` · `ADJUSTING` · `SETTLED` 복수 · 생략 시 전부(칩이 전부 켜진 기본 화면 = 파라미터 없음)
                    - `keyword` — 공구명 · 브랜드명 부분 일치(대소문자 무시 · 앞뒤 공백 제거) · **[검색] 버튼을 누를 때 보냅니다**
                    - `sort` — `ENDED_DESC`(종료일 최신순 · 기본) · `REWARD_DESC`(내 리워드 높은 순) · `CONVERSION_DESC`(구매전환율 높은 순).
                      값이 없는 행(집계 중 · 조정 협의)은 맨 뒤 · 같은 값은 종료일 최신순
                    - `page`(1부터) · `size`(기본 20 · 100 을 넘으면 100)

                    **행 값**

                    | `status` | 판매 3열(`confirmedSalesAmount` · `confirmedQuantity` · `conversionRate`) | `myRewardAmount` |
                    |---|---|---|
                    | `COLLECTING` | `null` — 표에 예정값을 섞으면 정렬 · 합계가 오염된다 | `null` |
                    | `ADJUSTING` | 값(주문이 다 끝났다) | `null`(「합의 후 확정」) |
                    | `SETTLED` | 값 | 판매 리워드(합의 반영) + 고정 지급비 |

                    집계 중 행도 상세(D2)는 열립니다.

                    """ + STATUS + VALUES)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성과 목록과 페이지 정보",
                    content = @Content(schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(summary = "집계 중 · 조정 협의 · 정산 완료", value = LIST))),
            @ApiResponse(responseCode = "400", description = "months 가 3 · 6 · 12 밖 · 없는 enum 값 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "months", value = ERR_INVALID_MONTHS),
                            @ExampleObject(name = "enum", value = ERR_INVALID_INPUT)})),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<PageResponse<CreatorPerformanceDto.ListItem>> getList(
            @Parameter(description = "조회 기간(개월) — 3 · 6(기본) · 12 · 종료일 기준", example = "6") Integer months,
            @Parameter(description = "상태 필터(복수) — COLLECTING · ADJUSTING · SETTLED · 생략 시 전부", example = "SETTLED")
            Set<CreatorPerformanceStatus> status,
            @Parameter(description = "공구명 · 브랜드명 부분 일치", example = "선크림") String keyword,
            @Parameter(description = "정렬 — ENDED_DESC(기본) · REWARD_DESC · CONVERSION_DESC", example = "ENDED_DESC")
            CreatorPerformanceSort sort,
            PagingRequest pagingRequest);

    @Operation(summary = "성과 요약 (KPI 8장 · 상태 칩)",
            description = """
                    목록 머리의 기간 표기 · KPI 8장 · 상태 칩 건수입니다. **기간(`months`)만 따르고** 검색어 · 상태 칩은 따라가지 않습니다.

                    **권한:** CREATOR

                    **KPI 모집단 = 정산 완료(`SETTLED`) 공구만** — 집계 중 · 조정 협의는 리워드가 확정되지 않아 평균을 왜곡합니다.
                    평균은 **공구별 값의 산술평균**(「공구 1건당」)입니다.

                    | 필드 | 산식 |
                    |---|---|
                    | `totalReward` | Σ(판매 리워드 + 고정 지급비) · 세전 |
                    | `averageReward` | `totalReward ÷ settledCount` · 1원 절사 |
                    | `settledCount` | 정산 완료 공구 수 — 유일하게 `null` 이 아닌 KPI(0 가능) |
                    | `averageConversionRate` | 공구별 (구매확정 ÷ 쇼룸 방문(명)) 평균 |
                    | `averageRepeatRate` | 공구별 (이전 내 공구 구매자 ÷ 구매 소비자) 평균 |
                    | `averageFollowerBuyerRate` | 공구별 (결제 시점 팔로워 구매자 ÷ 구매 소비자) 평균 |
                    | `averagePostImpressions` | 공구 게시물 조회수 평균 — 게시물이 있는 공구만 |
                    | `genderRatio` | 정산 완료 공구 구매 소비자 합산 성비 · 미확인 제외 · 큰 쪽이 `majorFirst` |

                    **빈 상태**
                    - `hasAnyEnded = false`(기간 안 종료 공구 0건) → L2 「아직 종료된 공구가 없습니다」. KPI 8장 자리는 두고 값만 「—」
                    - 종료 공구는 있는데 전부 집계 중 · 조정 협의 → L1 이고 KPI 는 「—」(`settledCount = 0`)
                    - `statusCounts` 는 세 상태 키가 항상 있습니다(0 포함)

                    """ + VALUES)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기간 · KPI · 상태 칩",
                    content = @Content(schema = @Schema(implementation = CreatorPerformanceDto.Summary.class), examples = {
                            @ExampleObject(name = "L1", summary = "정산 완료 4건", value = SUMMARY),
                            @ExampleObject(name = "L2", summary = "기간 안 종료 공구 없음", value = SUMMARY_EMPTY)})),
            @ApiResponse(responseCode = "400", description = "months 가 3 · 6 · 12 밖 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_INVALID_MONTHS))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<CreatorPerformanceDto.Summary> getSummary(
            @Parameter(description = "조회 기간(개월) — 3 · 6(기본) · 12", example = "6") Integer months);

    @Operation(summary = "공구 성과 상세 (D1 · D2 · D3 · D4)",
            description = """
                    공구 1건 리포트입니다. 블록 순서 = 상단 안내 → 리워드 바 → KPI → ① 유입 → 전환 퍼널 → ② 판매 → ③ 소비자 → ④ 팔로워 → ⑤ 리워드.

                    **권한:** CREATOR — 내 공구가 아니거나 없거나 판매 0건이면 404(존재를 알리지 않는다) · 내 공구지만 진행 중 · 중단이면 409

                    **화면별 블록**

                    | 화면 | 조건 | `notice` | 판매 · 소비자 · 퍼널 구간 2 | `reward.stage` |
                    |---|---|---|---|---|
                    | D1 | 정산 완료 | `null` | 확정 | `CONFIRMED` |
                    | D2 | 집계 중 · 정산 전 | `NEUTRAL` 「미처리 반품 N건 …」(미종결이 있을 때만) | **`estimated = true`**(「예정」) | `AFTER_SETTLEMENT` + 예정액 |
                    | D2' | 집계 중 · 정산 확인 중 · 지급 예정 | `null` | 확정(`estimated = false`) | `AFTER_SETTLEMENT` + 원래 금액 |
                    | D3 | 조정 협의 | `WARNING` + `link = ISSUE_THREAD` | 확정 | `AFTER_AGREEMENT` + 내 요청 · 브랜드 제안 |
                    | D4 | 정산 완료 + 합의 반영 | `NEUTRAL` + `link = ISSUE_THREAD_HISTORY` | 확정 · `sales.adjustmentDelta` | `CONFIRMED` + `reward.adjustment` |

                    - **「예정」은 판매 · 소비자 블록에만** 붙습니다(블록 단위 `estimated`). 방문 · 유입 경로 · 게시물 · 팔로워 수는 줄지 않는 값이라 언제나 확정입니다.
                      팔로워 비교표는 `followers.conversionEstimated` 로 구매전환율 열만 예정입니다
                    - **돈은 정산 행 그대로**입니다 — `stage` 가 `CONFIRMED` 가 아니면 `amount = null` 이고 금액 대신 `stage` 라벨(「정산 후 확정」 · 「합의 후 확정」)을 그립니다.
                      실지급(`reward.payout.amount`)은 정산의 인플루언서 실지급액이고 서버 · FE 모두 계산하지 않습니다
                    - 상품별 판매 표의 금액 · 리워드는 **계약 기준**입니다. 합의로 바뀐 차액은 상품에 배분하지 않고 `sales.adjustmentDelta` 한 줄로 냅니다(D4)
                    - **응답 버튼(동의 · 제안 · 반대)은 없습니다** — 조정 협의 응답은 이슈 스레드(`notice.threadId` · `reward.adjustmentThreadId`)에서만 합니다
                    - 링크 재료 — [계약서 보기] `groupBuy.contractId` · [정산 상세 보기] `reward.settlementId` · [이 브랜드와 재협업 제안] `recollaboration.threadId`(브랜드 1:1 스레드 · 없으면 `null`)
                    - 원가 · 정산액 · ROAS · 배송 운영 품질 · 취소 · 반품 사유 분해는 **파트너 전용**이라 이 응답에 없습니다
                    - 연령 · 성별 분포는 구매 소비자 30명 미만이면 비공개(`distribution.suppressed`) · 구간 5명 미만은 「기타」로 합산합니다

                    """ + VALUES)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공구 성과",
                    content = @Content(schema = @Schema(implementation = CreatorPerformanceDto.Detail.class), examples = {
                            @ExampleObject(name = "D1", summary = "정산 완료(전체)", value = DETAIL),
                            @ExampleObject(name = "D2", summary = "집계 중 · 정산 전(일부 블록 생략)", value = DETAIL_COLLECTING),
                            @ExampleObject(name = "D3", summary = "조정 협의(일부 블록 생략)", value = DETAIL_ADJUSTING),
                            @ExampleObject(name = "D4", summary = "정산 완료 · 합의 반영(일부 블록 생략)", value = DETAIL_AGREED)})),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "남의 공구 · 없음 · 판매 0건 공구 (`GROUP_BUY_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "진행 중 · 중단 공구 (`PERFORMANCE_NOT_READY`) — 공구 관리에서 봅니다",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_NOT_READY)))
    })
    ResponseEntity<CreatorPerformanceDto.Detail> getDetail(
            @Parameter(description = "공구 ID — 성과 id 는 따로 없다(공구 관리 · 정산 상세의 「성과 보기」도 공구 id 로 온다)", example = "21")
            Long groupBuyId);
}
