package showroomz.api.common.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.domain.market.type.ShopType;
import showroomz.api.common.market.dto.MarketRecommendationResponse;
import showroomz.api.common.market.dto.PopularProductResponse;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketFollowRepository;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.api.app.market.DTO.MarketListResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductImage;
import showroomz.domain.product.repository.ProductImageRepository;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.domain.review.repository.ReviewRepository;
import showroomz.domain.wishlist.repository.WishlistRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonMarketService {

    private final MarketRepository marketRepository;
    private final MarketFollowRepository marketFollowRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final WishlistRepository wishlistRepository;
    private final ReviewRepository reviewRepository;
    private final ProductVariantRepository productVariantRepository;

    private static final int MAX_IMAGE_COUNT = 3;
    private static final int POPULAR_PRODUCT_LIMIT = 10;

    /**
     * 추천 마켓(쇼룸) 목록 조회
     * - isRecommended=true인 상품이 있는 마켓 우선 정렬
     * - representativeImageUrls: 추천 상품 우선 → 일반 상품 최신순으로 최대 3개 (3개 미만이면 있는 만큼만)
     */
    public MarketRecommendationResponse getRecommendedMarkets(
            Long categoryId,
            Integer page,
            Integer limit
    ) {
        Users currentUser = resolveCurrentUser();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (limit != null && limit > 0) ? limit : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        Long mainCategoryId = resolveMainCategoryId(categoryId);
        Page<MarketListResponse> marketPage = marketRepository.findRecommendedMarkets(
                mainCategoryId, SellerStatus.APPROVED, pageable);

        List<MarketListResponse> marketList = marketPage.getContent();
        if (marketList.isEmpty()) {
            return MarketRecommendationResponse.of(List.of(), marketPage);
        }

        List<Long> marketIds = marketList.stream().map(MarketListResponse::getShopId).toList();

        // Batch Fetching: 마켓별 대표 상품(Product ID + 이미지 URL) 최대 3개 추출
        // 상품 0개 마켓은 map에 포함하지 않음 (기획안 4-2: 추천 알고리즘에서 제외, 이미지 클릭 시 상세 페이지 이동용 productId 포함)
        Map<Long, List<MarketRecommendationResponse.RepresentativeProduct>> marketRepProductsMap =
                buildMarketRepresentativeProducts(marketIds);
        List<Long> validMarketIds = marketIds.stream()
                .filter(marketRepProductsMap::containsKey)
                .toList();

        if (validMarketIds.isEmpty()) {
            return MarketRecommendationResponse.of(List.of(), marketPage);
        }

        List<Market> markets = marketRepository.findAllById(validMarketIds);
        Map<Long, Market> marketMap = markets.stream().collect(Collectors.toMap(Market::getId, m -> m));

        Set<Long> followingMarketIds = currentUserId != null
                ? marketFollowRepository.findMarketIdsByUserAndMarketIdIn(currentUserId, validMarketIds)
                : Set.of();

        List<MarketRecommendationResponse.MarketRecommendationItem> items = validMarketIds.stream()
                .map(marketMap::get)
                .filter(market -> market != null)
                .map(market -> toRecommendationItem(
                        market,
                        followingMarketIds.contains(market.getId()),
                        marketRepProductsMap.get(market.getId())))
                .toList();

        return MarketRecommendationResponse.of(items, marketPage);
    }

    /**
     * 특정 쇼룸(Market) 인기 상품 Top 10 조회
     * - 정렬: Wishlist 수 DESC → createdAt DESC
     * - 필터: isDisplay=true
     * - 비회원: isWished=false, 회원: SecurityContext 기반 매핑
     */
    public PopularProductResponse getPopularProducts(Long marketId) {
        Users currentUser = resolveCurrentUser();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        List<Product> products = productRepository.findPopularProductsByMarketId(marketId, POPULAR_PRODUCT_LIMIT);
        if (products.isEmpty()) {
            return PopularProductResponse.of(List.of());
        }

        List<Long> productIds = products.stream().map(Product::getProductId).distinct().toList();

        // Batch: 대표 이미지
        Map<Long, String> repImageMap = productImageRepository.findRepresentativeImagesByProductIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(pi -> pi.getProduct().getProductId(), ProductImage::getUrl, (a, b) -> a));

        // Batch: wishCount
        Map<Long, Long> wishCountMap = toMapFromCountQuery(
                wishlistRepository.countWishlistByProductIds(productIds));

        // Batch: reviewCount
        Map<Long, Long> reviewCountMap = toMapFromCountQuery(
                reviewRepository.countByProductIds(productIds));

        // Batch: isWished (로그인 시)
        Set<Long> wishedProductIds = currentUserId != null
                ? wishlistRepository.findProductIdsWishedByUserAndProductIdIn(currentUserId, productIds)
                : Set.of();

        // Batch: 재고 합계 (StockStatus용)
        Map<Long, Long> stockSumMap = toMapFromCountQuery(
                productVariantRepository.sumStockByProductIds(productIds));

        List<ProductDto.ProductItem> items = products.stream()
                .map(p -> toProductItem(p, repImageMap, wishCountMap, reviewCountMap, wishedProductIds, stockSumMap))
                .toList();

        return PopularProductResponse.of(items);
    }

    private Map<Long, Long> toMapFromCountQuery(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row.length >= 2 && row[0] instanceof Long productId && row[1] instanceof Number count) {
                    map.put(productId, count.longValue());
                }
            }
        }
        return map;
    }

    private ProductDto.ProductItem toProductItem(
            Product product,
            Map<Long, String> repImageMap,
            Map<Long, Long> wishCountMap,
            Map<Long, Long> reviewCountMap,
            Set<Long> wishedProductIds,
            Map<Long, Long> stockSumMap) {
        Long productId = product.getProductId();
        Integer regularPrice = product.getRegularPrice();
        Integer salePrice = product.getSalePrice();
        int discountRate = calculateDiscountRate(regularPrice, salePrice);

        ProductDto.PriceInfo priceInfo = ProductDto.PriceInfo.builder()
                .regularPrice(regularPrice)
                .discountRate(discountRate)
                .salePrice(salePrice)
                .maxBenefitPrice(salePrice)
                .build();

        String representativeImageUrl = repImageMap.getOrDefault(productId, product.getThumbnailUrl());
        Long wishCount = wishCountMap.getOrDefault(productId, 0L);
        Long reviewCount = reviewCountMap.getOrDefault(productId, 0L);
        Boolean isWished = wishedProductIds.contains(productId);

        return ProductDto.ProductItem.builder()
                .id(productId)
                .productNumber(product.getProductNumber())
                .name(product.getName())
                .sellerProductCode(product.getSellerProductCode())
                .representativeImageUrl(representativeImageUrl)
                .thumbnailUrl(product.getThumbnailUrl())
                .categoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .marketId(product.getMarket() != null ? product.getMarket().getId() : null)
                .marketName(product.getMarket() != null ? product.getMarket().getMarketName() : null)
                .price(priceInfo)
                .discountRate(discountRate)
                .purchasePrice(product.getPurchasePrice())
                .gender(product.getGender() != null ? product.getGender().name() : null)
                .isDisplay(product.getIsDisplay())
                .isRecommended(product.getIsRecommended())
                .productNotice(product.getProductNotice())
                .description(product.getDescription())
                .tags(product.getTags())
                .deliveryType(product.getDeliveryType())
                .deliveryFee(product.getDeliveryFee())
                .deliveryFreeThreshold(product.getDeliveryFreeThreshold())
                .deliveryEstimatedDays(product.getDeliveryEstimatedDays())
                .createdAt(product.getCreatedAt() != null ? product.getCreatedAt().toString() : null)
                .status(buildStockStatus(product, stockSumMap.getOrDefault(product.getProductId(), 0L)))
                .likeCount(0L)
                .wishCount(wishCount)
                .reviewCount(reviewCount)
                .isWished(isWished)
                .build();
    }

    private ProductDto.StockStatus buildStockStatus(Product product, long totalStock) {
        boolean isOutOfStockForced = Boolean.TRUE.equals(product.getIsOutOfStockForced());
        boolean hasStock = totalStock > 0;
        boolean isOutOfStock = isOutOfStockForced || !hasStock;
        return ProductDto.StockStatus.builder()
                .isOutOfStock(isOutOfStock)
                .isOutOfStockForced(isOutOfStockForced)
                .build();
    }

    private int calculateDiscountRate(Integer regularPrice, Integer salePrice) {
        if (regularPrice == null || salePrice == null || regularPrice <= 0) {
            return 0;
        }
        double rate = ((double) (regularPrice - salePrice) / regularPrice) * 100.0;
        int rounded = (int) Math.round(rate);
        return Math.max(0, Math.min(rounded, 100));
    }

    /**
     * 마켓별 대표 상품(Product ID + 이미지 URL) 최대 3개 추출 (Batch Fetching)
     * - 우선순위: isRecommended=true 우선 → isRecommended=false 최신순
     * - 상품 이미지: ProductImage order=0 또는 Product.thumbnailUrl
     * - 3개 미만이면 있는 만큼만, 3개 이상이면 3개만
     * - 상품 0개 마켓은 제외 (기획안 4-2: 추천 알고리즘에서 제외, 이미지 클릭 시 상세 페이지 이동용 productId 포함)
     */
    private Map<Long, List<MarketRecommendationResponse.RepresentativeProduct>> buildMarketRepresentativeProducts(
            List<Long> marketIds) {
        List<Product> products = productRepository.findByMarketIdInAndIsDisplayTrue(marketIds);
        if (products.isEmpty()) {
            return Map.of();
        }

        List<Long> productIds = products.stream().map(Product::getProductId).distinct().toList();
        List<ProductImage> repImages = productIds.isEmpty() ? List.of()
                : productImageRepository.findRepresentativeImagesByProductIdIn(productIds);
        Map<Long, String> productImageMap = repImages.stream()
                .collect(Collectors.toMap(pi -> pi.getProduct().getProductId(), ProductImage::getUrl, (a, b) -> a));

        Map<Long, List<Product>> productsByMarket = products.stream()
                .collect(Collectors.groupingBy(p -> p.getMarket().getId(), LinkedHashMap::new, Collectors.toList()));

        Map<Long, List<MarketRecommendationResponse.RepresentativeProduct>> result = new LinkedHashMap<>();
        for (Long marketId : marketIds) {
            List<Product> marketProducts = productsByMarket.getOrDefault(marketId, List.of());
            if (marketProducts.isEmpty()) {
                continue; // 상품 0개 마켓 제외 (기획안 4-2)
            }
            // 이미 정렬됨: isRecommended DESC, createdAt DESC
            List<MarketRecommendationResponse.RepresentativeProduct> repProducts = new ArrayList<>();
            for (Product p : marketProducts) {
                if (repProducts.size() >= MAX_IMAGE_COUNT) break;
                String url = productImageMap.containsKey(p.getProductId())
                        ? productImageMap.get(p.getProductId())
                        : (p.getThumbnailUrl() != null ? p.getThumbnailUrl() : "");
                if (url != null && !url.isBlank()) {
                    repProducts.add(MarketRecommendationResponse.RepresentativeProduct.builder()
                            .productId(p.getProductId())
                            .imageUrl(url)
                            .build());
                }
            }
            if (!repProducts.isEmpty()) {
                result.put(marketId, repProducts);
            }
        }
        return result;
    }

    private MarketRecommendationResponse.MarketRecommendationItem toRecommendationItem(
            Market market, boolean isFollowing,
            List<MarketRecommendationResponse.RepresentativeProduct> representativeProducts) {
        long followCount = marketFollowRepository.countByMarket(market);

        return MarketRecommendationResponse.MarketRecommendationItem.builder()
                .marketId(market.getId())
                .marketName(market.getMarketName())
                .sellerId(market.getSeller() != null ? market.getSeller().getId() : null)
                .marketImageUrl(market.getMarketImageUrl())
                .representativeProducts(representativeProducts != null ? representativeProducts : List.of())
                .marketDescription(market.getMarketDescription())
                .marketUrl(market.getMarketUrl())
                .shopType(toShopType(market.getSeller().getRoleType()))
                .followCount(followCount)
                .isFollowing(isFollowing)
                .mainCategoryId(market.getMainCategory() != null ? market.getMainCategory().getCategoryId() : null)
                .build();
    }

    private Users resolveCurrentUser() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserPrincipal userPrincipal) {
                return userRepository.findByUsername(userPrincipal.getUsername()).orElse(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Long resolveMainCategoryId(Long categoryId) {
        return categoryId;
    }

    private ShopType toShopType(RoleType roleType) {
        return roleType == RoleType.CREATOR ? ShopType.SHOWROOM : ShopType.MARKET;
    }
}
