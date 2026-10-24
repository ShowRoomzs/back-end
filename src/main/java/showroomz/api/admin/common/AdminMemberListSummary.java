package showroomz.api.admin.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 목록 탭 건수 · 툴바 수치(ui-admin-14 · 16 「총 N건 · 탈퇴 승인 대기 N건」) — 모두 <b>검색어 범위</b>로 센다(D13-2). 좌측 메뉴 배지는 홈
 * 큐가 따로 전체를 센다.
 */
@Schema(name = "AdminMemberListSummary")
public record AdminMemberListSummary(
        @Schema(description = "전체 = 아래 넷의 합", example = "8") long total,
        @Schema(description = "활성(브랜드는 휴면 포함) — 탈퇴 승인 대기 제외", example = "4") long active,
        @Schema(description = "정지 — 탈퇴 승인 대기 제외", example = "1") long suspended,
        @Schema(description = "탈퇴 승인 대기", example = "2") long withdrawalPending,
        @Schema(description = "탈퇴", example = "1") long withdrawn,
        @Schema(description = "탈퇴 승인 대기 탭에서만 — 가장 오래 기다린 신청의 경과 일수(시안 C2 「최장 대기 N일」). 다른 탭 · 대기 0건이면 null",
                example = "6", nullable = true) Long longestPendingDays) {
}
