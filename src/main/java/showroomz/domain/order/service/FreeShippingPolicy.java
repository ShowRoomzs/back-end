package showroomz.domain.order.service;

import showroomz.domain.market.entity.Market;

/**
 * 무료배송 기준금액의 해석(55 계획서 P3 · D1 확정 2026-10-24) — 시안 06 「0원이면 무료배송을 적용하지 않습니다」.
 *
 * <ul>
 *   <li>기준이 비었거나(null) 0 이하면 <b>무료배송 없음</b>이다. 0 을 그대로 두면 「합계 ≥ 0」이라 모든 주문이 무료배송이 된다.
 *       「항상 무료배송」은 기본 배송비 0 으로 표현한다.</li>
 *   <li>저장할 때도 이 규칙으로 0 을 null 로 바꾼다 — 화면 입력칸 기본값이 0 이라 빈칸이 0 으로 오기 때문이다.</li>
 *   <li><b>마켓의 현재 설정</b>을 읽는 곳만 이것을 쓴다. 결제 시점 스냅샷({@code order_delivery_group.free_shipping_threshold})을
 *       읽는 클레임 운임 판정은 건드리지 않는다 — 옛 주문은 결제 때의 사실을 유지한다(1021 기-20).</li>
 * </ul>
 */
public final class FreeShippingPolicy {

    private FreeShippingPolicy() {
    }

    /** 무료배송 기준 — 없거나 0 이하면 null(무료배송 없음). */
    public static Integer effective(Integer threshold) {
        return threshold == null || threshold <= 0 ? null : threshold;
    }

    /** 마켓의 현재 무료배송 기준 — 마켓이 없으면 null. */
    public static Integer thresholdOf(Market market) {
        return market == null ? null : effective(market.getFreeShippingThreshold());
    }
}
