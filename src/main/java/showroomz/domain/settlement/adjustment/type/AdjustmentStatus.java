package showroomz.domain.settlement.adjustment.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.settlement.type.SettlementTone;

/** 조정 협의 상태(44 이슈 스레드 설계서 1-3 · 1-4) — 나가는 전이는 합의 · 기한 만료 둘뿐이다. 라벨 · 톤을 enum 이 든다. */
@Getter
@RequiredArgsConstructor
public enum AdjustmentStatus {
    OPEN("협의 중", SettlementTone.WARNING),
    AGREED("합의 · 금액 변경", SettlementTone.SUCCESS),
    EXPIRED("기한 만료 · 원래 금액", SettlementTone.NEUTRAL);

    private final String label;
    private final SettlementTone tone;

    public boolean isClosed() {
        return this != OPEN;
    }
}
