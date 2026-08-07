package showroomz.api.common.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import showroomz.api.common.product.dto.ProductProcessingHistoryDto;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductProcessingHistory;
import showroomz.domain.product.repository.ProductProcessingHistoryRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductHideReasonType;
import showroomz.domain.product.type.ProductProcessingHistoryType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductProcessingHistoryService {

    private final ProductProcessingHistoryRepository historyRepository;
    private final SellerRepository sellerRepository;

    public void record(
            Product product,
            ProductProcessingHistoryType historyType,
            ProductDisplayStatus previousDisplayStatus,
            ProductDisplayStatus newDisplayStatus,
            ProductHideReasonType hideReasonType,
            String hideDetail,
            Integer stockQuantity,
            Long processedBy
    ) {
        historyRepository.save(new ProductProcessingHistory(
                product,
                historyType,
                previousDisplayStatus,
                newDisplayStatus,
                hideReasonType,
                StringUtils.hasText(hideDetail) ? hideDetail.trim() : null,
                stockQuantity,
                processedBy
        ));
    }

    public void recordCreated(Product product) {
        record(
                product,
                ProductProcessingHistoryType.PRODUCT_CREATED,
                null,
                product.getDisplayStatus(),
                null,
                null,
                null,
                null
        );
    }

    public void recordBrandInfoUpdated(Product product, ProductDisplayStatus previousStatus) {
        record(
                product,
                ProductProcessingHistoryType.PRODUCT_INFO_UPDATED,
                previousStatus,
                product.getDisplayStatus(),
                null,
                null,
                null,
                null
        );
    }

    public void recordStockUpdated(Product product, ProductDisplayStatus previousStatus, int stockQuantity) {
        record(
                product,
                ProductProcessingHistoryType.STOCK_UPDATED,
                previousStatus,
                product.getDisplayStatus(),
                null,
                null,
                stockQuantity,
                null
        );
    }

    public void recordHidden(
            Product product,
            ProductDisplayStatus previousStatus,
            ProductHideReasonType hideReasonType,
            String hideDetail,
            Long processedBy
    ) {
        record(
                product,
                ProductProcessingHistoryType.HIDDEN,
                previousStatus,
                ProductDisplayStatus.HIDDEN,
                hideReasonType,
                hideDetail,
                null,
                processedBy
        );
    }

    public void recordRedisplayed(Product product, ProductDisplayStatus previousStatus, Long processedBy) {
        record(
                product,
                ProductProcessingHistoryType.REDISPLAYED,
                previousStatus,
                ProductDisplayStatus.DISPLAY,
                null,
                null,
                null,
                processedBy
        );
    }

    public void recordHideRequested(Product product, ProductDisplayStatus previousStatus) {
        record(
                product,
                ProductProcessingHistoryType.HIDE_REQUESTED,
                previousStatus,
                ProductDisplayStatus.HIDE_REQUEST,
                ProductHideReasonType.BRAND_REQUEST,
                null,
                null,
                null
        );
    }

    public void recordPendingReview(Product product, ProductDisplayStatus previousStatus) {
        record(
                product,
                ProductProcessingHistoryType.PENDING_REVIEW,
                previousStatus,
                ProductDisplayStatus.PENDING_REVIEW,
                product.getHideReasonType(),
                product.getHideDetail(),
                null,
                null
        );
    }

    /**
     * 브랜드 요청이 아닌 사유로 미진열된 상품이면 재검토 대기로 전환한다.
     * 이력은 호출측에서 상품 정보 수정/재고 수정으로 기록한다.
     * @return 상태가 변경되었으면 true
     */
    public boolean moveToPendingReviewIfNeeded(Product product) {
        if (product.getDisplayStatus() != ProductDisplayStatus.HIDDEN) {
            return false;
        }
        ProductHideReasonType reason = product.getHideReasonType();
        if (reason == null || !reason.triggersPendingReviewOnBrandEdit()) {
            return false;
        }
        product.setDisplayStatus(ProductDisplayStatus.PENDING_REVIEW);
        return true;
    }

    @Transactional(readOnly = true)
    public List<ProductProcessingHistoryDto.HistoryItem> getHistoryItems(Long productId) {
        List<ProductProcessingHistory> histories =
                historyRepository.findByProduct_ProductIdOrderByCreatedAtDesc(productId);
        return toHistoryItems(histories);
    }

    @Transactional(readOnly = true)
    public ProductProcessingHistoryDto.HistoryListResponse getHistoryList(Long productId) {
        return ProductProcessingHistoryDto.HistoryListResponse.builder()
                .productId(productId)
                .processingHistory(getHistoryItems(productId))
                .build();
    }

    private List<ProductProcessingHistoryDto.HistoryItem> toHistoryItems(List<ProductProcessingHistory> histories) {
        Set<Long> processorIds = histories.stream()
                .map(ProductProcessingHistory::getProcessedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> processorNames = processorIds.isEmpty()
                ? Map.of()
                : sellerRepository.findAllById(processorIds).stream()
                .collect(Collectors.toMap(Seller::getId, Seller::getName, (a, b) -> a));

        return histories.stream()
                .map(h -> toHistoryItem(h, processorNames))
                .collect(Collectors.toList());
    }

    private ProductProcessingHistoryDto.HistoryItem toHistoryItem(
            ProductProcessingHistory history,
            Map<Long, String> processorNames
    ) {
        ProductProcessingHistoryDto.HideReason hideReason = null;
        if (history.getHistoryType() == ProductProcessingHistoryType.HIDDEN
                && history.getHideReasonType() != null) {
            hideReason = ProductProcessingHistoryDto.HideReason.builder()
                    .reasonType(history.getHideReasonType())
                    .reasonDescription(history.getHideReasonType().getDescription())
                    .detail(history.getHideDetail())
                    .build();
        }

        String processorName = null;
        if (history.getProcessedBy() != null) {
            String name = processorNames.get(history.getProcessedBy());
            if (StringUtils.hasText(name)) {
                processorName = name + " 운영자";
            }
        }

        return ProductProcessingHistoryDto.HistoryItem.builder()
                .historyId(history.getId())
                .historyType(history.getHistoryType())
                .title(history.getHistoryType().getDescription())
                .previousDisplayStatus(history.getPreviousDisplayStatus())
                .newDisplayStatus(history.getNewDisplayStatus())
                .hideReason(hideReason)
                .stockQuantity(history.getStockQuantity())
                .processorName(processorName)
                .createdAt(history.getCreatedAt() != null ? history.getCreatedAt().toString() : null)
                .build();
    }
}
