package showroomz.api.admin.productannouncement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.productannouncement.entity.ProductAnnouncement;
import showroomz.domain.productannouncement.type.ExposureType;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "상품 공지 목록 항목")
public class AdminProductAnnouncementListItem {

    private final Long id;
    private final String category;
    private final String title;
    private final ExposureType exposureType;
    private final ProductAnnouncementDisplayStatus displayStatus;
    private final boolean displayPeriodSet;
    private final LocalDateTime displayStartDate;
    private final LocalDateTime displayEndDate;
    private final boolean popup;
    private final LocalDateTime createdAt;

    public static AdminProductAnnouncementListItem from(ProductAnnouncement e) {
        return AdminProductAnnouncementListItem.builder()
                .id(e.getId())
                .category(e.getCategory())
                .title(e.getTitle())
                .exposureType(e.getExposureType())
                .displayStatus(e.getDisplayStatus())
                .displayPeriodSet(e.isDisplayPeriodSet())
                .displayStartDate(e.getDisplayStartDate())
                .displayEndDate(e.getDisplayEndDate())
                .popup(e.isPopup())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
