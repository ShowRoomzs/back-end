package showroomz.api.app.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.repository.MarketFollowRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductImage;
import showroomz.domain.product.entity.ProductOption;
import showroomz.domain.product.entity.ProductOptionGroup;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.type.ProductGender;
import showroomz.domain.filter.entity.Filter;
import showroomz.domain.filter.repository.FilterRepository;
import showroomz.domain.product.repository.ProductFilterCriteria;
import showroomz.domain.product.repository.ProductOptionGroupRepository;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.api.seller.category.service.CategoryService;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.api.app.wishlist.service.WishlistService;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.domain.member.user.entity.Users;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service("appProductService")
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final FilterRepository filterRepository;
    private final ProductOptionGroupRepository productOptionGroupRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final MarketFollowRepository marketFollowRepository;
    private final WishlistRepository wishlistRepository;
    private final WishlistService wishlistService;
    private final ObjectMapper objectMapper;
    private static final String DEFAULT_SORT = "RECOMMEND";
    private static final String SORT_FILTER_KEY = "sort";

    /**
     * 사용자용 상품 검색
     */
    public ProductDto.ProductSearchResponse searchProducts(
            ProductDto.ProductSearchRequest request,
            Integer page,
            Integer limit,
            Users currentUser // 좋아요 여부 확인용 (null 가능)
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
        FilterParsingResult parsedFilters = parseFilters(request.getFilters());
        String sortType = parsedFilters.sortType != null ? parsedFilters.sortType : DEFAULT_SORT;
        List<ProductFilterCriteria> filterCriteria = buildFilterCriteria(parsedFilters.filters);

        // 검색 실행
        Page<Product> productPage = productRepository.searchProductsForUser(
                keyword,
                categoryIds,
                request.getMarketId(),
                filterCriteria,
                sortType,
                pageable
        );

        // DTO 변환
        List<ProductDto.ProductItem> productItems = productPage.getContent().stream()
                .map(product -> convertToProductItem(product, currentUser))
                .collect(Collectors.toList());

        // 응답 생성
        ProductDto.ProductSearchResponse response = ProductDto.ProductSearchResponse.builder()
                .products(productItems)
                .pageInfo(convertToPageInfo(productPage))
                .build();

        return response;
    }

    /**
     * 사용자용 상품 상세 조회
     */
    public ProductDto.ProductDetailResponse getProductDetail(Long productId) {
        Product product = productRepository.findDetailByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        Users currentUser = resolveCurrentUser();
        boolean isWished = false;
        boolean isFollowing = false;
        if (currentUser != null) {
            isWished = wishlistRepository.existsByUserAndProduct(currentUser, product);
            if (product.getMarket() != null) {
                isFollowing = marketFollowRepository.existsByUserAndMarket(currentUser, product.getMarket());
            }
        }

        String representativeImageUrl = extractRepresentativeImageUrl(product);
        List<String> coverImageUrls = extractCoverImageUrls(product);
        List<ProductOptionGroup> optionGroupEntities = productOptionGroupRepository.findByProductIdWithOptions(productId);
        List<ProductVariant> variantEntities = productVariantRepository.findByProductIdWithOptions(productId);
        List<ProductDto.OptionGroupInfo> optionGroups = buildOptionGroups(optionGroupEntities);
        List<ProductDto.VariantInfo> variants = buildVariants(variantEntities);
        Integer regularPrice = product.getRegularPrice();
        Integer salePrice = product.getSalePrice();
        Integer deliveryFreeThreshold = product.getDeliveryFreeThreshold();
        Boolean isFreeDelivery = calculateIsFreeDelivery(salePrice, deliveryFreeThreshold);
        JsonNode productNotice = parseJsonSafely(product.getProductNotice());
        JsonNode tags = parseJsonSafely(product.getTags());

        String createdAt = product.getCreatedAt() != null ? product.getCreatedAt().toString() : null;

        return ProductDto.ProductDetailResponse.builder()
                .id(product.getProductId())
                .productNumber(product.getProductNumber())
                .marketId(product.getMarket() != null ? product.getMarket().getId() : null)
                .marketName(product.getMarket() != null ? product.getMarket().getMarketName() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .name(product.getName())
                .sellerProductCode(product.getSellerProductCode())
                .representativeImageUrl(representativeImageUrl)
                .coverImageUrls(coverImageUrls)
                .description(product.getDescription())
                .productNotice(productNotice)
                .tags(tags)
                .gender(product.getGender() != null ? product.getGender().name() : null)
                .isRecommended(product.getIsRecommended())
                .regularPrice(regularPrice)
                .salePrice(salePrice)
                .deliveryType(product.getDeliveryType())
                .deliveryFee(product.getDeliveryFee())
                .deliveryFreeThreshold(deliveryFreeThreshold)
                .deliveryEstimatedDays(product.getDeliveryEstimatedDays())
                .isFreeDelivery(isFreeDelivery)
                .optionGroups(optionGroups)
                .variants(variants)
                .isWished(isWished)
                .isFollowing(isFollowing)
                .createdAt(createdAt)
                .build();
    }

    /**
     * 사용자용 연관 상품 조회
     */
    public ProductDto.ProductSearchResponse getRelatedProducts(
            Long productId,
            Integer page,
            Integer limit,
            Users currentUser
    ) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (limit != null && limit > 0) ? limit : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        Long categoryId = product.getCategory() != null ? product.getCategory().getCategoryId() : null;
        ProductGender gender = product.getGender();

        Page<Product> relatedPage = productRepository.findRelatedProducts(
                productId,
                categoryId,
                gender,
                pageable
        );

        List<ProductDto.ProductItem> productItems = relatedPage.getContent().stream()
                .map(item -> convertToProductItem(item, currentUser))
                .collect(Collectors.toList());

        return ProductDto.ProductSearchResponse.builder()
                .products(productItems)
                .pageInfo(convertToPageInfo(relatedPage))
                .build();
    }

    /**
     * Product 엔티티를 ProductItem DTO로 변환
     */
    private ProductDto.ProductItem convertToProductItem(Product product, Users currentUser) {
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

        // 찜 여부 확인
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

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSortType(String sortType) {
        String normalized = normalize(sortType);
        return normalized != null ? normalized.toUpperCase() : null;
    }

    private FilterParsingResult parseFilters(List<ProductDto.FilterRequest> filters) {
        if (filters == null || filters.isEmpty()) {
            return new FilterParsingResult(null, List.of());
        }
        String sortType = null;
        List<ProductDto.FilterRequest> criteriaFilters = new java.util.ArrayList<>();
        for (ProductDto.FilterRequest filter : filters) {
            if (filter == null) {
                continue;
            }
            String key = normalize(filter.getKey());
            if (key != null && key.equalsIgnoreCase(SORT_FILTER_KEY)) {
                if (sortType == null && filter.getValues() != null && !filter.getValues().isEmpty()) {
                    sortType = normalizeSortType(filter.getValues().get(0));
                }
                continue;
            }
            criteriaFilters.add(filter);
        }
        return new FilterParsingResult(sortType, criteriaFilters);
    }

    private List<ProductFilterCriteria> buildFilterCriteria(List<ProductDto.FilterRequest> filters) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }

        List<String> keys = filters.stream()
                .map(ProductDto.FilterRequest::getKey)
                .filter(key -> key != null && !key.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .toList();

        List<Filter> filterDefinitions = filterRepository.findByFilterKeyIn(keys);

        return filters.stream()
                .map(filter -> {
                    String key = normalize(filter.getKey());
                    if (key == null) {
                        return null;
                    }
                    if (SORT_FILTER_KEY.equalsIgnoreCase(key)) {
                        return null;
                    }
                    Filter definition = filterDefinitions.stream()
                            .filter(item -> key.equalsIgnoreCase(item.getFilterKey()))
                            .findFirst()
                            .orElse(null);
                    if (definition == null || !Boolean.TRUE.equals(definition.getIsActive())) {
                        return null;
                    }
                    return new ProductFilterCriteria(
                            key,
                            definition.getFilterType(),
                            definition.getCondition(),
                            filter.getValues(),
                            filter.getMinValue(),
                            filter.getMaxValue()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();
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

    private String extractRepresentativeImageUrl(Product product) {
        return product.getProductImages().stream()
                .filter(image -> image.getOrder() != null && image.getOrder() == 0)
                .sorted(Comparator.comparing(ProductImage::getOrder))
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(product.getThumbnailUrl());
    }

    private List<String> extractCoverImageUrls(Product product) {
        return product.getProductImages().stream()
                .filter(image -> image.getOrder() != null && image.getOrder() >= 1)
                .sorted(Comparator.comparing(ProductImage::getOrder))
                .map(ProductImage::getUrl)
                .collect(Collectors.toList());
    }

    private List<ProductDto.OptionGroupInfo> buildOptionGroups(List<ProductOptionGroup> optionGroups) {
        return optionGroups.stream()
                .map(group -> ProductDto.OptionGroupInfo.builder()
                        .optionGroupId(group.getOptionGroupId())
                        .name(group.getName())
                        .options(group.getOptions().stream()
                                .map(option -> ProductDto.OptionInfo.builder()
                                        .optionId(option.getOptionId())
                                        .name(option.getName())
                                        .price(option.getPrice())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<ProductDto.VariantInfo> buildVariants(List<ProductVariant> variants) {
        return variants.stream()
                .map(variant -> ProductDto.VariantInfo.builder()
                        .variantId(variant.getVariantId())
                        .name(variant.getName())
                        .regularPrice(variant.getRegularPrice())
                        .salePrice(variant.getSalePrice())
                        .stock(variant.getStock())
                        .isRepresentative(variant.getIsRepresentative())
                        .isDisplay(variant.getIsDisplay())
                        .optionIds(variant.getOptions().stream()
                                .map(ProductOption::getOptionId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    private Boolean calculateIsFreeDelivery(Integer salePrice, Integer deliveryFreeThreshold) {
        if (salePrice == null || deliveryFreeThreshold == null) {
            return false;
        }
        return salePrice >= deliveryFreeThreshold;
    }

    private JsonNode parseJsonSafely(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception e) {
            return null;
        }
    }

    private static class FilterParsingResult {
        private final String sortType;
        private final List<ProductDto.FilterRequest> filters;

        private FilterParsingResult(String sortType, List<ProductDto.FilterRequest> filters) {
            this.sortType = sortType;
            this.filters = filters;
        }
    }
}
