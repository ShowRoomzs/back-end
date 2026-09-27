package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.ContractItemOption;
import showroomz.domain.contract.repository.ContractItemOptionRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 소비자 가격의 <b>유일한 원천</b> — 공구 계약의 옵션 스냅샷(가격 계획서 0·2절 · 옵션 계획서 5-1).
 *
 * <p>C7 상세·옵션 시트·장바구니·주문 단가가 전부 여기서 가격을 받는다. {@code product.sale_price}·
 * {@code product_variant.sale_price}는 옵션 생성 시 정가를 복사한 값이라 공구가가 아니고, 셀러가 정가를 고치면 따라
 * 바뀐다. 계약은 체결 뒤 스냅샷을 고치는 경로가 없으므로 여기서 나온 가격은 상품 관리가 무엇을 바꿔도 변하지 않는다.
 *
 * <p>규칙 셋:
 * <ul>
 *   <li><b>공구 상태를 판정하지 않는다</b>(가격 조회만). 판정 시점은 호출자마다 다르다 — 담기는 지금, 결제 확정은
 *       주문 시각이다. 예외는 {@link #resolveProductOffer} 하나로, C7이 「어느 공구의 가격을 보여 줄지」를 정하는 곳이다.</li>
 *   <li><b>지워진 옵션(variant NULL)은 보지 않는다</b> — 살 수 없는 옵션이다.</li>
 *   <li><b>없는 가격은 0이 아니라 부재다</b>. 0으로 물러서면 0원 결제, 정가로 물러서면 공구가 없는 결제다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupBuyPriceResolver {

    private final ContractItemOptionRepository contractItemOptionRepository;
    private final GroupBuyRepository groupBuyRepository;

    /**
     * 이 공구에서 옵션을 살 때의 가격.
     *
     * @return variantId → 가격. 계약에 없는 옵션은 맵에 없다
     */
    public Map<Long, GroupBuyPrice> resolveVariants(Long groupBuyId, Collection<Long> variantIds) {
        if (groupBuyId == null || variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        return toPrices(contractItemOptionRepository.findPricedByGroupBuyIdAndVariantIds(groupBuyId, variantIds));
    }

    public Optional<GroupBuyPrice> resolve(Long groupBuyId, Long variantId) {
        return Optional.ofNullable(resolveVariants(groupBuyId, List.of(variantId)).get(variantId));
    }

    /** 공구 계약에 실린 한 상품의 옵션 가격 전량. 순서는 계약의 옵션 순서다. */
    public Map<Long, GroupBuyPrice> resolveByProduct(Long groupBuyId, Long productId) {
        if (groupBuyId == null || productId == null) {
            return Map.of();
        }
        return toPrices(contractItemOptionRepository.findPricedByGroupBuyIdAndProductId(groupBuyId, productId));
    }

    /**
     * C7이 보여 줄 공구와 그 가격(가격 계획서 4절).
     *
     * <ul>
     *   <li>{@code requestedGroupBuyId}가 있으면 그 공구 — 지금 판매 중이고 계약에 이 상품이 있을 때만.</li>
     *   <li>없으면 이 상품을 담은 판매 중 공구가 <b>정확히 1개</b>일 때만 그 공구. 둘 이상이면 공구가도 둘이라
     *       서버가 고르지 않는다(결제 계획서 4-6 「귀속은 진입 경로가 정한다」).</li>
     * </ul>
     * 준비중 공구는 상세가 열려도 가격을 정하지 않는다 — 시작 전 공구가로 담기면 시작 전 결제가 된다.
     */
    public Optional<ProductOffer> resolveProductOffer(Long requestedGroupBuyId, Long productId, LocalDateTime now) {
        Optional<GroupBuy> candidate = requestedGroupBuyId != null
                ? groupBuyRepository.findById(requestedGroupBuyId)
                : soleSellingGroupBuy(productId, now);

        return candidate
                .filter(groupBuy -> groupBuy.isOngoing(now))
                .flatMap(groupBuy -> {
                    Map<Long, GroupBuyPrice> prices = resolveByProduct(groupBuy.getId(), productId);
                    return prices.isEmpty() ? Optional.empty() : Optional.of(new ProductOffer(groupBuy, prices));
                });
    }

    private Optional<GroupBuy> soleSellingGroupBuy(Long productId, LocalDateTime now) {
        List<GroupBuy> selling = groupBuyRepository.findSellingByProductId(productId, GroupBuyStatus.SELLING, now);
        return selling.size() == 1 ? Optional.of(selling.get(0)) : Optional.empty();
    }

    /** 같은 옵션이 한 계약에 두 번 실렸으면 앞 항목을 쓴다(쿼리가 항목 순서로 정렬한다). 공구가가 빈 행은 버린다. */
    private static Map<Long, GroupBuyPrice> toPrices(List<ContractItemOption> options) {
        Map<Long, GroupBuyPrice> prices = new LinkedHashMap<>();
        for (ContractItemOption option : options) {
            GroupBuyPrice price = GroupBuyPrice.of(option);
            if (price != null) {
                prices.putIfAbsent(price.variantId(), price);
            }
        }
        return prices;
    }

    /**
     * @param regularPrice     옵션 정가 스냅샷 — 화면의 취소선
     * @param groupBuyPrice    상품 공구가
     * @param optionExtraPrice 옵션가 = 옵션 정가 − 상품 정가(스냅샷끼리)
     * @param salePrice        옵션 판매가 = 공구가 + 옵션가 — 주문 단가
     */
    public record GroupBuyPrice(Long contractItemId, Long variantId, int regularPrice, int groupBuyPrice,
                                int optionExtraPrice, int salePrice) {

        static GroupBuyPrice of(ContractItemOption option) {
            Integer salePrice = option.salePrice();
            Integer extra = option.optionExtraPrice();
            if (salePrice == null || extra == null || option.getRegularPrice() == null) {
                return null;
            }
            return new GroupBuyPrice(option.getContractItem().getId(), option.getVariantId(),
                    option.getRegularPrice(), option.getContractItem().getGroupBuyPrice(), extra, salePrice);
        }
    }

    /** C7이 보여 줄 공구와 그 공구 계약의 옵션 가격(variantId → 가격, 계약 옵션 순서). */
    public record ProductOffer(GroupBuy groupBuy, Map<Long, GroupBuyPrice> prices) {
    }
}
