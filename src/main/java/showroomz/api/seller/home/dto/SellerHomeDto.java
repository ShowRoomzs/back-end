package showroomz.api.seller.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import showroomz.api.common.account.dto.MemberAccountStatusResponse;
import showroomz.api.common.calendar.dto.HolidayItem;
import showroomz.api.common.sanction.SanctionHoldView;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 파트너센터 홈(1017 설계서 4절 · ui-partner-16-home). */
public final class SellerHomeDto {

    private SellerHomeDto() {
    }

    // ------------------------------------------------------------------ GET /v1/seller/home(4-1)

    @Schema(name = "SellerHome")
    public record Home(
            @Schema(description = "계정 상태 — `GET /v1/seller/account-status` 와 같은 블록(정지 배너 · 탑바 「정지」 칩 · [+ 계약 작성] 비활성)")
            MemberAccountStatusResponse account,
            Guide guide,
            PendingTasks pendingTasks,
            @Schema(description = "진행 중 공구 실시간 — 첫 그리기용. 이후 1분 갱신은 `GET /v1/seller/home/live`") Live live,
            ContractsInProgress contractsInProgress,
            UpcomingGroupBuys upcomingGroupBuys,
            Settlement settlement
    ) {
    }

    @Schema(name = "SellerHomeGuide", description = "첫 공구 가이드(H3 · H3a) — 단계 판정은 서버, 단계 문구는 FE")
    public record Guide(
            @Schema(description = "표시 여부 = 숨기지 않음 ∧ 오픈된 공구가 아직 없음") boolean visible,
            @Schema(description = "[가이드 숨기기]를 눌렀는가") boolean hidden,
            @Schema(description = "완료 단계 수(0 ~ 6)", example = "3") int completed,
            @Schema(description = "지금 할 단계 — 첫 미완 단계. 모두 끝났으면 null", example = "CONTRACT", nullable = true)
            String currentStep,
            @Schema(description = "6단계 — BRAND_INFO · PRODUCT · CONNECTION · CONTRACT · OPEN · ORDERS 순") List<GuideStep> steps,
            @Schema(description = "연결된 쇼룸 수(「연결 1명」)", example = "1") long connectedCreatorCount,
            @Schema(description = "가장 최근 연결된 쇼룸 이름(「뷰티로그와 연결되었습니다」)", example = "뷰티로그", nullable = true)
            String connectedCreatorName,
            @Schema(description = "브랜드가 설정한 발송 영업일(규칙 카드 「설정한 영업일(N)」)", example = "3", nullable = true)
            Integer shippingLeadDays
    ) {
    }

    @Schema(name = "SellerHomeGuideStep")
    public record GuideStep(
            @Schema(example = "CONNECTION") String key,
            boolean done
    ) {
    }

    @Schema(name = "SellerHomePendingTasks", description = "처리 대기 5묶음 — 메뉴별 요약과 같은 값을 쓴다(숫자가 홈과 메뉴에서 어긋나지 않는다)")
    public record PendingTasks(
            Sales sales,
            Claims claims,
            Inquiry inquiry,
            ContractTasks contract,
            SettlementTasks settlement,
            @Schema(description = "합계 — 기한 경과 · 지연(`shipOverdue` · `inspectOverdue`)은 이미 각 칸에 든 건이라 더하지 않는다. "
                    + "0 이면 H2 「처리할 일 없음」", example = "31") long total
    ) {
    }

    @Schema(name = "SellerHomeSalesTasks")
    public record Sales(
            @Schema(description = "준비 시작 — 진입 `/orders?tab=NEW`", example = "12") long prepareStart,
            @Schema(description = "송장 등록 — 진입 `tab=PREPARING`", example = "7") long invoiceRegister,
            @Schema(description = "발송 기한 경과(경고) — 진입 `tab=ALL&shipOverdue=true`", example = "1") long shipOverdue,
            @Schema(description = "취소 요청 — 진입 `tab=CANCEL_REQUESTED`", example = "2") long cancelRequests,
            @Schema(description = "배송 이상(집화 확인 필요 · 추적 정지 · 반송중) — 진입 `tab=SHIPPING` · `RETURNING`", example = "3")
            long deliveryIssue
    ) {
    }

    @Schema(name = "SellerHomeClaimTasks")
    public record Claims(
            @Schema(description = "회수 대기 — 진입 `/claims?tab=COLLECT_WAIT`", example = "3") long collectWait,
            @Schema(description = "입고 · 검수 — 진입 `tab=INSPECTION`", example = "2") long inspection,
            @Schema(description = "검수 기한 경과(경고) — 회수 대기 방치는 세지 않는다", example = "1") long inspectOverdue,
            @Schema(description = "재발송 · 교환 — 진입 `tab=RESHIP`", example = "1") long reship
    ) {
    }

