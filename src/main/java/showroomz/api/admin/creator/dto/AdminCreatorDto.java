package showroomz.api.admin.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.api.admin.common.AdminReasonOption;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.WithholdingType;
import showroomz.domain.history.type.DeviceType;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.creator.type.CreatorAdminActorType;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.creator.type.CreatorPrivacyAccessReason;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.member.creator.type.CreatorSuspensionReason;
import showroomz.domain.member.creator.type.CreatorWithdrawalRejectReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 회원 관리 — 인플루언서 상세(ui-admin-18 rev.1 · 53 설계서 3절) 요청 · 응답. 값이 없는(수집하지 않는) 항목은 {@code null}로
 * 내려 FE 가 행을 그리지 않는다(0-8 「없는 데이터를 UI에 올리지 않는다」). 「— 파기됨」은 {@code destroyed = true}일 때만이다.
 */
public final class AdminCreatorDto {

    private AdminCreatorDto() {
    }

    // ------------------------------------------------------------------ 상세(상단 4카드 + 레일)

    @Schema(description = "인플루언서 상세 — 상단 4카드(기본 · 본인확인 · 사업자 · 정산) + 우측 레일")
    public record Detail(
            @Schema(example = "3018") Long creatorId,
            @Schema(description = "회원번호", example = "INF-3018") String memberNo,
            @Schema(description = "회원 상태 — 목록 상태 열과 같은 값") CreatorStatus status,
            @Schema(example = "활성") String statusLabel,
            @Schema(description = "배지 톤 — SUCCESS · DANGER · NEUTRAL", example = "SUCCESS") String statusTone,
            Basic basic,
            Identity identity,
            Unmask unmask,
            Business business,
            SettlementAccount settlementAccount,
            Rail rail) {
    }

    @Schema(description = "기본 정보 카드")
    public record Basic(
            @Schema(example = "소연 쇼룸") String showroomName,
            String profileImageUrl,
            @Schema(description = "쇼룸 주소 핸들", example = "soyeon_showroom") String showroomAddress,
            @Schema(description = "쇼룸 링크 — 탈퇴(비공개)면 null", example = "https://showroomz.kr/soyeon_showroom") String showroomUrl,
            @Schema(description = "쇼룸 공개 여부 — 탈퇴면 false(주소 · 인스타 링크 해제)") boolean showroomPublic,
            @Schema(description = "인스타 — 미등록 · 파기면 null") String instagramUrl,
            @Schema(description = "업무용 이메일 — 탈퇴 후에도 보존") String businessEmail,
            @Schema(description = "가입일(스튜디오 가입 = creator 생성)") LocalDateTime joinedAt,
            @Schema(description = "입점 승인일 — 입점 신청 승인 시각") LocalDateTime onboardingApprovedAt,
            LocalDateTime lastLoginAt,
            @Schema(description = "최근 로그인 기기 — 앱 / 스튜디오 서피스는 구분하지 않는다(로그인 이력에 없다)") DeviceType lastLoginDevice) {
    }

    @Schema(description = "본인확인 정보 — 기본 마스킹 · 유효한 해제 세션이 있으면 실값")
    public record Identity(
            @Schema(description = "탈퇴 파기 — true 면 값은 전부 null(「— 파기됨」)") boolean destroyed,
            @Schema(description = "실값으로 열렸는가") boolean unmasked,
            @Schema(example = "김*연") String name,
            @Schema(example = "1994.**.**") String birthday,
            @Schema(description = "만 나이 — 해제 상태에서만", example = "32") Integer age,
            @Schema(example = "010-****-8842") String phone,
            @Schema(description = "통신사 — PASS 미연동이라 수집하지 않는다(항상 null)") String carrier,
            @Schema(description = "최초 인증") LocalDateTime firstVerifiedAt,
            @Schema(description = "최근 갱신 — 재인증 경로가 없어 수집하지 않는다(항상 null)") LocalDateTime renewedAt) {
    }

    @Schema(description = "마스킹 해제 상태 — 카드 헤더 「해제됨 · 처리자 · 시각 · N 후 잠금」")
    public record Unmask(
            @Schema(description = "해제 버튼 활성 — 최고관리자 한정 · 파기 회원은 false") boolean allowed,
            boolean unmasked,
            String operatorName,
            LocalDateTime unmaskedAt,
            LocalDateTime expiresAt,
            Long remainingSeconds,
            @Schema(description = "직전 해제 사유(M4 프리필)") CreatorPrivacyAccessReason lastReason) {
    }

