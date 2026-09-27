package showroomz.api.app.order.service;

import org.springframework.stereotype.Component;
import showroomz.api.app.cart.dto.CartDto;
import showroomz.domain.cart.type.CartUnavailableReason;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyPriceResolver;
import showroomz.domain.groupbuy.service.GroupBuyPriceResolver.GroupBuyPrice;
import showroomz.domain.market.entity.Market;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 주문 금액의 <b>한 식</b> — C8 장바구니 합계 · C9 주문서 · 주문 생성 금액이 모두 여기서 나온다(결제 계획서 2-3 ① · 7-1).
 *
 * <p>줄({@link Line})은 「어느 공구에서 · 어느 옵션을 · 몇 개」다. 장바구니 행 · 바로 구매 한 줄 · 주문 상품 줄이 같은 모양으로
 * 들어온다. 가격은 공구 계약에서만 나온다({@link GroupBuyPriceResolver}) — {@code product_variant.sale_price}는 읽지 않는다.
 *
 * <p>규칙은 장바구니에 있던 것을 그대로 옮겼다(선행 수정 계획서 3-2).
 * <ul>
 *   <li>구매 가능 판정 순서는 「공구 → 계약 옵션 → 상품 → 재고」다. 공구가 끝났는데 재고 부족 문구가 먼저 나가면 헛수고다.</li>
 *   <li>합계는 <b>선택된 줄만</b> 더한다. 배송비는 그룹(공구)마다 매기고, 설정값(기본 배송비 · 무료배송 기준)은 쇼룸(마켓)에서 읽는다.</li>
 *   <li>그룹 키는 공구다. 귀속 없는 옛 장바구니 행만 쇼룸으로 묶인다 — 그런 줄은 살 수 없어 선택되지 않는다.</li>
 * </ul>
 *
 * <p>상태가 없다. 판정 시각 {@code now}는 호출자가 {@link #price}에 넘겨 한 요청 안의 목록·합계·D-day가 같은 시각을 쓴다.
 */
@Component
public class OrderPricingCalculator {

    /** 계약에 없는 옵션 — 「마감」 라벨로 접되 담기·주문 시점에는 이유를 따로 말한다. */
    public static final String NOT_IN_GROUP_BUY_MESSAGE = "이 공구에서 판매하지 않는 옵션이에요";

    private final GroupBuyPriceResolver priceResolver;

    public OrderPricingCalculator(GroupBuyPriceResolver priceResolver) {
        this.priceResolver = priceResolver;
    }

    // ------------------------------------------------------------------ 가격

    /**
     * 줄마다 담은 공구 계약의 가격 — 공구별로 한 번씩 읽는다(줄마다 읽으면 N+1). 귀속 없는 줄은 가격이 없다.
     *
     * @param lines {@link Line#lineId()}는 이 호출 안에서 유일해야 한다
     */
    public Pricing price(List<Line> lines, LocalDateTime now) {
        Map<Long, List<Line>> byGroupBuy = new LinkedHashMap<>();
        for (Line line : lines) {
            if (line.groupBuy() != null) {
                byGroupBuy.computeIfAbsent(line.groupBuy().getId(), key -> new ArrayList<>()).add(line);
            }
        }

        Map<Long, GroupBuyPrice> byLineId = new HashMap<>();
        byGroupBuy.forEach((groupBuyId, groupLines) -> {
            Set<Long> variantIds = groupLines.stream()
                    .map(line -> line.variant().getVariantId())
                    .collect(Collectors.toSet());
            Map<Long, GroupBuyPrice> prices = priceResolver.resolveVariants(groupBuyId, variantIds);
            for (Line line : groupLines) {
                GroupBuyPrice price = prices.get(line.variant().getVariantId());
                if (price != null) {
                    byLineId.put(line.lineId(), price);
                }
            }
        });
        return new Pricing(byLineId, now);
    }

    public long lineSaleTotal(Line line, Pricing pricing) {
        GroupBuyPrice price = pricing.priceOf(line);
        long sale = price != null ? price.salePrice() : 0;
        return sale * line.quantity();
    }

    // ------------------------------------------------------------------ 구매 가능 여부

    /**
     * 살 수 없는 사유. 살 수 있으면 null이다.
     *
     * <p>마감을 품절보다 먼저 본다 — 공구가 끝났으면 재고가 남아 있어도 살 수 없고, 이때는 다른 옵션으로 이어질 길도 없어
     * 사유를 "품절"로 말하면 사용자를 헛걸음시킨다.
     */
    public CartUnavailableReason unavailableReason(Line line, Pricing pricing) {
        // 공구부터 본다 — 담은 공구가 없거나(귀속 못 한 옛 행) 끝났거나, 그 공구 계약에 이 옵션이 없으면
        // 가격을 정할 수 없다. 사용자에게는 모두 「이 공구에서는 더 살 수 없다」로 같다.
        GroupBuy groupBuy = line.groupBuy();
        if (groupBuy == null || !groupBuy.isOngoing(pricing.now()) || pricing.priceOf(line) == null) {
            return CartUnavailableReason.GROUP_BUY_CLOSED;
        }
        return unavailableReason(line.variant());
    }

    /** 공구·계약을 뺀 상품·재고 판정. */
    public CartUnavailableReason unavailableReason(ProductVariant variant) {
        Product product = variant.getProduct();

        // 연결(isConnected)이 아니라 진행중만 판다 — 준비중·준비완료 공구 상품은 상세는 열리지만 결제되면 안 된다.
        ProductGroupBuyStatus groupBuyStatus = product.getGroupBuyStatus();
        boolean isGroupBuySelling = groupBuyStatus != null && groupBuyStatus.isPurchasable();
        ProductDisplayStatus displayStatus = product.getDisplayStatus();
        boolean isDisplayed = displayStatus != null && displayStatus.isVisible();
        if (!isGroupBuySelling || !isDisplayed) {
            return CartUnavailableReason.GROUP_BUY_CLOSED;
        }

        int stock = variant.getStock() != null ? variant.getStock() : 0;
        if (Boolean.TRUE.equals(product.getIsOutOfStockForced()) || stock <= 0) {
            return CartUnavailableReason.SOLD_OUT;
        }
        return null;
    }

    /**
     * 담기·수정·주문 판정 — 순서는 「공구 → 계약 → 상품 → 재고」다. {@code groupBuyId}는 앱이 보내는 값이라 조작될 수 있다 —
     * 다른 브랜드의 공구를 실어 와도 그 계약에 이 옵션이 없어 여기서 걸린다.
     */
    public void requirePurchasable(GroupBuy groupBuy, ProductVariant variant, LocalDateTime now) {
        if (groupBuy == null || !groupBuy.isOngoing(now)) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_PURCHASABLE,
                    CartUnavailableReason.GROUP_BUY_CLOSED.getMessage());
        }
        if (priceResolver.resolveVariants(groupBuy.getId(), List.of(variant.getVariantId())).isEmpty()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_PURCHASABLE, NOT_IN_GROUP_BUY_MESSAGE);
        }
        CartUnavailableReason reason = unavailableReason(variant);
        if (reason != null) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_PURCHASABLE, reason.getMessage());
        }
    }

    /** 수량 상한 — 화면의 수량 스테퍼가 {@link CartDto#MAX_QUANTITY}에서 멈춘다. 주문도 같은 선이다. */
    public void requireWithinQuantityLimit(int quantity) {
        if (quantity > CartDto.MAX_QUANTITY) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "수량은 " + CartDto.MAX_QUANTITY + "개까지 담을 수 있습니다.");
        }
    }

    /**
     * 선택 상태 정리 — {@code requested}가 null이면 살 수 있는 줄 전체, 값이 있으면 그중 실제로 있고 살 수 있는 줄만 남긴다.
     * 마감·품절 줄이 요청에 섞여 들어와도 합계에 들어가지 않는다.
     */
    public Set<Long> resolveSelection(List<Line> lines, Collection<Long> requested, Pricing pricing) {
        Set<Long> purchasable = lines.stream()
                .filter(line -> unavailableReason(line, pricing) == null)
                .map(Line::lineId)
                .collect(Collectors.toCollection(java.util.HashSet::new));

        if (requested == null) {
            return purchasable;
        }
        purchasable.retainAll(new java.util.HashSet<>(requested));
        return purchasable;
    }

    // ------------------------------------------------------------------ 합계 · 배송비

    /** 공구로 묶는다. 귀속 없는 옛 줄만 쇼룸으로 묶인다 — 합계에서 빠지지 않게 마켓이 없으면 0을 쓴다. */
    public GroupKey groupKeyOf(Line line) {
        if (line.groupBuy() != null) {
            return new GroupKey(line.groupBuy().getId(), null);
        }
        Market market = line.market();
        return new GroupKey(null, market != null ? market.getId() : 0L);
    }

    /**
     * 합계 — <b>선택된 줄만</b> 계산한다. 배송비는 그룹(공구)마다 따로 매겨지고, 그 그룹에서 선택된 것이 없으면 부과하지 않는다.
     */
    public Summary summarize(List<Line> lines, Set<Long> selectedIds, Pricing pricing) {
        long regularTotal = 0L;
        long saleTotal = 0L;

        Map<GroupKey, ShippingAccumulator> shippingByGroup = new HashMap<>();

        for (Line line : lines) {
            if (!selectedIds.contains(line.lineId())) {
                continue;
            }

            // 선택된 줄은 살 수 있는 줄이라 가격이 있다(resolveSelection이 가격 없는 줄을 뺀다).
            GroupBuyPrice price = pricing.priceOf(line);
            int quantity = line.quantity();
            long regular = price != null ? price.regularPrice() : 0;
            long sale = price != null ? price.salePrice() : 0;

            regularTotal += regular * quantity;
            saleTotal += sale * quantity;

            Market market = line.market();

            ShippingAccumulator acc = shippingByGroup.computeIfAbsent(groupKeyOf(line), key -> new ShippingAccumulator());
            acc.saleTotal += sale * quantity;
            Integer deliveryFee = market != null ? market.getDefaultDeliveryFee() : null;
            if (deliveryFee != null && deliveryFee > acc.maxDeliveryFee) {
                acc.maxDeliveryFee = deliveryFee;
            }
            Integer threshold = market != null ? market.getFreeShippingThreshold() : null;
            if (threshold != null) {
                if (acc.minFreeThreshold == null || threshold < acc.minFreeThreshold) {
                    acc.minFreeThreshold = threshold;
                }
            }
        }

        long deliveryFeeTotal = 0L;
        for (ShippingAccumulator acc : shippingByGroup.values()) {
            if (acc.minFreeThreshold != null && acc.saleTotal >= acc.minFreeThreshold) {
                continue;
            }
            deliveryFeeTotal += acc.maxDeliveryFee;
        }

        long discountTotal = regularTotal - saleTotal;
        long finalTotal = saleTotal + deliveryFeeTotal;
        return new Summary(regularTotal, saleTotal, discountTotal, deliveryFeeTotal, finalTotal);
    }

    /**
     * 그룹 배송비 — 선택된 줄만으로 계산한다.
     *
     * <p>선택된 것이 하나도 없으면 부과 배송비는 0이고 "○○원 더 담으면 무료"도 내리지 않는다. 아무것도 담기지 않은 그룹에
     * 남은 금액을 띄우면 전액을 더 담아야 하는 것처럼 읽힌다.
     */
    public GroupShipping groupShipping(Market market, List<Line> groupLines, Set<Long> selectedIds, Pricing pricing) {
        int deliveryFee = market != null && market.getDefaultDeliveryFee() != null
                ? market.getDefaultDeliveryFee()
                : 0;
        Integer threshold = market != null ? market.getFreeShippingThreshold() : null;

        long selectedTotal = groupLines.stream()
                .filter(line -> selectedIds.contains(line.lineId()))
                .mapToLong(line -> lineSaleTotal(line, pricing))
                .sum();

        boolean hasSelectedItems = groupLines.stream().anyMatch(line -> selectedIds.contains(line.lineId()));
        boolean isFreeShipping = hasSelectedItems && threshold != null && selectedTotal >= threshold;

        Long amountToFreeShipping = null;
        if (hasSelectedItems && threshold != null && selectedTotal < threshold) {
            amountToFreeShipping = threshold - selectedTotal;
        }

        return new GroupShipping(deliveryFee, threshold, hasSelectedItems, selectedTotal,
                hasSelectedItems && !isFreeShipping ? deliveryFee : 0, isFreeShipping, amountToFreeShipping);
    }

    // ------------------------------------------------------------------ 값 타입

    /**
     * 주문(또는 장바구니) 한 줄.
     *
     * @param lineId   호출 안에서 유일한 식별자 — 장바구니는 cart id, 바로 구매는 임의 값. 선택 집합이 이 값을 쓴다
     * @param groupBuy 담은 공구. 귀속 없는 옛 장바구니 행이면 null(살 수 없다)
     */
    public record Line(Long lineId, GroupBuy groupBuy, ProductVariant variant, int quantity) {

        public Market market() {
            Product product = variant.getProduct();
            return product != null ? product.getMarket() : null;
        }
    }

    public record Pricing(Map<Long, GroupBuyPrice> byLineId, LocalDateTime now) {

        public GroupBuyPrice priceOf(Line line) {
            return byLineId.get(line.lineId());
        }
    }

    public record GroupKey(Long groupBuyId, Long marketId) {
    }

    /** C9 「결제 금액」 4줄과 최종 금액. {@code discountTotal = regularTotal − saleTotal}, {@code finalTotal = saleTotal + deliveryFeeTotal}. */
    public record Summary(long regularTotal, long saleTotal, long discountTotal, long deliveryFeeTotal, long finalTotal) {
    }

    /**
     * @param chargedDeliveryFee   실제 부과 배송비 — 무료 조건 충족 또는 선택 없음이면 0
     * @param amountToFreeShipping 무료배송까지 남은 금액 — 기준이 없거나 충족했거나 선택이 없으면 null
     */
    public record GroupShipping(int deliveryFee, Integer freeShippingThreshold, boolean hasSelectedItems,
                                long selectedProductTotal, int chargedDeliveryFee, boolean isFreeShipping,
                                Long amountToFreeShipping) {
    }

    private static class ShippingAccumulator {
        private long saleTotal = 0L;
        private int maxDeliveryFee = 0;
        private Integer minFreeThreshold = null;
    }
}
