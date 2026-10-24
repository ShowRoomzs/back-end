package showroomz.api.admin.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 어드민 홈(1017 설계서 6절 · ui-admin-00-home rev.4) · 사이드바 배지. */
public final class AdminHomeDto {

    private AdminHomeDto() {
    }

    @Schema(name = "AdminHome")
    public record Home(
            LocalDateTime asOf,
            @Schema(description = "연동 상태 3종 — PG · DELIVERY_TRACKING · E_SIGN") List<Integration> integrations,
            Todo todo,
            @Schema(description = "오늘 일정 — 시각순") List<ScheduleItem> todaySchedule,
            MetricsToday metricsToday
    ) {
    }

    @Schema(name = "AdminHomeIntegration")
    public record Integration(
            @Schema(example = "DELIVERY_TRACKING") String key,
            @Schema(example = "택배 추적") String label,
            @Schema(description = "OK · DEGRADED · DOWN · DISABLED · NOT_INTEGRATED", example = "DEGRADED") String status,
            @Schema(description = "장애 시작 시각(DEGRADED) — PG 는 임계를 넘은 결제 운영 지표에 걸린 가장 이른 건의 시각 · 택배 추적은 첫 실패 기록",
                    nullable = true) LocalDateTime since,
            @Schema(example = "조회 실패", nullable = true) String message,
            @Schema(description = "영향받는 큐 — 배너 「배송 예외 판정 일시 중지」", example = "[\"배송 예외 확인\"]") List<String> affectedQueues
    ) {
    }

    @Schema(name = "AdminHomeTodo", description = "할 일 — 시안 「할 일 38 · 실패 2 · 기한 초과 1 · 기한 임박 3 · 대기 32」")
    public record Todo(
            @Schema(description = "합계 = 사이드바 배지 합(회원 수 제외)", example = "38") long total,
            @Schema(example = "2") long failed,
            @Schema(example = "1") long overdue,
            @Schema(description = "기한 임박 = 집행 대기 + D-1", example = "3") long dueSoon,
            @Schema(example = "32") long waiting,
            @Schema(description = "오늘 처리 완료 — 운영팀 전체 · 처리 시각이 있는 큐만", example = "14") long processedToday,
            @Schema(description = "처리 완료 집계 안내") String note,
            @Schema(description = "급한 순(실패 → 기한 초과 → 집행 대기 → 기한 임박 → 대기)") List<TodoRow> rows
    ) {
    }

    @Schema(name = "AdminHomeTodoRow")
    public record TodoRow(
            @Schema(description = "FAILED · OVERDUE · EXECUTION_PENDING · DUE_SOON · WAITING", example = "DUE_SOON") String urgency,
            @Schema(description = "상태 배지 문구", example = "D-1") String badge,
            @Schema(description = "큐 코드", example = "OPEN_REVIEW") String queue,
            @Schema(example = "오픈 승인 · 밤 리페어 크림 공구") String title,
            @Schema(description = "보조줄 — 건별 행은 건의 요약, 대기 행은 큐 요약(「추적 정지 2 · 집화 확인 필요 1」 · 「브랜드 2 · 인플루언서 2」 · "
                    + "「기한 초과 1건은 위에」). 시안에 「—」인 큐는 null", example = "브랜드 2 · 인플루언서 2", nullable = true) String detail,
            @Schema(description = "사이드바 메뉴 — ONBOARDING · MEMBERS · CONTRACTS · GROUP_BUYS · TRANSACTIONS · SETTLEMENTS · CS · POSTS",
                    example = "GROUP_BUYS") String menu,
            @Schema(description = "경과 — 가장 오래된 건 기준", example = "2일 경과", nullable = true) String ageText,
            @Schema(description = "기한 문구", example = "마감 10.23", nullable = true) String dueText,
            Target target,
            @Schema(description = "이 행이 대표하는 건수 — 건별 행은 1", example = "1") long count
    ) {
    }

