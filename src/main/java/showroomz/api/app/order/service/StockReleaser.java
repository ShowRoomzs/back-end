package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.product.repository.ProductVariantRepository;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * 재고 복원 — 주문당 <b>1회</b>(결제 계획서 4-3). 만료·결제 전 취소·결제 후 취소·불일치 취소가 모두 이 메서드를 쓰지만,
 * 이미 복원된 주문에 뒤늦게 결제가 도착해 자동 취소되는 경로에서는 {@code stock_released_at} 선점이 0행이라 두 번 늘지 않는다.
 *
 * <p>호출자의 트랜잭션에 참여한다({@code MANDATORY}) — 주문 전이와 같은 트랜잭션이어야 전이는 됐는데 재고가 안 돌아오는
 * 상태가 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class StockReleaser {

    private final OrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;
    private final ProductVariantRepository productVariantRepository;

    /** @return 실제로 복원했으면 true — 두 번째 호출은 false */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean release(Long orderId, LocalDateTime now) {
        if (orderRepository.claimStockRelease(orderId, now) != 1) {
            return false;
        }
        List<OrderProduct> products = orderProductRepository.findByOrderIdWithVariant(orderId);
        // 차감과 같은 순서(variant_id 오름차순)로 되돌린다 — 잠금 순서가 같아야 데드락이 없다.
        products.stream()
                .sorted((a, b) -> Long.compare(a.getVariantId(), b.getVariantId()))
                .forEach(product -> productVariantRepository.restoreStock(product.getVariantId(), product.getQuantity()));
        orderProductRepository.transitionByOrder(orderId,
                EnumSet.of(OrderProductStatus.PENDING, OrderProductStatus.PAID), OrderProductStatus.CANCELLED);
        return true;
    }
}