    @Schema(name = "SellerHomeInquiryTasks")
    public record Inquiry(
            @Schema(description = "답변 대기 상품 문의 — 문의 관리 `waitingCount` 와 같은 수", example = "3") long unansweredProductInquiries
    ) {
    }

    @Schema(name = "SellerHomeContractTasks")
    public record ContractTasks(
            @Schema(description = "내 서명 필요 — 상대만 서명한 서명 진행중", example = "1") long mySignatureNeeded,
            @Schema(description = "검토 반려", example = "1") long reviewRejected
    ) {
    }

    @Schema(name = "SellerHomeSettlementTasks")
    public record SettlementTasks(
            @Schema(description = "조정 협의 「내 응답 필요」", example = "1") long adjustmentMyTurn
    ) {
    }

    @Schema(name = "SellerHomeContractsInProgress", description = "계약 진행 — 체결 전 4상태(검토 대기 · 검토 반려 · 서명 진행중 · 체결 처리 대기)")
    public record ContractsInProgress(
            @Schema(example = "4") long count,
            @Schema(description = "공구 시작일 순 최대 5건") List<ContractRow> items
    ) {
    }

    @Schema(name = "SellerHomeContractRow")
    public record ContractRow(
            Long contractId,
            @Schema(description = "공구명(계약 제목)", example = "밤 리페어 크림 공구") String groupBuyName,
            Long creatorId,
            @Schema(example = "뷰티로그") String creatorName,
            ContractStatus status,
            @Schema(example = "서명 진행중") String statusLabel,
            @Schema(nullable = true) LocalDateTime groupBuyStartAt,
            @Schema(description = "정지 보류 배지(체결 보류 · 검토 보류)", nullable = true) SanctionHoldView hold
    ) {
    }

    @Schema(name = "SellerHomeUpcomingGroupBuys", description = "오픈 예정 — 준비중 · 준비완료")
    public record UpcomingGroupBuys(
            @Schema(example = "2") long count,
            @Schema(description = "오픈일 순 최대 5건") List<UpcomingRow> items
    ) {
    }

    @Schema(name = "SellerHomeUpcomingRow")
    public record UpcomingRow(
            Long groupBuyId,
            @Schema(example = "밤 리페어 크림 공구") String name,
            Long creatorId,
            @Schema(example = "뷰티로그") String creatorName,
            LocalDateTime startAt,
            GroupBuyStatus status,
            @Schema(example = "준비중") String statusLabel,
            GroupBuyPostStatus postStatus,
            @Schema(example = "반려") String postStatusLabel,
            @Schema(description = "막힌 이유 — 게시물 반려 · 최소 준비 물량 미확인 · 운영팀 검토 중. 없으면 null",
                    example = "게시물 반려 — 인플루언서 수정 대기", nullable = true) String blockedReason,
            @Schema(description = "정지 보류 배지(승인 보류)", nullable = true) SanctionHoldView hold
    ) {
    }

    @Schema(name = "SellerHomeSettlement")
    public record Settlement(
            @Schema(description = "다음 지급 예정 금액 — 지급 예정이 없으면 null", example = "4712800", nullable = true) Long nextPayoutAmount,
            @Schema(nullable = true) LocalDate nextPayoutDate,
            @Schema(description = "정산 확인 중(요청 가능 창 열림)", example = "1") long reviewingCount,
            @Schema(description = "조정 협의 「내 응답 필요」", example = "1") long adjustmentMyTurn
    ) {
    }

    // ------------------------------------------------------------------ GET /v1/seller/home/live(4-2)

    @Schema(name = "SellerHomeLive", description = "진행 중 공구 실시간 — 결제 기준 · 취소 전 포함")
    public record Live(
            @Schema(description = "집계 시각(「n분 전 갱신」)") LocalDateTime asOf,
            @Schema(description = "진행 중 · 중단 예정 공구 — 보통 1건 · 겹치면 시작일 순") List<LiveItem> items
    ) {
    }

