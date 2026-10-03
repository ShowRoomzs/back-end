package showroomz.api.seller.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 파트너센터 주문 API 진입 검사 — 모든 조회·조작이 <b>하위주문의 market_id 가 내 브랜드인지</b>를 먼저 본다.
 * 내 마켓이 아니면 404 다 — 타 브랜드 주문의 존재를 노출하지 않는다(34 설계서 4-4).
 *
 * <p>마켓 해석은 {@code MarketRepository}로 한다 — 기획 제외 패키지의 {@code MarketService}를 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerOrderAccessGuard {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;

    public SellerScope resolve(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        return new SellerScope(seller.getId(), market);
    }

    public OrderDeliveryGroup loadOwned(Long deliveryGroupId, SellerScope scope) {
        return deliveryGroupRepository.findOwned(deliveryGroupId, scope.market().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
    }

    public record SellerScope(Long sellerId, Market market) {
    }
}
