package showroomz.domain.groupbuy.type;

/**
 * 배지 색 — 같은 상태가 세 서피스에서 다른 색이면 안 된다.
 * FE 3개가 각자 매핑표를 들면 반드시 어긋나므로 매핑은 enum 한 곳에만 둔다(설계서 1-2).
 */
public enum GroupBuyTone {
    NEUTRAL, INFO, WARNING, SUCCESS, DANGER
}
