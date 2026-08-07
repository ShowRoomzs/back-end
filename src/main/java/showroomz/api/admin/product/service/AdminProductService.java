package showroomz.api.admin.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.product.DTO.AdminProductDto;
import showroomz.api.common.product.service.ProductProcessingHistoryService;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductHideReasonType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ProductProcessingHistoryService processingHistoryService;

    /**
     * 상품 추천 상태 변경
     */
    public AdminProductDto.UpdateRecommendationResponse updateRecommendation(
            Long productId,
            AdminProductDto.UpdateRecommendationRequest request
    ) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        product.setIsRecommended(request.getIsRecommended());
        Product savedProduct = productRepository.save(product);

        return AdminProductDto.UpdateRecommendationResponse.builder()
                .productId(savedProduct.getProductId())
                .productNumber(savedProduct.getProductNumber())
                .isRecommended(savedProduct.getIsRecommended())
                .message("상품 추천 상태가 성공적으로 변경되었습니다.")
                .build();
    }

    /**
     * 관리자 미진열 / 다시 진열 처리
     */
    public AdminProductDto.UpdateDisplayStatusResponse updateDisplayStatus(
            Long productId,
            AdminProductDto.UpdateDisplayStatusRequest request,
            Long adminSellerId
    ) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        applyDisplayStatusChange(product, request.getDisplayStatus(), request.getHideReasonType(),
                request.getHideDetail(), adminSellerId);
        productRepository.save(product);

        return AdminProductDto.UpdateDisplayStatusResponse.builder()
                .productId(product.getProductId())
                .productNumber(product.getProductNumber())
                .displayStatus(product.getDisplayStatus())
                .hideReasonType(product.getHideReasonType())
                .hideDetail(product.getHideDetail())
                .message(buildMessage(request.getDisplayStatus(), 1))
                .build();
    }

    /**
     * 관리자 미진열 / 다시 진열 일괄 처리
     */
    public AdminProductDto.BulkUpdateDisplayStatusResponse bulkUpdateDisplayStatus(
            AdminProductDto.BulkUpdateDisplayStatusRequest request,
            Long adminSellerId
    ) {
        if (request.getProductIds() == null || request.getProductIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "상품 ID 목록은 필수입니다.");
        }

        List<Product> products = productRepository.findAllById(request.getProductIds());
        if (products.size() != request.getProductIds().size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        List<Long> processed = new ArrayList<>();
        for (Product product : products) {
            applyDisplayStatusChange(product, request.getDisplayStatus(), request.getHideReasonType(),
                    request.getHideDetail(), adminSellerId);
            productRepository.save(product);
            processed.add(product.getProductId());
        }

        return AdminProductDto.BulkUpdateDisplayStatusResponse.builder()
                .productIds(processed)
                .count(processed.size())
                .displayStatus(request.getDisplayStatus())
                .message(buildMessage(request.getDisplayStatus(), processed.size()))
                .build();
    }

    private void applyDisplayStatusChange(
            Product product,
            ProductDisplayStatus next,
            ProductHideReasonType hideReasonType,
            String hideDetail,
            Long adminSellerId
    ) {
        if (next != ProductDisplayStatus.HIDDEN && next != ProductDisplayStatus.DISPLAY) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "관리자는 HIDDEN(미진열) 또는 DISPLAY(다시 진열)만 처리할 수 있습니다.");
        }

        ProductDisplayStatus previous = product.getDisplayStatus();

        // 이미 동일 상태면 이력 저장 없이 성공 처리 (멱등)
        if (next == previous) {
            return;
        }

        if (next == ProductDisplayStatus.HIDDEN) {
            if (hideReasonType == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "미진열 처리 시 hideReasonType은 필수입니다.");
            }
            product.setDisplayStatus(ProductDisplayStatus.HIDDEN);
            product.setHideReasonType(hideReasonType);
            product.setHideDetail(hideDetail != null && !hideDetail.isBlank() ? hideDetail.trim() : null);
            processingHistoryService.recordHidden(
                    product, previous, hideReasonType, product.getHideDetail(), adminSellerId);
            return;
        }

        product.setDisplayStatus(ProductDisplayStatus.DISPLAY);
        product.setHideReasonType(null);
        product.setHideDetail(null);
        processingHistoryService.recordRedisplayed(product, previous, adminSellerId);
    }

    private String buildMessage(ProductDisplayStatus status, int count) {
        if (status == ProductDisplayStatus.HIDDEN) {
            return count == 1
                    ? "상품이 미진열 처리되었습니다."
                    : count + "개 상품이 미진열 처리되었습니다.";
        }
        return count == 1
                ? "상품이 다시 진열되었습니다."
                : count + "개 상품이 다시 진열되었습니다.";
    }
}
