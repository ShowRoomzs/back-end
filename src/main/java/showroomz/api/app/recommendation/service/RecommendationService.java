package showroomz.api.app.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.global.dto.PageResponse;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductGender;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.domain.category.service.CategoryHierarchyService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final WishlistRepository wishlistRepository;
    private final CategoryHierarchyService categoryHierarchyService;

    /**
     * 추천 상품 조회 (isRecommended=true만)
     * content + pageInfo 공통 응답 형식
     */
    public PageResponse<ProductDto.ProductItem> getRecommendedProducts(
            Long categoryId,
            Integer page,
            Integer limit
    ) {
        Users user = resolveCurrentUser();
        ProductGender userGender = resolveUserGender(user);

        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (limit != null && limit > 0) ? limit : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        List<Long> categoryIds = resolveCategoryIds(categoryId);

        Page<Product> productPage = productRepository.findRecommendedProducts(
                categoryIds, userGender, pageable);

        List<ProductDto.ProductItem> productItems = productPage.getContent().stream()
                .map(product -> convertToProductItem(product, user))
                .collect(Collectors.toList());

        return new PageResponse<>(productItems, productPage);
    }

    private ProductGender resolveUserGender(Users user) {
        if (user == null || user.getGender() == null) {
            return null;
        }
        try {
            return ProductGender.valueOf(user.getGender().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
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
            if (principal instanceof UserPrincipal userPrincipal) {
                return userRepository.findByUsername(userPrincipal.getUsername()).orElse(null);
            }
        } catch (Exception ignored) {
            // guest user
        }
        return null;
    }

    private List<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        try {
            return categoryHierarchyService.getAllSubCategoryIds(categoryId);
        } catch (Exception e) {
            return List.of(categoryId);
        }
    }
}
