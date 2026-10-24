package showroomz.api.common.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 파트너센터 · 쇼룸 스튜디오 계정 상태(1017 설계서 8-10) — 모든 페이지의 정지 배너 · 탑바 「정지」 칩 · [운영팀에 소명하기] 목적지.
 * 소비자 {@code /v1/user/account-status} 와 같은 꼴이지만 <b>상세 사유를 내린다</b> — 브랜드 · 인플루언서에게는 운영팀 채널
 * 정지 카드에 이미 상세가 보인다(파트너 S5 · 스튜디오 S8).
 */
@Schema(description = "계정 상태 — 정지 배너 재료")
public record MemberAccountStatusResponse(
        @Schema(description = "ACTIVE · SUSPENDED · WITHDRAWN(브랜드는 DORMANT 도 있다)", example = "SUSPENDED") String status,
        @Schema(description = "열린 정지 — 정지가 아니면 null", nullable = true) Sanction sanction,
        @Schema(description = "탈퇴 신청 심사 중 — 인플루언서 · 브랜드(열린 신청 행)", example = "false") boolean withdrawalPending
) {

    @Schema(name = "MemberAccountSanction")
    public record Sanction(
            @Schema(description = "정형 사유 코드", example = "SETTLEMENT_INFO_MISMATCH") String reason,
            @Schema(description = "정형 사유 라벨 — 배너 「사유: …」", example = "정산 정보 불일치") String reasonLabel,
            @Schema(description = "운영자가 입력한 상세 사유 — 있을 때만 배너 [자세히]로 펼친다", nullable = true) String reasonDetail,
            @Schema(example = "2026-10-08T14:20:00") LocalDateTime suspendedAt,
            @Schema(description = "지급 정지 사유인가 — true 면 배너에 정산 줄(정지가 풀리면 다음 영업일 지급)을 붙인다") boolean payoutHeld,
            @Schema(description = "운영팀 채널 스레드 id — [운영팀에 소명하기] 목적지", nullable = true) Long operatorChannelThreadId,
            @Schema(description = "그 채널의 「계정 이용 정지」 시스템 카드 message id — 스크롤 위치", nullable = true) Long suspendCardMessageId,
            @Schema(description = "정지 기간 · 이의 신청 기한(1021 기-02)") Term term
    ) {
    }

    /** 정지 기간 · 자동 해제 · 이의 신청 기한(1021 기-02 【잠정】) — 소비자 앱 시트 · 파트너 · 스튜디오 배너가 같은 꼴을 쓴다. */
    @Schema(name = "MemberSanctionTerm")
    public record Term(
            @Schema(description = "정지 종료일(포함) — 기간 없는 정지면 null", example = "2026-10-29", nullable = true)
            java.time.LocalDate endsOn,
            @Schema(description = "자동 해제 시각 = 종료일 다음 날 00:00", nullable = true) LocalDateTime autoReleaseAt,
            @Schema(description = "정지 기간(일) — 시작일 · 종료일 포함", example = "7", nullable = true) Integer periodDays,
            @Schema(description = "이의 신청 기한 — 통지 + N영업일 23:59:59(기한 뒤에도 접수한다)", nullable = true)
            LocalDateTime appealDeadlineAt,
            @Schema(description = "소명 검토 N영업일 — [정책값 미정]이면 null", nullable = true) Integer appealReviewBusinessDays
    ) {
        public static Term of(java.time.LocalDate endsOn, LocalDateTime suspendedAt, LocalDateTime appealDeadlineAt,
                              Integer appealReviewBusinessDays) {
            return new Term(endsOn, showroomz.domain.member.common.service.SanctionTermPolicy.autoReleaseAt(endsOn),
                    showroomz.domain.member.common.service.SanctionTermPolicy.periodDays(suspendedAt, endsOn),
                    appealDeadlineAt, appealReviewBusinessDays);
        }
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
