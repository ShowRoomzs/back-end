package showroomz.api.app.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.recommendation.DTO.RecommendationDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketFollowRepository;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductGender;
import showroomz.domain.wishlist.repository.WishlistRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final ProductRepository productRepository;
    private final MarketRepository marketRepository;
    private final MarketFollowRepository marketFollowRepository;
    private final UserRepository userRepository;
    private final WishlistRepository wishlistRepository;

    /**
     * 상품 추천 조회
     */
    public RecommendationDto.ProductRecommendationResponse getRecommendedProducts(
            Long categoryId,
            Integer page,
            Integer limit
    ) {
        // 사용자 조회 (게스트면 null)
        Users user = resolveCurrentUser();

        // 사용자 성별 변환
        ProductGender userGender = null;
        if (user != null && user.getGender() != null) {
            try {
                userGender = ProductGender.valueOf(user.getGender().toUpperCase());
            } catch (IllegalArgumentException e) {
                // 성별이 유효하지 않은 경우 null로 처리
            }
        }

        // 페이징 설정
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (limit != null && limit > 0) ? limit : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        // 추천 상품 조회
        Page<Product> productPage = productRepository.findRecommendedProducts(
                categoryId, userGender, pageable);

        // ProductItem DTO 변환
        List<ProductDto.ProductItem> productItems = productPage.getContent().stream()
                .map(product -> convertToProductItem(product, user))
                .collect(Collectors.toList());

        // PageInfo 생성
        ProductDto.PageInfo pageInfo = ProductDto.PageInfo.builder()
                .currentPage(productPage.getNumber() + 1)
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .isLast(productPage.isLast())
                .hasNext(productPage.hasNext())
                .build();

        return RecommendationDto.ProductRecommendationResponse.builder()
                .products(productItems)
                .pageInfo(pageInfo)
                .build();
    }

    /**
     * 마켓 추천 조회
     */
    public RecommendationDto.MarketRecommendationResponse getRecommendedMarkets(
            Long categoryId,
            Integer page,
            Integer limit
    ) {
        // 사용자 조회 (게스트면 null)
        Users user = resolveCurrentUser();

        // 페이징 설정
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (limit != null && limit > 0) ? limit : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        // 추천 마켓 조회
        Page<showroomz.api.app.market.DTO.MarketListResponse> marketPage = marketRepository.findRecommendedMarkets(
                categoryId, SellerStatus.APPROVED, pageable);

        // MarketRecommendationItem DTO 변환
        List<RecommendationDto.MarketRecommendationItem> marketItems = marketPage.getContent().stream()
                .map(marketResponse -> {
                    // 마켓 엔티티 조회 (팔로워 수, 팔로우 여부 확인용)
                    Market market = marketRepository.findById(marketResponse.getShopId())
                            .orElse(null);

                    Long followerCount = 0L;
                    Boolean isFollowing = false;

                    if (market != null) {
                        followerCount = marketFollowRepository.countByMarket(market);
                        if (user != null) {
                            isFollowing = marketFollowRepository.existsByUserAndMarket(user, market);
                        }
                    }

                    return RecommendationDto.MarketRecommendationItem.builder()
                            .marketId(marketResponse.getShopId())
                            .marketName(marketResponse.getShopName())
                            .marketImageUrl(marketResponse.getShopImageUrl())
                            .mainCategoryId(marketResponse.getMainCategoryId())
                            .mainCategoryName(marketResponse.getMainCategoryName())
                            .followerCount(followerCount)
                            .isFollowing(isFollowing)
                            .build();
                })
                .collect(Collectors.toList());

        // PageInfo 생성
        ProductDto.PageInfo pageInfo = ProductDto.PageInfo.builder()
                .currentPage(marketPage.getNumber() + 1)
                .pageSize(marketPage.getSize())
                .totalElements(marketPage.getTotalElements())
                .totalPages(marketPage.getTotalPages())
                .isLast(marketPage.isLast())
                .hasNext(marketPage.hasNext())
                .build();

        return RecommendationDto.MarketRecommendationResponse.builder()
                .markets(marketItems)
                .pageInfo(pageInfo)
                .build();
    }

    /**
     * Product 엔티티를 ProductItem DTO로 변환
     */
    private ProductDto.ProductItem convertToProductItem(Product product, Users currentUser) {
        // 가격 정보
        Integer regularPrice = product.getRegularPrice();
        Integer salePrice = product.getSalePrice();
        Integer discountRate = calculateDiscountRate(regularPrice, salePrice);
        ProductDto.PriceInfo priceInfo = ProductDto.PriceInfo.builder()
                .regularPrice(regularPrice)
                .discountRate(discountRate)
                .salePrice(salePrice)
                .maxBenefitPrice(salePrice)
                .build();

        // 찜 여부 및 찜 수 확인
        Boolean isWished = false;
        if (currentUser != null) {
            isWished = wishlistRepository.existsByUserAndProduct(currentUser, product);
        }
        Long wishCount = wishlistRepository.countByProduct(product);
        Long reviewCount = 0L; // TODO: MVP 제외, 추후 리뷰 집계 연동

        return ProductDto.ProductItem.builder()
                .id(product.getProductId())
                .productNumber(product.getProductNumber())
                .name(product.getName())
                .sellerProductCode(product.getSellerProductCode())
                .representativeImageUrl(product.getThumbnailUrl())
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
                .status(buildStockStatus(product))
                .likeCount(0L) // TODO: 실제 좋아요 수 조회
                .wishCount(wishCount)
                .reviewCount(reviewCount)
                .isWished(isWished)
                .build();
    }

    /**
     * 재고 상태 생성
     */
    private ProductDto.StockStatus buildStockStatus(Product product) {
        boolean isOutOfStockForced = Boolean.TRUE.equals(product.getIsOutOfStockForced());
        boolean hasStock = product.getVariants().stream()
                .anyMatch(variant -> variant.getStock() != null && variant.getStock() > 0);
        boolean isOutOfStock = isOutOfStockForced || !hasStock;

        return ProductDto.StockStatus.builder()
                .isOutOfStock(isOutOfStock)
                .isOutOfStockForced(isOutOfStockForced)
                .build();
    }

    /**
     * 통합 추천 조회 (마켓 + 상품)
     */
    public RecommendationDto.UnifiedRecommendationResponse getUnifiedRecommendations(
            Long categoryId,
            Integer page,
            Integer limit
    ) {
        // 사용자 조회 (게스트면 null)
        Users user = resolveCurrentUser();

        // 사용자 성별 변환
        ProductGender userGender = null;
        if (user != null && user.getGender() != null) {
            try {
                userGender = ProductGender.valueOf(user.getGender().toUpperCase());
            } catch (IllegalArgumentException e) {
                // 성별이 유효하지 않은 경우 null로 처리
            }
        }

        // 페이징 설정 (상품용)
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (limit != null && limit > 0) ? limit : 20;
        Pageable productPageable = PageRequest.of(pageNumber, pageSize);

        // 마켓 조회 (페이징 없이 최대 20개)
        Pageable marketPageable = PageRequest.of(0, 20);
        Page<showroomz.api.app.market.DTO.MarketListResponse> marketPage = marketRepository.findRecommendedMarkets(
                categoryId, SellerStatus.APPROVED, marketPageable);

        // 마켓 추천 항목 변환 (대표 상품 3개 포함)
        List<RecommendationDto.MarketRecommendationItem> marketItems = marketPage.getContent().stream()
                .map(marketResponse -> {
                    // 마켓 엔티티 조회
                    Market market = marketRepository.findById(marketResponse.getShopId())
                            .orElse(null);

                    Long followerCount = 0L;
                    Boolean isFollowing = false;
                    List<ProductDto.ProductItem> representativeProducts = List.of();

                    if (market != null) {
                        followerCount = marketFollowRepository.countByMarket(market);
                        if (user != null) {
                            isFollowing = marketFollowRepository.existsByUserAndMarket(user, market);
                        }

                        // 대표 상품 3개 조회
                        Pageable top3Pageable = PageRequest.of(0, 3);
                        List<Product> top3Products = productRepository.findTop3RepresentativeProductsByMarket(
                                market.getId(), categoryId, top3Pageable);
                        representativeProducts = top3Products.stream()
                                .map(product -> convertToProductItem(product, user))
                                .collect(Collectors.toList());
                    }

                    return RecommendationDto.MarketRecommendationItem.builder()
                            .marketId(marketResponse.getShopId())
                            .marketName(marketResponse.getShopName())
                            .marketImageUrl(marketResponse.getShopImageUrl())
                            .mainCategoryId(marketResponse.getMainCategoryId())
                            .mainCategoryName(marketResponse.getMainCategoryName())
                            .followerCount(followerCount)
                            .isFollowing(isFollowing)
                            .representativeProducts(representativeProducts)
                            .build();
                })
                .collect(Collectors.toList());

        // 추천 상품 조회
        Page<Product> productPage = productRepository.findRecommendedProducts(
                categoryId, userGender, productPageable);

        // ProductItem DTO 변환
        List<ProductDto.ProductItem> productItems = productPage.getContent().stream()
                .map(product -> convertToProductItem(product, user))
                .collect(Collectors.toList());

        // PageInfo 생성 (상품용)
        ProductDto.PageInfo pageInfo = ProductDto.PageInfo.builder()
                .currentPage(productPage.getNumber() + 1)
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .isLast(productPage.isLast())
                .hasNext(productPage.hasNext())
                .build();

        return RecommendationDto.UnifiedRecommendationResponse.builder()
                .recommendedMarkets(marketItems)
                .recommendedProducts(productItems)
                .pageInfo(pageInfo)
                .build();
    }

    /**
     * 할인율 계산
     */
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

    private Users resolveCurrentUser() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof User userPrincipal) {
                return userRepository.findByUsername(userPrincipal.getUsername()).orElse(null);
            }
        } catch (Exception ignored) {
            // guest user
        }
        return null;
    }
}
