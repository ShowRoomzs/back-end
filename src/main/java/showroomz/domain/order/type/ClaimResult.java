package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 클레임 종결 결과(35 설계서 1-2) — {@code COMPLETED}일 때만 있다.
 * {@link #CANCELLED}는 검수 전에 요청이 사라진 종결이다 — 환불도 반송도 없고 거절률 집계에서 뺀다.
 */
@Getter
@RequiredArgsConstructor
public enum ClaimResult {
    REFUNDED("환불 완료"),
    EXCHANGED("교환 완료"),
    REJECTED("거절 종결"),
    CANCELLED("요청 취소");

    private final String label;
}
