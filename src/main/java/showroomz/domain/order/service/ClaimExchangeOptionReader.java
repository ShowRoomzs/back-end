package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.service.GroupBuyPriceResolver;
import showroomz.domain.groupbuy.service.GroupBuyPriceResolver.GroupBuyPrice;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.domain.product.service.VariantOptionNames;

import java.util.List;
import java.util.Map;

/**
 * 교환할 수 있는 옵션(앱 클레임 설계서 3-1) — 같은 상품 · 같은 공구의 옵션 중 <b>판매가가 받은 옵션과 같은 것</b>만이다
 * (차액 결제·환불 모델이 없다 · Q7). 받은 옵션도 든다 — 불량·오배송은 같은 옵션으로 다시 받는다.
 *
 * <p>요청 폼의 옵션 목록 · 신청 검증 · 주문 상세의 「교환 불가 (재고 없음)」 판정이 전부 이 한 곳을 읽는다 —
 * 셋의 집합이 어긋나면 고를 수 있는 옵션이 신청에서 거절된다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimExchangeOptionReader {

    private final GroupBuyPriceResolver priceResolver;
    private final ProductVariantRepository productVariantRepository;

    /**
     * @param soldOut 재고 없음 — 목록에서 빼지 않고 표시한다(시안: 회색 + 「품절」)
     * @param current 받은 옵션
     */
    public record Option(Long variantId, String optionName, boolean soldOut, boolean current) {
    }

    /** 공구 귀속이 없는 옛 주문 항목은 교환할 옵션이 없다. 순서는 옵션 id 오름차순. */
    public List<Option> optionsOf(OrderProduct product) {
        ProductVariant received = product.getVariant();
        if (product.getGroupBuyId() == null || received == null || received.getProduct() == null) {
            return List.of();
        }
        Long productId = received.getProduct().getProductId();
        Map<Long, GroupBuyPrice> prices = priceResolver.resolveByProduct(product.getGroupBuyId(), productId);
        return productVariantRepository.findByProductIdWithOptions(productId).stream()
                .filter(variant -> {
                    GroupBuyPrice price = prices.get(variant.getVariantId());
                    return price != null && price.salePrice() == product.getPrice();
                })
                .sorted((a, b) -> Long.compare(a.getVariantId(), b.getVariantId()))
                .map(variant -> new Option(variant.getVariantId(), VariantOptionNames.of(variant),
                        variant.getStock() == null || variant.getStock() < 1,
                        variant.getVariantId().equals(received.getVariantId())))
                .toList();
    }

    /** 교환할 수 있는 옵션 중 재고가 있는 것이 하나라도 있는가 — 신청 API 의 재고 판정과 같은 기준이다. */
    public boolean hasAvailableOption(OrderProduct product) {
        return optionsOf(product).stream().anyMatch(option -> !option.soldOut());
    }
}
