package showroomz.api.admin.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.groupbuy.type.AdminGroupBuyQueue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 조치 큐 · 탭 카운트 · 최단 기한 · 정산 지연 감시(32 설계 3-3). <b>파라미터를 받지 않는다</b> — 검색어가 걸린 카운트는
 * 「처리할 건이 남아 있다」는 사실을 가린다. GNB가 공구 화면 밖에서도 폴링하므로 목록과 분리된 가벼운 쿼리다.
 */
@Schema(description = "어드민 공구 요약 — GNB 배지 · 탭 배지 · 툴바")
public record AdminGroupBuySummaryResponse(

        @Schema(description = "큐 4종 건수 — 네 큐는 서로 배타적이다",
                example = "{\"OPEN_REVIEW\":2,\"SUSPEND_REQUEST\":1,\"EARLY_CLOSE_REQUEST\":1,\"APPEAL_REVIEW\":1}")
        Map<AdminGroupBuyQueue, Long> queues,

        @Schema(description = "GNB 배지 = 큐 4개의 합 = 조치 필요 탭 행 수. 0이면 FE가 배지를 그리지 않는다", example = "5")
        long actionRequiredCount,

        @Schema(description = "탭 8종 건수",
                example = "{\"ALL\":10,\"ACTION_REQUIRED\":5,\"PREPARING\":2,\"READY\":0,\"IN_PROGRESS\":5,\"ENDED\":1,\"SETTLED\":1,\"SUSPENDED\":1}")
        Map<String, Long> tabCounts,

        @Schema(description = "큐마다 가장 이른 1건 — 기한이 정해진 큐(OPEN_REVIEW · APPEAL_REVIEW)만. 요청 2종은 검토 SLA 미정이라 없다")
        List<NearestDeadline> nearestDeadlines,

        SettlementWatch settlementWatch
) {

    public record NearestDeadline(
            AdminGroupBuyQueue queue,
            Long groupBuyId,
            String title,
            @Schema(description = "OPEN_REVIEW: 제출일 + SLA 영업일 23:59:59 · APPEAL_REVIEW: 집행 예정 일시") LocalDateTime dueAt,
            @Schema(description = "오늘 기준 남은 일수 — 지났으면 음수", example = "1") long daysLeft
    ) {
    }

    /** 조치와 다른 축 — 기한을 넘겨도 조치 큐에 더하지 않는다(누를 버튼이 없다). 톤은 FE가 {@code reached}로 고른다. */
    public record SettlementWatch(
            @Schema(description = "정산이 끝나지 않은 종료(ENDED) 공구 전체", example = "2") long watchingCount,
            @Schema(description = "종료 + 감시 일수가 지난 건수", example = "0") long overdueCount,
            @Schema(nullable = true) Nearest nearest
    ) {
    }

    public record Nearest(Long groupBuyId, String title, LocalDate dueAt, boolean reached) {
    }
}
