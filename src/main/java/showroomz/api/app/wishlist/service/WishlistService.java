package showroomz.api.app.wishlist.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.category.service.CategoryHierarchyService;
import showroomz.domain.wishlist.entitiy.Wishlist;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.dto.PageResponse;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryHierarchyService categoryHierarchyService;

    /**
     * 위시리스트 추가 (멱등성 보장)
     * 이미 존재하면 저장하지 않고 성공(void) 리턴
     */
    @Transactional
    public void addWishlist(String username, Long productId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 이미 위시리스트에 존재하면 아무것도 하지 않고 종료 (200 OK)
        if (wishlistRepository.existsByUserAndProduct(user, product)) {
            return;
        }

        Wishlist wishlist = new Wishlist(user, product);
        wishlistRepository.save(wishlist);
    }

    /**
     * 위시리스트 삭제 (멱등성 보장)
     * 존재하지 않으면 아무것도 하지 않고 성공(void) 리턴
     */
    @Transactional
    public void deleteWishlist(String username, Long productId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 위시리스트에 존재할 때만 삭제 수행
        wishlistRepository.findByUserAndProduct(user, product)
                .ifPresent(wishlistRepository::delete);
    }

    /**
     * 위시리스트 여부 확인
     * @param userId 사용자 ID
     * @param productId 상품 ID
     * @return 위시리스트에 존재하면 true, 아니면 false
     */
    public boolean isWished(Long userId, Long productId) {
        if (userId == null || productId == null) {
            return false;
        }

        Users user = userRepository.findById(userId).orElse(null);
        Product product = productRepository.findById(productId).orElse(null);

        if (user == null || product == null) {
            return false;
        }
        return wishlistRepository.existsByUserAndProduct(user, product);
    }

    /**
     * 위시리스트 조회 (페이징)
     * @param username 사용자명
     * @param page 페이지 번호 (1부터 시작)
     * @param limit 페이지당 항목 수
     * @param categoryId 카테고리 ID (선택)
     * @return 위시리스트 상품 목록
     */
    public PageResponse<ProductDto.ProductItem> getWishlist(
            String username,
            Integer page,
            Integer limit,
            Long categoryId
    ) {
        // 사용자 조회
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 페이징 설정 (page는 1부터 시작)
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (limit != null && limit > 0) ? limit : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        List<Long> categoryIds = null;
        if (categoryId != null) {
            try {
                categoryIds = categoryHierarchyService.getAllSubCategoryIds(categoryId);
            } catch (Exception e) {
                categoryIds = List.of(categoryId);
            }
        }

        // 위시리스트 조회 (Fetch Join으로 N+1 문제 방지)
        Page<Wishlist> wishlistPage = wishlistRepository.findByUserWithProduct(
                user.getId(),
                categoryIds,
                pageable
        );

        // Product 엔티티 추출
        List<Product> products = wishlistPage.getContent().stream()
                .map(Wishlist::getProduct)
                .collect(Collectors.toList());

        // ProductItem DTO 변환 (모든 상품의 isWished는 true)
        List<ProductDto.ProductItem> productItems = products.stream()
                .map(product -> convertToProductItem(product, user))
                .collect(Collectors.toList());

        return new PageResponse<>(productItems, wishlistPage);
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

        // 찜 수 조회
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
                .isWished(true) // 위시리스트 조회 결과이므로 항상 true
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
}
