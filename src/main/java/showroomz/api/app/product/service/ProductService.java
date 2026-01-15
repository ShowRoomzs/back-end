package showroomz.api.app.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.api.seller.category.service.CategoryService;
import showroomz.domain.product.type.ProductGender;

import java.util.List;
import java.util.stream.Collectors;

@Service("appProductService")
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    /**
     * 사용자용 상품 검색
     */
    public ProductDto.ProductSearchResponse searchProducts(
            ProductDto.ProductSearchRequest request,
            Integer page,
            Integer limit,
            Long userId // 좋아요 여부 확인용 (null 가능)
    ) {
        // 페이징 설정 (page는 1부터 시작)
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (limit != null && limit > 0) ? limit : 20;

        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        // 카테고리 ID 처리 (하위 카테고리 포함)
        Long categoryId = request.getCategoryId();
        List<Long> categoryIds = null;
        if (categoryId != null) {
            try {
                categoryIds = categoryService.getAllSubCategoryIds(categoryId);
            } catch (Exception e) {
                log.warn("카테고리 조회 실패: {}", categoryId, e);
                categoryIds = List.of(categoryId);
            }
        }

        String keyword = normalize(request.getQ());
        ProductGender gender = parseGender(normalize(request.getGender()));
        String color = normalize(request.getColor());
        String sortType = normalize(request.getSort());

        // 검색 실행
        Page<Product> productPage = productRepository.searchProductsForUser(
                keyword,
                categoryIds,
                request.getMarketId(),
                gender,
                color,
                request.getMinPrice(),
                request.getMaxPrice(),
                sortType,
                pageable
        );

        // DTO 변환
        List<ProductDto.ProductItem> productItems = productPage.getContent().stream()
                .map(product -> convertToProductItem(product, userId))
                .collect(Collectors.toList());

        // 응답 생성
        ProductDto.ProductSearchResponse response = ProductDto.ProductSearchResponse.builder()
                .products(productItems)
                .pageInfo(convertToPageInfo(productPage))
                .build();

        return response;
    }

    /**
     * Product 엔티티를 ProductItem DTO로 변환
     */
    private ProductDto.ProductItem convertToProductItem(Product product, Long userId) {
        // 가격 정보 (최대 혜택가는 할인가와 동일하게 설정, 추후 할인 로직 추가 가능)
        Integer regularPrice = product.getRegularPrice();
        Integer salePrice = product.getSalePrice();
        Integer discountRate = calculateDiscountRate(regularPrice, salePrice);
        ProductDto.PriceInfo priceInfo = ProductDto.PriceInfo.builder()
                .regularPrice(regularPrice)
                .discountRate(discountRate)
                .salePrice(salePrice)
                .maxBenefitPrice(salePrice) // TODO: 할인 로직 추가 시 수정
                .build();

        // 좋아요 여부 확인 (TODO: 실제 좋아요 테이블 조회)
        Boolean isWished = false;
        if (userId != null) {
            // isWished = wishlistService.isWished(userId, product.getProductId());
        }

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
                .reviewCount(0L) // TODO: 실제 리뷰 수 조회
                .isWished(isWished)
                .build();
    }

    /**
     * Page 객체를 PageInfo DTO로 변환
     */
    private ProductDto.PageInfo convertToPageInfo(Page<Product> page) {
        return ProductDto.PageInfo.builder()
                .currentPage(page.getNumber() + 1) // 0-based to 1-based
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLast(page.isLast())
                .hasNext(page.hasNext())
                .build();
    }

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

    private ProductGender parseGender(String gender) {
        if (gender == null) {
            return null;
        }
        try {
            return ProductGender.valueOf(gender.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
