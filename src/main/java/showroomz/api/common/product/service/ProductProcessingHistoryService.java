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

    /**
     * 미진열(HIDDEN) 상태일 때만 가장 최근 미진열 이력을 반환합니다.
     */
    @Transactional(readOnly = true)
    public ProductProcessingHistoryDto.LatestHideInfo getLatestHideInfo(Product product) {
        if (product == null || product.getDisplayStatus() != ProductDisplayStatus.HIDDEN) {
            return null;
        }

        return historyRepository
                .findFirstByProduct_ProductIdAndHistoryTypeOrderByCreatedAtDesc(
                        product.getProductId(), ProductProcessingHistoryType.HIDDEN)
                .map(this::toLatestHideInfo)
                .orElseGet(() -> {
                    if (product.getHideReasonType() == null && !StringUtils.hasText(product.getHideDetail())) {
                        return null;
                    }
                    return ProductProcessingHistoryDto.LatestHideInfo.builder()
                            .hideReasonType(product.getHideReasonType())
                            .hideReasonDescription(
                                    product.getHideReasonType() != null
                                            ? product.getHideReasonType().getDescription()
                                            : null)
                            .hideDetail(product.getHideDetail())
                            .hiddenAt(null)
                            .processorName(null)
                            .build();
                });
    }

    private ProductProcessingHistoryDto.LatestHideInfo toLatestHideInfo(ProductProcessingHistory history) {
        String processorName = null;
        if (history.getProcessedBy() != null) {
            processorName = sellerRepository.findById(history.getProcessedBy())
                    .map(Seller::getEmail)
                    .filter(StringUtils::hasText)
                    .orElse(null);
        }

        return ProductProcessingHistoryDto.LatestHideInfo.builder()
                .hideReasonType(history.getHideReasonType())
                .hideReasonDescription(
                        history.getHideReasonType() != null
                                ? history.getHideReasonType().getDescription()
                                : null)
                .hideDetail(history.getHideDetail())
                .hiddenAt(history.getCreatedAt() != null ? history.getCreatedAt().toString() : null)
                .processorName(processorName)
                .build();
    }

    private List<ProductProcessingHistoryDto.HistoryItem> toHistoryItems(List<ProductProcessingHistory> histories) {
        Set<Long> processorIds = histories.stream()
                .map(ProductProcessingHistory::getProcessedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Seller> processors = processorIds.isEmpty()
                ? Map.of()
                : sellerRepository.findAllById(processorIds).stream()
                .collect(Collectors.toMap(Seller::getId, s -> s, (a, b) -> a));

        return histories.stream()
                .map(h -> toHistoryItem(h, processors))
                .collect(Collectors.toList());
    }

    private ProductProcessingHistoryDto.HistoryItem toHistoryItem(
            ProductProcessingHistory history,
            Map<Long, Seller> processors
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

        String processorName = resolveProcessorDisplayName(history, processors);

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

    /**
     * 미진열 처리(HIDDEN)는 운영자 이메일, 그 외 어드민 처리는 '이름 운영자'로 표시.
     */
    private String resolveProcessorDisplayName(
            ProductProcessingHistory history,
            Map<Long, Seller> processors
    ) {
        if (history.getProcessedBy() == null) {
            return null;
        }
        Seller processor = processors.get(history.getProcessedBy());
        if (processor == null) {
            return null;
        }
        if (history.getHistoryType() == ProductProcessingHistoryType.HIDDEN) {
            return StringUtils.hasText(processor.getEmail()) ? processor.getEmail() : null;
        }
        if (StringUtils.hasText(processor.getName())) {
            return processor.getName() + " 운영자";
        }
        return null;
    }
}
