package showroomz.api.seller.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.api.common.product.service.ProductProcessingHistoryService;
import showroomz.api.seller.product.DTO.ProductDto;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.api.seller.category.service.CategoryService;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.product.entity.*;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.domain.product.type.ProductHideReasonType;
import showroomz.domain.product.type.ProductInspectionStatus;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service("sellerProductService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final SellerRepository adminRepository;
    private final MarketRepository marketRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ProductProcessingHistoryService processingHistoryService;

    public ProductDto.CreateProductResponse createProduct(String adminEmail, ProductDto.CreateProductRequest request) {
        // 1. 카테고리 조회 및 검증 (카테고리 ID로 조회)
        Category category = categoryRepository.findByCategoryId(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // 2. 마켓 조회 (관리자의 마켓 사용)
        Seller admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Market market = marketRepository.findBySeller(admin)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. 상품 번호 생성 (SRZ-YYYYMMDD-XXX 형식)
        String productNumber = generateProductNumber();

        // 4. Product 엔티티 생성
        Product product = new Product();
        product.setCategory(category);
        product.setMarket(market);
        product.setName(request.getName());
        product.setSellerProductCode(request.getSellerProductCode());
        product.setRegularPrice(request.getRegularPrice());
        // 할인가는 계약 단계에서 결정 — 등록 시점에는 판매가와 동일하게 저장
        product.setSalePrice(request.getRegularPrice());
        product.setGender(request.getGender());
        product.setDisplayStatus(ProductDisplayStatus.DISPLAY);
        product.setIsOutOfStockForced(request.getIsOutOfStockForced() != null ? request.getIsOutOfStockForced() : false);
        product.setIsRecommended(false);
        product.setDescription(request.getDescription());
        product.setProductNumber(productNumber);
        product.setInspectionStatus(ProductInspectionStatus.WAITING);

        // 5. 상품정보제공고시 JSON 변환
        if (request.getProductNotice() != null) {
            try {
                String productNoticeJson = objectMapper.writeValueAsString(request.getProductNotice());
                product.setProductNotice(productNoticeJson);
            } catch (Exception e) {
                log.error("상품정보제공고시 JSON 변환 실패", e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 6. 대표 이미지 설정
        if (request.getRepresentativeImageUrl() != null) {
            product.setThumbnailUrl(request.getRepresentativeImageUrl());
        }

        // 7. 이미지 저장
        List<ProductImage> productImages = new ArrayList<>();
        int imageOrder = 0;
        
        // 대표 이미지 추가
        if (request.getRepresentativeImageUrl() != null) {
            ProductImage representativeImage = new ProductImage(product, request.getRepresentativeImageUrl(), imageOrder++);
            productImages.add(representativeImage);
        }
        
        // 커버 이미지 추가 (최대 4개)
        if (request.getCoverImageUrls() != null && !request.getCoverImageUrls().isEmpty()) {
            for (String coverImageUrl : request.getCoverImageUrls()) {
                if (imageOrder >= 5) break; // 대표 이미지 포함 최대 5개
                ProductImage coverImage = new ProductImage(product, coverImageUrl, imageOrder++);
                productImages.add(coverImage);
            }
        }
        
        product.setProductImages(productImages);

        // 8. 옵션 그룹 및 옵션 생성
        Map<String, Map<String, ProductOption>> optionMap = new HashMap<>(); // 그룹명 -> (옵션명 -> ProductOption)
        
        if (request.getOptionGroups() != null && !request.getOptionGroups().isEmpty()) {
            for (ProductDto.OptionGroupRequest groupRequest : request.getOptionGroups()) {
                ProductOptionGroup optionGroup = new ProductOptionGroup(product, groupRequest.getName());
                product.getOptionGroups().add(optionGroup);
                
                Map<String, ProductOption> optionsInGroup = new HashMap<>();
                for (ProductDto.OptionRequest optionRequest : groupRequest.getOptions()) {
                    ProductOption option = new ProductOption(optionGroup, optionRequest.getName(), optionRequest.getPrice());
                    optionGroup.getOptions().add(option);
                    optionsInGroup.put(optionRequest.getName(), option);
                }
                optionMap.put(groupRequest.getName(), optionsInGroup);
            }
        }

        // 9. Variant 생성 및 옵션 매핑
        boolean hasVariantRequests = request.getVariants() != null && !request.getVariants().isEmpty();
        boolean hasOptionGroups = request.getOptionGroups() != null && !request.getOptionGroups().isEmpty();

        if (hasOptionGroups && !hasVariantRequests) {
            throw new BusinessException(
                    ErrorCode.INVALID_VARIANT_OPTIONS,
                    "최소 한 개 이상의 옵션 조합을 등록해야 합니다."
            );
        }

        if (hasVariantRequests) {
            for (ProductDto.VariantRequest variantRequest : request.getVariants()) {
                // 옵션명으로 옵션 조합 생성
                List<ProductOption> variantOptions = new ArrayList<>();
                
                if (request.getOptionGroups() != null) {
                    int optionIndex = 0;
                    for (ProductDto.OptionGroupRequest groupRequest : request.getOptionGroups()) {
                        if (optionIndex >= variantRequest.getOptionNames().size()) {
                            throw new BusinessException(ErrorCode.INVALID_VARIANT_OPTIONS);
                        }
                        String optionName = variantRequest.getOptionNames().get(optionIndex);
                        Map<String, ProductOption> optionsInGroup = optionMap.get(groupRequest.getName());
                        if (optionsInGroup == null || !optionsInGroup.containsKey(optionName)) {
                            throw new BusinessException(ErrorCode.INVALID_VARIANT_OPTIONS);
                        }
                        variantOptions.add(optionsInGroup.get(optionName));
                        optionIndex++;
                    }
                    if (optionIndex != variantRequest.getOptionNames().size()) {
                        throw new BusinessException(ErrorCode.INVALID_VARIANT_OPTIONS);
                    }
                }
                
                // Variant 이름 생성 (옵션명을 조합)
                String variantName = variantRequest.getOptionNames().stream()
                        .collect(Collectors.joining(" / "));
                
                ProductVariant variant = new ProductVariant(
                        product,
                        variantName,
                        variantRequest.getRegularPrice(),
                        variantRequest.getRegularPrice(),
                        variantRequest.getStock(),
                        variantRequest.getIsRepresentative() != null ? variantRequest.getIsRepresentative() : false
                );
                
                variant.setOptions(variantOptions);
                product.getVariants().add(variant);
            }
        } else {
            // 옵션이 없는 경우 단일 Variant 생성
            ProductVariant variant = new ProductVariant(
                    product,
                    null,
                    request.getRegularPrice(),
                    request.getRegularPrice(),
                    0,
                    true
            );
            product.getVariants().add(variant);
        }

        // 10. Product 저장
        Product savedProduct = productRepository.save(product);
        processingHistoryService.recordCreated(savedProduct);

        // 11. 응답 생성
        return ProductDto.CreateProductResponse.builder()
                .productId(savedProduct.getProductId())
                .productNumber(savedProduct.getProductNumber())
                .message("상품이 성공적으로 등록되었습니다.")
                .build();
    }

    /**
     * 상품 번호 생성 (SRZ-YYYYMMDD-XXX 형식)
     * XXX는 해당 일자의 순차 번호 (001, 002, ...)
     */
    private String generateProductNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "SRZ-" + datePrefix + "-";
        
        // 해당 일자의 마지막 상품 번호 찾기
        Optional<Product> lastProduct = productRepository.findAll().stream()
                .filter(p -> p.getProductNumber() != null && p.getProductNumber().startsWith(prefix))
                .max(Comparator.comparing(Product::getProductNumber));
        
        int sequenceNumber = 1;
        if (lastProduct.isPresent()) {
            String lastNumber = lastProduct.get().getProductNumber();
            String lastSequence = lastNumber.substring(lastNumber.lastIndexOf("-") + 1);
            try {
                sequenceNumber = Integer.parseInt(lastSequence) + 1;
            } catch (NumberFormatException e) {
                sequenceNumber = 1;
            }
        }
        
        return prefix + String.format("%03d", sequenceNumber);
    }


    @Transactional(readOnly = true)
    public PageResponse<ProductDto.ProductListItem> getProductList(String adminEmail, ProductDto.ProductListRequest request, PagingRequest pagingRequest) {
        // 1. Admin과 Market 조회
        Seller admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Market market = marketRepository.findBySeller(admin)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        // 2. 페이징 정보 생성
        Pageable pageable = pagingRequest.toPageable();
        
        // 3. 필터 파라미터 설정
        Long categoryId = request != null ? request.getCategoryId() : null;
        ProductDisplayStatus displayStatusFilter = resolveDisplayStatusFilter(
                request != null ? request.getDisplayStatus() : null);
        String stockStatus = (request != null && request.getStockStatus() != null) 
                ? request.getStockStatus() : "ALL";
        String keyword = (request != null && request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) 
                ? request.getKeyword().trim() : null;
        String keywordType = (request != null && request.getKeywordType() != null && !request.getKeywordType().trim().isEmpty()) 
                ? request.getKeywordType().trim() : null;
        
        // 4. 카테고리 필터링 처리: 상위 카테고리인 경우 모든 하위 카테고리 ID를 포함
        List<Long> categoryIds = null;
        if (categoryId != null) {
            // 해당 카테고리와 모든 하위 카테고리 ID를 조회
            categoryIds = categoryService.getAllSubCategoryIds(categoryId);
            // 빈 리스트인 경우 null로 변환 (JPQL에서 IN ()는 에러 발생)
            if (categoryIds != null && categoryIds.isEmpty()) {
                categoryIds = null;
            }
        }
        
        // 5. 필터링된 상품 조회 (모든 필터는 쿼리에서 처리)
        Page<Product> productPage = productRepository.findByMarketIdWithFilters(
                market.getId(),
                categoryIds,
                displayStatusFilter,
                stockStatus,
                keyword,
                keywordType,
                pageable
        );
        
        // 5. ProductListItem으로 변환
        List<ProductDto.ProductListItem> productList = productPage.getContent().stream()
                .map(product -> {
                    String calculatedStockStatus = calculateStockStatus(product, null);
                    return convertToProductListItem(product, calculatedStockStatus);
                })
                .collect(Collectors.toList());
        
        // 7. PageResponse 생성
        return new PageResponse<>(
                productList,
                productPage
        );
    }
    
    @Transactional(readOnly = true)
    public ProductDto.ProductDetailResponse getProductById(String adminEmail, Long productId) {
        // 1. Admin과 Market 조회
        Seller admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Market market = marketRepository.findBySeller(admin)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        // 2. 상품 조회 및 권한 확인 (해당 Market의 상품인지 확인)
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        
        // 3. 해당 seller의 상품인지 확인
        if (product.getMarket() == null || !product.getMarket().getId().equals(market.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        // 4. 품절 상태 계산
        String stockStatus = calculateStockStatus(product, null);
        
        // 5. 응답 생성 (모든 필드 포함)
        return convertToProductDetailResponse(product, stockStatus);
    }

    /**
     * 선택된 상품들을 일괄 삭제
     */
    public ProductDto.BatchDeleteResponse batchDeleteProducts(String adminEmail, ProductDto.BatchDeleteRequest request) {
        // 1. Admin과 Market 조회
        Seller admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Market market = marketRepository.findBySeller(admin)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        // 2. 상품 조회 및 존재 여부 확인
        List<Product> products = productRepository.findAllById(request.getProductIds());
        
        // 존재하지 않는 상품 ID 찾기
        Set<Long> foundProductIds = products.stream()
                .map(Product::getProductId)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> notFoundProductIds = request.getProductIds().stream()
                .filter(id -> !foundProductIds.contains(id))
                .collect(java.util.stream.Collectors.toList());
        
        if (!notFoundProductIds.isEmpty()) {
            String productIdsStr = notFoundProductIds.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "));
            String errorMessage = String.format("productId: %s에 대한 상품이 존재하지 않습니다.", productIdsStr);
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, errorMessage);
        }
        
        // 3. 본인의 상품인지 확인 및 삭제 대상 수집
        List<Long> deletedProductIds = new ArrayList<>();
        List<Long> unauthorizedProductIds = new ArrayList<>();
        
        for (Product product : products) {
            if (product.getMarket() == null || !product.getMarket().getId().equals(market.getId())) {
                unauthorizedProductIds.add(product.getProductId());
            } else {
                deletedProductIds.add(product.getProductId());
            }
        }
        
        // 권한이 없는 상품이 있으면 에러 발생
        if (!unauthorizedProductIds.isEmpty()) {
            String productIdsStr = unauthorizedProductIds.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "));
            String errorMessage = String.format("productId: %s에 대한 권한이 없습니다.", productIdsStr);
            throw new BusinessException(ErrorCode.FORBIDDEN, errorMessage);
        }
        
        // 4. 상품 일괄 삭제
        productRepository.deleteAll(products);
        
        // 5. 응답 생성
        return ProductDto.BatchDeleteResponse.builder()
                .productIds(deletedProductIds)
                .count(deletedProductIds.size())
                .message(deletedProductIds.size() + "개의 상품이 성공적으로 삭제되었습니다.")
                .build();
    }
    
    /**
     * 선택된 상품들의 품절 상태를 명시적으로 설정
     */
    public ProductDto.BatchUpdateResponse batchUpdateStockStatus(String adminEmail, ProductDto.BatchStockStatusRequest request) {
        // 1. Admin과 Market 조회
        Seller admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Market market = marketRepository.findBySeller(admin)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        // 2. 상품 조회 및 권한 확인
        List<Product> products = productRepository.findAllById(request.getProductIds());
        
        if (products.size() != request.getProductIds().size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        
        // 3. 본인의 상품인지 확인 및 품절 상태 설정
        List<Long> processedProductIds = new ArrayList<>();
        List<Long> unauthorizedProductIds = new ArrayList<>();
        
        for (Product product : products) {
            if (product.getMarket() == null || !product.getMarket().getId().equals(market.getId())) {
                unauthorizedProductIds.add(product.getProductId());
            } else {
                // 요청받은 상태로 명시적 설정
                product.setIsOutOfStockForced(request.getIsOutOfStocked());
                processedProductIds.add(product.getProductId());
            }
        }
        
        // 권한이 없는 상품이 있으면 에러 발생
        if (!unauthorizedProductIds.isEmpty()) {
            String productIdsStr = unauthorizedProductIds.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "));
            String errorMessage = String.format("productId: %s에 대한 권한이 없습니다.", productIdsStr);
            throw new BusinessException(ErrorCode.FORBIDDEN, errorMessage);
        }
        
        // 4. 저장
        productRepository.saveAll(products);
        
        // 5. 응답 메시지 생성
        String message;
        if (request.getIsOutOfStocked()) {
            message = String.format("%d개의 상품이 성공적으로 품절 처리되었습니다.", processedProductIds.size());
        } else {
            message = String.format("%d개의 상품이 성공적으로 품절 해제되었습니다.", processedProductIds.size());
        }
        
        // 6. 응답 생성
        return ProductDto.BatchUpdateResponse.builder()
                .productIds(processedProductIds)
                .count(processedProductIds.size())
                .message(message)
                .build();
    }
    
    /**
     * 선택된 상품들의 진열 상태를 명시적으로 설정
     */
    public ProductDto.BatchUpdateResponse batchUpdateDisplayStatus(String adminEmail, ProductDto.BatchDisplayStatusRequest request) {
        // 1. Admin과 Market 조회
        Seller admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Market market = marketRepository.findBySeller(admin)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        // 2. 상품 조회 및 권한 확인
        List<Product> products = productRepository.findAllById(request.getProductIds());
        
        if (products.size() != request.getProductIds().size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        
        // 3. 본인의 상품인지 확인 및 진열 상태 설정
        List<Long> processedProductIds = new ArrayList<>();
        List<Long> unauthorizedProductIds = new ArrayList<>();
        
        for (Product product : products) {
            if (product.getMarket() == null || !product.getMarket().getId().equals(market.getId())) {
                unauthorizedProductIds.add(product.getProductId());
            } else {
                // 요청된 상태로 명시적 설정
                ProductDisplayStatus previous = product.getDisplayStatus();
                applySellerDisplayStatusChange(product, previous, request.getDisplayStatus());
                processedProductIds.add(product.getProductId());
            }
        }
        
        // 권한이 없는 상품이 있으면 에러 발생
        if (!unauthorizedProductIds.isEmpty()) {
            String productIdsStr = unauthorizedProductIds.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "));
            String errorMessage = String.format("productId: %s에 대한 권한이 없습니다.", productIdsStr);
            throw new BusinessException(ErrorCode.FORBIDDEN, errorMessage);
        }
        
        // 4. 저장
        productRepository.saveAll(products);
        
        // 5. 응답 메시지 생성
        String message = String.format("%d개의 상품이 성공적으로 %s 처리되었습니다.",
                processedProductIds.size(),
                request.getDisplayStatus().getDescription());
        
        // 6. 응답 생성
        return ProductDto.BatchUpdateResponse.builder()
                .productIds(processedProductIds)
                .count(processedProductIds.size())
                .message(message)
                .build();
    }
    
    public ProductDto.UpdateProductResponse updateProduct(String adminEmail, Long productId, ProductDto.UpdateProductRequest request) {
        // 1. Admin과 Market 조회
        Seller admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Market market = marketRepository.findBySeller(admin)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 상품 조회 및 권한 확인
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        
        if (product.getMarket() == null || !product.getMarket().getId().equals(market.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        ProductDisplayStatus previousDisplayStatus = product.getDisplayStatus();
        int previousTotalStock = sumVariantStock(product);
        boolean hasInfoChange = hasProductInfoChange(request);
        boolean hasVariantUpdate = request.getOptionGroups() != null && request.getVariants() != null;

        // 3. 카테고리 업데이트 (제공된 경우)
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findByCategoryId(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            product.setCategory(category);
        }

        // 4. 기본 정보 업데이트
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getSellerProductCode() != null) {
            product.setSellerProductCode(request.getSellerProductCode());
        }
        if (request.getDisplayStatus() != null) {
            applySellerDisplayStatusChange(product, product.getDisplayStatus(), request.getDisplayStatus());
        }
        if (request.getIsOutOfStockForced() != null) {
            product.setIsOutOfStockForced(request.getIsOutOfStockForced());
        }
        if (request.getRegularPrice() != null) {
            product.setRegularPrice(request.getRegularPrice());
            // 할인가는 계약 단계에서 결정 — 수정 시점에는 판매가와 동일하게 저장
            product.setSalePrice(request.getRegularPrice());
        }
        if (request.getGender() != null) {
            product.setGender(request.getGender());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }

        // 5. 상품정보제공고시 JSON 변환 (제공된 경우)
        if (request.getProductNotice() != null) {
            try {
                String productNoticeJson = objectMapper.writeValueAsString(request.getProductNotice());
                product.setProductNotice(productNoticeJson);
            } catch (Exception e) {
                log.error("상품정보제공고시 JSON 변환 실패", e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 6. 이미지 업데이트 (제공된 경우)
        if (request.getRepresentativeImageUrl() != null || request.getCoverImageUrls() != null) {
            // 기존 이미지 삭제 (orphanRemoval을 위해 기존 컬렉션을 유지하고 clear 후 addAll 사용)
            List<ProductImage> existingImages = product.getProductImages();
            existingImages.clear();
            
            int imageOrder = 0;
            
            // 대표 이미지 추가
            if (request.getRepresentativeImageUrl() != null) {
                product.setThumbnailUrl(request.getRepresentativeImageUrl());
                ProductImage representativeImage = new ProductImage(product, request.getRepresentativeImageUrl(), imageOrder++);
                existingImages.add(representativeImage);
            }
            
            // 커버 이미지 추가 (최대 4개)
            if (request.getCoverImageUrls() != null && !request.getCoverImageUrls().isEmpty()) {
                for (String coverImageUrl : request.getCoverImageUrls()) {
                    if (imageOrder >= 5) break; // 대표 이미지 포함 최대 5개
                    ProductImage coverImage = new ProductImage(product, coverImageUrl, imageOrder++);
                    existingImages.add(coverImage);
                }
            }
        }

        // 7. 옵션 그룹 및 옵션 업데이트 (제공된 경우)
        if (request.getOptionGroups() != null && request.getVariants() != null) {
            // 기존 옵션 그룹 및 variant 삭제
            product.getOptionGroups().clear();
            product.getVariants().clear();
            
            Map<String, Map<String, ProductOption>> optionMap = new HashMap<>();
            
            // 옵션 그룹 생성
            for (ProductDto.OptionGroupRequest groupRequest : request.getOptionGroups()) {
                ProductOptionGroup optionGroup = new ProductOptionGroup(product, groupRequest.getName());
                product.getOptionGroups().add(optionGroup);
                
                Map<String, ProductOption> optionsInGroup = new HashMap<>();
                for (ProductDto.OptionRequest optionRequest : groupRequest.getOptions()) {
                    ProductOption option = new ProductOption(optionGroup, optionRequest.getName(), optionRequest.getPrice());
                    optionGroup.getOptions().add(option);
                    optionsInGroup.put(optionRequest.getName(), option);
                }
                optionMap.put(groupRequest.getName(), optionsInGroup);
            }

            // Variant 생성 및 옵션 매핑
            for (ProductDto.VariantRequest variantRequest : request.getVariants()) {
                List<ProductOption> variantOptions = new ArrayList<>();
                
                if (request.getOptionGroups() != null) {
                    int optionIndex = 0;
                    for (ProductDto.OptionGroupRequest groupRequest : request.getOptionGroups()) {
                        if (optionIndex >= variantRequest.getOptionNames().size()) {
                            throw new BusinessException(ErrorCode.INVALID_VARIANT_OPTIONS);
                        }
                        String optionName = variantRequest.getOptionNames().get(optionIndex);
                        Map<String, ProductOption> optionsInGroup = optionMap.get(groupRequest.getName());
                        if (optionsInGroup == null || !optionsInGroup.containsKey(optionName)) {
                            throw new BusinessException(ErrorCode.INVALID_VARIANT_OPTIONS);
                        }
                        variantOptions.add(optionsInGroup.get(optionName));
                        optionIndex++;
                    }
                }
                
                String variantName = variantRequest.getOptionNames().stream()
                        .collect(Collectors.joining(" / "));
                
                ProductVariant variant = new ProductVariant(
                        product,
                        variantName,
                        variantRequest.getRegularPrice(),
                        variantRequest.getRegularPrice(),
                        variantRequest.getStock(),
                        variantRequest.getIsRepresentative() != null ? variantRequest.getIsRepresentative() : false
                );
                
                variant.setOptions(variantOptions);
                product.getVariants().add(variant);
            }
        } else if (request.getVariants() != null) {
            // variants만 제공된 경우 (옵션 그룹은 유지하고 variant만 업데이트)
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 8. Product 저장
        Product savedProduct = productRepository.save(product);

        int newTotalStock = sumVariantStock(savedProduct);
        boolean stockChanged = hasVariantUpdate && previousTotalStock != newTotalStock;
        boolean displayOnlyChange = request.getDisplayStatus() != null
                && !hasInfoChange
                && !hasVariantUpdate;

        if (!displayOnlyChange) {
            processingHistoryService.moveToPendingReviewIfNeeded(savedProduct);
            if (stockChanged && !hasInfoChange) {
                processingHistoryService.recordStockUpdated(
                        savedProduct, savedProduct.getDisplayStatus(), newTotalStock);
            } else if (hasInfoChange || hasVariantUpdate) {
                processingHistoryService.recordBrandInfoUpdated(
                        savedProduct, savedProduct.getDisplayStatus());
            }
            productRepository.save(savedProduct);
        }

        // 9. 응답 생성
        return ProductDto.UpdateProductResponse.builder()
                .productId(savedProduct.getProductId())
                .productNumber(savedProduct.getProductNumber())
                .message("상품이 성공적으로 수정되었습니다.")
                .build();
    }

    /**
     * 상품의 품절 상태 계산
     */
    private String calculateStockStatus(Product product, String requestedStockStatus) {
        // 강제 품절 처리된 경우
        if (Boolean.TRUE.equals(product.getIsOutOfStockForced())) {
            return "OUT_OF_STOCK";
        }
        
        // 모든 variant의 재고 확인
        boolean hasStock = product.getVariants().stream()
                .anyMatch(variant -> variant.getStock() > 0);
        
        return hasStock ? "IN_STOCK" : "OUT_OF_STOCK";
    }
    
    /**
     * Product 엔티티를 ProductListItem DTO로 변환
     */
    private ProductDto.ProductListItem convertToProductListItem(Product product, String stockStatus) {
        // 진열 상태
        ProductDisplayStatus displayStatus = product.getDisplayStatus() != null
                ? product.getDisplayStatus()
                : ProductDisplayStatus.DISPLAY;

        // 등록일/수정일 포맷팅 (ISO 8601 형식)
        String createdAtStr = product.getCreatedAt() != null
                ? product.getCreatedAt().toString()
                : null;
        String modifiedAtStr = product.getModifiedAt() != null
                ? product.getModifiedAt().toString()
                : createdAtStr;

        // 공구 상태 (더미) — productId 기준으로 순환
        ProductGroupBuyStatus[] groupBuyStatuses = ProductGroupBuyStatus.values();
        ProductGroupBuyStatus groupBuyStatus = groupBuyStatuses[
                (int) (Math.floorMod(product.getProductId() != null ? product.getProductId() : 0L, groupBuyStatuses.length))
        ];

        return ProductDto.ProductListItem.builder()
                .productId(product.getProductId())
                .productNumber(product.getProductNumber())
                .sellerProductCode(product.getSellerProductCode())
                .thumbnailUrl(product.getThumbnailUrl())
                .name(product.getName())
                .regularPrice(product.getRegularPrice())
                .createdAt(createdAtStr)
                .modifiedAt(modifiedAtStr)
                .displayStatus(displayStatus)
                .groupBuyStatus(groupBuyStatus)
                .stockStatus(stockStatus)
                .isOutOfStockForced(product.getIsOutOfStockForced())
                .build();
    }
    
    /**
     * Product 엔티티를 ProductDetailResponse DTO로 변환
     */
    private ProductDto.ProductDetailResponse convertToProductDetailResponse(Product product, String stockStatus) {
        // 등록일 포맷팅 (ISO 8601 형식)
        String createdAtStr = product.getCreatedAt() != null 
                ? product.getCreatedAt().toString() 
                : null;
        
        // 대표 이미지 URL (product.thumbnail_url 값)
        String representativeImageUrl = product.getThumbnailUrl();
        
        // 커버 이미지 URL 목록 (product_image 테이블에서 order >= 1인 이미지들)
        List<String> coverImageUrls = product.getProductImages().stream()
                .filter(image -> image.getOrder() != null && image.getOrder() >= 1)
                .sorted(Comparator.comparing(ProductImage::getOrder))
                .map(ProductImage::getUrl)
                .collect(Collectors.toList());
        
        // 옵션 그룹 목록 변환
        List<ProductDto.OptionGroupInfo> optionGroups = product.getOptionGroups().stream()
                .map(group -> {
                    List<ProductDto.OptionInfo> options = group.getOptions().stream()
                            .map(option -> ProductDto.OptionInfo.builder()
                                    .optionId(option.getOptionId())
                                    .name(option.getName())
                                    .price(option.getPrice())
                                    .build())
                            .collect(Collectors.toList());
                    
                    return ProductDto.OptionGroupInfo.builder()
                            .optionGroupId(group.getOptionGroupId())
                            .name(group.getName())
                            .options(options)
                            .build();
                })
                .collect(Collectors.toList());
        
        // Variant 목록 변환
        List<ProductDto.VariantInfo> variants = product.getVariants().stream()
                .map(variant -> {
                    List<Long> optionIds = variant.getOptions().stream()
                            .map(ProductOption::getOptionId)
                            .collect(Collectors.toList());
                    
                    return ProductDto.VariantInfo.builder()
                            .variantId(variant.getVariantId())
                            .name(variant.getName())
                            .regularPrice(variant.getRegularPrice())
                            .stock(variant.getStock())
                            .isRepresentative(variant.getIsRepresentative())
                            .optionIds(optionIds)
                            .build();
                })
                .collect(Collectors.toList());
        
        return ProductDto.ProductDetailResponse.builder()
                .productId(product.getProductId())
                .productNumber(product.getProductNumber())
                .marketId(product.getMarket() != null ? product.getMarket().getId() : null)
                .marketName(product.getMarket() != null ? product.getMarket().getMarketName() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .name(product.getName())
                .sellerProductCode(product.getSellerProductCode())
                .representativeImageUrl(representativeImageUrl)
                .coverImageUrls(coverImageUrls)
                .regularPrice(product.getRegularPrice())
                .gender(product.getGender())
                .displayStatus(product.getDisplayStatus())
                .hideReasonType(product.getHideReasonType())
                .hideDetail(product.getHideDetail())
                .latestHideInfo(processingHistoryService.getLatestHideInfo(product))
                .processingHistory(processingHistoryService.getHistoryItems(product.getProductId()))
                .isOutOfStockForced(product.getIsOutOfStockForced())
                .isRecommended(product.getIsRecommended())
                .productNotice(product.getProductNotice())
                .description(product.getDescription())
                .createdAt(createdAtStr)
                .optionGroups(optionGroups)
                .variants(variants)
                .build();
    }

    private void applySellerDisplayStatusChange(
            Product product,
            ProductDisplayStatus previous,
            ProductDisplayStatus next
    ) {
        if (next == null) {
            return;
        }
        if (next == previous) {
            return;
        }
        product.setDisplayStatus(next);
        if (next == ProductDisplayStatus.HIDDEN) {
            product.setHideReasonType(ProductHideReasonType.BRAND_REQUEST);
            product.setHideDetail(null);
            processingHistoryService.recordHidden(
                    product, previous, ProductHideReasonType.BRAND_REQUEST, null, null);
        } else if (next == ProductDisplayStatus.DISPLAY) {
            product.setHideReasonType(null);
            product.setHideDetail(null);
            processingHistoryService.recordRedisplayed(product, previous, null);
        } else if (next == ProductDisplayStatus.HIDE_REQUEST) {
            product.setHideReasonType(ProductHideReasonType.BRAND_REQUEST);
            product.setHideDetail(null);
            processingHistoryService.recordHideRequested(product, previous);
        } else if (next == ProductDisplayStatus.PENDING_REVIEW) {
            processingHistoryService.recordPendingReview(product, previous);
        }
    }

    private int sumVariantStock(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return 0;
        }
        return product.getVariants().stream()
                .mapToInt(v -> v.getStock() != null ? v.getStock() : 0)
                .sum();
    }

    private boolean hasProductInfoChange(ProductDto.UpdateProductRequest request) {
        return request.getCategoryId() != null
                || request.getName() != null
                || request.getSellerProductCode() != null
                || request.getIsOutOfStockForced() != null
                || request.getRegularPrice() != null
                || request.getGender() != null
                || request.getDescription() != null
                || request.getProductNotice() != null
                || request.getRepresentativeImageUrl() != null
                || request.getCoverImageUrls() != null;
    }

    private boolean hasAnyUpdate(ProductDto.UpdateProductRequest request) {
        return hasProductInfoChange(request)
                || request.getDisplayStatus() != null
                || (request.getOptionGroups() != null && request.getVariants() != null);
    }

    private ProductDisplayStatus resolveDisplayStatusFilter(String displayStatus) {
        if (displayStatus == null || displayStatus.isBlank() || "ALL".equalsIgnoreCase(displayStatus)) {
            return null;
        }
        try {
            return ProductDisplayStatus.valueOf(displayStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 진열 상태입니다.");
        }
    }
}

