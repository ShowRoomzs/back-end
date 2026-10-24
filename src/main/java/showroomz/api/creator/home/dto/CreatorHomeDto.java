package showroomz.api.creator.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.api.common.account.dto.MemberAccountStatusResponse;
import showroomz.api.common.calendar.dto.HolidayItem;
import showroomz.api.common.sanction.SanctionHoldView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 쇼룸 스튜디오 홈(1017 설계서 5절 · ui-studio-14-home). */
public final class CreatorHomeDto {

    private CreatorHomeDto() {
    }

    // ------------------------------------------------------------------ GET /v1/creator/home(5-1)

    @Schema(name = "CreatorHome")
    public record Home(
            @Schema(description = "계정 상태 — `GET /v1/creator/account-status` 와 같은 블록(H5 정지 배너)") MemberAccountStatusResponse account,
            Guide guide,
            @Schema(description = "진행 중 공구 실시간 띠 — 없으면 null(H3). 이후 갱신은 `GET /v1/creator/home/live`", nullable = true) Live live,
            @Schema(description = "할 일 — 서버가 정렬한다(세금계산서 맨 위 · 기한순 · 기한 없는 일 · 연결 요청 맨 아래)") List<Todo> todos,
            @Schema(description = "다가오는 일정 최대 3건 — 할 일이 없을 때(H2) 그린다") List<Upcoming> upcoming,
            Reward reward,
            Showroom7d showroom7d
    ) {
    }

    @Schema(name = "CreatorHomeGuide", description = "시작 가이드(H3) — 첫 연결 요청이 오면 사라진다(숨기기 버튼 없음)")
    public record Guide(
            @Schema(description = "표시 여부 = 브랜드 연결 요청을 한 번도 받지 않음") boolean visible,
            @Schema(description = "SETTLEMENT_INFO · PROFILE · FIRST_POST · SHOWROOM_LINK 순") List<ChecklistItem> checklist,
            @Schema(description = "판정 가능한 3개 중 완료 수 — 링크 복사는 FE 가 로컬로 센다", example = "2") int completed,
            @Schema(example = "4") int total
    ) {
    }

    @Schema(name = "CreatorHomeGuideItem")
    public record ChecklistItem(
            @Schema(example = "PROFILE") String key,
            @Schema(description = "SHOWROOM_LINK 는 판정할 수 없어(복사는 기기 동작) null", nullable = true) Boolean done,
            @Schema(description = "SHOWROOM_LINK 만 — 복사할 쇼룸 주소", example = "https://showroomz.shop/@beauty_log", nullable = true)
            String showroomUrl
    ) {
    }

    @Schema(name = "CreatorHomeTodo", description = "할 일 카드 한 장 — 문구는 서버가 내린다(다섯 상태에서 같은 카드가 같은 말을 한다)")
    public record Todo(
            @Schema(description = "TAX_INVOICE_ISSUE · CONTRACT_SIGN · EXTENSION_RESPOND · GROUP_BUY_POST_WRITE · "
                    + "GROUP_BUY_POST_RESUBMIT · GROUP_BUY_POST_HIDDEN_FIX · CONNECTION_REQUESTS", example = "CONTRACT_SIGN")
            String kind,
            @Schema(description = "기한 — 게시물 작성 · 재등록은 오픈일(제출 마감 규칙 없음 · 달력에는 넣지 않는다). 없으면 null", nullable = true)
            LocalDateTime dueAt,
            @Schema(description = "기한 칩 문구", example = "서명 기한 10.24", nullable = true) String dueLabel,
            @Schema(example = "계약 서명 · 밤 리페어 크림 공구") String title,
            @Schema(example = "메일 · 문자로 받은 링크에서 서명해 주세요") String description,
            @Schema(example = "계약 보기") String actionLabel,
            Target target,
            @Schema(description = "정지 중 막힌 카드 — 버튼 비활성 + 이유. 막히지 않으면 null. "
                    + "연장 응답은 거절이 허용돼 항상 null(수락만 extra.acceptBlocked)", nullable = true) Blocked blocked,
            @Schema(description = "종류별 보조 값 — TAX_INVOICE_ISSUE: settlementId · groupBuyName · confirmedAt · amount · rejected / "
                    + "CONTRACT_SIGN: brandSigned · brandName / EXTENSION_RESPOND: extensionDays · brandName · acceptBlocked"
                    + "(정지 중 true — [수락]만 비활성) · acceptBlockedMessage(acceptBlocked 일 때만 「연장 수락은 정지 해제 후 할 수 있어요」) / "
                    + "CONNECTION_REQUESTS: count",
                    nullable = true)
            Map<String, Object> extra
    ) {
    }

