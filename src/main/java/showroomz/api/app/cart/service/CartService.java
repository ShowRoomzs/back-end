package showroomz.api.app.cart.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.cart.dto.CartDto;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.cart.repository.CartRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductOption;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional
    public CartDto.AddCartResponse addCart(String username, CartDto.AddCartRequest request) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Cart saved = addCartForUser(user, request);

        return CartDto.AddCartResponse.builder()
                .cartId(saved.getId())
                .variantId(saved.getVariant().getVariantId())
                .quantity(saved.getQuantity())
                .message("장바구니에 추가되었습니다.")
                .build();
    }

    @Transactional
    public CartDto.BulkAddCartResponse addCartBulk(String username, List<CartDto.AddCartRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "추가할 상품이 없습니다.");
        }

        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        for (CartDto.AddCartRequest request : requests) {
            if (request == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "잘못된 요청이 포함되어 있습니다.");
            }
            addCartForUser(user, request);
        }

        return CartDto.BulkAddCartResponse.builder()
                .addedCount(requests.size())
                .message("상품 " + requests.size() + "개가 장바구니에 추가되었습니다.")
                .build();
    }

    @Transactional(readOnly = true)
    public CartDto.CartListResponse getCart(String username) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Cart> carts = cartRepository.findAllByUser(user);
        List<CartDto.CartItem> items = carts.stream()
                .map(this::toCartItem)
                .toList();

        CartSummaryData summaryData = calculateSummary(carts);

        CartDto.CartSummary summary = CartDto.CartSummary.builder()
                .regularTotal(summaryData.regularTotal)
                .saleTotal(summaryData.saleTotal)
                .discountTotal(summaryData.discountTotal)
                .deliveryFeeTotal(summaryData.deliveryFeeTotal)
                .finalTotal(summaryData.finalTotal)
                .build();

        return CartDto.CartListResponse.builder()
                .items(items)
                .summary(summary)
                .build();
    }

    @Transactional
    public CartDto.UpdateCartResponse updateCart(String username, Long cartItemId, CartDto.UpdateCartRequest request) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Cart cart = cartRepository.findByIdAndUser(cartItemId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (request.getVariantId() == null && request.getQuantity() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "변경할 항목이 없습니다.");
        }

        ProductVariant targetVariant = cart.getVariant();
        if (request.getVariantId() != null && !request.getVariantId().equals(cart.getVariant().getVariantId())) {
            targetVariant = productVariantRepository.findByVariantId(request.getVariantId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
            if (!Boolean.TRUE.equals(targetVariant.getIsDisplay())) {
                throw new BusinessException(ErrorCode.VARIANT_NOT_AVAILABLE);
            }
        }

        int requestedQuantity = request.getQuantity() != null ? request.getQuantity() : cart.getQuantity();
        int availableStock = targetVariant.getStock() != null ? targetVariant.getStock() : 0;

        Cart mergedTarget = null;
        if (!targetVariant.getVariantId().equals(cart.getVariant().getVariantId())) {
            mergedTarget = cartRepository.findByUserAndVariant(user, targetVariant).orElse(null);
        }

        if (mergedTarget != null && !mergedTarget.getId().equals(cart.getId())) {
            int finalQuantity = mergedTarget.getQuantity() + requestedQuantity;
            if (finalQuantity > availableStock) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "재고가 부족합니다");
            }
            mergedTarget.updateQuantity(finalQuantity);
            cartRepository.delete(cart);
            cart = cartRepository.save(mergedTarget);
        } else {
            if (requestedQuantity > availableStock) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "재고가 부족합니다");
            }
            cart.updateQuantity(requestedQuantity);
            cart.updateVariant(targetVariant);
            cart = cartRepository.save(cart);
        }

        List<Cart> carts = cartRepository.findAllByUser(user);
        CartSummaryData summaryData = calculateSummary(carts);

        CartDto.UpdateSummary summary = CartDto.UpdateSummary.builder()
                .regularTotal(summaryData.regularTotal)
                .saleTotal(summaryData.saleTotal)
                .discountTotal(summaryData.discountTotal)
                .deliveryFeeTotal(summaryData.deliveryFeeTotal)
                .totalProductPrice(summaryData.saleTotal)
                .expectedTotalPrice(summaryData.finalTotal)
                .build();

        return CartDto.UpdateCartResponse.builder()
                .cartId(cart.getId())
                .variantId(cart.getVariant().getVariantId())
                .quantity(cart.getQuantity())
                .summary(summary)
                .build();
    }

    /**
     * 장바구니 삭제 (개별/선택/전체 통합)
     * - cartItemIds가 null 또는 비어있으면: 전체 삭제
     * - cartItemIds가 있으면: 해당 ID들만 삭제 (본인 소유 검증 후 deleteAllByIdInBatch)
     */
    @Transactional
    public CartDto.DeleteCartResponse deleteCart(String username, List<Long> cartItemIds) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Long> deletedIds;
        String message;

        if (cartItemIds == null || cartItemIds.isEmpty()) {
            // 전체 삭제
            long count = cartRepository.countByUser(user);
            if (count == 0) {
                return CartDto.DeleteCartResponse.builder()
                        .deletedCartItemIds(List.of())
                        .deletedCount(0)
                        .message("이미 장바구니가 비어 있습니다")
                        .summary(emptySummary())
                        .build();
            }
            List<Cart> allCarts = cartRepository.findAllByUser(user);
            deletedIds = allCarts.stream().map(Cart::getId).toList();
            cartRepository.deleteByUser(user);
            message = count == 1 ? "1개 항목이 삭제되었습니다." : count + "개 항목이 삭제되었습니다.";
        } else {
            // 선택 삭제: 본인 소유 검증 후 삭제
            List<Cart> toDelete = cartRepository.findByIdInAndUser(cartItemIds, user);
            if (toDelete.size() != cartItemIds.size()) {
                Set<Long> foundIds = toDelete.stream().map(Cart::getId).collect(Collectors.toSet());
                List<Long> unauthorized = cartItemIds.stream().filter(id -> !foundIds.contains(id)).toList();
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "장바구니 항목을 찾을 수 없거나 삭제 권한이 없습니다. cartItemIds: " + unauthorized);
            }
            deletedIds = toDelete.stream().map(Cart::getId).toList();
            cartRepository.deleteAllByIdInBatch(deletedIds);
            int count = deletedIds.size();
            message = count == 1 ? "1개 항목이 삭제되었습니다." : count + "개 항목이 삭제되었습니다.";
        }

        List<Cart> remainingCarts = cartRepository.findAllByUser(user);
        CartSummaryData summaryData = calculateSummary(remainingCarts);
        CartDto.UpdateSummary summary = CartDto.UpdateSummary.builder()
                .regularTotal(summaryData.regularTotal)
                .saleTotal(summaryData.saleTotal)
                .discountTotal(summaryData.discountTotal)
                .deliveryFeeTotal(summaryData.deliveryFeeTotal)
                .totalProductPrice(summaryData.saleTotal)
                .expectedTotalPrice(summaryData.finalTotal)
                .build();

        return CartDto.DeleteCartResponse.builder()
                .deletedCartItemIds(deletedIds)
                .deletedCount(deletedIds.size())
                .message(message)
                .summary(summary)
                .build();
    }

    private CartDto.CartItem toCartItem(Cart cart) {
        ProductVariant variant = cart.getVariant();
        Product product = variant.getProduct();
        Market market = product.getMarket();

        ProductDto.PriceInfo priceInfo = buildPriceInfo(variant.getRegularPrice(), variant.getSalePrice());

        Integer stock = variant.getStock() != null ? variant.getStock() : 0;
        boolean isOutOfStockForced = Boolean.TRUE.equals(product.getIsOutOfStockForced());
        boolean isOutOfStock = isOutOfStockForced || stock <= 0;

        CartDto.StockInfo stockInfo = CartDto.StockInfo.builder()
                .stock(stock)
                .isOutOfStock(isOutOfStock)
                .isOutOfStockForced(isOutOfStockForced)
                .build();

        return CartDto.CartItem.builder()
                .cartId(cart.getId())
                .productId(product.getProductId())
                .variantId(variant.getVariantId())
                .productName(product.getName())
                .thumbnailUrl(product.getThumbnailUrl())
                .marketId(market != null ? market.getId() : null)
                .marketName(market != null ? market.getMarketName() : null)
                .optionName(buildOptionName(variant.getOptions()))
                .quantity(cart.getQuantity())
                .price(priceInfo)
                .deliveryFee(product.getDeliveryFee() != null ? product.getDeliveryFee() : 0)
                .stock(stockInfo)
                .build();
    }

    private Cart addCartForUser(Users user, CartDto.AddCartRequest request) {
        ProductVariant variant = productVariantRepository.findByVariantId(request.getVariantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));

        if (!Boolean.TRUE.equals(variant.getIsDisplay())) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_AVAILABLE);
        }

        int stock = variant.getStock() != null ? variant.getStock() : 0;
        int addQuantity = request.getQuantity();

        Cart cart = cartRepository.findByUserAndVariant(user, variant).orElse(null);
        int finalQuantity = cart != null ? cart.getQuantity() + addQuantity : addQuantity;

        if (finalQuantity > stock) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "재고가 부족합니다");
        }

        if (cart == null) {
            cart = new Cart(user, variant, addQuantity);
        } else {
            cart.updateQuantity(finalQuantity);
        }

        return cartRepository.save(cart);
    }

    private String buildOptionName(List<ProductOption> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        return options.stream()
                .sorted(Comparator.comparing(
                        option -> option.getOptionGroup() != null
                                ? option.getOptionGroup().getOptionGroupId()
                                : 0L
                ))
                .map(option -> {
                    String groupName = option.getOptionGroup() != null
                            ? option.getOptionGroup().getName()
                            : null;
                    String optionName = option.getName();
                    return (groupName != null ? groupName : "옵션") + ": " + optionName;
                })
                .collect(Collectors.joining(" / "));
    }

    private ProductDto.PriceInfo buildPriceInfo(Integer regularPrice, Integer salePrice) {
        Integer discountRate = calculateDiscountRate(regularPrice, salePrice);
        return ProductDto.PriceInfo.builder()
                .regularPrice(regularPrice)
                .discountRate(discountRate)
                .salePrice(salePrice)
                .maxBenefitPrice(salePrice)
                .build();
    }

    private Integer calculateDiscountRate(Integer regularPrice, Integer salePrice) {
        if (regularPrice == null || salePrice == null || regularPrice <= 0) {
            return 0;
        }
        double rate = ((double) (regularPrice - salePrice) / regularPrice) * 100.0;
        int rounded = (int) Math.round(rate);
        if (rounded < 0) {
            return 0;
        }
        return Math.min(rounded, 100);
    }

    private CartSummaryData calculateSummary(List<Cart> carts) {
        long regularTotal = 0L;
        long saleTotal = 0L;

        Map<Long, MarketShippingAccumulator> shippingByMarket = new HashMap<>();

        for (Cart cart : carts) {
            ProductVariant variant = cart.getVariant();
            Product product = variant.getProduct();
            int quantity = cart.getQuantity() != null ? cart.getQuantity() : 0;
            long regular = variant.getRegularPrice() != null ? variant.getRegularPrice() : 0;
            long sale = variant.getSalePrice() != null ? variant.getSalePrice() : 0;

            regularTotal += regular * quantity;
            saleTotal += sale * quantity;

            Market market = product.getMarket();
            Long marketId = market != null ? market.getId() : 0L;

            MarketShippingAccumulator acc = shippingByMarket.computeIfAbsent(
                    marketId,
                    key -> new MarketShippingAccumulator()
            );
            acc.saleTotal += sale * quantity;
            Integer deliveryFee = product.getDeliveryFee();
            if (deliveryFee != null && deliveryFee > acc.maxDeliveryFee) {
                acc.maxDeliveryFee = deliveryFee;
            }
            Integer threshold = product.getDeliveryFreeThreshold();
            if (threshold != null) {
                if (acc.minFreeThreshold == null || threshold < acc.minFreeThreshold) {
                    acc.minFreeThreshold = threshold;
                }
            }
        }

        long deliveryFeeTotal = 0L;
        for (MarketShippingAccumulator acc : shippingByMarket.values()) {
            if (acc.minFreeThreshold != null && acc.saleTotal >= acc.minFreeThreshold) {
                continue;
            }
            deliveryFeeTotal += acc.maxDeliveryFee;
        }

        long discountTotal = regularTotal - saleTotal;
        long finalTotal = saleTotal + deliveryFeeTotal;

        return new CartSummaryData(
                regularTotal,
                saleTotal,
                discountTotal,
                deliveryFeeTotal,
                finalTotal
        );
    }

    private CartDto.UpdateSummary emptySummary() {
        return CartDto.UpdateSummary.builder()
                .regularTotal(0L)
                .saleTotal(0L)
                .discountTotal(0L)
                .deliveryFeeTotal(0L)
                .totalProductPrice(0L)
                .expectedTotalPrice(0L)
                .build();
    }

    private static class MarketShippingAccumulator {
        private long saleTotal = 0L;
        private int maxDeliveryFee = 0;
        private Integer minFreeThreshold = null;
    }

    private record CartSummaryData(
            long regularTotal,
            long saleTotal,
            long discountTotal,
            long deliveryFeeTotal,
            long finalTotal
    ) {}
}
