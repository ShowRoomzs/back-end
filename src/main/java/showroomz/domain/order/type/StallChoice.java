package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 추적 정지 소비자 선택(1021 결정 노트 기-15 【잠정】 · 설계서 5-1) — 추적이 멈추고 N일이 지나면 소비자가 재발송 · 환불 중 하나를 고른다.
 * 처리는 브랜드가 한다(자동 집행 없음) — 재발송은 송장 수정, 환불은 [소비자 환불 처리].
 */
@Getter
@RequiredArgsConstructor
public enum StallChoice {
    RESHIP("재발송"),
    REFUND("환불");

    private final String label;
}