    @Schema(name = "SellerHomeLiveItem")
    public record LiveItem(
            Long groupBuyId,
            String name,
            Long creatorId,
            String creatorName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            @Schema(description = "기간 진행률 0 ~ 1", example = "0.42") double progressRatio,
            @Schema(description = "오늘 쇼룸 방문자(사람 수)", example = "312") long visitorsToday,
            @Schema(description = "공구 기간 쇼룸 방문자(사람 수)", example = "1184") long visitorsCumulative,
            @Schema(description = "오늘 주문", example = "38") long ordersToday,
            @Schema(description = "어제 같은 시각까지 주문", example = "31") long ordersYesterdaySameTime,
            @Schema(description = "오늘 결제 금액", example = "1254000") long paidToday,
            @Schema(description = "누적 결제 금액", example = "8420000") long paidCumulative,
            @Schema(description = "누적 주문", example = "256") long ordersCumulative,
            @Schema(description = "판매 수량", example = "301") long soldQuantity,
            Stock stock,
            @Schema(description = "오늘 시간대별 24칸(0 ~ 23시)") List<HourBucket> hourly
    ) {
    }

    @Schema(name = "SellerHomeLiveStock", description = "상품 재고 — 공구 전용 재고는 없다")
    public record Stock(
            @Schema(description = "공구 상품 옵션 재고 합", example = "212") long remaining,
            @Schema(description = "계약 최소 준비 물량 합(「300개 중」) — 옵션 최소 물량이 비어 있으면 null", example = "300", nullable = true)
            Integer minQuantity,
            @Schema(description = "[상품 재고 수정] 대상 — 공구 첫 상품", nullable = true) Long productId
    ) {
    }

    @Schema(name = "SellerHomeLiveHour")
    public record HourBucket(
            @Schema(example = "14") int hour,
            @Schema(example = "6") long orders,
            @Schema(example = "198000") long amount,
            @Schema(description = "지금 시각의 칸 — 「집계 중」") boolean collecting
    ) {
    }

    // ------------------------------------------------------------------ GET /v1/seller/home/calendar(4-3)

    @Schema(name = "SellerHomeCalendar")
    public record Calendar(
            @Schema(example = "2026-10") String month,
            @Schema(description = "조회 구간 시작 — 요청 월 1일 − 7일(앞 달 칸)", example = "2026-09-24") LocalDate from,
            @Schema(description = "조회 구간 끝(포함) — 요청 월 말일 + 7일(뒤 달 칸)", example = "2026-11-07") LocalDate to,
            List<HolidayItem> holidays,
            List<CalendarGroupBuy> groupBuys,
            List<Deadline> deadlines
    ) {
    }

    @Schema(name = "SellerHomeCalendarGroupBuy")
    public record CalendarGroupBuy(
            Long groupBuyId,
            String name,
            LocalDateTime startAt,
            @Schema(description = "종료 — 조기 마감 · 중단이면 실제 종료 시각") LocalDateTime endAt,
            @Schema(description = "IN_PROGRESS · UPCOMING · ENDED — 막대 3색", example = "IN_PROGRESS") String status,
            @Schema(description = "정지 보류 배지(승인 보류)", nullable = true) SanctionHoldView hold
    ) {
    }

    @Schema(name = "SellerHomeCalendarDeadline")
    public record Deadline(
            LocalDate date,
            @Schema(description = "SHIP_DUE(발송 기한) · SIGN_DUE(서명 기한)", example = "SHIP_DUE") String kind,
            @Schema(example = "발송 기한 · 밤 리페어 크림 공구") String label,
            @Schema(description = "GROUP_BUY · CONTRACT", example = "GROUP_BUY") String targetType,
            Long targetId
    ) {
    }

    // ------------------------------------------------------------------ PATCH /v1/seller/home/guide(4-4)

    @Schema(name = "SellerHomeGuideRequest")
    public record GuideRequest(
            @Schema(description = "true = 숨김 · false = 다시 표시", example = "true") @NotNull Boolean hidden
    ) {
    }

    // ------------------------------------------------------------------ GET /v1/seller/badges(4-7)

    @Schema(name = "SellerBadges", description = "GNB 배지 — 0 이면 FE 가 숨긴다. 홈 처리 대기와 같은 계산을 쓴다")
    public record Badges(
            @Schema(description = "연결·소통 — 안 읽은 메시지", example = "2") long connections,
            @Schema(description = "계약 — 내 서명 필요 + 검토 반려", example = "2") long contracts,
            @Schema(description = "공구 — 최소 물량 확인 대기 + 소명 가능한 운영자 중단", example = "2") long groupBuys,
            @Schema(description = "판매 — 준비 시작 + 송장 등록 + 취소 요청 + 배송 이상 + 회수 대기 + 입고·검수 + 재발송·교환", example = "23")
            long sales,
            @Schema(description = "정산 — 조정 협의 「내 응답 필요」", example = "1") long settlements,
            @Schema(description = "문의 — 답변 대기 상품 문의", example = "3") long inquiries
    ) {
    }
}
