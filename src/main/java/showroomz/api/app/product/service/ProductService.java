package showroomz.api.app.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductImage;
import showroomz.domain.product.entity.ProductOption;
import showroomz.domain.product.entity.ProductOptionGroup;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.domain.filter.entity.Filter;
import showroomz.domain.filter.repository.FilterRepository;
import showroomz.domain.product.repository.ProductFilterCriteria;
import showroomz.domain.product.repository.ProductOptionGroupRepository;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.domain.category.service.CategoryHierarchyService;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

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
    private final CategoryHierarchyService categoryHierarchyService;
    private final FilterRepository filterRepository;
    private final ProductOptionGroupRepository productOptionGroupRepository;
    private final ProductVariantRepository productVariantRepository;
    private final WishlistRepository wishlistRepository;
    private final ObjectMapper objectMapper;
    private static final String DEFAULT_SORT = "RECOMMEND";
    private static final String SORT_FILTER_KEY = "sort";

    /**
     * 사용자용 상품 검색
     */
    public PageResponse<ProductDto.ProductItem> searchProducts(
            ProductDto.ProductSearchRequest request,
            PagingRequest pagingRequest,
            Users currentUser // 좋아요 여부 확인용 (null 가능)
    ) {
        // 페이징: 정렬은 filters의 sort(쿼리DSL sortType)로 처리 — Pageable에는 offset/size만 반영
        int pageNumber = pagingRequest.getPage() > 0 ? pagingRequest.getPage() - 1 : 0;
        int pageSize = pagingRequest.getSize() > 0 ? pagingRequest.getSize() : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        // 카테고리 ID 처리 (하위 카테고리 포함)
        Long categoryId = request.getCategoryId();
        List<Long> categoryIds = null;
        if (categoryId != null) {
            try {
                categoryIds = categoryHierarchyService.getAllSubCategoryIds(categoryId);
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

        return new PageResponse<>(productItems, productPage);
    }

    /**
     * 사용자용 상품 상세 조회 (C7).
     *
     * <p>응답은 C7 화면이 실제로 그리는 값만 담는다 — 갤러리 · 브랜드 줄 · 가격 · 배송 블록 ·
     * 상세정보/판매자 정보 탭 · 옵션 시트. 문의 탭은 별도 API가, 찜은 게시물 단위가 담당하므로
     * 여기서 내려주지 않는다.
     */
    public ProductDto.ProductDetailResponse getProductDetail(Long productId) {
        Product product = productRepository.findDetailByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        requireVisibleForDetail(product);

        String representativeImageUrl = extractRepresentativeImageUrl(product);
        List<String> coverImageUrls = extractCoverImageUrls(product);
        List<ProductOptionGroup> optionGroupEntities = productOptionGroupRepository.findByProductIdWithOptions(productId);
        List<ProductVariant> variantEntities = productVariantRepository.findByProductIdWithOptions(productId);
        List<ProductDto.OptionGroupInfo> optionGroups = buildOptionGroups(optionGroupEntities);
        List<ProductDto.VariantInfo> variants = buildVariants(variantEntities, product);
        Integer regularPrice = product.getRegularPrice();
        Integer salePrice = product.getSalePrice();
        JsonNode productNotice = parseJsonSafely(product.getProductNotice());
        Market market = product.getMarket();

        return ProductDto.ProductDetailResponse.builder()
                .id(product.getProductId())
                .name(product.getName())
                .representativeImageUrl(representativeImageUrl)
                .coverImageUrls(coverImageUrls)
                .marketId(market != null ? market.getId() : null)
                .marketName(market != null ? market.getMarketName() : null)
                .brandSiteUrl(market != null ? market.getBrandSiteUrl() : null)
                .regularPrice(regularPrice)
                .discountRate(calculateDiscountRate(regularPrice, salePrice))
                .salePrice(salePrice)
                .groupBuyStatus(product.getGroupBuyStatus() != null
                        ? product.getGroupBuyStatus().name()
                        : ProductGroupBuyStatus.NOT_CONNECTED.name())
                .status(buildStockStatus(product, variantEntities))
                .delivery(buildDeliveryInfo(market))
                .description(product.getDescription())
                .productNotice(productNotice)
                .optionGroups(optionGroups)
                .variants(variants)
                .sellerInfo(buildSellerInfo(market))
                .build();
    }

    /**
     * 옵션별 재고 및 가격 다중 조회 (IN 절로 1회 쿼리)
     * 페이징 미적용 - 요청한 variantIds에 해당하는 결과만 반환
     */
    public ProductDto.VariantStockListResponse getVariantStocks(Long productId, List<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "variantIds는 필수이며 1개 이상이어야 합니다.");
        }

        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        requireVisibleForDetail(product);

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndVariantIdIn(productId, variantIds);

        List<ProductDto.ProductVariantStockResponse> list = variants.stream()
                .map(this::toVariantStockResponse)
                .collect(Collectors.toList());

        return ProductDto.VariantStockListResponse.builder()
                .variants(list)
                .build();
    }

    private ProductDto.ProductVariantStockResponse toVariantStockResponse(ProductVariant variant) {
        Product product = variant.getProduct();
        Integer regularPrice = variant.getRegularPrice();
        Integer salePrice = variant.getSalePrice();
        Integer discountRate = calculateDiscountRate(regularPrice, salePrice);
        ProductDto.PriceInfo priceInfo = ProductDto.PriceInfo.builder()
                .regularPrice(regularPrice)
                .discountRate(discountRate)
                .salePrice(salePrice)
                .maxBenefitPrice(salePrice)
                .build();

        boolean isOutOfStockForced = Boolean.TRUE.equals(product != null && product.getIsOutOfStockForced());
        int stock = variant.getStock() != null ? variant.getStock() : 0;
        boolean isOutOfStock = isOutOfStockForced || stock <= 0;

        return ProductDto.ProductVariantStockResponse.builder()
                .productId(product != null ? product.getProductId() : null)
                .variantId(variant.getVariantId())
                .stock(variant.getStock())
                .isOutOfStock(isOutOfStock)
                .isOutOfStockForced(isOutOfStockForced)
                .price(priceInfo)
                .build();
    }

    /**
     * Product 엔티티를 ProductItem DTO로 변환
     */
    public ProductDto.ProductItem convertToProductItem(Product product, Users currentUser) {
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
                .gender(product.getGender() != null ? product.getGender().name() : null)
                .isDisplay(product.getDisplayStatus() != null && product.getDisplayStatus().isVisible())
                .isRecommended(product.getIsRecommended())
                .productNotice(product.getProductNotice())
                .description(product.getDescription())
                .createdAt(product.getCreatedAt() != null ? product.getCreatedAt().toString() : null)
                .status(buildStockStatus(product))
                .likeCount(0L) // TODO: 실제 좋아요 수 조회
                .wishCount(wishCount)
                .reviewCount(reviewCount)
                .isWished(isWished)
                .build();
    }

    private ProductDto.StockStatus buildStockStatus(Product product) {
        return buildStockStatus(product, product.getVariants());
    }

    /**
     * 상품 전체 품절 판정 — 재고는 옵션마다 소진되므로, <b>남은 옵션이 하나도 없을 때</b> 비로소
     * 상품 전체가 품절이다. 강제 품절은 그 위를 덮는다(재고가 남아 있어도 브랜드가 내려둘 수 있다).
     *
     * <p>상세는 이미 읽어 둔 옵션 목록으로 판정한다 — 엔티티의 지연 컬렉션을 다시 건드리면 같은
     * 옵션을 두 번 읽는다.
     */
    private ProductDto.StockStatus buildStockStatus(Product product, List<ProductVariant> variants) {
        boolean isOutOfStockForced = Boolean.TRUE.equals(product.getIsOutOfStockForced());
        boolean hasStock = variants.stream()
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

    /**
     * 상품 상세는 진열중 + 공구 연결, 두 조건을 모두 요구한다 (C7).
     *
     * <p>진열 상태는 검색 목록과 같은 기준(DISPLAY만)이고, 공구 연결은 그 위에 얹는 조건이다 —
     * 공구가 끝나 연결이 풀리면(NOT_CONNECTED로 되돌아가면) 진열 상태와 무관하게 다시 막힌다.
     * 상품 상세는 공구 게시물의 상품 카드에서만 진입하는 화면이라 화면상 도달할 경로가 없지만,
     * 상품 ID가 순번이라 주소만 바꿔도 열리므로 서버가 두 조건 모두 확인한다. 403이 아니라
     * 404로 돌려주는 것은 의도적이다 — 권한 오류로 나누면 "그 번호의 상품은 있다"는 사실이
     * 밖으로 드러난다.
     */
    private void requireVisibleForDetail(Product product) {
        boolean isDisplayed = product.getDisplayStatus() != null && product.getDisplayStatus().isVisible();
        ProductGroupBuyStatus groupBuyStatus = product.getGroupBuyStatus();
        boolean isGroupBuyConnected = groupBuyStatus != null && groupBuyStatus.isConnected();
        if (!isDisplayed || !isGroupBuyConnected) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
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

    /**
     * 옵션 시트에 필요한 값만 담는다. 품절 판정은 옵션 재고 조회(getVariantStocks)와 같은 규칙이다 —
     * 재고 0이거나 상품이 강제 품절이면 품절. 시트를 연 뒤의 실시간 재고는 옵션 재고 API가 갱신한다.
     */
    private List<ProductDto.VariantInfo> buildVariants(List<ProductVariant> variants, Product product) {
        boolean isOutOfStockForced = Boolean.TRUE.equals(product.getIsOutOfStockForced());
        return variants.stream()
                .map(variant -> {
                    int stock = variant.getStock() != null ? variant.getStock() : 0;
                    return ProductDto.VariantInfo.builder()
                            .variantId(variant.getVariantId())
                            .name(variant.getName())
                            .regularPrice(variant.getRegularPrice())
                            .salePrice(variant.getSalePrice())
                            .stock(variant.getStock())
                            .isOutOfStock(isOutOfStockForced || stock <= 0)
                            .isRepresentative(variant.getIsRepresentative())
                            .optionIds(variant.getOptions().stream()
                                    .map(ProductOption::getOptionId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 배송 · 교환 · 반품 값은 브랜드(마켓)의 배송 설정에서 온다. 화면은 이 숫자로 "3,000원",
     * "30,000원 이상 구매시 무료배송", "N일 이내 출발 예정" 문구를 조립하므로 서버는 금액만 내려준다.
     */
    private ProductDto.DeliveryInfo buildDeliveryInfo(Market market) {
        if (market == null) {
            return null;
        }
        return ProductDto.DeliveryInfo.builder()
                .shippingLeadDays(market.getShippingLeadDays())
                .deliveryFee(market.getDefaultDeliveryFee())
                .freeShippingThreshold(market.getFreeShippingThreshold())
                .remoteAreaSurcharge(market.getRemoteAreaSurcharge())
                .returnFee(market.getReturnFee())
                .exchangeFee(market.getExchangeFee())
                .build();
    }

    /**
     * 판매자 정보 탭의 전자상거래법 표시 항목. 마켓의 고객센터 번호와 셀러의 사업자 정보를 합친다.
     */
    private ProductDto.SellerInfo buildSellerInfo(Market market) {
        if (market == null) {
            return null;
        }
        Seller seller = market.getSeller();
        if (seller == null) {
            return ProductDto.SellerInfo.builder()
                    .csNumber(market.getCsNumber())
                    .build();
        }
        return ProductDto.SellerInfo.builder()
                .companyName(seller.getCompanyName())
                .representativeName(seller.getRepresentativeName())
                .businessRegistrationNumber(seller.getBusinessRegistrationNumber())
                .mailOrderRegNumber(seller.getMailOrderRegNumber())
                .businessAddress(joinAddress(seller.getBusinessAddress(), seller.getDetailAddress()))
                .csNumber(market.getCsNumber())
                .email(seller.getEmail())
                .build();
    }

    private String joinAddress(String address, String detailAddress) {
        if (address == null || address.isBlank()) {
            return null;
        }
        if (detailAddress == null || detailAddress.isBlank()) {
            return address.trim();
        }
        return address.trim() + " " + detailAddress.trim();
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
