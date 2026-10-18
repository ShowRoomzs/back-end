package showroomz.api.admin.brand.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.api.admin.common.AdminReasonOption;
import showroomz.domain.member.brand.type.BrandSuspensionReason;
import showroomz.domain.member.brand.type.BrandWithdrawalRejectReason;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.global.dto.PaginationInfo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 회원 관리 · 브랜드(ui-admin-15 · 52 설계서 2 · 9절). 좌측은 사실 조회(7카드), 우측 레일은 판단과 조치(현재 상태 · 진행 중 ·
 * 탈퇴 신청 심사 · 처리 이력)다. 파기된 값은 {@code null} 이고 {@code purged=true} 가 「— 파기됨」과 「미입력」을 가른다.
 */
public final class AdminBrandDto {

    private AdminBrandDto() {
    }

    // ------------------------------------------------------------------ 목록

    @Schema(name = "AdminBrandListItem")
    public record ListItem(
            Long marketId,
            @Schema(example = "BRD-1042") String brandNo,
            String brandName,
            String managerName,
            @Schema(description = "사업자 구분", example = "법인사업자") String businessType,
            @Schema(example = "ACTIVE") String memberStatus,
            @Schema(example = "활성") String memberStatusLabel,
            @Schema(description = "탈퇴 승인 대기 — 회원 상태가 아니라 별도 플래그") boolean withdrawalPending,
            LocalDateTime joinedAt,
            LocalDateTime lastLoginAt) {
    }

    @Schema(name = "AdminBrandListSummary")
    public record ListSummary(long total, long active, long suspended, long withdrawn, long withdrawalPending) {
    }

    @Schema(name = "AdminBrandListResponse")
    public record ListResponse(List<ListItem> content, PaginationInfo pageInfo, ListSummary summary) {
    }

    // ------------------------------------------------------------------ 상세

    @Schema(name = "AdminBrandDetail")
    public record Detail(
            Long marketId,
            @Schema(example = "BRD-1042") String brandNo,
            String brandName,
            @Schema(example = "ACTIVE") String memberStatus,
            String memberStatusLabel,
            boolean withdrawalPending,
            @Schema(description = "탈퇴로 파기됨 — 파기 대상 필드의 null 을 「— 파기됨」으로 그린다") boolean purged,
            Navigation navigation,
            BasicInfo basicInfo,
            BusinessInfo businessInfo,
            SettlementInfo settlementInfo,
            DeliveryPolicy deliveryPolicy,
            Operations operations,
            ConnectedCreators connectedCreators,
            OperatorThread operatorThread,
            Rail rail,
            Memos memos) {
    }

    @Schema(name = "AdminBrandNavigation", description = "회원번호 순 이전 · 다음 — 없으면 null")
    public record Navigation(Long previousMarketId, Long nextMarketId) {
    }

    @Schema(name = "AdminBrandBasicInfo")
    public record BasicInfo(String managerName, String managerContact, String email,
                            @Schema(description = "외부 이동 ↗") String siteUrl,
                            LocalDateTime joinedAt, LocalDateTime approvedAt) {
    }

    @Schema(name = "AdminBrandBusinessInfo", description = "업태 · 종목은 내리지 않는다(§52-1)")
    public record BusinessInfo(String businessType, String companyName, String representativeName,
                               String businessRegistrationNumber, String mailOrderRegNumber, String businessAddress) {
    }

    @Schema(name = "AdminBrandSettlementInfo")
    public record SettlementInfo(String bankName, String accountHolder,
                                 @Schema(description = "가운데 마스킹 — 전체 값은 정산 관리", example = "110-****-3841")
                                 String maskedAccountNumber,
                                 @Schema(description = "세금계산서 이메일(가입 시 입력)") String taxEmail,
                                 @Schema(description = "검토 대기 변경 요청 — 카드 머리 「변경 요청 대기 N건」")
                                 List<PendingChangeRequest> pendingChangeRequests) {
    }

    @Schema(name = "AdminBrandPendingChangeRequest")
    public record PendingChangeRequest(Long requestId, String requestCode,
                                       @Schema(example = "SETTLEMENT_ACCOUNT") String type, String typeLabel,
                                       LocalDateTime requestedAt) {
    }

