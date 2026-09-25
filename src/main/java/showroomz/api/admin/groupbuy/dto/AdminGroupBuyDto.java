package showroomz.api.admin.groupbuy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.EmergencySuspensionReason;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.domain.groupbuy.type.GroupBuyPostHideReason;
import showroomz.domain.groupbuy.type.GroupBuyPostRejectReason;
import showroomz.domain.groupbuy.type.GroupBuyPostRevisionKind;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.SuspensionReasonClause;
import showroomz.domain.groupbuy.type.SuspensionWithdrawReason;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 공구 판정 API의 요청·응답(32 설계 5~8절). <b>쓰기 API가 받는 것은 판정과 사유뿐이다</b>(0-2) — 조건 필드를
 * 받는 엔드포인트가 존재하지 않는다는 것 자체가 「어드민도 조건을 못 고친다」의 집행이다.
 *
 * <p>필수 문장 필드는 {@code @NotBlank}를 달지 않는다 — 비었을 때 사유별 에러 코드
 * ({@code GROUP_BUY_REJECT_DETAIL_REQUIRED} · {@code GROUP_BUY_DECISION_REASON_REQUIRED})를 서비스가 내린다.
 */
public final class AdminGroupBuyDto {

    private AdminGroupBuyDto() {
    }

    // ── 요청 ────────────────────────────────────────────────────────────────

    @Schema(description = "오픈 반려(M1) — 설명은 사유와 무관하게 필수다. 고칠 문장을 지목하지 않으면 재등록이 반복된다")
    public record OpenRejectRequest(
            @NotNull GroupBuyPostRejectReason reasonCode,
            @Schema(description = "인플루언서에게 전달할 설명 · 필수 · 1,000자") @Size(max = 1000) String detail
    ) {
    }

    @Schema(description = "게시물 숨김(M5)")
    public record PostHideRequest(
            @NotNull GroupBuyPostHideReason reasonCode,
            @Schema(description = "인플루언서에게 전달할 설명 · 필수 · 1,000자") @Size(max = 1000) String detail,
            @Schema(description = "운영자가 본 판본 — 검증하지 않고 기록 대조에만 쓴다. 다르면 응답 revisionAdvanced=true",
                    nullable = true)
            Integer observedRevisionNo
    ) {
    }

    @Schema(description = "숨김 해제 — 읽은 판본을 돌려준다. 그사이 수정됐으면 409 GROUP_BUY_POST_CHANGED_SINCE_VIEW")
    public record PostUnhideRequest(
            @Schema(description = "상세의 post.latestRevisionNo") @NotNull Integer expectedRevisionNo
    ) {
    }

    @Schema(description = "직권 중단 사전 통지(M6)")
    public record SuspensionNoticeRequest(
            @NotNull SuspensionReasonClause clause,
            @Schema(description = "집행 예정 일시 — 통지일 +3영업일 이후 · 소명 기한보다 엄격히 뒤 · 공구 종료 전")
            @NotNull LocalDateTime executeScheduledAt,
            @Schema(description = "소명 기한 — 통지일 +3영업일 23:59:59 이후") @NotNull LocalDateTime appealDeadlineAt,
            @Schema(description = "브랜드에 그대로 노출되는 통지 본문 · 필수 · 2,000자") @Size(max = 2000) String noticeBody
    ) {
    }

    @Schema(description = "중단 집행 — 집행 판정 사유는 브랜드에 전달된다")
    public record SuspensionExecuteRequest(
            @Schema(description = "필수 · 1,000자") @Size(max = 1000) String executionNote
    ) {
    }

    @Schema(description = "직권 중단 철회(M7)")
    public record SuspensionWithdrawRequest(
            @NotNull SuspensionWithdrawReason reasonCode,
            @Schema(description = "브랜드에 전달할 설명 · 필수 · 1,000자") @Size(max = 1000) String detail
    ) {
    }

    @Schema(description = "긴급 직권 중단(M4) — 사유 3종 고정 · 기타 없음")
    public record EmergencySuspensionRequest(
            @NotNull EmergencySuspensionReason emergencyReason,
            @Schema(description = "필수 · 2,000자") @Size(max = 2000) String body
    ) {
    }

    @Schema(description = "중단·조기 마감 요청 판정(M2 · M3) — 사유는 요청자와 상대 모두에게 간다")
    public record ChangeRequestDecisionRequest(
            @Schema(description = "필수 · 1,000자") @Size(max = 1000) String decisionReason
    ) {
    }

    @Schema(description = "이슈 스레드 개설")
    public record IssueOpenRequest(
            @NotNull GroupBuyIssueType issueType,
            @Schema(description = "필수 · 2,000자") @Size(max = 2000) String content
    ) {
    }

    // ── 응답 ────────────────────────────────────────────────────────────────

    /**
     * 판정 결과 — 전이 결과를 싣는다. FE는 이 값으로 완료 문구를 고르고 상세를 다시 읽는다.
     * 예: 오픈 승인 후 게이트 ①이 남아 있으면 {@code status = PREPARING}(「브랜드 물량 확인을 기다립니다」).
     */
    public record ActionResponse(
            Long groupBuyId,
            GroupBuyStatus status,
            String statusLabel,
            GroupBuyPostStatus postStatus,
            String postStatusLabel,
            @Schema(description = "준비완료가 됐으면 시작 일시", nullable = true) LocalDateTime openAt,
            @Schema(description = "숨김 — 운영자가 본 판본 이후 수정이 있었다. 최신 본문을 확인하라고 띄운다", nullable = true)
            Boolean revisionAdvanced,
            @Schema(description = "통지 3호 — C2_POST_ALTERATION: 게시물 변경을 이유로 삼으면 브랜드가 소명할 수 없는 사유다",
                    nullable = true)
            String clauseCaution
    ) {
    }

    public record IssueOpenResponse(Long issueId, @Schema(nullable = true) Long threadId) {
    }

    /** 게시물 판본 — 차분은 서버가 계산하지 않는다. 판본마다 표지를 붙여 「통지 판 vs 최신 판」을 바로 고르게 한다. */
    public record PostRevisionItem(
            int revisionNo,
            GroupBuyPostRevisionKind kind,
            String title,
            String content,
            LocalDateTime createdAt,
            @Schema(description = "운영자가 승인한 판") boolean approved,
            @Schema(description = "현재·마지막 숨김의 기준 판") boolean hiddenBasis,
            @Schema(description = "숨김 해제 판단에 쓴 판") boolean unhiddenBasis,
            @Schema(description = "최근 직권 중단 통지의 기준 판") boolean noticeBasis,
            @Schema(description = "최신 판") boolean latest
    ) {
    }

    /** M6 날짜 선택지 — 영업일만 나열한다. 칩은 편의이고 규칙은 통지 API가 같은 식으로 집행한다. */
    public record NoticeOptionsResponse(
            LocalDate today,
            AppealDeadline appealDeadline,
            List<ExecutionDate> executionDates,
            @Schema(description = "집행 예정은 이 시각보다 앞이어야 한다 = 공구 종료 예정") LocalDateTime latestExecutionBefore,
            boolean available,
            @Schema(nullable = true) AdminGroupBuyDetailResponse.NoticeUnavailableReason unavailableReason
    ) {
    }

    public record AppealDeadline(@JsonProperty("default") LocalDateTime defaultAt, LocalDateTime min) {
    }

    public record ExecutionDate(LocalDate date, boolean selectable) {
    }

    public record AttachmentUrlResponse(Long attachmentId, String url, String fileName, long expiresInSeconds) {
    }
}
