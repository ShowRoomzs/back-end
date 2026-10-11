package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 차감 상태(44 어드민 설계서 1-1 · 6절) — 라벨 · 톤을 enum 이 든다. */
@Getter
@RequiredArgsConstructor
public enum ClawbackStatus {
    PENDING("차감 예정", SettlementTone.NEUTRAL),
    APPLIED("차감 반영", SettlementTone.INFO),
    UNRECOVERABLE("미회수", SettlementTone.WARNING);

    private final String label;
    private final SettlementTone tone;
}