    @Schema(name = "AdminBrandDeliveryPolicy", description = "조회 전용 · 탈퇴 후에도 보존(남은 반품의 비용 기준)")
    public record DeliveryPolicy(Integer defaultDeliveryFee, Integer freeShippingThreshold, Integer jejuSurcharge,
                                 @Schema(description = "도서산간 추가비 — 제주 제외") Integer remoteAreaSurcharge,
                                 Integer returnFee, Integer exchangeFee,
                                 @Schema(description = "발송 기한 N(공구 마감 + N영업일)") int shipLeadBusinessDays,
                                 String shipLeadText,
                                 @Schema(description = "최근 변경 — 「08.20 2→3 변경」 보조줄 · 없으면 null")
                                 ShipLeadChange shipLeadLastChange,
                                 ReturnAddress returnAddress) {
    }

    @Schema(name = "AdminBrandShipLeadChange")
    public record ShipLeadChange(LocalDateTime changedAt, Integer previousDays, Integer newDays) {
    }

    @Schema(name = "AdminBrandReturnAddress")
    public record ReturnAddress(String recipientName, String contact, String address, String detailAddress) {
    }

    @Schema(name = "AdminBrandOperations")
    public record Operations(long connectedCreatorCount,
                             @Schema(description = "진행중 · 중단 예정 공구") long activeGroupBuyCount,
                             @Schema(description = "누적 정산액 = 브랜드 몫 지급 완료 합") long settledAmountTotal,
                             @Schema(description = "미정산액 = 브랜드 몫 지급 전 합 — 0 이 아니면 경고색") long unsettledAmount,
                             @Schema(description = "지급 정지 중 — 미정산액에 「지급 정지 중」") boolean payoutHeld,
                             ContractCounts contracts,
                             ProductCounts products) {
    }

    @Schema(name = "AdminBrandContractCounts")
    public record ContractCounts(long signing, long concluded) {
    }

    @Schema(name = "AdminBrandProductCounts")
    public record ProductCounts(long selling, long soldOut) {
    }

    @Schema(name = "AdminBrandConnectedCreators")
    public record ConnectedCreators(@Schema(description = "연결중 수") long total, List<ConnectedCreator> items) {
    }

    @Schema(name = "AdminBrandConnectedCreator", description = "행 클릭 → 인플루언서 상세")
    public record ConnectedCreator(Long creatorId, String showroomName, LocalDateTime connectedAt,
                                   LocalDateTime disconnectedAt, long activeGroupBuyCount,
                                   @Schema(example = "CONNECTED") String status, String statusLabel) {
    }

    @Schema(name = "AdminBrandOperatorThread", description = "운영팀 소통 스레드 — 소명 · 문의 경로. 채널이 아직 없으면 null")
    public record OperatorThread(Long threadId, long unreadByOperator, LocalDateTime lastMessageAt) {
    }

    // ------------------------------------------------------------------ 레일

    @Schema(name = "AdminBrandRail")
    public record Rail(CurrentStatus currentStatus,
                       @Schema(description = "1건 이상인 칩만") List<PendingChip> pendingChips,
                       @Schema(description = "탈퇴 신청이 대기 중일 때만") WithdrawalReview withdrawalReview,
                       Histories histories) {
    }

    @Schema(name = "AdminBrandCurrentStatus")
    public record CurrentStatus(Long marketId, String memberStatus, String label, String tone,
                                LocalDateTime joinedAt, LocalDateTime approvedAt, LocalDateTime lastLoginAt,
                                @Schema(description = "열린 정지 — 정지가 아니면 null") Sanction sanction,
                                @Schema(description = "탈퇴 계정만") Withdrawal withdrawal,
                                Actions actions) {
    }

    @Schema(name = "AdminBrandSanction")
    public record Sanction(Long sanctionId, @Schema(example = "SETTLEMENT_INFO_MISMATCH") String reason,
                           String reasonLabel, @Schema(description = "상세 사유 전문") String reasonDetail,
                           @Schema(description = "브랜드 몫 지급 정지 — 사유가 정한다") boolean payoutHeld,
                           LocalDateTime suspendedAt, String suspendedByName,
                           @Schema(description = "소명 경로 — 운영팀 스레드") Long threadId) {
    }

