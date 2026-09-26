package showroomz.domain.groupbuy.type;

import showroomz.domain.groupbuy.entity.GroupBuy;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 소비자 앱의 공구 판매 상태 — <b>저장하지 않고 파생한다</b>(공구 게시물 설계 4-3).
 *
 * <p>{@code PostStatus} 주석대로 D-3·품절·마감은 게시물 상태가 아니다. 카드 배지·상품 행·하트가 모두 이 값 하나에서
 * 갈린다. 배지 문구(C3 「공구 종료」 / C5 「공구 마감」)는 앱이 정한다 — 서버는 상태만 내린다.
 *
 * <pre>
 * CLOSED              group_buy 종결 ∨ end_at <= now   배지 「공구 마감」 · 상품 행 전부 CLOSED · 하트 잠김
 * SOLD_OUT            모든 상품 품절                   배지 「품절」
 * PARTIALLY_SOLD_OUT  일부 품절                        배지 「공동구매 D-n」 · 해당 행만 SOLD_OUT
 * ON_SALE             그 외                            배지 「공동구매 D-n」
 * </pre>
 */
public enum GroupBuySaleState {

    ON_SALE,
    PARTIALLY_SOLD_OUT,
    SOLD_OUT,
    CLOSED;

    /**
     * @param soldOutFlags 상품별 품절 여부 — 계약 상품 전부
     */
    public static GroupBuySaleState of(GroupBuy groupBuy, Collection<Boolean> soldOutFlags, LocalDateTime now) {
        if (isClosed(groupBuy, now)) {
            return CLOSED;
        }
        long soldOut = soldOutFlags.stream().filter(Boolean.TRUE::equals).count();
        if (soldOut == 0) {
            return ON_SALE;
        }
        return soldOut == soldOutFlags.size() ? SOLD_OUT : PARTIALLY_SOLD_OUT;
    }

    /**
     * 마감 — 종결 상태이거나 종료 시각이 지났다. 종료 스케줄러의 최대 1분 지연을 읽는 쪽에서 보정한다(4-3).
     */
    public static boolean isClosed(GroupBuy groupBuy, LocalDateTime now) {
        return groupBuy.getStatus().isTerminal() || !groupBuy.getEndAt().isAfter(now);
    }

    public boolean isClosed() {
        return this == CLOSED;
    }
}
