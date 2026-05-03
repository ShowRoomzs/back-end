package showroomz.api.admin.product.inspection.service;

import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import showroomz.api.admin.product.inspection.dto.AdminProductInspectionDto;
import showroomz.api.admin.product.inspection.dto.ProductInspectionSearchCondition;
import showroomz.api.seller.product.DTO.ProductDto;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductImage;
import showroomz.domain.product.entity.ProductInspectionHistory;
import showroomz.domain.product.entity.ProductOption;
import showroomz.domain.product.repository.ProductInspectionHistoryRepository;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductInspectionStatus;
import showroomz.domain.product.type.ProductRejectReasonType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminProductInspectionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProductRepository productRepository;
    private final ProductInspectionHistoryRepository productInspectionHistoryRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminProductInspectionDto.ListItem> search(
            ProductInspectionSearchCondition condition,
            PagingRequest pagingRequest
    ) {
        Pageable pageable = pagingRequest.toPageable();
        Instant createdFrom = null;
        Instant createdTo = null;
        if (condition.getCreatedFrom() != null) {
            createdFrom = condition.getCreatedFrom().atStartOfDay(KST).toInstant();
        }
        if (condition.getCreatedTo() != null) {
            createdTo = condition.getCreatedTo().atTime(LocalTime.MAX).atZone(KST).toInstant();
        }

        Page<Product> page = productRepository.searchAdminInspection(
                condition.getInspectionStatus(),
                createdFrom,
                createdTo,
                condition.getKeyword(),
                condition.getMarketId(),
                pageable
        );

        List<AdminProductInspectionDto.ListItem> items = page.getContent().stream()
                .map(this::toListItem)
                .collect(Collectors.toList());

        return new PageResponse<>(items, page);
    }

    @Transactional(readOnly = true)
    public AdminProductInspectionDto.InspectionDetailResponse getDetail(Long productId) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        Market market = product.getMarket();
        if (market != null) {
            Hibernate.initialize(market.getSeller());
        }

        product.getOptionGroups().size();
        product.getVariants().forEach(v -> v.getOptions().size());

        List<ProductInspectionHistory> histories =
                productInspectionHistoryRepository.findByProduct_ProductIdOrderByCreatedAtAsc(productId);

        return AdminProductInspectionDto.InspectionDetailResponse.builder()
                .product(toProductDetail(product))
                .market(toMarketSummary(market))
                .inspectionHistory(toHistoryItems(histories))
                .build();
    }

    public AdminProductInspectionDto.UpdateStatusResponse updateStatus(
            Long productId,
            AdminProductInspectionDto.UpdateStatusRequest request
    ) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        validateRejectPayload(request.getInspectionStatus(), request.getRejectReasonType(), request.getRejectDetail());

        ProductInspectionStatus previous = product.getInspectionStatus();
        ProductInspectionStatus next = request.getInspectionStatus();

        applyInspectionToProduct(product, next, request.getAdminMemo(), request.getRejectReasonType(), request.getRejectDetail());

        productRepository.save(product);

        ProductInspectionHistory history = new ProductInspectionHistory(
                product,
                previous,
                next,
                next == ProductInspectionStatus.REJECTED ? request.getRejectReasonType() : null,
                next == ProductInspectionStatus.REJECTED ? resolveRejectDetail(request.getRejectReasonType(), request.getRejectDetail()) : null
        );
        productInspectionHistoryRepository.save(history);

        return AdminProductInspectionDto.UpdateStatusResponse.builder()
                .productId(product.getProductId())
                .inspectionStatus(product.getInspectionStatus())
                .message("검수 상태가 반영되었습니다.")
                .build();
    }

    public AdminProductInspectionDto.BulkUpdateStatusResponse bulkUpdateStatus(
            AdminProductInspectionDto.BulkUpdateStatusRequest request
    ) {
        validateRejectPayload(request.getInspectionStatus(), request.getRejectReasonType(), request.getRejectDetail());

        List<Product> products = productRepository.findAllById(request.getProductIds());
        if (products.size() != request.getProductIds().size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        List<Long> processed = new ArrayList<>();
        for (Product product : products) {
            ProductInspectionStatus previous = product.getInspectionStatus();
            ProductInspectionStatus next = request.getInspectionStatus();

            applyInspectionToProduct(product, next, request.getAdminMemo(), request.getRejectReasonType(), request.getRejectDetail());
            productRepository.save(product);

            ProductInspectionHistory history = new ProductInspectionHistory(
                    product,
                    previous,
                    next,
                    next == ProductInspectionStatus.REJECTED ? request.getRejectReasonType() : null,
                    next == ProductInspectionStatus.REJECTED
                            ? resolveRejectDetail(request.getRejectReasonType(), request.getRejectDetail())
                            : null
            );
            productInspectionHistoryRepository.save(history);
            processed.add(product.getProductId());
        }

        return AdminProductInspectionDto.BulkUpdateStatusResponse.builder()
                .productIds(processed)
                .count(processed.size())
                .message(processed.size() + "개 상품의 검수 상태가 반영되었습니다.")
                .build();
    }

    private void validateRejectPayload(
            ProductInspectionStatus next,
            ProductRejectReasonType reasonType,
            String detail
    ) {
        if (next == ProductInspectionStatus.REJECTED) {
            if (reasonType == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "반려 시 rejectReasonType은 필수입니다.");
            }
            if (reasonType == ProductRejectReasonType.OTHER
                    && (!StringUtils.hasText(detail))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "OTHER 유형은 rejectDetail이 필수입니다.");
            }
        }
    }

    private void applyInspectionToProduct(
            Product product,
            ProductInspectionStatus next,
            String adminMemo,
            ProductRejectReasonType reasonType,
            String detail
    ) {
        product.setInspectionStatus(next);
        product.setAdminMemo(adminMemo);
        if (next == ProductInspectionStatus.REJECTED) {
            product.setRejectReasonType(reasonType);
            product.setRejectDetail(resolveRejectDetail(reasonType, detail));
        } else {
            product.setRejectReasonType(null);
            product.setRejectDetail(null);
        }
    }

    private String resolveRejectDetail(ProductRejectReasonType type, String detail) {
        if (type == null) {
            return null;
        }
        if (type == ProductRejectReasonType.OTHER) {
            return detail != null ? detail.trim() : null;
        }
        return type.getDescription();
    }

    private AdminProductInspectionDto.ListItem toListItem(Product product) {
        String createdAtStr = product.getCreatedAt() != null ? product.getCreatedAt().toString() : null;
        Long marketId = product.getMarket() != null ? product.getMarket().getId() : null;
        String marketName = product.getMarket() != null ? product.getMarket().getMarketName() : null;
        return AdminProductInspectionDto.ListItem.builder()
                .productId(product.getProductId())
                .productNumber(product.getProductNumber())
                .name(product.getName())
                .thumbnailUrl(product.getThumbnailUrl())
                .marketId(marketId)
                .marketName(marketName)
                .inspectionStatus(product.getInspectionStatus())
                .createdAt(createdAtStr)
                .build();
    }

    private AdminProductInspectionDto.MarketSummary toMarketSummary(Market market) {
        if (market == null) {
            return null;
        }
        Seller seller = market.getSeller();
        return AdminProductInspectionDto.MarketSummary.builder()
                .marketId(market.getId())
                .marketName(market.getMarketName())
                .csNumber(market.getCsNumber())
                .sellerName(seller != null ? seller.getName() : null)
                .sellerPhone(seller != null ? seller.getPhoneNumber() : null)
                .sellerEmail(seller != null ? seller.getEmail() : null)
                .build();
    }

    private List<AdminProductInspectionDto.HistoryItem> toHistoryItems(List<ProductInspectionHistory> histories) {
        return histories.stream()
                .map(h -> AdminProductInspectionDto.HistoryItem.builder()
                        .historyId(h.getId())
                        .previousStatus(h.getPreviousStatus())
                        .newStatus(h.getNewStatus())
                        .rejectReasonType(h.getRejectReasonType())
                        .rejectDetail(h.getRejectDetail())
                        .createdAt(h.getCreatedAt() != null ? h.getCreatedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private AdminProductInspectionDto.ProductDetail toProductDetail(Product product) {
        List<String> coverImageUrls = product.getProductImages().stream()
                .filter(image -> image.getOrder() != null && image.getOrder() >= 1)
                .sorted(Comparator.comparing(ProductImage::getOrder))
                .map(ProductImage::getUrl)
                .collect(Collectors.toList());

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

        List<ProductDto.VariantInfo> variants = product.getVariants().stream()
                .map(variant -> {
                    List<Long> optionIds = variant.getOptions().stream()
                            .map(ProductOption::getOptionId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    return ProductDto.VariantInfo.builder()
                            .variantId(variant.getVariantId())
                            .name(variant.getName())
                            .regularPrice(variant.getRegularPrice())
                            .salePrice(variant.getSalePrice())
                            .stock(variant.getStock())
                            .isRepresentative(variant.getIsRepresentative())
                            .isDisplay(variant.getIsDisplay())
                            .optionIds(optionIds)
                            .build();
                })
                .collect(Collectors.toList());

        String createdAtStr = product.getCreatedAt() != null ? product.getCreatedAt().toString() : null;

        return AdminProductInspectionDto.ProductDetail.builder()
                .productId(product.getProductId())
                .productNumber(product.getProductNumber())
                .marketId(product.getMarket() != null ? product.getMarket().getId() : null)
                .marketName(product.getMarket() != null ? product.getMarket().getMarketName() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .name(product.getName())
                .sellerProductCode(product.getSellerProductCode())
                .representativeImageUrl(product.getThumbnailUrl())
                .coverImageUrls(coverImageUrls)
                .regularPrice(product.getRegularPrice())
                .salePrice(product.getSalePrice())
                .gender(product.getGender())
                .purchasePrice(product.getPurchasePrice())
                .isDisplay(product.getIsDisplay())
                .isOutOfStockForced(product.getIsOutOfStockForced())
                .isRecommended(product.getIsRecommended())
                .productNotice(product.getProductNotice())
                .description(product.getDescription())
                .tags(product.getTags())
                .deliveryType(product.getDeliveryType())
                .deliveryFee(product.getDeliveryFee())
                .deliveryFreeThreshold(product.getDeliveryFreeThreshold())
                .deliveryEstimatedDays(product.getDeliveryEstimatedDays())
                .createdAt(createdAtStr)
                .inspectionStatus(product.getInspectionStatus())
                .adminMemo(product.getAdminMemo())
                .rejectReasonType(product.getRejectReasonType())
                .rejectDetail(product.getRejectDetail())
                .optionGroups(optionGroups)
                .variants(variants)
                .build();
    }
}
