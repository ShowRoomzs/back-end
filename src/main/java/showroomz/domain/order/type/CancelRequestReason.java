package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 취소 요청 사유 — 소비자 앱 4택 그대로(§34-8). 브랜드 화면이 선택지를 따로 만들지 않는다.
 * ETC 만 자유 입력(사유 아래 보조 줄)이다.
 */
@Getter
@RequiredArgsConstructor
public enum CancelRequestReason {

    CHANGE_OF_MIND("단순 변심"),
    ORDER_MISTAKE("주문 실수"),
    PAYMENT_CHANGE("다른 결제 수단으로 변경"),
    ETC("기타");

    private final String label;
}