    @Schema(description = "사업자 정보 카드 — 비사업자면 「비사업자 + 3.3% 원천징수」로 대체")
    public record Business(
            boolean destroyed,
            CreatorBusinessType businessType,
            @Schema(example = "개인사업자") String businessTypeLabel,
            @Schema(description = "사업자등록번호 — 사업자만") String businessRegistrationNumber,
            @Schema(description = "사업자등록증 이미지 — 상호 · 대표자 · 주소는 수집하지 않아 등록증으로 확인한다") String businessLicenseImageUrl,
            @Schema(description = "비사업자의 주민등록번호 등록 여부 — 값은 내리지 않는다") Boolean residentNumberRegistered,
            WithholdingType withholdingType,
            @Schema(example = "세금계산서 발행") String withholdingLabel) {
    }

    @Schema(description = "정산 정보 카드 — 계좌는 가운데 마스킹 · 해제 경로 없음(전체 값은 정산 관리에서만)")
    public record SettlementAccount(
            boolean destroyed,
            String bankName,
            @Schema(example = "김*연") String accountHolder,
            @Schema(example = "333*-**-**8842") String accountNumber,
            @Schema(description = "최근 계좌 변경 — 없으면 null") AccountChange lastChange,
            @Schema(description = "사업자 구분 변경 요청 대기(§16-2a) — 「변경 요청 확인 중 ↗」 · 없으면 null")
            PendingBusinessTypeChange pendingBusinessTypeChange) {
    }

    @Schema(description = "열린 사업자 구분 변경 요청 — 상세는 /v1/admin/change-requests/{changeRequestId}")
    public record PendingBusinessTypeChange(Long changeRequestId,
                                            @Schema(example = "CHG-2026-0083") String requestCode,
                                            @Schema(description = "TO_BUSINESS · TO_INDIVIDUAL", example = "TO_BUSINESS") String type,
                                            LocalDateTime requestedAt) {
    }

    @Schema(description = "「변경 08.29 14:02 · 이전 신한 ****3301」")
    public record AccountChange(LocalDateTime changedAt, String previousBankName, String previousLast4) {
    }

    @Schema(description = "우측 레일 — 현재 상태 · 이슈 스레드 · 탈퇴 신청 심사 · 처리 이력 최신 N건")
    public record Rail(
            CurrentStatus currentStatus,
            @Schema(description = "운영팀 채널 스레드 id(소명 · 「스레드 열기 ↗」) — 채널이 없으면 null") Long operatorChannelThreadId,
            @Schema(description = "열린 조정 협의") List<OpenAdjustment> openAdjustments,
            @Schema(description = "탈퇴 신청 심사 — 신청이 있을 때만") WithdrawalReview withdrawalReview,
            List<HistoryItem> recentHistories,
            long historyTotal) {
    }

    @Schema(description = "현재 상태 카드")
    public record CurrentStatus(
            CreatorStatus status,
            String label,
            String tone,
            LocalDateTime joinedAt,
            LocalDateTime onboardingApprovedAt,
            LocalDateTime lastLoginAt,
            @Schema(description = "정지 정보 — 정지일 때만") Suspension suspension,
            @Schema(description = "탈퇴 정보 — 탈퇴일 때만") Withdrawal withdrawal) {
    }

    @Schema(description = "정지 사유 · 처리자 · 일시 · 리워드 지급 여부")
    public record Suspension(
            Long sanctionId,
            CreatorSuspensionReason reason,
            String reasonLabel,
            String reasonDetail,
            LocalDateTime suspendedAt,
            String operatorName,
            @Schema(description = "리워드 지급 정지 — 정산 정보 불일치만") boolean payoutHeld,
            @Schema(example = "유지 · 사유가 정산 정보와 무관") String payoutNote) {
    }

    public record Withdrawal(LocalDateTime withdrawnAt, LocalDateTime requestedAt, String operatorName) {
    }

    public record OpenAdjustment(Long adjustmentId, Long settlementId, String settlementNumber,
                                 LocalDateTime deadlineAt, Long threadId) {
    }

    @Schema(description = "탈퇴 신청 심사 카드 — 조건 건수는 실시간(승인 API 가 다시 판정한다)")
    public record WithdrawalReview(
            Long requestId,
            LocalDateTime requestedAt,
            long elapsedDays,
            @Schema(description = "승인 기한 안내(신청일 + 5영업일)") LocalDate dueDate,
            String reasonText,
            Conditions conditions) {
    }

    public record Conditions(long unfinishedGroupBuys, long unfinishedContracts, long unsettled) {
    }

    // ------------------------------------------------------------------ 하단 카드

    @Schema(description = "운영 현황 KPI 4 + 쇼룸 활동")
    public record Overview(Operations operations, Activity activity) {
    }

