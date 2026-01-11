package showroomz.api.seller.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.global.error.exception.ErrorCode;
import showroomz.api.seller.product.DTO.ProductDto;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.api.seller.category.service.CategoryService;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.product.entity.*;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
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
        product.setSalePrice(request.getSalePrice());
        product.setPurchasePrice(request.getPurchasePrice());
        product.setIsDisplay(request.getIsDisplay() != null ? request.getIsDisplay() : true);
        product.setIsOutOfStockForced(request.getIsOutOfStockForced() != null ? request.getIsOutOfStockForced() : false);
        product.setIsRecommended(false);
        product.setDescription(request.getDescription());
        product.setDeliveryType(request.getDeliveryType() != null ? request.getDeliveryType() : "STANDARD");
        // 배송 정보 기본값 설정 (배포 DB의 NOT NULL 제약조건 대응)
        product.setDeliveryFee(request.getDeliveryFee() != null ? request.getDeliveryFee() : 0);
        product.setDeliveryFreeThreshold(request.getDeliveryFreeThreshold() != null ? request.getDeliveryFreeThreshold() : 0);
        product.setDeliveryEstimatedDays(request.getDeliveryEstimatedDays() != null ? request.getDeliveryEstimatedDays() : 1);
        product.setProductNumber(productNumber);

        // 5. 태그 JSON 변환
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            try {
                String tagsJson = objectMapper.writeValueAsString(request.getTags());
                product.setTags(tagsJson);
            } catch (Exception e) {
                log.error("태그 JSON 변환 실패", e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 6. 상품정보제공고시 JSON 변환
        if (request.getProductNotice() != null) {
            try {
                String productNoticeJson = objectMapper.writeValueAsString(request.getProductNotice());
                product.setProductNotice(productNoticeJson);
            } catch (Exception e) {
                log.error("상품정보제공고시 JSON 변환 실패", e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 7. 대표 이미지 설정
        if (request.getRepresentativeImageUrl() != null) {
            product.setThumbnailUrl(request.getRepresentativeImageUrl());
        }

        // 8. 이미지 저장
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

        // 9. 옵션 그룹 및 옵션 생성
        Map<String, Map<String, ProductOption>> optionMap = new HashMap<>(); // 그룹명 -> (옵션명 -> ProductOption)
        
        if (request.getOptionGroups() != null && !request.getOptionGroups().isEmpty()) {
            for (ProductDto.OptionGroupRequest groupRequest : request.getOptionGroups()) {
                ProductOptionGroup optionGroup = new ProductOptionGroup(product, groupRequest.getName());
                product.getOptionGroups().add(optionGroup);
                
                Map<String, ProductOption> optionsInGroup = new HashMap<>();
                for (String optionName : groupRequest.getOptions()) {
                    ProductOption option = new ProductOption(optionGroup, optionName);
                    optionGroup.getOptions().add(option);
                    optionsInGroup.put(optionName, option);
                }
                optionMap.put(groupRequest.getName(), optionsInGroup);
            }
        }

        // 10. Variant 생성 및 옵션 매핑
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
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
                }
                
                // Variant 이름 생성 (옵션명을 조합)
                String variantName = variantRequest.getOptionNames().stream()
                        .collect(Collectors.joining(" / "));
                
                ProductVariant variant = new ProductVariant(
                        product,
                        variantName,
                        request.getRegularPrice(), // 기본 가격
                        variantRequest.getSalePrice(),
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
                    request.getSalePrice(),
                    0,
                    true
            );
            product.getVariants().add(variant);
        }

        // 11. Product 저장
        Product savedProduct = productRepository.save(product);

        // 12. 응답 생성
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
        String displayStatus = (request != null && request.getDisplayStatus() != null) 
                ? request.getDisplayStatus() : "ALL";
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
                displayStatus,
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
    public ProductDto.ProductListItem getProductById(String adminEmail, Long productId) {
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
        
        // 5. 품절 상태 계산
        String stockStatus = calculateStockStatus(product, null);
        
        // 6. 응답 생성
        return convertToProductListItem(product, stockStatus);
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
                // 요청받은 상태로 명시적 설정
                product.setIsDisplay(request.getIsDisplayed());
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
        if (request.getIsDisplayed()) {
            message = String.format("%d개의 상품이 성공적으로 진열 처리되었습니다.", processedProductIds.size());
        } else {
            message = String.format("%d개의 상품이 성공적으로 미진열 처리되었습니다.", processedProductIds.size());
        }
        
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
        if (request.getIsDisplay() != null) {
            product.setIsDisplay(request.getIsDisplay());
        }
        if (request.getIsOutOfStockForced() != null) {
            product.setIsOutOfStockForced(request.getIsOutOfStockForced());
        }
        if (request.getPurchasePrice() != null) {
            product.setPurchasePrice(request.getPurchasePrice());
        }
        if (request.getRegularPrice() != null) {
            product.setRegularPrice(request.getRegularPrice());
        }
        if (request.getSalePrice() != null) {
            product.setSalePrice(request.getSalePrice());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getDeliveryType() != null) {
            product.setDeliveryType(request.getDeliveryType());
        }
        if (request.getDeliveryFee() != null) {
            product.setDeliveryFee(request.getDeliveryFee());
        }
        if (request.getDeliveryFreeThreshold() != null) {
            product.setDeliveryFreeThreshold(request.getDeliveryFreeThreshold());
        }
        if (request.getDeliveryEstimatedDays() != null) {
            product.setDeliveryEstimatedDays(request.getDeliveryEstimatedDays());
        }

        // 5. 태그 JSON 변환 (제공된 경우)
        if (request.getTags() != null) {
            try {
                String tagsJson = objectMapper.writeValueAsString(request.getTags());
                product.setTags(tagsJson);
            } catch (Exception e) {
                log.error("태그 JSON 변환 실패", e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 6. 상품정보제공고시 JSON 변환 (제공된 경우)
        if (request.getProductNotice() != null) {
            try {
                String productNoticeJson = objectMapper.writeValueAsString(request.getProductNotice());
                product.setProductNotice(productNoticeJson);
            } catch (Exception e) {
                log.error("상품정보제공고시 JSON 변환 실패", e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 7. 이미지 업데이트 (제공된 경우)
        if (request.getRepresentativeImageUrl() != null || request.getCoverImageUrls() != null) {
            // 기존 이미지 삭제
            product.getProductImages().clear();
            
            List<ProductImage> productImages = new ArrayList<>();
            int imageOrder = 0;
            
            // 대표 이미지 추가
            if (request.getRepresentativeImageUrl() != null) {
                product.setThumbnailUrl(request.getRepresentativeImageUrl());
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
        }

        // 8. 옵션 그룹 및 옵션 업데이트 (제공된 경우)
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
                for (String optionName : groupRequest.getOptions()) {
                    ProductOption option = new ProductOption(optionGroup, optionName);
                    optionGroup.getOptions().add(option);
                    optionsInGroup.put(optionName, option);
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
                
                Integer variantRegularPrice = request.getRegularPrice() != null 
                        ? request.getRegularPrice() 
                        : product.getRegularPrice();
                
                ProductVariant variant = new ProductVariant(
                        product,
                        variantName,
                        variantRegularPrice,
                        variantRequest.getSalePrice(),
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

        // 9. Product 저장
        Product savedProduct = productRepository.save(product);

        // 10. 응답 생성
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
        // 진열 상태 변환
        String displayStatus = Boolean.TRUE.equals(product.getIsDisplay()) ? "DISPLAY" : "HIDDEN";
        
        // 가격 정보
        ProductDto.PriceInfo priceInfo = ProductDto.PriceInfo.builder()
                .purchasePrice(product.getPurchasePrice())
                .regularPrice(product.getRegularPrice())
                .salePrice(product.getSalePrice())
                .build();
        
        // 등록일 포맷팅 (ISO 8601 형식)
        String createdAtStr = product.getCreatedAt() != null 
                ? product.getCreatedAt().toString() 
                : null;
        
        return ProductDto.ProductListItem.builder()
                .productId(product.getProductId())
                .productNumber(product.getProductNumber())
                .sellerProductCode(product.getSellerProductCode())
                .thumbnailUrl(product.getThumbnailUrl())
                .name(product.getName())
                .price(priceInfo)
                .createdAt(createdAtStr)
                .displayStatus(displayStatus)
                .stockStatus(stockStatus)
                .isOutOfStockForced(product.getIsOutOfStockForced())
                .build();
    }
}

