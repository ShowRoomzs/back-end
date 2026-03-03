package showroomz.api.common.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.common.market.dto.MarketRecommendationResponse;
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

import java.util.ArrayList;
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

    private static final int MAX_IMAGE_COUNT = 3;

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

        // Batch Fetching: 마켓별 대표 상품 이미지 최대 3개 추출
        // 상품 0개 마켓은 map에 포함하지 않음 (기획안 4-2: 추천 알고리즘에서 제외)
        Map<Long, List<String>> marketImageUrlsMap = buildMarketRepresentativeImageUrls(marketIds);
        List<Long> validMarketIds = marketIds.stream()
                .filter(marketImageUrlsMap::containsKey)
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
                        marketImageUrlsMap.get(market.getId())))
                .toList();

        return MarketRecommendationResponse.of(items, marketPage);
    }

    /**
     * 마켓별 대표 상품 이미지 URL 최대 3개 추출 (Batch Fetching)
     * - 우선순위: isRecommended=true 우선 → isRecommended=false 최신순
     * - 상품 이미지: ProductImage order=0 또는 Product.thumbnailUrl
     * - 3개 미만이면 있는 만큼만, 3개 이상이면 3개만
     * - 상품 0개 마켓은 제외 (기획안 4-2: 추천 알고리즘에서 제외)
     */
    private Map<Long, List<String>> buildMarketRepresentativeImageUrls(List<Long> marketIds) {
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

        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (Long marketId : marketIds) {
            List<Product> marketProducts = productsByMarket.getOrDefault(marketId, List.of());
            if (marketProducts.isEmpty()) {
                continue; // 상품 0개 마켓 제외 (기획안 4-2)
            }
            // 이미 정렬됨: isRecommended DESC, createdAt DESC
            List<String> urls = new ArrayList<>();
            for (Product p : marketProducts) {
                if (urls.size() >= MAX_IMAGE_COUNT) break;
                String url = productImageMap.containsKey(p.getProductId())
                        ? productImageMap.get(p.getProductId())
                        : (p.getThumbnailUrl() != null ? p.getThumbnailUrl() : "");
                if (url != null && !url.isBlank()) {
                    urls.add(url);
                }
            }
            result.put(marketId, urls);
        }
        return result;
    }

    private MarketRecommendationResponse.MarketRecommendationItem toRecommendationItem(
            Market market, boolean isFollowing, List<String> representativeImageUrls) {
        long followCount = marketFollowRepository.countByMarket(market);

        return MarketRecommendationResponse.MarketRecommendationItem.builder()
                .marketId(market.getId())
                .marketName(market.getMarketName())
                .sellerId(market.getSeller() != null ? market.getSeller().getId() : null)
                .marketImageUrl(market.getMarketImageUrl())
                .representativeImageUrls(representativeImageUrls != null ? representativeImageUrls : List.of())
                .marketDescription(market.getMarketDescription())
                .marketUrl(market.getMarketUrl())
                .shopType(market.getShopType())
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
}
