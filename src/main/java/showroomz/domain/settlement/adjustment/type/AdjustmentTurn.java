package showroomz.domain.settlement.adjustment.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.settlement.type.SettlementTone;

/** 뷰어 기준 차례(44 이슈 스레드 설계서 1-5) — 서버가 정한다. 반대 뒤는 OPEN_FLOOR(양측 모두 제안 가능 · 잠정 §46 A-9). */
@Getter
@RequiredArgsConstructor
public enum AdjustmentTurn {
    MY_TURN("내 응답 필요", SettlementTone.WARNING),
    THEIR_TURN("상대 응답 대기", SettlementTone.INFO),
    OPEN_FLOOR("협의 중", SettlementTone.NEUTRAL),
    CLOSED("종결", SettlementTone.NEUTRAL);

    private final String label;
    private final SettlementTone tone;
}
