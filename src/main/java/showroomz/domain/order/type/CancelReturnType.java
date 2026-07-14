package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CancelReturnType {
    CANCEL("단순취소"),   // 결제완료·준비 착수 전 — PG 자동 취소(운영자 불필요)
    RETURN("반품");        // 배송완료~구매확정 전 — 회수·입고 확인 후 운영자 PG 취소

    private final String description;
}
