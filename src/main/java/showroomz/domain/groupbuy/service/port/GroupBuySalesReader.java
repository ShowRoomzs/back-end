package showroomz.domain.groupbuy.service.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 판매 관리 포트(설계서 5-1). 공구 도메인은 인터페이스만 정의하고 의존한다.
 *
 * <p>판매 모듈이 이 포트를 구현하려면 주문에 공구 귀속({@code order_product.group_buy_id})이 필요하다 —
 * 지금은 없다. 구현이 없는 동안 {@link EmptyGroupBuySalesReader}가 empty를 돌려주고 응답은 null이 된다.
 *
 * <p><b>없는 값은 empty지 0이 아니다</b>(설계서 0-6). 미종결 주문 0은 「정산해도 된다」는 뜻이라
 * 판매 모듈이 없어서 0이 나오면 정산 게이트가 거짓으로 열린다.
 */
public interface GroupBuySalesReader {

    /** 취소·반품 반영 판매 실적. */
    Optional<GroupBuySales> readSales(Long groupBuyId);

    /** 전체·종결·미종결 주문 건수. §29-11 종결 경로 5종의 판정은 판매 모듈이 한다. */
    Optional<GroupBuyOrderClosure> readClosure(Long groupBuyId);

    /**
     * 기준 시각 이후 주문 건수 — 스튜디오 B5a 「숨김 이후 0건」(31 설계 8-1). empty와 0은 다르다 — 0은
     * 「숨김이 판매를 멈췄다」는 사실 주장이라 판매 모듈이 없어서 0이 나오면 거짓말이 된다.
     */
    Optional<Long> countOrdersSince(Long groupBuyId, LocalDateTime since);

    /**
     * 기준 시각 이후 이 공구 주문에 연결된 1:1 문의 건수 — 어드민 B3 「CS 문의」(32 설계 4-5). 「이 주문이 어느 공구의
     * 것인가」는 판매 모듈만 안다 — 공구가 1:1 문의 테이블을 직접 조인하면 판매 모듈이 생기기 전까지 항상 0이 나온다.
     */
    Optional<Long> countOneToOneInquiriesSince(Long groupBuyId, LocalDateTime since);

    record GroupBuySales(int orderCount, long amount, List<ItemQuantity> itemQuantities) {
    }

    record ItemQuantity(Long productId, int quantity) {
    }

    /**
     * @param awaitingShipment   미종결 중 배송 처리 대기 — 스튜디오 B7 「배송 처리 대기 18」. 모르면 null
     * @param inReturnOrExchange 미종결 중 반품·교환 처리중 — B7 「반품 처리중 6」. 모르면 null
     *                           (§29-11이 막은 것은 종결 경로 내역이고, 이 둘은 <b>남은 건이 어디 걸려 있나</b>다 — 31 설계 8-1)
     */
    record GroupBuyOrderClosure(int totalCount, int closedCount, int unclosedCount,
                                Integer awaitingShipment, Integer inReturnOrExchange,
                                List<UnclosedStage> unclosedStages) {
    }

    /**
     * 미종결 주문의 단계별 건수 — 서피스마다 다른 칸을 요구해(스튜디오 2칸 · 어드민 「교환 재발송 대기 · 반품 심사」)
     * 판매 모듈이 단계 목록을 돌려주고 서피스가 묶는다(32 설계 4-8 ⑦). 단계 enum은 판매 관리가 소유한다.
     * 종결 경로별 내역(구매확정·환불)은 싣지 않는다 — 판매 관리 소관이다(§29-11).
     */
    record UnclosedStage(String stage, String label, int count) {
    }
}
