package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정지 해제 사유(52 설계서 1-1) — 해제 모달이 시안에 없어 선택이고, 비면 {@link #RESOLVED}다. {@link #WITHDRAWN}은 탈퇴 승인이 열린
 * 정지를 종결할 때만 시스템이 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum SanctionReleaseReason {
    RESOLVED("소명 · 시정 확인"),
    MISTAKE("오정지"),
    WITHDRAWN("탈퇴 승인으로 종결"),
    /** 정지 기간 종료 — 시스템 자동 해제(1021 기-02). 운영자가 고를 수 없다. */
    TERM_ENDED("정지 기간 종료");

    private final String label;
}
