package showroomz.api.app.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.member.user.type.UserStatus;

import java.time.LocalDateTime;

/**
 * 앱 계정 상태(C20 · 51-6 설계서 3-1) — 배너(1a) · 비활성 버튼(1b) · 안내 시트(1c) · 소명 유형 노출(1d) · 배지(1f) · 해제 토스트가 전부 이 응답
 * 하나로 갈린다. 「할 수 없는 것 / 할 수 있는 것」 목록은 고정 문구라 앱이 든다.
 */
@Schema(description = "계정 이용 상태")
public record AccountStatusResponse(
        @Schema(description = "회원 상태 — NORMAL(정상) · SUSPENDED(이용 정지)", example = "SUSPENDED")
        UserStatus status,
        @Schema(description = "이용 정지 정보 — SUSPENDED 일 때만, 아니면 null")
        Suspension suspension,
        @Schema(description = "해제 토스트 1회 — 정지가 풀린 뒤 아직 확인하지 않았으면 값이 있다. 보여준 뒤 release-notice/ack 를 부른다")
        ReleaseNotice releaseNotice) {

    @Schema(description = "이용 정지 정보 — 상세 사유는 내리지 않는다(§51-6)")
    public record Suspension(
            @Schema(description = "정형 사유 코드 — 사유 기록 없이 정지된 옛 회원은 null", example = "ABUSIVE_LANGUAGE")
            String reasonCode,
            @Schema(description = "배너 · 시트에 그대로 쓰는 사유 문구 — 옛 회원은 null(배너는 사유 없이 그린다)", example = "욕설·비방")
            String reasonLabel,
            @Schema(description = "정지 일시 — 옛 회원은 null", example = "2026-10-10T14:20:00")
            LocalDateTime suspendedAt,
            @Schema(description = "true 면 정지 안내 시트를 자동으로 1회 띄우고 suspension-notice/ack 를 부른다", example = "true")
            boolean noticePending,
            @Schema(description = "답변 전 정지 문의 id — 있으면 「1:1 문의로 이의 신청」이 새 작성 대신 이 문의로 간다", example = "null")
            Long pendingAppealInquiryId) {
    }

    @Schema(description = "해제 토스트")
    public record ReleaseNotice(
            @Schema(description = "해제 일시", example = "2026-10-14T09:00:00")
            LocalDateTime releasedAt) {
    }
}
