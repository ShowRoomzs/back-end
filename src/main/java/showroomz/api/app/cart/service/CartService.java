package showroomz.api.app.cart.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.cart.dto.CartDto;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.cart.repository.CartRepository;
import showroomz.domain.cart.type.CartUnavailableReason;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductOption;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    /**
     * 추천 후보를 넉넉히 읽어 오는 배수 — 장바구니 쇼룸의 상품을 앞으로 당기는 재정렬이
     * DB 정렬 뒤에 오기 때문에, 딱 필요한 개수만 읽으면 당길 상품이 창 밖에 남는다.
     */
    private static final int RECOMMENDATION_CANDIDATE_MULTIPLIER = 3;

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final CreatorFollowRepository creatorFollowRepository;

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

    /**
     * 장바구니 조회 — 항목을 <b>공구(쇼룸) 단위로 묶어</b> 내려준다 (C8).
     *
     * <p>배송비·마감일·발송 시점이 공구마다 다르기 때문에 상품이 아니라 공구별로 묶는다. 그룹
     * 끝에 그 공구의 배송비와 무료배송까지 남은 금액을 붙이는 것도 같은 이유다 — 결제 화면에서
     * 처음 알게 되는 배송비가 가장 흔한 이탈 원인이라 담는 단계에서 미리 보여 준다.
     *
     * <p>{@code selectedCartItemIds}는 화면의 체크 상태다. 넘기지 않으면 <b>구매 가능한 항목 전체</b>가
     * 선택된 것으로 보고 계산한다(화면에 처음 들어왔을 때의 상태). 담은 뒤 마감·품절된 항목은
     * 요청에 담겨 있어도 선택에서 빠진다 — 전체 선택도 이 항목은 건너뛴다.
     *
     * <p>구매 불가 항목을 <b>목록에서 지우지는 않는다.</b> 담아 둔 것은 사용자의 기억이자 의도이고,
     * 말없이 사라지면 합계가 줄어든 이유도 알 수 없다. 삭제는 사용자가 결정한다.
     */
    @Transactional(readOnly = true)
    public CartDto.CartListResponse getCart(String username, List<Long> selectedCartItemIds) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Cart> carts = sortedByRecentlyAdded(cartRepository.findAllByUser(user));
        Set<Long> selectedIds = resolveSelection(carts, selectedCartItemIds);

        List<CartDto.CartGroup> groups = buildGroups(carts, selectedIds);
        CartSummaryData summaryData = calculateSummary(carts, selectedIds);

        CartDto.CartSummary summary = CartDto.CartSummary.builder()
                .regularTotal(summaryData.regularTotal)
                .saleTotal(summaryData.saleTotal)
                .discountTotal(summaryData.discountTotal)
                .deliveryFeeTotal(summaryData.deliveryFeeTotal)
                .finalTotal(summaryData.finalTotal)
                .selectedCount(selectedIds.size())
                .selectableCount((int) carts.stream().filter(cart -> unavailableReason(cart) == null).count())
                .totalCount(carts.size())
                .build();

        return CartDto.CartListResponse.builder()
                .groups(groups)
                .summary(summary)
                .build();
    }

    /**
     * 장바구니 수정 — 수량 변경과 옵션 변경이 같은 경로로 들어온다.
     *
     * <p>담은 뒤 마감·품절된 항목은 수정하지 못한다. 화면이 그 행의 수량 스테퍼와 옵션 변경
     * 버튼을 모두 비활성으로 그리기 때문이고(못 사는 상품에 조작할 컨트롤을 남겨 두지 않는다),
     * 주소로 직접 호출하는 경로가 남으므로 서버도 같은 선에서 막는다.
     *
     * <p>{@code selectedCartItemIds}는 조회와 같은 뜻이다 — 수량을 하나 올렸을 때 하단 요약이
     * <b>체크된 항목만</b>으로 다시 계산돼야 화면이 목록을 다시 부르지 않는다. 생략하면 구매
     * 가능한 항목 전체를 선택한 것으로 본다.
     */
    @Transactional
    public CartDto.UpdateCartResponse updateCart(String username, Long cartItemId, CartDto.UpdateCartRequest request,
                                                 List<Long> selectedCartItemIds) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Cart cart = cartRepository.findByIdAndUser(cartItemId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (request.getVariantId() == null && request.getQuantity() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "변경할 항목이 없습니다.");
        }

        requirePurchasable(cart.getVariant());

        ProductVariant targetVariant = cart.getVariant();
        if (request.getVariantId() != null && !request.getVariantId().equals(cart.getVariant().getVariantId())) {
            targetVariant = productVariantRepository.findByVariantId(request.getVariantId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
            requirePurchasable(targetVariant);
        }

        int requestedQuantity = request.getQuantity() != null ? request.getQuantity() : cart.getQuantity();
        int availableStock = targetVariant.getStock() != null ? targetVariant.getStock() : 0;

        Cart mergedTarget = null;
        if (!targetVariant.getVariantId().equals(cart.getVariant().getVariantId())) {
            mergedTarget = cartRepository.findByUserAndVariant(user, targetVariant).orElse(null);
        }

        if (mergedTarget != null && !mergedTarget.getId().equals(cart.getId())) {
            int finalQuantity = mergedTarget.getQuantity() + requestedQuantity;
            requireWithinQuantityLimit(finalQuantity);
            if (finalQuantity > availableStock) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "재고가 부족합니다");
            }
            mergedTarget.updateQuantity(finalQuantity);
            cartRepository.delete(cart);
            cart = cartRepository.save(mergedTarget);
        } else {
            requireWithinQuantityLimit(requestedQuantity);
            if (requestedQuantity > availableStock) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "재고가 부족합니다");
            }
            cart.updateQuantity(requestedQuantity);
            cart.updateVariant(targetVariant);
            cart = cartRepository.save(cart);
        }

        List<Cart> carts = cartRepository.findAllByUser(user);
        Set<Long> selectedIds =
                resolveSelection(carts, carryOverSelection(selectedCartItemIds, cartItemId, cart.getId()));
        CartDto.UpdateSummary summary = toUpdateSummary(calculateSummary(carts, selectedIds));

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
     *
     * <p>{@code selectedCartItemIds}는 삭제 후 요약을 계산할 <b>화면의 체크 상태</b>다. 지워진
     * 항목은 알아서 빠지므로 화면은 삭제 전 목록을 그대로 넘겨도 된다. 생략하면 남은 항목 중
     * 구매 가능한 것 전체를 선택한 것으로 본다.
     */
    @Transactional
    public CartDto.DeleteCartResponse deleteCart(String username, List<Long> cartItemIds,
                                                 List<Long> selectedCartItemIds) {
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
            // 선택 삭제: 본인 소유 검증 후 삭제. 같은 ID가 두 번 실려 와도 한 번 지운 것으로 본다
            List<Long> requestedIds = cartItemIds.stream().distinct().toList();
            List<Cart> toDelete = cartRepository.findByIdInAndUser(requestedIds, user);
            if (toDelete.size() != requestedIds.size()) {
                Set<Long> foundIds = toDelete.stream().map(Cart::getId).collect(Collectors.toSet());
                List<Long> unauthorized = requestedIds.stream().filter(id -> !foundIds.contains(id)).toList();
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "장바구니 항목을 찾을 수 없거나 삭제 권한이 없습니다. cartItemIds: " + unauthorized);
            }
            deletedIds = toDelete.stream().map(Cart::getId).toList();
            cartRepository.deleteAllByIdInBatch(deletedIds);
            int count = deletedIds.size();
            message = count == 1 ? "1개 항목이 삭제되었습니다." : count + "개 항목이 삭제되었습니다.";
        }

        List<Cart> remainingCarts = cartRepository.findAllByUser(user);
        CartDto.UpdateSummary summary = toUpdateSummary(
                calculateSummary(remainingCarts, resolveSelection(remainingCarts, selectedCartItemIds)));

        return CartDto.DeleteCartResponse.builder()
                .deletedCartItemIds(deletedIds)
                .deletedCount(deletedIds.size())
                .message(message)
                .summary(summary)
                .build();
    }

    /**
     * 팔로우한 쇼룸의 공구 — 목록 아래 가로 스크롤 영역 (C8).
     *
     * <p>무료배송 조건이 공구 단위라 "○○원 더 담으면 무료"를 본 직후 <b>같은 쇼룸의 다른 상품</b>이
     * 바로 아래 있으면 실제로 도움이 된다. 그래서 장바구니에 이미 있고 무료배송까지 조금 남은
     * 쇼룸의 상품을 앞으로 당긴다({@code helpsFreeShipping}).
     *
     * <p>목록과 따로 부르는 이유는 이 영역이 <b>담긴 상품이 있을 때만</b> 그려지고, 목록을 다시
     * 부르는 조작(선택 토글·수량 변경)마다 같이 계산될 이유가 없기 때문이다.
     *
     * <p>D-day 배지는 아직 내려보내지 않는다 — 공구 게시물(마감 시각)이 없어 그룹 머리의 D-day와
     * 사정이 같다.
     */
    @Transactional(readOnly = true)
    public CartDto.RecommendationListResponse getRecommendations(String username, Integer limit) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        int size = resolveRecommendationLimit(limit);

        List<Long> creatorIds = creatorFollowRepository.findCreatorIdsByUserId(user.getId());
        if (creatorIds.isEmpty()) {
            return CartDto.RecommendationListResponse.builder().products(List.of()).build();
        }

        List<Cart> carts = cartRepository.findAllByUser(user);
        Set<Long> nearFreeShippingMarkets = marketsShortOfFreeShipping(carts);

        List<Product> candidates = productRepository.findOngoingGroupBuyProductsOfShowrooms(
                creatorIds,
                heldProductIds(carts),
                PageRequest.of(0, size * RECOMMENDATION_CANDIDATE_MULTIPLIER)
        );

        List<CartDto.RecommendedProduct> products = candidates.stream()
                // false가 앞이므로 "무료배송까지 조금 남은 쇼룸"이 먼저 온다. 정렬이 안정적이라
                // 그 안에서는 DB 정렬(추천 상품 · 최신순)이 그대로 유지된다.
                .sorted(Comparator.comparing(product -> !helpsFreeShipping(product, nearFreeShippingMarkets)))
                .limit(size)
                .map(product -> toRecommendedProduct(product, nearFreeShippingMarkets))
                .toList();

        return CartDto.RecommendationListResponse.builder().products(products).build();
    }

    private int resolveRecommendationLimit(Integer limit) {
        if (limit == null) {
            return CartDto.DEFAULT_RECOMMENDATION_LIMIT;
        }
        return Math.max(1, Math.min(limit, CartDto.MAX_RECOMMENDATION_LIMIT));
    }

    /**
     * 이미 담아 둔 상품 ID — 추천에서 뺀다.
     *
     * <p>비어 있으면 {@code NOT IN ()}이 되지 않도록 어떤 상품과도 겹치지 않는 값을 하나 넣는다.
     */
    private List<Long> heldProductIds(List<Cart> carts) {
        List<Long> ids = carts.stream()
                .map(cart -> cart.getVariant().getProduct().getProductId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return ids.isEmpty() ? List.of(0L) : ids;
    }

    /**
     * 무료배송까지 조금 남은 장바구니 그룹의 쇼룸.
     *
     * <p>여기서는 화면의 체크 상태를 받지 않으므로 목록 진입 시와 같은 기준(구매 가능한 항목 전체)으로
     * 본다 — 이 영역은 곁다리라 체크를 옮길 때마다 순서가 흔들릴 이유가 없다.
     */
    private Set<Long> marketsShortOfFreeShipping(List<Cart> carts) {
        Set<Long> selectedIds = resolveSelection(carts, null);

        Map<Long, Long> selectedTotalByMarket = new LinkedHashMap<>();
        Map<Long, Integer> thresholdByMarket = new HashMap<>();
        for (Cart cart : carts) {
            if (!selectedIds.contains(cart.getId())) {
                continue;
            }
            Long marketId = marketIdOf(cart);
            selectedTotalByMarket.merge(marketId, lineSaleTotal(cart), Long::sum);
            Market market = marketOf(cart);
            if (market != null && market.getFreeShippingThreshold() != null) {
                thresholdByMarket.put(marketId, market.getFreeShippingThreshold());
            }
        }

        Set<Long> markets = new HashSet<>();
        selectedTotalByMarket.forEach((marketId, total) -> {
            Integer threshold = thresholdByMarket.get(marketId);
            if (threshold != null && total < threshold) {
                markets.add(marketId);
            }
        });
        return markets;
    }

    private boolean helpsFreeShipping(Product product, Set<Long> nearFreeShippingMarkets) {
        Market market = product.getMarket();
        return market != null && nearFreeShippingMarkets.contains(market.getId());
    }

    private CartDto.RecommendedProduct toRecommendedProduct(Product product, Set<Long> nearFreeShippingMarkets) {
        Market market = product.getMarket();
        return CartDto.RecommendedProduct.builder()
                .productId(product.getProductId())
                .productName(product.getName())
                .thumbnailUrl(product.getThumbnailUrl())
                .marketId(market != null ? market.getId() : null)
                .marketName(market != null ? market.getMarketName() : null)
                .price(buildPriceInfo(product.getRegularPrice(), product.getSalePrice()))
                .helpsFreeShipping(helpsFreeShipping(product, nearFreeShippingMarkets))
                .build();
    }

    /**
     * 옵션을 바꾸다 다른 줄과 합쳐지면 항목 ID가 갈린다. 원래 줄이 체크돼 있었다면 합쳐진 줄도
     * 체크된 것으로 본다 — 사용자가 옵션만 고쳤을 뿐인데 합계에서 빠지면 금액이 이유 없이 준다.
     */
    private List<Long> carryOverSelection(List<Long> requested, Long originalCartItemId, Long resultCartItemId) {
        if (requested == null || !requested.contains(originalCartItemId) || requested.contains(resultCartItemId)) {
            return requested;
        }
        List<Long> carried = new ArrayList<>(requested);
        carried.add(resultCartItemId);
        return carried;
    }

    // ------------------------------------------------------------------ 그룹 구성

    /**
     * 공구(쇼룸) 단위 묶음.
     *
     * <p>공구 게시물({@code group_buy_post})이 아직 없어 지금의 묶음 키는 상품이 속한 쇼룸(마켓)이다.
     * 배송비가 쇼룸 단위로 매겨져 있어 배송비·무료배송 기준은 이 키로도 정확히 계산되고, 공구 게시물이
     * 들어오면 키만 공구 ID로 바뀐다 — 그래서 응답 모양을 미리 그룹으로 잡았다. 같은 이유로 그룹
     * 머리의 D-day(마감 시각)는 아직 내려보내지 않는다. 대신 그룹 전체가 마감·미진열이면
     * {@code isClosed}로 알려, 화면이 끝난 공구에 D-day 자리를 비워 둘 수 있게 한다.
     */
    private List<CartDto.CartGroup> buildGroups(List<Cart> carts, Set<Long> selectedIds) {
        Map<Long, List<Cart>> byMarket = new LinkedHashMap<>();
        for (Cart cart : carts) {
            byMarket.computeIfAbsent(marketIdOf(cart), key -> new ArrayList<>()).add(cart);
        }

        List<CartDto.CartGroup> groups = new ArrayList<>();
        for (List<Cart> groupCarts : byMarket.values()) {
            Market market = marketOf(groupCarts.get(0));

            List<CartDto.CartItem> items = groupCarts.stream()
                    .map(cart -> toCartItem(cart, selectedIds.contains(cart.getId())))
                    .toList();

            boolean isClosed = groupCarts.stream()
                    .allMatch(cart -> unavailableReason(cart) == CartUnavailableReason.GROUP_BUY_CLOSED);

            groups.add(CartDto.CartGroup.builder()
                    .marketId(market != null ? market.getId() : null)
                    .marketName(market != null ? market.getMarketName() : null)
                    .marketImageUrl(market != null ? market.getMarketImageUrl() : null)
                    .isClosed(isClosed)
                    .items(items)
                    .shipping(buildGroupShipping(market, groupCarts, selectedIds))
                    .build());
        }
        return groups;
    }

    /**
     * 그룹 배송비 — 선택된 항목만으로 계산한다.
     *
     * <p>선택된 것이 하나도 없으면 부과 배송비는 0이고 "○○원 더 담으면 무료"도 내려보내지 않는다.
     * 아무것도 담기지 않은 그룹에 남은 금액을 띄우면 전액을 더 담아야 하는 것처럼 읽힌다.
     */
    private CartDto.GroupShipping buildGroupShipping(Market market, List<Cart> groupCarts, Set<Long> selectedIds) {
        int deliveryFee = market != null && market.getDefaultDeliveryFee() != null
                ? market.getDefaultDeliveryFee()
                : 0;
        Integer threshold = market != null ? market.getFreeShippingThreshold() : null;

        long selectedTotal = groupCarts.stream()
                .filter(cart -> selectedIds.contains(cart.getId()))
                .mapToLong(this::lineSaleTotal)
                .sum();

        boolean hasSelectedItems = groupCarts.stream().anyMatch(cart -> selectedIds.contains(cart.getId()));
        boolean isFreeShipping = hasSelectedItems && threshold != null && selectedTotal >= threshold;

        Long amountToFreeShipping = null;
        if (hasSelectedItems && threshold != null && selectedTotal < threshold) {
            amountToFreeShipping = threshold - selectedTotal;
        }

        return CartDto.GroupShipping.builder()
                .deliveryFee(deliveryFee)
                .freeShippingThreshold(threshold)
                .hasSelectedItems(hasSelectedItems)
                .selectedProductTotal(selectedTotal)
                .chargedDeliveryFee(hasSelectedItems && !isFreeShipping ? deliveryFee : 0)
                .isFreeShipping(isFreeShipping)
                .amountToFreeShipping(amountToFreeShipping)
                .build();
    }

    // ------------------------------------------------------------------ 선택·구매 가능 여부

    /**
     * 화면의 체크 상태를 서버 계산에 쓸 수 있는 형태로 정리한다.
     *
     * <p>{@code requested}가 null이면 구매 가능한 항목 전체를 선택으로 본다. 값이 있으면 그중
     * 실제로 담겨 있고 살 수 있는 항목만 남긴다 — 마감·품절 항목이 요청에 섞여 들어와도
     * 합계에 들어가지 않는다.
     */
    private Set<Long> resolveSelection(List<Cart> carts, Collection<Long> requested) {
        Set<Long> purchasable = carts.stream()
                .filter(cart -> unavailableReason(cart) == null)
                .map(Cart::getId)
                .collect(Collectors.toCollection(HashSet::new));

        if (requested == null) {
            return purchasable;
        }
        purchasable.retainAll(new HashSet<>(requested));
        return purchasable;
    }

    /**
     * 담은 뒤 살 수 없게 된 사유. 살 수 있으면 null이다.
     *
     * <p>마감을 품절보다 먼저 본다 — 공구가 끝났으면 재고가 남아 있어도 살 수 없고, 이때는
     * 다른 옵션으로 이어질 길도 없어 사유를 "품절"로 말하면 사용자를 헛걸음시킨다.
     */
    private CartUnavailableReason unavailableReason(Cart cart) {
        return unavailableReason(cart.getVariant());
    }

    private CartUnavailableReason unavailableReason(ProductVariant variant) {
        Product product = variant.getProduct();

        ProductGroupBuyStatus groupBuyStatus = product.getGroupBuyStatus();
        boolean isGroupBuyConnected = groupBuyStatus != null && groupBuyStatus.isConnected();
        ProductDisplayStatus displayStatus = product.getDisplayStatus();
        boolean isDisplayed = displayStatus != null && displayStatus.isVisible();
        if (!isGroupBuyConnected || !isDisplayed) {
            return CartUnavailableReason.GROUP_BUY_CLOSED;
        }

        int stock = variant.getStock() != null ? variant.getStock() : 0;
        if (Boolean.TRUE.equals(product.getIsOutOfStockForced()) || stock <= 0) {
            return CartUnavailableReason.SOLD_OUT;
        }
        return null;
    }

    private void requirePurchasable(ProductVariant variant) {
        CartUnavailableReason reason = unavailableReason(variant);
        if (reason != null) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_PURCHASABLE, reason.getMessage());
        }
    }

    private void requireWithinQuantityLimit(int quantity) {
        if (quantity > CartDto.MAX_QUANTITY) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "수량은 " + CartDto.MAX_QUANTITY + "개까지 담을 수 있습니다.");
        }
    }

    // ------------------------------------------------------------------ 매핑

    private CartDto.CartItem toCartItem(Cart cart, boolean isSelected) {
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
                .deliveryFee(market != null && market.getDefaultDeliveryFee() != null ? market.getDefaultDeliveryFee() : 0)
                .stock(stockInfo)
                .availability(buildAvailability(unavailableReason(cart)))
                .isSelected(isSelected)
                .build();
    }

    private CartDto.Availability buildAvailability(CartUnavailableReason reason) {
        return CartDto.Availability.builder()
                .isPurchasable(reason == null)
                .reason(reason != null ? reason.name() : null)
                .label(reason != null ? reason.getLabel() : null)
                .message(reason != null ? reason.getMessage() : null)
                .build();
    }

    private Cart addCartForUser(Users user, CartDto.AddCartRequest request) {
        ProductVariant variant = productVariantRepository.findByVariantId(request.getVariantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));

        requirePurchasable(variant);

        int stock = variant.getStock() != null ? variant.getStock() : 0;
        int addQuantity = request.getQuantity();

        Cart cart = cartRepository.findByUserAndVariant(user, variant).orElse(null);
        int finalQuantity = cart != null ? cart.getQuantity() + addQuantity : addQuantity;

        requireWithinQuantityLimit(finalQuantity);
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

    /**
     * 합계 — <b>선택된 항목만</b> 계산한다 (C8 §선택 합산).
     *
     * <p>하단 요약과 [주문하기]가 같은 값을 써야 하므로 버튼 라벨에 들어가는 금액도 여기서 나온다.
     * 배송비는 그룹(쇼룸)마다 따로 매겨지고, 그 그룹에서 선택된 것이 없으면 부과하지 않는다.
     */
    private CartSummaryData calculateSummary(List<Cart> carts, Set<Long> selectedIds) {
        long regularTotal = 0L;
        long saleTotal = 0L;

        Map<Long, MarketShippingAccumulator> shippingByMarket = new HashMap<>();

        for (Cart cart : carts) {
            if (!selectedIds.contains(cart.getId())) {
                continue;
            }

            ProductVariant variant = cart.getVariant();
            int quantity = cart.getQuantity() != null ? cart.getQuantity() : 0;
            long regular = variant.getRegularPrice() != null ? variant.getRegularPrice() : 0;
            long sale = variant.getSalePrice() != null ? variant.getSalePrice() : 0;

            regularTotal += regular * quantity;
            saleTotal += sale * quantity;

            Market market = marketOf(cart);

            MarketShippingAccumulator acc = shippingByMarket.computeIfAbsent(
                    marketIdOf(cart),
                    key -> new MarketShippingAccumulator()
            );
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

    private CartDto.UpdateSummary toUpdateSummary(CartSummaryData data) {
        return CartDto.UpdateSummary.builder()
                .regularTotal(data.regularTotal)
                .saleTotal(data.saleTotal)
                .discountTotal(data.discountTotal)
                .deliveryFeeTotal(data.deliveryFeeTotal)
                .totalProductPrice(data.saleTotal)
                .expectedTotalPrice(data.finalTotal)
                .build();
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

    /** 최근에 담은 항목이 위로 온다 — 방금 담은 상품을 찾으러 스크롤하지 않게 한다 */
    private List<Cart> sortedByRecentlyAdded(List<Cart> carts) {
        return carts.stream()
                .sorted(Comparator.comparing(Cart::getId, Comparator.reverseOrder()))
                .toList();
    }

    private long lineSaleTotal(Cart cart) {
        long sale = cart.getVariant().getSalePrice() != null ? cart.getVariant().getSalePrice() : 0;
        int quantity = cart.getQuantity() != null ? cart.getQuantity() : 0;
        return sale * quantity;
    }

    private Market marketOf(Cart cart) {
        return cart.getVariant().getProduct().getMarket();
    }

    /** 마켓이 비어 있는 데이터도 한 그룹으로 모이도록 0을 쓴다 — 합계에서 빠지지 않게 하기 위한 것이다 */
    private Long marketIdOf(Cart cart) {
        Market market = marketOf(cart);
        return market != null ? market.getId() : 0L;
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
