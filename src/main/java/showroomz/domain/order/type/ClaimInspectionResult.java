package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 검수 판정 — 요청 단위 요약(1021 설계서 4-3 · 결정 노트 기-17 「통과 / 사유 번복 / 반려」). */
@Getter
@RequiredArgsConstructor
public enum ClaimInspectionResult {
    PASSED("검수 통과"),
    REASON_OVERTURNED("사유 번복"),
    REJECTED("검수 반려");

    private final String label;
}
