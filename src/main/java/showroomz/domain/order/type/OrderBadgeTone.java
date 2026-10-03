package showroomz.domain.order.type;

/**
 * 주문 관리 배지 색 — 매핑은 enum 한 곳에만 둔다(34 설계서 1-2). FE가 매핑표를 들지 않는다.
 */
public enum OrderBadgeTone {
    NEUTRAL, INFO, WARNING, SUCCESS, DANGER
}