    public record Operations(
            long connectedBrandCount,
            long sellingGroupBuyCount,
            @Schema(description = "정산 진행 중 리워드(세전) — 인플루언서 몫이 지급 완료 전인 정산의 리워드 합") long rewardInProgressAmount,
            @Schema(description = "조정 협의 — 경고색은 이 수치만") long openAdjustmentCount) {
    }

    @Schema(description = "쇼룸 활동 — 앱 내 기록만(외부 채널 수치 아님)")
    public record Activity(
            long groupBuyPostCount,
            @Schema(description = "공구 게시물 검토 반려 횟수 — 사실 기록만") long reviewRejectCount,
            @Schema(description = "숨김 · 노출 중지 횟수 — 공구 게시물 숨김 + 일반 게시물 노출 중지") long hiddenCount,
            long generalPostCount,
            @Schema(description = "쇼룸 팔로워 — 앱 내 팔로우 수(인스타 팔로워 아님)") long followerCount,
            long threadCount,
            long openThreadCount) {
    }

    public record SettlementList(long total, List<SettlementRow> content) {
    }

    public record SettlementRow(
            Long settlementId,
            String settlementNumber,
            Long groupBuyId,
            String groupBuyTitle,
            String marketName,
            SettlementStatus status,
            String statusLabel,
            String tone,
            @Schema(description = "리워드(세전)") long rewardAmount,
            PayoutStatus creatorPayoutStatus,
            String creatorPayoutStatusLabel,
            LocalDate payoutDueDate,
            LocalDateTime paidAt,
            @Schema(description = "조정 협의 — 열린 협의일 때만") OpenAdjustment adjustment) {
    }

    public record ContractRow(
            Long contractId,
            String contractNumber,
            String marketName,
            String title,
            ContractStatus status,
            String statusLabel,
            String tone,
            @Schema(description = "고정 지급비 — null 이면 「없음」") Integer fixedFeeAmount,
            FixedFeeTrigger fixedFeeTrigger,
            String fixedFeeTriggerLabel,
            @Schema(description = "지급 시점 도래 시각(사실) — 미도래면 null") LocalDateTime fixedFeeTriggerReachedAt,
            @Schema(description = "브랜드 지급 기록(자기 신고) — 없으면 null. 플랫폼은 보증하지 않는다") LocalDateTime fixedFeePaidAt) {
    }

    public record ConnectionList(long total, List<ConnectionRow> content) {
    }

    public record ConnectionRow(Long connectionId, Long marketId, String marketName, LocalDateTime connectedAt,
                                long sellingGroupBuyCount, ConnectionStatus status, LocalDateTime disconnectedAt) {
    }

    // ------------------------------------------------------------------ 이력 · 메모

    public record HistoryItem(
            @Schema(description = "저장 이력 id — 파생 행(가입 신청 · 입점 승인)은 null") Long historyId,
            CreatorAdminEventType eventType,
            String label,
            String tone,
            String detail,
            CreatorAdminActorType actorType,
            @Schema(example = "김운영") String actorName,
            String refType,
            Long refId,
            LocalDateTime occurredAt) {
    }

    public record HistoryPage(long total, int page, int size, List<HistoryItem> content) {
    }

    public record Memo(Long memoId, String authorName, String content, LocalDateTime createdAt,
                       LocalDateTime modifiedAt, @Schema(description = "작성자 본인 ∧ 24시간 이내") boolean editable) {
    }

    public record MemoPage(long total, List<Memo> content) {
    }

    public record MemoRequest(
            @NotNull @Size(max = 300) @Schema(description = "300자 이내", example = "벨라코스 고정 지급비 미지급 문의 — 계약서상 지급 시점을 근거로 브랜드 확인 안내")
            String content) {
    }

    // ------------------------------------------------------------------ 조치

    public record UnmaskRequest(@NotNull CreatorPrivacyAccessReason reason) {
    }

    public record SuspendRequest(@NotNull CreatorSuspensionReason reason,
                                 @Size(max = 500) @Schema(description = "기타(OTHER)일 때만 필수") String reasonDetail) {
    }

    public record ReleaseRequest(@Schema(description = "해제 사유 — 비우면 RESOLVED. WITHDRAWN 은 시스템 전용") SanctionReleaseReason reason,
                                 @Size(max = 500) String note) {
    }

    public record RejectWithdrawalRequest(@NotNull CreatorWithdrawalRejectReason reason,
                                          @Size(max = 500) @Schema(description = "인플루언서에게 전달할 안내 — 기타일 때 필수") String note) {
    }

    @Schema(description = "모달 셀렉트의 단일 소스")
    public record ReasonOptions(List<AdminReasonOption> suspensionReasons, List<AdminReasonOption> releaseReasons,
                                List<AdminReasonOption> withdrawalRejectReasons,
                                List<AdminReasonOption> privacyUnmaskReasons) {
    }
}
