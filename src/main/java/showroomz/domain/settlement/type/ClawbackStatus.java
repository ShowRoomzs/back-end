package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 차감 상태(44 어드민 설계서 1-1 · 6절) — 라벨 · 톤을 enum 이 든다. */
@Getter
@RequiredArgsConstructor
public enum ClawbackStatus {
    PENDING("차감 예정", SettlementTone.NEUTRAL),
    APPLIED("차감 반영", SettlementTone.INFO),
    /** 상대 탈퇴 등으로 회수할 곳이 없다 — 베타 동안 플랫폼 손실로 처리한다(1021 기-13 · 회계 처리는 하지 않는다 · 표시). */
    UNRECOVERABLE("미회수 · 베타: 플랫폼 손실 처리", SettlementTone.WARNING);

    private final String label;
    private final SettlementTone tone;
}
