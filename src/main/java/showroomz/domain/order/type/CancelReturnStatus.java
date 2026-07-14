package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 취소·반품 상태.
 * 단순취소: REQUESTED → REFUNDED.
 * 반품: REQUESTED → COLLECTING(회수중) → RETURNED(반송완료·입고) → REFUNDED.
 * 거절(REJECTED)은 요청(1차)·입고 검수(2차) 시점 모두 가능.
 */
@Getter
@RequiredArgsConstructor
public enum CancelReturnStatus {
    REQUESTED("요청"),
    COLLECTING("회수중"),
    RETURNED("반송완료"),
    REFUNDED("환불완료"),
    REJECTED("거절");

    private final String description;
}
