package showroomz.domain.order.type;

/**
 * 소비자 앱 주문 상태 색 — 2단뿐이다(C10 설계서 0-4). 파트너센터용 {@link OrderBadgeTone} 5종과 의미 축이 달라 재사용하지 않는다.
 */
public enum UserOrderTone {
    /** 로즈 — 지금 볼·할 것이 있다. */
    ACTIVE,
    /** 회색 — 개입 불가. */
    MUTED
}
