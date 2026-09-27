package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.OrderStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 판매 관리 포트 구현(결제 계획서 7-2) — {@code order_product.group_buy_id}로 공구별 판매 실적을 읽는다.
 *
 * <p>{@link #readClosure}는 <b>empty를 유지</b>한다. 배송·반품 상태가 없어 「종결」을 판정할 수 없고, 0을 돌려주면 정산 게이트가
 * 거짓으로 열린다(포트 주석 0-6). 배송 상태 머신이 생기면 채운다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderGroupBuySalesReader implements GroupBuySalesReader {

    private final OrderProductRepository orderProductRepository;

    @Override
    public Optional<GroupBuySales> readSales(Long groupBuyId) {
        List<Object[]> rows = orderProductRepository.sumSalesByGroupBuy(groupBuyId, OrderStatus.PAID_OR_LATER);
        int orderCount = 0;
        long amount = 0L;
        if (!rows.isEmpty() && rows.get(0) != null) {
            Object[] row = rows.get(0);
            orderCount = row[0] instanceof Number n ? n.intValue() : 0;
            amount = row[1] instanceof Number n ? n.longValue() : 0L;
        }
        List<ItemQuantity> quantities = new ArrayList<>();
        for (Object[] row : orderProductRepository.sumQuantityByProductForGroupBuy(groupBuyId, OrderStatus.PAID_OR_LATER)) {
            if (row[0] instanceof Long productId && row[1] instanceof Number quantity) {
                quantities.add(new ItemQuantity(productId, quantity.intValue()));
            }
        }
        return Optional.of(new GroupBuySales(orderCount, amount, quantities));
    }

    @Override
    public Optional<GroupBuyOrderClosure> readClosure(Long groupBuyId) {
        return Optional.empty();
    }

    @Override
    public Optional<Long> countOrdersSince(Long groupBuyId, LocalDateTime since) {
        return Optional.of(orderProductRepository.countOrdersSince(groupBuyId, OrderStatus.PAID_OR_LATER, since));
    }

    @Override
    public Optional<Long> countOneToOneInquiriesSince(Long groupBuyId, LocalDateTime since) {
        return Optional.of(orderProductRepository.countOneToOneInquiriesSince(groupBuyId, since));
    }
}