    @Schema(name = "AdminHomeTarget")
    public record Target(
            @Schema(description = "이동 대상 — REFUND · SETTLEMENT · INQUIRY · GROUP_BUY · SELLER_APPLICATION · CREATOR_APPLICATION · "
                    + "TAX_DOCUMENT · QUEUE(목록 필터)", example = "GROUP_BUY") String type,
            @Schema(nullable = true) Long id,
            @Schema(description = "QUEUE 일 때 목록 경로 · 필터", example = "/group-buys?queue=OPEN_REVIEW", nullable = true) String filter
    ) {
    }

    @Schema(name = "AdminHomeScheduleItem")
    public record ScheduleItem(
            LocalDateTime time,
            @Schema(description = "GROUP_BUY_OPEN · GROUP_BUY_END · SETTLEMENT_PAYOUT · SETTLEMENT_REVIEW_DUE · ADJUSTMENT_DEADLINE · "
                    + "ADMIN_SUSPENSION_EXECUTE", example = "GROUP_BUY_OPEN") String kind,
            String title,
            @Schema(nullable = true) String detail,
            Target target
    ) {
    }

    @Schema(name = "AdminHomeMetricsToday")
    public record MetricsToday(
            LocalDateTime asOf,
            @Schema(description = "ORDERS · PAID_AMOUNT · SIGNUPS · CLAIMS") List<MetricItem> items
    ) {
    }

    @Schema(name = "AdminHomeMetricItem")
    public record MetricItem(
            @Schema(example = "ORDERS") String key,
            @Schema(example = "주문") String label,
            @Schema(description = "오늘 00:00 ~ 지금") long today,
            @Schema(description = "어제 00:00 ~ 어제 이 시각") long yesterdaySameTime
    ) {
    }

    // ------------------------------------------------------------------ GET /v1/admin/badges

    @Schema(name = "AdminBadges", description = "사이드바 배지 — 홈 할 일과 같은 큐 계산(합계가 어긋나지 않는다)")
    public record Badges(
            Onboarding onboarding,
            Members members,
            @Schema(description = "계약 — 검토 대기 · 공구 생성 대기 · 만료 확인 · 재발송 요청 · 해지 요청(계약 요약 actionRequiredCount 와 같다)",
                    example = "5") long contracts,
            @Schema(description = "공구 — 오픈 승인 · 중단 요청 · 조기 마감 요청 · 소명 검토") long groupBuys,
            @Schema(description = "거래 — 환불 실패 · 사유 환불 집행 대기 · 처리 지연 · 배송 예외(배지 범위 ALL 일 때) · 반려 이의") long transactions,
            @Schema(description = "정산 — 분배 실패 · 세금계산서 발행 · 대조 · 차감 미회수") long settlements,
            Cs cs,
            Posts posts
    ) {
    }

    @Schema(name = "AdminBadgesOnboarding")
    public record Onboarding(long total, long brand, long creator, long changeRequests) {
    }

    @Schema(name = "AdminBadgesMembers")
    public record Members(
            @Schema(description = "탈퇴 승인 검토") long todo,
            @Schema(description = "브랜드 회원(탈퇴 제외)") long brandCount,
            @Schema(description = "인플루언서 회원(탈퇴 제외)") long creatorCount,
            @Schema(description = "소비자 회원(탈퇴 제외)") long userCount
    ) {
    }

    @Schema(name = "AdminBadgesCs")
    public record Cs(
            @Schema(description = "1:1 문의 답변 대기 + 상품 문의 삭제 요청") long total,
            @Schema(description = "1:1 문의 답변 대기") long inquiries
    ) {
    }

    @Schema(name = "AdminBadgesPosts")
    public record Posts(
            @Schema(description = "게시물 조치 필요 합 — 홈 할 일에 드는 큐만(소통 스레드 threads 는 더하지 않는다)") long total,
            @Schema(description = "일반 게시물 — 신고 접수 + 이의 신청 심사 중") long general,
            @Schema(description = "소통 스레드 — 브랜드 · 인플루언서 운영팀 채널의 운영팀 기준 안 읽은 메시지 합(채널 수 아님 · 이슈 탭 제외). "
                    + "할 일이 아니라 total 과 홈 할 일 합계에는 들지 않는다(55 계획서 A12 · D24)", example = "3") long threads
    ) {
    }
}
