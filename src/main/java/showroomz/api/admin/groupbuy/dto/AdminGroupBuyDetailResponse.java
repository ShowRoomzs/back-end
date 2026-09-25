package showroomz.api.admin.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.EmergencySuspensionReason;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.FulfillmentResult;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;
import showroomz.domain.groupbuy.type.SuspensionReasonClause;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementGateway.SettlementStage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 공구 상세 — 상세 13종(B1~B6)과 모달(M1~M7)의 배경이 <b>이 응답 하나</b>를 쓴다(32 설계 4절).
 *
 * <p>서버는 {@code viewPhase} 같은 값을 만들지 않는다 — 상태 × 사실 테이블 조합으로 FE가 화면을 고른다. 시안에 없는
 * 조합(준비중 미제출·반려 · 준비완료 · 정산완료 · 조기 마감 종결)에서도 값은 나온다.
 *
 * <p><b>판단 근거 숫자는 포트에서 오고, 없으면 null이다</b>(0-7) — 판정 화면에서 거짓 0은 오판으로 직행한다.
 */
@Schema(description = "어드민 공구 상세")
public record AdminGroupBuyDetailResponse(
        Summary groupBuy,
        Timeline timeline,
        Brand brand,
        CreatorRef creator,
        ContractRef contract,
        List<Item> items,
        FixedFee fixedFee,
        @Schema(description = "준비 게이트 3개 — PREPARING · READY에서만", nullable = true) Readiness readiness,
        @Schema(description = "오픈 승인 대기(게시물 PENDING)일 때만", nullable = true) OpenReview openReview,
        Post post,
        @Schema(description = "진행중·중단 예정 = LIVE · 정산완료 = SETTLED · 그 밖은 null. 판매 포트가 비면 항상 null",
                nullable = true) Sales sales,
        @Schema(description = "검토 중(PENDING) 중단·조기 마감 요청", nullable = true) ActiveRequest activeRequest,
        @Schema(description = "기간 연장 — 조회 카드. 어드민은 판정하지 않는다(§32-4)", nullable = true) Extension extension,
        @Schema(description = "진행 중(NOTICED) 직권 중단 통지", nullable = true) AdminSuspension adminSuspension,
        @Schema(description = "종료·정산완료", nullable = true) AfterEnd afterEnd,
        @Schema(description = "중단 · 조기 마감 종결", nullable = true) Closure closure,
        Permissions permissions,
        @Schema(description = "처리 이력 — 최신순. 전량 + 게시물 수정 합성 행") List<HistoryEntry> history,
        Navigation navigation
) {

    public record Summary(
            Long groupBuyId,
            @Schema(example = "GB-20260814-041") String groupBuyNumber,
            String title,
            GroupBuyStatus status,
            String statusLabel,
            GroupBuyTone statusTone,
            LocalDateTime createdAt,
            @Schema(nullable = true) LocalDateTime readyAt,
            @Schema(nullable = true) LocalDateTime openedAt,
            @Schema(nullable = true) LocalDateTime endedAt,
            @Schema(nullable = true) GroupBuyCloseType closeType,
            @Schema(nullable = true) String closeTypeLabel,
            @Schema(nullable = true) LocalDateTime settledAt
    ) {
    }

    public record Timeline(
            LocalDateTime startAt,
            LocalDateTime endAt,
            @Schema(description = "계약 원래 종료 — 연장 전 값") LocalDateTime originalEndAt,
            int totalDays,
            int elapsedDays,
            @Schema(nullable = true) Integer daysUntilStart,
            @Schema(nullable = true) Integer daysUntilEnd,
            @Schema(description = "시작 시각이 지난 준비중 — 승인하면 다음 tick에 단축된 기간으로 바로 열린다") boolean startOverdue
    ) {
    }

    public record Brand(Long marketId, String name,
                        @Schema(description = "브랜드-인플루언서 PAIR 스레드", nullable = true) Long pairThreadId) {
    }

    public record CreatorRef(Long creatorId, String name,
                             @Schema(description = "쇼룸 계정 — URL은 FE가 만든다", example = "minjae") String accountId) {
    }

    public record ContractRef(Long contractId, String contractNumber, @Schema(nullable = true) LocalDateTime concludedAt) {
    }

    public record Item(
            Long productId,
            String productName,
            Integer regularPrice,
            Integer groupBuyPrice,
            BigDecimal rewardRate,
            @Schema(nullable = true) Long expectedUnitReward,
            @Schema(description = "최소 준비 물량 — B4 소진율의 분모") Integer minQuantity
    ) {
    }

    public record FixedFee(
            @Schema(nullable = true) Integer amount,
            @Schema(nullable = true) FixedFeeTrigger trigger,
            @Schema(nullable = true) String triggerLabel,
            @Schema(description = "3서피스 문자 단위 동일 표준 표기(§29-9). 지급 여부는 내리지 않는다", nullable = true)
            String displayText
    ) {
    }

    public record Readiness(List<Gate> gates) {
    }

    public record Gate(
            GateKey key,
            @Schema(description = "채울 주체") GroupBuyActorType actorType,
            boolean done,
            @Schema(nullable = true) LocalDateTime doneAt,
            @Schema(description = "브랜드명 · 쇼룸명 · 운영자 실명", nullable = true) String doneByName,
            GateState state,
            @Schema(description = "MY_TURN만 WARNING(§29-4 「내 차례인 줄만 경고 톤」)") GroupBuyTone tone
    ) {
    }

    public enum GateKey {
        STOCK_CONFIRMED, POST_SUBMITTED, OPEN_APPROVED
    }

    public enum GateState {
        DONE, WAITING,
        /** 반려 후 재등록 대기 */
        REJECTED,
        /** 운영자 차례 — 오픈 승인 */
        MY_TURN
    }

    /** 「SLA 영업일 3일 · 기한 08.19 (D-2)」와 시작일을 한 블록에 싣는다 — 승인이 늦으면 공구가 열리지 않는다(§32-2). */
    public record OpenReview(
            LocalDateTime submittedAt,
            int slaBusinessDays,
            LocalDateTime dueAt,
            @Schema(description = "지났으면 음수") long daysLeft,
            LocalDateTime startAt,
            @Schema(description = "기한 초과 — 표시만 한다. 자동 처리 없음(§33-1 #1)") boolean overdue
    ) {
    }

    public record Post(
            GroupBuyPostStatus status,
            String statusLabel,
            GroupBuyTone statusTone,
            @Schema(description = "POST-GB-{제출일}-{post_id} — 표시 전용 파생값", nullable = true) String postNumber,
            @Schema(description = "원문 전체 — 요약·잘라내기 없음", nullable = true) String title,
            @Schema(nullable = true) String content,
            @Schema(description = "소비자 앱에 실제로 붙는 대가관계 표시 — 심사 기준 「대가관계 표시 훼손」 판정용") String disclosureText,
            @Schema(nullable = true) LocalDateTime submittedAt,
            @Schema(nullable = true) LocalDateTime reviewedAt,
            @Schema(nullable = true) String reviewedByName,
            @Schema(nullable = true) LocalDateTime lastEditedAt,
            @Schema(description = "승인 후 수정 횟수") long editCount,
            @Schema(description = "최신 판본 — 숨김 해제 요청에 그대로 돌려준다", nullable = true) Integer latestRevisionNo,
            @Schema(description = "반려 — 승인된 게시물에는 내리지 않는다(과거 반려는 이력에 있다)", nullable = true)
            Rejection rejection,
            @Schema(nullable = true) Hidden hidden,
            @Schema(description = "종결로 내려간 게시물 — COMPLETED · EARLY_CLOSED · SUSPENDED", nullable = true)
            GroupBuyCloseType closedBy
    ) {
    }

    public record Rejection(
            String code,
            @Schema(nullable = true) String label,
            @Schema(description = "심사 축 — AD_LAW · CONTRACT_MISMATCH · DISCLOSURE · ETC", nullable = true) String axis,
            @Schema(nullable = true) String axisLabel,
            String detail,
            LocalDateTime rejectedAt,
            @Schema(nullable = true) String rejectedByName
    ) {
    }

    public record Hidden(
            String code,
            @Schema(nullable = true) String label,
            String detail,
            LocalDateTime hiddenAt,
            @Schema(nullable = true) String hiddenByName,
            @Schema(description = "숨김 경과일 — 양끝 포함") int hiddenDays,
            @Schema(description = "숨김 당시 판본", nullable = true) Integer revisionNo,
            @Schema(description = "숨김 이후 주문 — 판매 포트가 비면 null(0이 아니다)", nullable = true) Long ordersSinceHidden
    ) {
    }

    public record Sales(
            SalesBasis basis,
            int orderCount,
            long amount,
            long rewardAmount,
            List<ItemQuantity> itemQuantities
    ) {
    }

    public enum SalesBasis {
        LIVE, SETTLED
    }

    public record ItemQuantity(Long productId, int quantity) {
    }

    public record ActiveRequest(
            Long requestId,
            ChangeRequestType type,
            String typeLabel,
            GroupBuyActorType requesterType,
            String requesterName,
            String reasonCode,
            @Schema(nullable = true) String reasonLabel,
            @Schema(description = "운영자에게 쓴 메모 — 인플루언서 요청 메모도 그대로 내린다", nullable = true) String memo,
            @Schema(description = "요청 시점 상태 — M2 경고문 분기") GroupBuyStatus statusAtRequest,
            LocalDateTime requestedAt,
            @Schema(description = "검토 경과 — 변경 요청 화면과 같은 표기(18h · 2일 3h)", example = "5일 2h") String elapsed,
            DecisionBasis decisionBasis
    ) {
    }

    /**
     * 판단 근거(32 설계 4-5). 요청 유형마다 채우는 칸이 다르다 — SUSPEND는 요청 후 증가분·CS 문의, EARLY_CLOSE는
     * 소진율·품절 문의. <b>포트가 비면 null이다</b> — 부분 합을 전체 합처럼 내리지 않는다.
     */
    public record DecisionBasis(
            @Schema(description = "SUSPEND · 요청 시점 주문", nullable = true) Integer ordersAtRequest,
            @Schema(nullable = true) Integer ordersNow,
            @Schema(description = "SUSPEND · 요청 후 증가분 — 둘 중 하나라도 모르면 null", nullable = true) Integer ordersSinceRequest,
            @Schema(nullable = true) Integer quantityNow,
            @Schema(nullable = true) Long amountNow,
            @Schema(description = "SUSPEND · 요청 후 CS 문의", nullable = true) Inquiries inquiries,
            @Schema(description = "EARLY_CLOSE · 계약 상품 최소 물량 합 — 실제 재고가 아니다", nullable = true) Integer preparedQuantity,
            @Schema(description = "EARLY_CLOSE · 소진율(%)", nullable = true) Integer sellThroughRate,
            @Schema(description = "EARLY_CLOSE · 요청 후 재입고 문의", nullable = true) Long soldOutInquiriesSinceRequest,
            @Schema(description = "EARLY_CLOSE · 현재 종료 예정", nullable = true) LocalDateTime originalEndAt,
            @Schema(description = "EARLY_CLOSE · 승인하면 누르는 시각이 종료 시각이다", nullable = true) Boolean endsImmediatelyIfApproved
    ) {
    }

    public record Inquiries(
            @Schema(description = "상품 문의 + 1:1 문의 — 1:1 쪽을 모르면 null", nullable = true) Long total,
            @Schema(description = "하자 관련 — 문의 유형에 하자 분류가 없어 항상 null", nullable = true) Long defectRelated
    ) {
    }

    public record Extension(
            ExtensionRequestStatus status,
            int days,
            @Schema(nullable = true) String reason,
            LocalDateTime beforeEndAt,
            LocalDateTime afterEndAt,
            int beforeTotalDays,
            int afterTotalDays,
            LocalDateTime requestedAt,
            @Schema(description = "인플루언서 응답 기한 = 현재 종료 시각") LocalDateTime respondDeadlineAt,
            @Schema(nullable = true) LocalDateTime respondedAt,
            @Schema(nullable = true) GroupBuyActorType responseActorType,
            @Schema(nullable = true) String rejectReasonCode,
            @Schema(nullable = true) String rejectReasonLabel,
            @Schema(description = "「브랜드에게 남길 메모」 — 분쟁 경위", nullable = true) String rejectMemo
    ) {
    }

    public record AdminSuspension(
            Long adminSuspensionId,
            AdminSuspensionKind kind,
            @Schema(nullable = true) SuspensionReasonClause clause,
            @Schema(nullable = true) String clauseLabel,
            String noticeBody,
            LocalDateTime noticedAt,
            @Schema(nullable = true) String noticedByName,
            @Schema(description = "통지 후 경과일", example = "3") int elapsedDays,
            LocalDateTime executeScheduledAt,
            LocalDateTime appealDeadlineAt,
            @Schema(description = "통지 시점 게시물 판본 — post.latestRevisionNo와 다르면 통지 후 수정됐다", nullable = true)
            Integer noticeRevisionNo,
            @Schema(description = "통지 후 증가분 — 판매 포트가 비면 null", nullable = true) SalesSinceNotice salesSinceNotice,
            @Schema(description = "브랜드 소명 — 미제출이면 null", nullable = true) Appeal appeal,
            @Schema(description = "소명 기한 경과 — 미제출이어도 운영자가 최종 판정한다(제17조④)") boolean appealDeadlinePassed
    ) {
    }

    public record SalesSinceNotice(int orders, long amount) {
    }

    public record Appeal(String content, LocalDateTime submittedAt,
                         @Schema(description = "소명 제출자 셀러 계정 이름 — 브랜드 명의와 다르다", nullable = true)
                         String submittedByName,
                         @Schema(description = "업로드 완료 첨부만. URL은 클릭 시 발급한다") List<AppealAttachment> attachments) {
    }

    public record AppealAttachment(Long attachmentId, String name, String contentType, long sizeBytes) {
    }

    public record AfterEnd(
            @Schema(description = "종료·정산완료만", nullable = true) Fulfillment fulfillment,
            @Schema(description = "종료·정산완료만", nullable = true) Settlement settlement,
            @Schema(description = "판매 포트가 비면 null", nullable = true) OrderClosure orderClosure,
            @Schema(nullable = true) OpenIssue openIssue
    ) {
    }

    /**
     * 방향으로 이름 짓는다 — 운영자에게는 「나」가 없다(4-8 ①). 확인 전이면 해당 방향이 null이고 대상 의무는
     * {@code targets}가 항상 내린다.
     */
    public record Fulfillment(
            @Schema(description = "브랜드가 인플루언서의 콘텐츠 의무를 확인한 결과", nullable = true) FulfillmentCheck brandToCreator,
            @Schema(description = "인플루언서가 브랜드의 주문·배송 의무를 확인한 결과", nullable = true) FulfillmentCheck creatorToBrand,
            FulfillmentTargets targets,
            @Schema(nullable = true) LocalDateTime dueAt,
            boolean duePassed,
            @Schema(description = "false면 「무응답은 이행으로 처리됩니다」 문구를 쓰면 안 된다") boolean autoConfirmOnTimeout,
            @Schema(description = "미이행 3자 스레드", nullable = true) Long threadId,
            @Schema(description = "양측 동의 종결 — 미이행 행은 그대로 UNFULFILLED다(불가역)", nullable = true) LocalDateTime agreedAt,
            @Schema(description = "당사자 합의 문장 — 운영자 판정이 아니다", nullable = true) String resolutionNote,
            @Schema(description = "정산 보류 해제(정산 관리)", nullable = true) LocalDateTime resolvedAt,
            boolean onHold
    ) {
    }

    public record FulfillmentCheck(
            FulfillmentResult result,
            @Schema(nullable = true) String reason,
            LocalDateTime checkedAt,
            @Schema(description = "자동 이행이면 null", nullable = true) String checkedByName,
            @Schema(description = "무응답 → 자동 이행 — 반드시 그 사실을 적는다(§32-6)") boolean auto
    ) {
    }

    public record FulfillmentTargets(Target brandToCreator, Target creatorToBrand) {
    }

    public record Target(
            List<FulfillmentDuty> duties,
            @Schema(nullable = true) ContentCounts counts
    ) {
    }

    public record ContentCounts(Integer feed, Integer reels, Integer story) {
    }

    public record Settlement(
            SettlementStage stage,
            @Schema(description = "PORT(정산 모듈) · DERIVED(공구 상태에서 파생 — 중간 단계를 확정값처럼 그리지 않는다)")
            String stageSource,
            @Schema(description = "정산이 아직 안 되는 사유 — 사유가 둘이면 카드도 둘(§32-6). "
                    + "UNCLOSED_ORDERS · FULFILLMENT_PENDING · FULFILLMENT_DISPUTE · CLOSURE_UNKNOWN")
            List<SettlementBlocker> blockers,
            @Schema(nullable = true) Watch watch,
            Preview preview
    ) {
    }

    public enum SettlementBlocker {
        /** 정산 선행 조건 미충족 — 보류가 아니다 */
        UNCLOSED_ORDERS,
        /** 한쪽이라도 이행 확인 전 */
        FULFILLMENT_PENDING,
        /** 정산 보류 — 미이행 ∧ 보류 해제 전 */
        FULFILLMENT_DISPUTE,
        /** 판매 포트가 비어 미종결을 알 수 없다 — 판정 불가 */
        CLOSURE_UNKNOWN
    }

    public record Watch(LocalDate dueAt, long elapsedDays, boolean reached) {
    }

    /** 리워드는 확정 대기 — 잠정 금액을 쓰지 않는다(§32-6). {@code rewardAmount}는 항상 null. */
    public record Preview(
            @Schema(description = "취소·반품 반영 매출 — 판매 포트가 비면 null", nullable = true) Long provisionalSalesAmount,
            List<BigDecimal> rewardRates,
            @Schema(nullable = true) Long rewardAmount
    ) {
    }

    /** 미종결만 단계별로 쪼갠다 — 종결 경로 내역은 판매 관리 소관이다(§29-11). */
    public record OrderClosure(int totalCount, int closedCount, int unclosedCount, List<UnclosedStage> unclosed) {
    }

    public record UnclosedStage(String stage, String label, int count) {
    }

    public record OpenIssue(Long issueId, GroupBuyIssueType issueType, String issueTypeLabel,
                            GroupBuyActorType openerType, LocalDateTime openedAt, @Schema(nullable = true) Long threadId) {
    }

    public record Closure(
            GroupBuyCloseType closeType,
            String closeTypeLabel,
            LocalDateTime endedAt,
            @Schema(description = "REQUEST · ADMIN_NOTICE · ADMIN_EMERGENCY", nullable = true) ClosureSource source,
            @Schema(nullable = true) String reasonLabel,
            @Schema(nullable = true) String reasonDetail,
            @Schema(description = "요청 승인 종결만", nullable = true) Requester requester,
            @Schema(nullable = true) String decidedByName,
            @Schema(nullable = true) String decisionReason,
            @Schema(description = "접수분 주문 — 판매 포트가 비면 null", nullable = true) Integer acceptedOrderCount,
            @Schema(description = "직권 중단 종결만", nullable = true) AdminBasis adminBasis
    ) {
    }

    public enum ClosureSource {
        REQUEST, ADMIN_NOTICE, ADMIN_EMERGENCY
    }

    public record Requester(GroupBuyActorType type, String name, LocalDateTime requestedAt) {
    }

    public record AdminBasis(
            AdminSuspensionKind kind,
            @Schema(nullable = true) SuspensionReasonClause clause,
            @Schema(nullable = true) EmergencySuspensionReason emergencyReason,
            @Schema(description = "호 라벨 또는 긴급 사유 라벨", nullable = true) String basisLabel,
            String body,
            @Schema(description = "집행 사유 — 사전 통지 집행만", nullable = true) String executionNote
    ) {
    }

    /**
     * 버튼 판정(32 설계 4-10). 실행 API는 같은 판정 메서드를 다시 호출해 409로 막는다. 시각이 여는 버튼(집행)은
     * 서버 {@code now}로 판정한다 — 운영자 PC 시계로 판정하지 않는다.
     */
    public record Permissions(
            boolean canApproveOpen,
            boolean canRejectOpen,
            boolean canHidePost,
            boolean canUnhidePost,
            boolean canNoticeSuspension,
            @Schema(description = "STATUS · REQUEST_PENDING · NO_WINDOW_BEFORE_END — 버튼이 왜 잠겼는지", nullable = true)
            NoticeUnavailableReason noticeUnavailableReason,
            boolean canEmergencySuspend,
            boolean canExecuteSuspension,
            boolean canWithdrawSuspension,
            boolean canApproveRequest,
            boolean canRejectRequest,
            boolean canOpenIssue,
            @Schema(description = "정산 모듈 연동 전에는 항상 false") boolean canConfirmSettlement
    ) {
    }

    public enum NoticeUnavailableReason {
        STATUS, REQUEST_PENDING, NO_WINDOW_BEFORE_END
    }

    public record HistoryEntry(
            @Schema(description = "GroupBuyEventType — 합성 행은 POST_EDITED") String eventType,
            GroupBuyActorType actorType,
            @Schema(description = "운영자 행은 실명", nullable = true) String actorDisplayName,
            @Schema(nullable = true) String detail,
            LocalDateTime occurredAt,
            @Schema(description = "저장된 이벤트가 아닌 합성 행 — 게시물 리비전에서 만든다") boolean synthetic,
            @Schema(description = "POST_EDITED의 판본", nullable = true) Integer revisionNo
    ) {
    }

    public record Navigation(@Schema(nullable = true) Long prevGroupBuyId, @Schema(nullable = true) Long nextGroupBuyId) {
    }

    public enum FulfillmentDuty {
        SHOWROOM_POST, FEED, REELS, STORY, ORDER_DELIVERY
    }

    public static final String STAGE_SOURCE_PORT = "PORT";
    public static final String STAGE_SOURCE_DERIVED = "DERIVED";
}
