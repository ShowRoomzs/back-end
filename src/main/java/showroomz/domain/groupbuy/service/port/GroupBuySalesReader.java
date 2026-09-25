package showroomz.domain.groupbuy.service.port;

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

    record GroupBuySales(int orderCount, long amount, List<ItemQuantity> itemQuantities) {
    }

    record ItemQuantity(Long productId, int quantity) {
    }

    record GroupBuyOrderClosure(int totalCount, int closedCount, int unclosedCount) {
    }
}
