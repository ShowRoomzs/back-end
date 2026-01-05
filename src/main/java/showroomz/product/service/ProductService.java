package showroomz.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.market.entity.Market;
import showroomz.market.repository.MarketRepository;
import showroomz.admin.entity.Admin;
import showroomz.admin.repository.AdminRepository;
import showroomz.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.product.DTO.ProductDto;
import showroomz.product.entity.*;
import showroomz.product.repository.BrandRepository;
import showroomz.product.repository.CategoryRepository;
import showroomz.product.repository.ProductRepository;

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
    private final BrandRepository brandRepository;
    private final AdminRepository adminRepository;
    private final MarketRepository marketRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public ProductDto.CreateProductResponse createProduct(String adminEmail, ProductDto.CreateProductRequest request) {
        // 1. 카테고리 조회 및 검증 (카테고리명으로 조회)
        Category category = categoryRepository.findByName(request.getCategoryName())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // 2. 브랜드 조회 (관리자의 마켓에 연결된 브랜드 사용)
        // TODO: 실제로는 마켓별로 브랜드를 관리하거나, 요청에 브랜드 ID를 포함시켜야 할 수 있음
        // 현재는 임시로 기본 브랜드를 사용하거나, 마켓명을 브랜드명으로 사용
        Admin admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Market market = marketRepository.findByAdmin(admin)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        // 마켓명으로 브랜드를 찾거나 생성 (기본 브랜드 전략)
        Brand brand = brandRepository.findAll().stream()
                .filter(b -> market.getMarketName().equals(b.getName()))
                .findFirst()
                .orElseGet(() -> {
                    // 브랜드가 없으면 생성
                    Brand newBrand = new Brand();
                    newBrand.setName(market.getMarketName());
                    return brandRepository.save(newBrand);
                });

        // 3. 상품 번호 생성 (SRZ-YYYYMMDD-XXX 형식)
        String productNumber = generateProductNumber();

        // 4. Product 엔티티 생성
        Product product = new Product();
        product.setCategory(category);
        product.setBrand(brand);
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
        product.setDeliveryFee(request.getDeliveryFee());
        product.setDeliveryFreeThreshold(request.getDeliveryFreeThreshold());
        product.setDeliveryEstimatedDays(request.getDeliveryEstimatedDays());
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
}