    @Schema(name = "AdminBrandWithdrawal")
    public record Withdrawal(LocalDateTime requestedAt, String reason, String reasonLabel, String message,
                             LocalDateTime approvedAt, String approvedByName) {
    }

    @Schema(name = "AdminBrandActions")
    public record Actions(boolean canSuspend, boolean canRelease, boolean canReviewWithdrawal) {
    }

    @Schema(name = "AdminBrandPendingChip")
    public record PendingChip(@Schema(example = "SHIP_OVERDUE") String key, String label, String tone, long count,
                              @Schema(description = "이동할 화면 — ORDERS · CLAIMS · PRODUCT_INQUIRIES · THREAD · SETTLEMENTS")
                              String target,
                              @Schema(description = "THREAD 일 때 운영팀 채널") Long threadId) {
    }

    @Schema(name = "AdminBrandWithdrawalReview")
    public record WithdrawalReview(Long requestId, LocalDateTime requestedAt, long elapsedDays, String requesterName,
                                   String reason, String reasonLabel, String message) {
    }

    @Schema(name = "AdminBrandHistories")
    public record Histories(List<HistoryItem> items, long total) {
    }

    @Schema(name = "AdminBrandHistoryItem")
    public record HistoryItem(@Schema(example = "SUSPENDED") String type, String label,
                              @Schema(description = "SUCCESS · INFO · WARNING · DANGER · NEUTRAL") String tone,
                              @Schema(example = "ADMIN") String actorType, String actorName, String detail,
                              String refType, Long refId, LocalDateTime occurredAt) {
    }

    @Schema(name = "AdminBrandHistoryPage")
    public record HistoryPage(List<HistoryItem> content, PaginationInfo pageInfo) {
    }

    // ------------------------------------------------------------------ 메모

    @Schema(name = "AdminBrandMemos")
    public record Memos(List<Memo> items, long total) {
    }

    @Schema(name = "AdminBrandMemo")
    public record Memo(Long memoId, String authorName, String content, LocalDateTime createdAt,
                       @Schema(description = "값이 있으면 「수정됨」") LocalDateTime modifiedAt,
                       @Schema(description = "작성자 본인 · 24시간 이내 · 탈퇴 계정 아님") boolean editable) {
    }

    @Schema(name = "AdminBrandMemoPage")
    public record MemoPage(List<Memo> content, PaginationInfo pageInfo) {
    }

    @Schema(name = "AdminBrandConnectedCreatorPage")
    public record ConnectedCreatorPage(List<ConnectedCreator> content, PaginationInfo pageInfo) {
    }

    // ------------------------------------------------------------------ 요청

    @Schema(name = "AdminBrandSuspendRequest")
    public record SuspendRequest(@NotNull BrandSuspensionReason reason,
                                 @Size(max = 500) @Schema(description = "기타(직접 입력)일 때만 필수") String reasonDetail) {
    }

    @Schema(name = "AdminBrandReleaseRequest")
    public record ReleaseRequest(@Schema(description = "비면 RESOLVED · WITHDRAWN 은 고를 수 없다") SanctionReleaseReason reason,
                                 @Size(max = 500) String note) {
    }

    @Schema(name = "AdminBrandWithdrawalRejectRequest")
    public record WithdrawalRejectRequest(@NotNull BrandWithdrawalRejectReason reason,
                                          @Size(max = 500) @Schema(description = "브랜드에 전달할 안내 — 기타면 필수") String note) {
    }

    @Schema(name = "AdminBrandMemoRequest")
    public record MemoRequest(@NotBlank @Size(max = 300) String content) {
    }

    @Schema(description = "모달 셀렉트 사유 목록 — 정지 · 해제 · 탈퇴 반려. 인플루언서 상세와 같은 모양")
    public record ReasonOptions(List<AdminReasonOption> suspensionReasons, List<AdminReasonOption> releaseReasons,
                                List<AdminReasonOption> withdrawalRejectReasons) {
    }
}
