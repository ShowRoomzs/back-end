package showroomz.api.creator.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.EmergencySuspensionReason;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.FulfillmentResult;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;
import showroomz.domain.groupbuy.type.SuspensionReasonClause;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 스튜디오 공구 상세 — B1~B13 + 모달 C1~C7의 배경 화면이 <b>이 응답 하나</b>를 쓴다(31 설계 4-1). 분기는 FE가 값으로 고른다.
 *
 * <p><b>파트너 응답과 DTO를 공유하지 않는다</b>(31 설계 0-5). 공유하면 언젠가 파트너 전용 필드가 하나 붙고 그게 스튜디오로
 * 샌다. 덜어낸 것 — 최소 준비 물량 · 비고 · 직권 중단 소명 · 상대가 운영자에게 쓴 메모 · 고정 지급비 지급 신고 · version.
 */
@Schema(description = "스튜디오 공구 상세")
public record CreatorGroupBuyDetailResponse(
        Summary groupBuy,
        Timeline timeline,
        Brand brand,
        ContractRef contract,
        List<Item> items,
        FixedFee fixedFee,
        @Schema(description = "「내가 받는 금액」 — 비종결(준비중·준비완료·진행중·중단 예정)에서만. 종결 3종은 null", nullable = true)
        Payout payout,
        @Schema(description = "준비 게이트 3개 — PREPARING·READY에서만", nullable = true) Readiness readiness,
        Post post,
        @Schema(description = "판매 실적 — 진행중·중단 예정(LIVE) · 종료(PROVISIONAL · 잠정) · 정산완료(SETTLED) · "
                + "중단(AT_SUSPENSION). 준비중·준비완료는 null. 판매 모듈이 없으면 항상 null — 0이 아니다", nullable = true)
        Sales sales,
        @Schema(description = "주문 종결 건수 — 판매 모듈이 없으면 null(0이 아니다)", nullable = true)
        OrderClosure orderClosure,
        @Schema(description = "기간 연장 요청 — 요청이 없으면 null", nullable = true) Extension extension,
        @Schema(description = "검토 중(PENDING) 중단·조기 마감 요청 — 요청자 무관", nullable = true) ActiveRequest activeRequest,
        @Schema(description = "진행 중인 직권 중단 통지 — 중단 예정(SUSPENSION_SCHEDULED)에서만", nullable = true)
        AdminSuspension adminSuspension,
        @Schema(description = "종결 정보 — 종결 3종에서만", nullable = true) Closure closure,
        @Schema(description = "정산 — 정산완료(SETTLED)에서만", nullable = true) Settlement settlement,
        @Schema(description = "종료 후 이행 확인 — 종료·정산완료에서만", nullable = true) AfterEnd afterEnd,
        Permissions permissions,
        List<HistoryEntry> history,
        @Schema(description = "목록 이웃 — 목록 조건(tab·keyword·sort)이 쿼리로 오지 않으면 둘 다 null") Navigation navigation
) {

    public record Summary(
            Long groupBuyId,
            @Schema(example = "GB-20260814-041") String groupBuyNumber,
            @Schema(description = "공구명 — 계약에서 읽는다") String title,
            GroupBuyStatus status,
            String statusLabel,
            GroupBuyTone statusTone,
            LocalDateTime createdAt,
            @Schema(nullable = true) LocalDateTime readyAt,
            @Schema(nullable = true) LocalDateTime openedAt,
            @Schema(description = "실제 종결 시각", nullable = true) LocalDateTime endedAt,
            @Schema(nullable = true) GroupBuyCloseType closeType,
            @Schema(nullable = true) String closeTypeLabel,
            @Schema(nullable = true) LocalDateTime settledAt
    ) {
    }

    @Schema(description = "기간 — 일수는 서버 now(Asia/Seoul) 기준 · 양끝 포함 일자 계산")
    public record Timeline(
            LocalDateTime startAt,
            @Schema(description = "현재 종료 예정 — 연장 수락이 반영된 값") LocalDateTime endAt,
            @Schema(description = "원래 종료 예정(계약)") LocalDateTime originalEndAt,
            int totalDays,
            @Schema(description = "진행 N일차 — 시작 전이면 0") int elapsedDays,
            @Schema(description = "시작까지 N일 — 시작 후면 null", nullable = true) Integer daysUntilStart,
            @Schema(description = "종료까지 N일 — 판매 중일 때만", nullable = true) Integer daysUntilEnd,
            @Schema(description = "시작 시각이 지났는데 아직 준비중 — 배너 문구가 「시작 시각이 지났습니다」로 바뀐다") boolean startOverdue
    ) {
    }

    public record Brand(
            Long marketId,
            String name,
            @Schema(description = "「스레드 열기」 대상 PAIR 스레드 — 연결이 끊겼으면 null", nullable = true) Long pairThreadId
    ) {
    }

    @Schema(description = "「계약서 보기」 · 「게시 완료 기한」")
    public record ContractRef(Long contractId, String contractNumber, LocalDateTime concludedAt,
                              @Schema(nullable = true) LocalDate contentDueDate) {
    }

    @Schema(description = "공구 상품 = 계약 상품 전부. 정가·최소 준비 물량은 싣지 않는다 — 브랜드 소관이다(B1)")
    public record Item(
            @Schema(nullable = true) Long productId,
            String productName,
            Integer groupBuyPrice,
            @Schema(description = "내 리워드율(%)", example = "15.0") BigDecimal myRewardRate,
            @Schema(description = "개당 리워드 — RewardCalculator.calcUnitReward(계약·파트너·정산과 같은 메서드)", example = "4200")
            Long unitReward
    ) {
    }

    public record FixedFee(
            @Schema(nullable = true) Integer amount,
            @Schema(nullable = true) FixedFeeTrigger trigger,
            @Schema(nullable = true) String triggerLabel,
            @Schema(description = "3서피스 문자 단위 동일 표준 표기 — 고정 지급비가 없으면 null", nullable = true) String displayText
    ) {
    }

    @Schema(description = "「내가 받는 금액」 — 공제 전 금액이다. 공제·실지급액은 정산 관리 소관")
    public record Payout(
            @Schema(nullable = true) Integer fixedFeeAmount,
            @Schema(description = "판매 리워드(잠정) — 판매 중이고 판매 모듈이 값을 줄 때만", nullable = true) SalesReward salesReward,
            @Schema(description = "플랫폼 지급 보증 여부 — 항상 false. FE는 이 값으로 미보증 고지(§29-9)를 붙인다")
            boolean platformGuaranteed,
            @Schema(description = "미보증 고지의 「이슈 스레드에서 운영자 중재」가 갈 곳") DisputeChannel disputeChannel
    ) {
    }

    public record SalesReward(long amount, SalesBasis basis) {
    }

    public record DisputeChannel(@Schema(description = "PAIR 스레드 id", nullable = true) Long threadId) {
    }

    public record Readiness(
            List<Gate> gates,
            @Schema(description = "등록 마감일 — 제출 후 SLA 영업일 안에 승인이 나도 시작일 전날까지 끝나는 가장 늦은 날(서버 역산)")
            LocalDate registrationDeadline,
            @Schema(description = "등록 마감일이 지났고 게이트 ②가 아직이다") boolean registrationOverdue,
            @Schema(description = "오픈 승인 SLA(영업일)", example = "3") int reviewSlaBusinessDays
    ) {
    }

    public record Gate(
            @Schema(description = "STOCK_CONFIRMED(① 브랜드) · POST_SUBMITTED(② 내 게시물) · OPEN_APPROVED(③ 운영자)") GateKey key,
            @Schema(description = "게이트를 채우는 주체") GroupBuyActorType actorType,
            @Schema(description = "DONE · MY_TURN(내 차례 — 경고 톤) · WAITING · IN_REVIEW") GateState state,
            @Schema(description = "MY_TURN일 때만 WARNING") GroupBuyTone tone,
            @Schema(description = "① 확인 시각 · ② 제출 시각 · ③ 승인 시각. 수량은 싣지 않는다", nullable = true) LocalDateTime doneAt
    ) {
    }

    public enum GateKey { STOCK_CONFIRMED, POST_SUBMITTED, OPEN_APPROVED }

    public enum GateState { DONE, MY_TURN, WAITING, IN_REVIEW }

    public record Post(
            GroupBuyPostStatus status,
            String statusLabel,
            GroupBuyTone statusTone,
            @Schema(nullable = true) String title,
            @Schema(nullable = true) String content,
            @Schema(description = "대가관계 표시 — 저장하지 않고 서버가 브랜드명으로 조립한다. 게시물이 없어도 미리보기용으로 내린다",
                    example = "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다")
            String disclosureText,
            @Schema(description = "판매자 정보(상호·사업자번호·교환·반품)는 소비자 화면에 자동 표기된다 — 값은 싣지 않는다")
            boolean sellerInfoAutoAttached,
            @Schema(nullable = true) LocalDateTime submittedAt,
            @Schema(description = "승인대기일 때 예상 승인일 — 제출일 + SLA 영업일(제출일은 세지 않는다)", nullable = true)
            LocalDate expectedReviewDate,
            @Schema(nullable = true) LocalDateTime reviewedAt,
            @Schema(description = "노출 시작 — 공구 오픈 시각", nullable = true) LocalDateTime openedAt,
            @Schema(description = "노출 종료 — 공구 종결 시각", nullable = true) LocalDateTime closedAt,
            @Schema(description = "승인 후 마지막 수정", nullable = true) LocalDateTime lastEditedAt,
            @Schema(description = "반려 — 반려 상태일 때만", nullable = true) Rejection rejection,
            @Schema(description = "숨김 — 숨김 중일 때만", nullable = true) Hidden hidden
    ) {
    }

    public record Rejection(String code, @Schema(nullable = true) String detail,
                            @Schema(nullable = true) LocalDateTime rejectedAt) {
    }

    public record Hidden(
            String code,
            @Schema(nullable = true) String detail,
            LocalDateTime hiddenAt,
            @Schema(description = "숨김 N일차 — 양끝 포함", example = "2") int hiddenDays
    ) {
    }

    public record Sales(
            @Schema(description = "LIVE · PROVISIONAL(종료 · 확정 시 변동) · SETTLED · AT_SUSPENSION") SalesBasis basis,
            int orderCount,
            List<ItemQuantity> itemQuantities,
            long amount,
            @Schema(description = "항목별 수량 × 개당 리워드의 합") long myReward,
            @Schema(description = "숨김 이후 주문 수 — 숨김 중일 때만. 판매 모듈이 모르면 null(0이 아니다)", nullable = true)
            Long ordersSinceHidden
    ) {
    }

    public enum SalesBasis { LIVE, PROVISIONAL, SETTLED, AT_SUSPENSION }

    public record ItemQuantity(Long productId, int quantity) {
    }

    public record OrderClosure(int totalCount, int closedCount, Unclosed unclosed) {
    }

    @Schema(description = "미종결이 어디에 걸려 있나 — B7 「배송 처리 대기 18 · 반품 처리중 6」")
    public record Unclosed(
            int total,
            @Schema(nullable = true) Integer awaitingShipment,
            @Schema(nullable = true) Integer inReturnOrExchange
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
            @Schema(description = "응답 기한 = 현재 종료 시각. 대기 중일 때만", nullable = true) LocalDateTime respondDeadlineAt,
            @Schema(nullable = true) LocalDateTime respondedAt,
            @Schema(description = "CREATOR(수락·거절) / SYSTEM(기간 만료)", nullable = true) GroupBuyActorType responseActorType,
            @Schema(nullable = true) String rejectReasonCode,
            @Schema(nullable = true) String rejectReasonLabel,
            @Schema(nullable = true) String rejectMemo
    ) {
    }

    public record ActiveRequest(
            ChangeRequestType type,
            GroupBuyActorType requesterType,
            @Schema(description = "내가 낸 요청인가") boolean mine,
            String reasonCode,
            @Schema(nullable = true) String reasonLabel,
            @Schema(description = "내 요청일 때만 — 상대의 메모는 운영자에게 쓴 글이라 내리지 않는다", nullable = true) String memo,
            LocalDateTime requestedAt
    ) {
    }

    @Schema(description = "직권 중단 사전 통지 — 소명 기한·소명 내용은 싣지 않는다(브랜드↔운영자 절차)")
    public record AdminSuspension(
            AdminSuspensionKind kind,
            @Schema(description = "제17조① 호수 — 표시 문구는 FE가 호수로 고른다", nullable = true) SuspensionReasonClause reasonClause,
            String noticeBody,
            LocalDateTime noticedAt,
            @Schema(nullable = true) LocalDateTime executeScheduledAt,
            @Schema(description = "집행까지 N영업일 — B13 「D-3」", nullable = true) Integer businessDaysUntilExecution
    ) {
    }

    public record Closure(
            GroupBuyCloseType closeType,
            LocalDateTime endedAt,
            @Schema(description = "REQUEST · ADMIN_NOTICE · ADMIN_EMERGENCY — 기간 완주는 null", nullable = true) ClosureSource source,
            @Schema(description = "종결을 만든 요청 — 메모는 싣지 않는다", nullable = true) Requester requester,
            @Schema(description = "운영자가 양측에 쓴 결론", nullable = true) String decisionReason,
            @Schema(nullable = true) LocalDateTime decidedAt,
            @Schema(description = "직권 중단 근거", nullable = true) AdminBasis adminBasis
    ) {
    }

    public enum ClosureSource { REQUEST, ADMIN_NOTICE, ADMIN_EMERGENCY }

    public record Requester(
            GroupBuyActorType type,
            boolean mine,
            String name,
            String reasonCode,
            @Schema(nullable = true) String reasonLabel,
            LocalDateTime requestedAt
    ) {
    }

    public record AdminBasis(
            AdminSuspensionKind kind,
            @Schema(nullable = true) SuspensionReasonClause reasonClause,
            @Schema(nullable = true) EmergencySuspensionReason emergencyReason,
            String body
    ) {
    }

    public record Settlement(
            LocalDateTime settledAt,
            @Schema(nullable = true) Integer fixedFeeAmount,
            @Schema(description = "정산 모듈이 확정한 리워드 — 모듈 연동 전이면 null", nullable = true) Long confirmedReward,
            @Schema(description = "공제 전 합계 = 고정 지급비 + 확정 리워드. 확정 리워드를 모르면 null", nullable = true)
            Long totalBeforeDeduction
    ) {
    }

    public record AfterEnd(Fulfillment fulfillment) {
    }

    public record Fulfillment(
            @Schema(description = "내가 브랜드의 이행을 확인한 결과 — 미확인이면 null", nullable = true) FulfillmentCheck mine,
            @Schema(description = "브랜드가 내 이행을 확인한 결과", nullable = true) FulfillmentCheck theirs,
            @Schema(description = "내가 확인할 대상 — 브랜드의 의무") Target myTarget,
            @Schema(description = "상대가 확인할 대상 — 나의 콘텐츠 의무") Target theirTarget,
            @Schema(nullable = true) LocalDateTime dueAt,
            @Schema(description = "기한까지 답하지 않으면 이행으로 처리되는가 — false면 「놔두면 이행」 문구를 쓰면 안 된다")
            boolean autoConfirmOnTimeout,
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

    public record Target(
            Party party,
            List<Duty> duties,
            @Schema(description = "콘텐츠 의무 수 — 인플루언서 대상일 때만", nullable = true) ContentCounts counts
    ) {
    }

    public enum Party { BRAND, CREATOR }

    public enum Duty { ORDER_DELIVERY, FIXED_FEE_PAYMENT, SHOWROOM_POST, FEED, REELS, STORY }

    public record ContentCounts(int feed, int reels, int story) {
    }

    /** 버튼 판정 — 실행 API가 같은 판정으로 409를 내므로 FE가 복제하지 않는다(31 설계 4-8). */
    @Schema(description = "버튼 노출 판정 — 서버가 내려준다")
    public record Permissions(
            @Schema(description = "임시저장 · 등록하고 검토 요청") boolean canWritePost,
            @Schema(description = "승인 후 게시물 수정") boolean canEditPost,
            boolean canRespondExtension,
            boolean canRequestSuspension,
            boolean canCheckFulfillment,
            boolean canOpenPairThread
    ) {
    }

    public record HistoryEntry(
            GroupBuyEventType eventType,
            @Schema(description = "운영자 호칭(「운영자」)은 FE가 고른다") GroupBuyActorType actorType,
            @Schema(description = "브랜드명·쇼룸명 스냅샷 — 운영자·시스템은 null", nullable = true) String actorDisplayName,
            @Schema(description = "이벤트별로 내릴지가 정해진다 — 브랜드에게 쓴 문장은 내리지 않는다", nullable = true) String detail,
            LocalDateTime occurredAt
    ) {
    }

    public record Navigation(@Schema(nullable = true) Long prevGroupBuyId,
                             @Schema(nullable = true) Long nextGroupBuyId) {
    }
}