    @Schema(name = "CreatorHomeTarget")
    public record Target(
            @Schema(description = "SETTLEMENT · CONTRACT · GROUP_BUY · CONNECTION_REQUESTS", example = "CONTRACT") String type,
            @Schema(nullable = true) Long id
    ) {
    }

    @Schema(name = "CreatorHomeBlocked")
    public record Blocked(@Schema(example = "정지 해제 후 등록할 수 있어요") String message) {
    }

    @Schema(name = "CreatorHomeUpcoming")
    public record Upcoming(
            LocalDate date,
            @Schema(example = "공구 종료 · 밤 리페어 크림 공구") String title,
            @Schema(example = "종료 후 주문이 모두 끝나면 정산이 생성돼요") String description,
            Target target
    ) {
    }

    @Schema(name = "CreatorHomeReward")
    public record Reward(
            @Schema(description = "지급 예정 금액 — 없으면 null", example = "602800", nullable = true) Long payoutScheduledAmount,
            @Schema(description = "가장 가까운 지급 예정일(보류 건 제외)", nullable = true) LocalDate payoutScheduledDate,
            @Schema(description = "`TAX_INVOICE_UNVERIFIED` — 「지급 조건 세금계산서 확인 후」", nullable = true) String payoutCondition,
            @Schema(description = "누적 수령(세후) — 없으면 null", example = "4815000", nullable = true) Long totalPaidNet
    ) {
    }

    @Schema(name = "CreatorHomeShowroom7d", description = "쇼룸 최근 7일 — 쇼룸 통계(DAYS_7)와 같은 정의")
    public record Showroom7d(
            @Schema(description = "방문자(사람 수)", example = "1184") long visitors,
            @Schema(example = "37") long newFollowers,
            @Schema(description = "팔로우 전환율(%) — 신규 팔로워 × 100 / 방문자 · 방문자 0 이면 null", example = "3.1", nullable = true)
            Double conversionRate
    ) {
    }

    // ------------------------------------------------------------------ GET /v1/creator/home/live(5-2)

    @Schema(name = "CreatorHomeLive", description = "진행 중 공구 실시간 띠 — 공구 관리 잠정 실적과 같은 정의(취소 · 반품 제외)")
    public record Live(
            @Schema(description = "집계 시각(「n분 전 갱신」)") LocalDateTime asOf,
            Long groupBuyId,
            String name,
            String brandName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            @Schema(description = "판매 금액(잠정)", example = "8420000") long salesAmount,
            @Schema(description = "오늘 판매 금액 「오늘 +」", example = "1254000") long salesAmountToday,
            @Schema(example = "256") long orders,
            @Schema(example = "38") long ordersToday,
            @Schema(description = "쇼룸 방문(횟수) — 공구 기간", example = "3120") long visits,
            @Schema(description = "오늘 쇼룸 방문", example = "412") long visitsToday
    ) {
    }

    // ------------------------------------------------------------------ GET /v1/creator/home/calendar(5-3)

    @Schema(name = "CreatorHomeCalendar")
    public record Calendar(
            @Schema(example = "2026-10") String month,
            @Schema(description = "조회 구간 시작 — 요청 월 1일 − 7일") LocalDate from,
            @Schema(description = "조회 구간 끝(포함) — 요청 월 말일 + 7일") LocalDate to,
            List<HolidayItem> holidays,
            List<CalendarGroupBuy> groupBuys,
            List<Deadline> deadlines
    ) {
    }

    @Schema(name = "CreatorHomeCalendarGroupBuy")
    public record CalendarGroupBuy(
            Long groupBuyId,
            String name,
            String brandName,
            LocalDateTime startAt,
            @Schema(description = "종료 — 조기 마감 · 중단이면 실제 종료 시각") LocalDateTime endAt,
            @Schema(description = "IN_PROGRESS · UPCOMING · ENDED", example = "IN_PROGRESS") String status,
            @Schema(description = "정지 보류 배지(승인 보류 · 오픈 보류)", nullable = true) SanctionHoldView hold
    ) {
    }

    @Schema(name = "CreatorHomeCalendarDeadline")
    public record Deadline(
            LocalDate date,
            @Schema(description = "SIGN_DUE(내 서명 기한) · EXTENSION_RESPOND_DUE(연장 응답 — 공구 종료일)", example = "SIGN_DUE") String kind,
            @Schema(example = "서명 기한 · 밤 리페어 크림 공구") String label,
            @Schema(description = "CONTRACT · GROUP_BUY", example = "CONTRACT") String targetType,
            Long targetId
    ) {
    }
}
