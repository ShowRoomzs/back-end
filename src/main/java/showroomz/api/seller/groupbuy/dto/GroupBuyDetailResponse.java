package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.EmergencySuspensionReason;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.FulfillmentResult;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;
import showroomz.domain.groupbuy.type.SuspensionReasonClause;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공구 상세 — B1~B7a 22종이 <b>이 응답 하나</b>를 쓴다(설계서 4-4). 화면 분기는 FE가 값으로 고른다.
 *
 * <p>값이 없는 블록은 null이다. 특히 {@code sales}는 상태에 따라 <b>서버가 내려주지 않는다</b> — 종료 화면에
 * KPI를 두지 않는 것은 의미 규칙이고(§30-4), 응답에 값이 있으면 3서피스 중 어느 한 곳은 결국 그린다.
 */
@Schema(description = "공구 상세")
public record GroupBuyDetailResponse(
        Summary groupBuy,
        Timeline timeline,
        Counterparty counterparty,
        ContractRef contract,
        List<Item> items,
        FixedFee fixedFee,
        ContentDuty contentDuty,
        @Schema(description = "준비 게이트 3개 — PREPARING·READY에서만", nullable = true) Readiness readiness,
        Post post,
        @Schema(description = "판매 실적 — 진행중·중단 예정(LIVE) · 정산완료(SETTLED) · 중단(AT_SUSPENSION)에서만. "
                + "준비중·준비완료·종료는 null이다. 판매 모듈이 없으면 항상 null — 0이 아니다", nullable = true)
        Sales sales,
        @Schema(description = "주문 종결 건수 — 판매 모듈이 없으면 null(0이 아니다). "
                + "0은 「정산해도 된다」는 뜻이라 모르는 값을 0으로 내리지 않는다", nullable = true)
        OrderClosure orderClosure,
        Extension extension,
        @Schema(description = "검토 중(PENDING) 중단·조기 마감 요청 — 요청자 무관", nullable = true)
        ActiveRequest activeRequest,
        @Schema(description = "가장 최근에 판정된 요청 — B4b·B4h 반려 결과", nullable = true)
        LastDecision lastDecision,
        @Schema(description = "직권 중단 — 진행 중 통지, 없으면 종결을 만든 통지, 없으면 가장 최근 통지", nullable = true)
        AdminSuspension adminSuspension,
        @Schema(description = "종결 정보 — 종결 3종에서만", nullable = true) Closure closure,
        @Schema(description = "종료 후 — 종결 3종에서만", nullable = true) AfterEnd afterEnd,
        Permissions permissions,
        List<HistoryEntry> history
) {

    public record Summary(
            Long groupBuyId,
            @Schema(example = "GB-20260806-018") String groupBuyNumber,
            @Schema(description = "공구명 — 계약에서 읽는다") String title,
            GroupBuyStatus status,
            String statusLabel,
            GroupBuyTone statusTone,
            LocalDateTime createdAt,
            @Schema(nullable = true) LocalDateTime readyAt,
            @Schema(nullable = true) LocalDateTime openedAt,
            @Schema(description = "실제 종결 시각", nullable = true) LocalDateTime endedAt,
            @Schema(nullable = true) GroupBuyCloseType closeType,
            @Schema(nullable = true) String closeTypeLabel
    ) {
    }

    @Schema(description = "기간 — 일수는 서버 now(Asia/Seoul) 기준 · 양끝 포함 일자 계산. FE 시계를 믿지 않는다")
    public record Timeline(
            LocalDateTime startAt,
            @Schema(description = "현재 종료 예정 — 연장 수락이 반영된 값") LocalDateTime endAt,
            @Schema(description = "원래 종료 예정(계약) — B4d 「8일 → 15일」 병기의 원본") LocalDateTime originalEndAt,
            @Schema(example = "8") int totalDays,
            @Schema(description = "진행 N일차 — 시작 전이면 0", example = "3") int elapsedDays,
            @Schema(description = "시작까지 N일 — 시작 후면 null", nullable = true) Integer daysUntilStart,
            @Schema(description = "종료까지 N일 — 판매 중일 때만", nullable = true) Integer daysUntilEnd,
            @Schema(description = "시작 시각이 지났는데 아직 준비중 — 게이트가 채워지면 다음 tick에 바로 열린다(설계서 7-2 #2)")
            boolean startOverdue
    ) {
    }

    public record Counterparty(
            Long creatorId,
            String name,
            @Schema(description = "「스레드 열기」 대상 PAIR 스레드 — 연결이 끊겼으면 null", nullable = true) Long pairThreadId
    ) {
    }

    @Schema(description = "「계약서 보기」의 목적지")
    public record ContractRef(Long contractId, String contractNumber, LocalDateTime concludedAt) {
    }

    @Schema(description = "공구 상품 = 계약 상품 전부(계약 확정값 · 수정 불가)")
    public record Item(
            @Schema(nullable = true) Long productId,
            String productName,
            Integer regularPrice,
            Integer groupBuyPrice,
            BigDecimal rewardRate,
            @Schema(description = "RewardCalculator.calcUnitReward — 계약·정산·계약서 PDF와 같은 메서드", example = "3264")
            Long expectedUnitReward,
            Integer minQuantity
    ) {
    }

    public record FixedFee(
            @Schema(nullable = true) Integer amount,
            @Schema(nullable = true) FixedFeeTrigger trigger,
            @Schema(nullable = true) String triggerLabel,
            @Schema(description = "3서피스 문자 단위 동일 표준 표기 — 서버가 짓는다(§29-9). 고정 지급비가 없으면 null. "
                    + "지급 여부(paidAt)는 싣지 않는다 — 공구 화면은 지급 배지를 두지 않는다",
                    example = "고정 지급비 300,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급", nullable = true)
            String displayText
    ) {
    }

    @Schema(description = "콘텐츠 의무 — 이행 확인의 대상")
    public record ContentDuty(Integer feed, Integer reels, Integer story, LocalDate dueDate) {
    }

    public record Readiness(List<Gate> gates) {
    }

    public record Gate(
            @Schema(description = "STOCK_CONFIRMED(①) · POST_SUBMITTED(②) · OPEN_APPROVED(③)") GateKey key,
            @Schema(description = "게이트를 채우는 주체") GroupBuyActorType actorType,
            boolean done,
            @Schema(nullable = true) LocalDateTime doneAt,
            @Schema(description = "DONE · ACTION_REQUIRED(브랜드 차례 — 경고 톤) · WAITING · REJECTED(오픈 승인 반려)")
            GateState state
    ) {
    }

    public enum GateKey { STOCK_CONFIRMED, POST_SUBMITTED, OPEN_APPROVED }

    public enum GateState { DONE, ACTION_REQUIRED, WAITING, REJECTED }

    public record Post(
            GroupBuyPostStatus status,
            String statusLabel,
            GroupBuyTone statusTone,
            @Schema(nullable = true) String title,
            @Schema(nullable = true) String content,
            @Schema(nullable = true) LocalDateTime submittedAt,
            @Schema(description = "노출 시작 — 공구 오픈 시각", nullable = true) LocalDateTime openedAt,
            @Schema(description = "노출 종료 — 공구 종결 시각", nullable = true) LocalDateTime closedAt,
            @Schema(nullable = true) GroupBuyCloseType closeReason,
            @Schema(nullable = true) Reason rejectReason,
            @Schema(nullable = true) Reason hiddenReason,
            @Schema(nullable = true) LocalDateTime hiddenAt,
            @Schema(description = "숨김 경과 일수", nullable = true) Integer hiddenDays
    ) {
    }

    public record Reason(String code, @Schema(nullable = true) String detail) {
    }

    public record Sales(
            @Schema(description = "LIVE · SETTLED · AT_SUSPENSION") SalesBasis basis,
            int orderCount,
            long amount,
            @Schema(description = "항목별 수량 × 예상 리워드 단가의 합") long rewardAmount,
            List<ItemQuantity> itemQuantities
    ) {
    }

    public enum SalesBasis { LIVE, SETTLED, AT_SUSPENSION }

    public record ItemQuantity(Long productId, int quantity) {
    }

    @Schema(description = "경로별 내역은 싣지 않는다 — 공구 화면은 남은 건수만 센다(§29-11)")
    public record OrderClosure(int totalCount, int closedCount, int unclosedCount) {
    }

    @Schema(description = "기간 연장 — 요청이 없으면 status 이하 요청 필드가 null이다. maxDays·requestCutoffAt은 C1 즉시 계산용")
    public record Extension(
            @Schema(nullable = true) ExtensionRequestStatus status,
            @Schema(nullable = true) Integer days,
            @Schema(nullable = true) String reason,
            @Schema(nullable = true) LocalDateTime beforeEndAt,
            @Schema(nullable = true) LocalDateTime afterEndAt,
            @Schema(nullable = true) LocalDateTime requestedAt,
            @Schema(nullable = true) LocalDateTime respondedAt,
            @Schema(description = "CREATOR(수락·명시 거절) / SYSTEM(기간 만료)", nullable = true)
            GroupBuyActorType responseActorType,
            @Schema(nullable = true) String rejectReasonCode,
            @Schema(nullable = true) String rejectMemo,
            @Schema(description = "연장 가능 최대 일수 = 총 기간 상한 − 현재 총 일수", example = "22") int maxDays,
            @Schema(description = "이 시각 이후엔 연장을 요청할 수 없다 — 종료 12시간 전") LocalDateTime requestCutoffAt
    ) {
    }

    public record ActiveRequest(
            Long changeRequestId,
            ChangeRequestType type,
            GroupBuyActorType requesterType,
            @Schema(description = "브랜드명 또는 쇼룸명") String requesterName,
            String reasonCode,
            @Schema(description = "알 수 없는 코드는 null", nullable = true) String reasonLabel,
            @Schema(description = "브랜드가 낸 요청일 때만 — 인플루언서 메모는 운영자에게 쓴 글이라 내리지 않는다", nullable = true)
            String memo,
            @Schema(description = "요청 당시 상태 — C2(진행중)·C4(준비완료) 문구 분기") GroupBuyStatus statusAtRequest,
            LocalDateTime requestedAt
    ) {
    }

    public record LastDecision(
            Long changeRequestId,
            ChangeRequestType type,
            GroupBuyActorType requesterType,
            ChangeRequestStatus result,
            @Schema(nullable = true) String decisionReason,
            @Schema(nullable = true) LocalDateTime decidedAt
    ) {
    }

    public record AdminSuspension(
            Long adminSuspensionId,
            AdminSuspensionKind kind,
            AdminSuspensionStatus status,
            @Schema(description = "NOTICE: 제17조① 호수", nullable = true) SuspensionReasonClause reasonClause,
            @Schema(description = "EMERGENCY: 제17조③ 사유", nullable = true) EmergencySuspensionReason emergencyReason,
            @Schema(description = "B4i · B7a 「상세 사유」") String noticeBody,
            LocalDateTime noticedAt,
            @Schema(nullable = true) LocalDateTime executeScheduledAt,
            @Schema(nullable = true) LocalDateTime appealDeadlineAt,
            @Schema(nullable = true) LocalDateTime withdrawnAt,
            @Schema(nullable = true) String withdrawReason,
            @Schema(nullable = true) LocalDateTime executedAt,
            @Schema(description = "제출한 소명 — 미제출이면 null", nullable = true) Appeal appeal
    ) {
    }

    public record Appeal(String content, LocalDateTime submittedAt, List<AppealAttachment> attachments) {
    }

    public record AppealAttachment(Long attachmentId, String originalName, String contentType, long sizeBytes) {
    }

    public record Closure(
            GroupBuyCloseType closeType,
            LocalDateTime endedAt,
            @Schema(description = "REQUEST(요청 승인) · ADMIN_NOTICE(사전 통지 집행) · ADMIN_EMERGENCY(긴급) — 기간 완주는 null",
                    nullable = true) ClosureSource source,
            @Schema(description = "종결을 만든 요청 — B7 「내 요청 사유」", nullable = true) Requester requester,
            @Schema(nullable = true) String decisionReason
    ) {
    }

    public enum ClosureSource { REQUEST, ADMIN_NOTICE, ADMIN_EMERGENCY }

    public record Requester(
            GroupBuyActorType type,
            String name,
            String reasonCode,
            @Schema(nullable = true) String reasonLabel,
            @Schema(description = "브랜드가 낸 요청일 때만 — 인플루언서 메모는 내리지 않는다", nullable = true) String memo,
            LocalDateTime requestedAt
    ) {
    }

    public record AfterEnd(
            @Schema(description = "이행 확인 — 종료·정산완료에서만", nullable = true) Fulfillment fulfillment,
            @Schema(nullable = true) OpenIssue openIssue,
            @Schema(description = "종료 +30일 — 어드민 정산 지연 감시 기준", nullable = true) LocalDateTime settlementWatchAt,
            @Schema(nullable = true) LocalDateTime settledAt
    ) {
    }

    public record Fulfillment(
            @Schema(description = "내가 확인한 것 — 인플루언서의 콘텐츠 의무", nullable = true) FulfillmentCheck mine,
            @Schema(description = "상대가 확인한 것 — 브랜드의 의무", nullable = true) FulfillmentCheck theirs,
            @Schema(description = "확인 기한 — 자동 이행이 꺼져 있는 동안은 표시값이다", nullable = true) LocalDateTime dueAt,
            @Schema(description = "정산 보류 — 미이행이 있고 양측 합의 종결 전") boolean onHold,
            @Schema(description = "미이행 3자 스레드", nullable = true) Long threadId,
            @Schema(nullable = true) LocalDateTime resolvedAt
    ) {
    }

    public record FulfillmentCheck(
            FulfillmentResult result,
            @Schema(nullable = true) String reason,
            LocalDateTime checkedAt,
            @Schema(description = "기한 경과 자동 이행 여부") boolean auto
    ) {
    }

    public record OpenIssue(Long issueId, GroupBuyIssueType type, LocalDateTime openedAt,
                            @Schema(nullable = true) Long threadId) {
    }

    /**
     * 버튼 판정 — 조건이 상태 × 요청 × 통지 × 게시물 × 시각으로 갈린다. 실행 API가 같은 판정을 다시 호출해
     * 409로 막으므로 버튼과 집행이 어긋날 수 없다(설계서 4-5).
     */
    @Schema(description = "버튼 노출 판정 — 서버가 내려준다")
    public record Permissions(
            boolean canConfirmStock,
            boolean canRequestExtension,
            boolean canRequestEarlyClose,
            boolean canRequestSuspension,
            boolean canSubmitAppeal,
            boolean canOpenIssue,
            boolean canCheckFulfillment,
            boolean canOpenPairThread
    ) {
    }

    public record HistoryEntry(
            GroupBuyEventType eventType,
            @Schema(description = "운영자 호칭은 FE가 고른다 — 서버는 ADMIN만 내린다") GroupBuyActorType actorType,
            @Schema(description = "브랜드명·쇼룸명 스냅샷 — 운영자·시스템은 null", nullable = true) String actorDisplayName,
            @Schema(nullable = true) String detail,
            LocalDateTime occurredAt
    ) {
    }
}
