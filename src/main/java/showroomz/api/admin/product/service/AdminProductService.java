package showroomz.api.admin.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.product.DTO.AdminProductDto;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminProductService {

    private final ProductRepository productRepository;

    /**
     * 상품 추천 상태 변경
     */
    public AdminProductDto.UpdateRecommendationResponse updateRecommendation(
            Long productId,
            AdminProductDto.UpdateRecommendationRequest request
    ) {
        // 상품 조회
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 추천 상태 변경
        product.setIsRecommended(request.getIsRecommended());

        // 저장 (JPA 변경 감지로 자동 저장되지만 명시적으로 저장)
        Product savedProduct = productRepository.save(product);

        // 응답 생성
        return AdminProductDto.UpdateRecommendationResponse.builder()
                .productId(savedProduct.getProductId())
                .productNumber(savedProduct.getProductNumber())
                .isRecommended(savedProduct.getIsRecommended())
                .message("상품 추천 상태가 성공적으로 변경되었습니다.")
                .build();
    }
}
