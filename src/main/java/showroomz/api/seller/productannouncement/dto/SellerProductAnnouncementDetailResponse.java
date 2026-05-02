package showroomz.api.seller.productannouncement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.product.entity.Product;
import showroomz.domain.productannouncement.entity.ProductAnnouncement;
import showroomz.domain.productannouncement.entity.ProductAnnouncementTarget;
import showroomz.domain.productannouncement.type.ExposureType;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "상품 공지 상세 응답")
public class SellerProductAnnouncementDetailResponse {

    private final Long id;
    private final String category;
    private final String title;
    private final String content;
    private final ExposureType exposureType;
    private final boolean displayPeriodSet;
    private final LocalDateTime displayStartDate;
    private final LocalDateTime displayEndDate;
    private final boolean popup;
    private final ProductAnnouncementDisplayStatus displayStatus;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final List<TargetProductItem> targetProducts;

    @Getter
    @Builder
    @Schema(description = "지정 노출 대상 상품")
    public static class TargetProductItem {
        private final Long productId;
        private final String name;
        private final String thumbnailUrl;

        public static TargetProductItem from(Product p) {
            return TargetProductItem.builder()
                    .productId(p.getProductId())
                    .name(p.getName())
                    .thumbnailUrl(p.getThumbnailUrl())
                    .build();
        }
    }

    public static SellerProductAnnouncementDetailResponse from(ProductAnnouncement e) {
        List<TargetProductItem> targets = e.getTargets().stream()
                .map(ProductAnnouncementTarget::getProduct)
                .map(TargetProductItem::from)
                .toList();
        return SellerProductAnnouncementDetailResponse.builder()
                .id(e.getId())
                .category(e.getCategory())
                .title(e.getTitle())
                .content(e.getContent())
                .exposureType(e.getExposureType())
                .displayPeriodSet(e.isDisplayPeriodSet())
                .displayStartDate(e.getDisplayStartDate())
                .displayEndDate(e.getDisplayEndDate())
                .popup(e.isPopup())
                .displayStatus(e.getDisplayStatus())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getModifiedAt())
                .targetProducts(targets)
                .build();
    }
}
