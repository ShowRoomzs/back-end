package showroomz.api.creator.performance.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.creator.performance.dto.CreatorPerformanceDto;
import showroomz.api.creator.performance.type.CreatorPerformanceSort;
import showroomz.api.creator.performance.type.CreatorPerformanceStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.Set;

@Tag(name = "Creator - Performance", description = "쇼룸 스튜디오 성과 관리(GNB #6) — 종료 공구의 확정 성과 · 조회 전용")
public interface CreatorPerformanceControllerDocs {

    @Operation(
            summary = "성과 목록(L1 · L2)",
            description = """
                    **종료 공구 전용** — 진행 중 · 중단 · 판매 0건 공구는 없다(진행 중은 공구 관리에서).

                    - 상태 3종: `COLLECTING`(집계 중 · 중립) · `ADJUSTING`(조정 협의 · 경고) · `SETTLED`(정산 완료 · 성공).
                      주문 종결 대기 · 정산 확인 중 · 지급 예정은 전부 집계 중이다
                    - 집계 중 행은 판매 지표 · 리워드가 `null`(「—」) · 조정 협의 행은 판매 지표가 값이고 리워드만 `null`(`rewardStage = AFTER_AGREEMENT`)
                    - `months` 3 · 6(기본) · 12 — 종료일 기준 · `status` 복수 · `keyword` 공구명 · 브랜드명 · **[검색] 버튼으로 실행**
                    - `sort` `ENDED_DESC`(기본) · `REWARD_DESC` · `CONVERSION_DESC` — 값이 없는 행은 맨 뒤
                    """,
            security = @SecurityRequirement(name = "Authorization"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록"),
            @ApiResponse(responseCode = "400", description = "months · status · sort 범위 밖",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<CreatorPerformanceDto.ListItem>> getList(
            @Parameter(description = "조회 기간(개월) — 3 · 6 · 12", example = "6") Integer months,
            @Parameter(description = "상태(복수) — 비면 전체") Set<CreatorPerformanceStatus> status,
            @Parameter(description = "공구명 · 브랜드명", example = "선크림") String keyword,
            @Parameter(description = "정렬") CreatorPerformanceSort sort,
            PagingRequest pagingRequest);

    @Operation(
            summary = "성과 요약 — KPI 8장 · 상태 칩",
            description = """
                    KPI 모집단은 **정산 완료 공구만**이다 — 집계 중 · 조정 협의는 리워드가 확정되지 않았다.
                    평균은 **공구별 값의 산술평균**(「공구 1건당」). 값이 없으면 `null`(「—」)이고 `settledCount` 만 0 이다.
                    `hasAnyEnded = false` 면 빈 상태(L2) — KPI 8장 자리는 그대로 두고 값만 「—」.
                    """,
            security = @SecurityRequirement(name = "Authorization"))
    @ApiResponses(@ApiResponse(responseCode = "200", description = "요약"))
    ResponseEntity<CreatorPerformanceDto.Summary> getSummary(
            @Parameter(description = "조회 기간(개월) — 3 · 6 · 12", example = "6") Integer months);

    @Operation(
            summary = "성과 상세(D1 ~ D4)",
            description = """
                    공구 1건 리포트 — 유입 → 판매 → 소비자 → 팔로워 → 리워드.

                    - 집계 중(정산 전)은 판매 · 소비자 · 퍼널 구간 2 · 팔로워 구매전환율이 `estimated = true`(「예정」).
                      방문 · 게시물 · 팔로워 수는 확정값
                    - 돈은 정산 행 그대로 — `rewardStage` 가 `CONFIRMED` 가 아니면 금액 대신 「정산 후 확정」/「합의 후 확정」
                    - 조정 협의 중에도 응답 버튼은 없다 — `reward.adjustmentThreadId` 로 이슈 스레드로 이동
                    - 원가 · 배송 운영 품질 · 취소 · 반품 사유 분해는 없다(파트너 전용)
                    """,
            security = @SecurityRequirement(name = "Authorization"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상세"),
            @ApiResponse(responseCode = "404", description = "남의 공구 · 없음 · 판매 0건 공구",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "GROUP_BUY_NOT_FOUND", "message": "존재하지 않는 공구입니다." }
                                    """))),
            @ApiResponse(responseCode = "409", description = "진행 중 · 중단 공구",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "PERFORMANCE_NOT_READY", "message": "공구가 종료되면 성과를 열람할 수 있습니다." }
                                    """)))
    })
    ResponseEntity<CreatorPerformanceDto.Detail> getDetail(
            @Parameter(description = "공구 ID", example = "21") Long groupBuyId);
}
