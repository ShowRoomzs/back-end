package showroomz.api.admin.metrics.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.metrics.dto.AdminMetricsDto;
import showroomz.api.admin.metrics.type.MetricsPreset;
import showroomz.api.admin.metrics.type.MetricsTab;
import showroomz.api.app.auth.DTO.ErrorResponse;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Admin - Metrics", description = "관리자 운영 지표 API")
public interface AdminMetricsControllerDocs {

    @Operation(
            summary = "운영 지표 조회",
            description = """
                    **집계 전용 · 저장 없음** — 같은 기간 계산은 서버가 10분 캐시합니다.

                    **기간 규칙(서버가 확정해 `period` · `compare` 에 적는다)** — 기준 시각은 오늘 00:00.

                    | preset | 기간 · 단위 | 비교 |
                    |---|---|---|
                    | `TODAY` | 오늘 00:00 ~ 지금 · 시간별 | 어제 같은 시간대 |
                    | `D7`(기본) | 7일 전 ~ 어제 · 일별 | 직전 7일 |
                    | `M1` | 이번 달 1일 ~ 어제 · 일별(1일이면 지난달 전체) | 전월 1일 ~ 같은 일 |
                    | `M6` | 6개월 · 월별 | 직전 같은 길이 |
                    | `CUSTOM` | `from` ~ `to`(포함 · 최대 1년) · 90일 초과면 월별 | 직전 같은 길이 |

                    **탭** — `CORE`(기본 · `cards` 4칸 포함) · `SUPPLY` · `GROUP_BUY` · `DEMAND` · `OPERATION`.
                    `rows[].deltaKind` 는 지표 방향(좋아지는 쪽이 UP 인지 DOWN 인지)으로 판정한 GOOD · BAD · NEUTRAL 입니다.
                    비율 지표는 `currentCount`(「4/6」)를 함께 내립니다. 분모가 0 이면 `current = null`.
                    신규 입점처럼 값을 나눠 적는 지표는 `currentBreakdown` · `previousBreakdown`(「1곳 · 3명」)을 함께 내립니다 — 값 칸에 이 문구를 씁니다.

                    - 거래액 = 결제 시점 · 취소 포함 · 공구 주문만 / 반품 · 교환율 = 접수 ÷ 배송완료 / 공구당 지표 = 중앙값
                    - 재공구 판정 60일(`admin.metrics.regroupbuy-window-days`) · 수수료 수익 = 거래액 × `admin.metrics.standard-fee-rate`(예상)
                    - 운영 「처리 완료」는 처리 시각이 기록되는 큐만(어드민 홈 「오늘 처리 완료」와 같은 원천)
                    - 운영 분해 표 「큐별 처리」 6줄(1:1 문의 답변 · 브랜드 · 인플루언서 입점 심사 · 오픈 승인 검토 · 배송 예외 확인 · 신고 게시물 검토 ·
                      변경 요청 검토) — 평균 소요는 큐마다 단위가 다르다(문의 시간 · 입점 심사 · 오픈 승인 · 변경 요청 영업일 · 신고 게시물 일) ·
                      배송 예외는 처리 시각이 없어 「—」

                    **권한:** ADMIN"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminMetricsDto.Response.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE — CUSTOM 기간 오류(from > to · 1년 초과)")
    })
    ResponseEntity<AdminMetricsDto.Response> getMetrics(
            @Parameter(description = "TODAY · D7(기본) · M1 · M6 · CUSTOM", example = "D7") MetricsPreset preset,
            @Parameter(description = "CUSTOM 시작일(포함)", example = "2026-09-01") LocalDate from,
            @Parameter(description = "CUSTOM 종료일(포함)", example = "2026-09-30") LocalDate to,
            @Parameter(description = "CORE(기본) · SUPPLY · GROUP_BUY · DEMAND · OPERATION", example = "CORE") MetricsTab tab);

    @Operation(
            summary = "탭 배지(나빠진 지표 수)",
            description = """
                    5탭 배지를 한 번에 — 탭마다 `rows` 중 `deltaKind = BAD` 인 지표 수입니다. **핵심 탭은 `cards` 4칸도 함께 셉니다**
                    (표 줄과 4칸에 같은 지표가 있으면 한 번). 0 이면 배지를 숨깁니다. 기간 파라미터는 지표 조회와 같습니다.

                    **권한:** ADMIN"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminMetricsDto.TabBadge.class)))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE — CUSTOM 기간 오류(from · to 누락 · from > to · 1년 초과)"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<AdminMetricsDto.TabBadge>> getTabs(
            @Parameter(description = "TODAY · D7(기본) · M1 · M6 · CUSTOM", example = "D7") MetricsPreset preset,
            @Parameter(description = "CUSTOM 시작일(포함)", example = "2026-09-01") LocalDate from,
            @Parameter(description = "CUSTOM 종료일(포함)", example = "2026-09-30") LocalDate to);
}
