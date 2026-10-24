package showroomz.domain.performance.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배송 예외 분해(48 파트너 설계서 2-4) — 반송 · 분실은 소비자 요청이 아니라 배송 과정의 건이라 반품률에 넣지 않는다(§47-2).
 * 정산 생성 전에 집행된 운영자 사유 환불(구매확정 후 하자 · 리콜)도 정산이 배송 예외 차감으로 잡으므로 여기 한 줄로 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum PerformanceExceptionKind {

    RETURNED_TO_SENDER("반송"),
    LOST("분실"),
    OPERATOR_REFUND("운영자 사유 환불");

    private final String label;
}
