package showroomz.api.admin.productannouncement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import showroomz.domain.productannouncement.type.ExposureType;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Schema(description = "상품 공지 수정 요청")
public class AdminProductAnnouncementUpdateRequest {

    @NotBlank
    private String category;

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    @NotNull
    private ExposureType exposureType;

    @NotNull
    private Boolean displayPeriodSet;

    private LocalDateTime displayStartDate;

    private LocalDateTime displayEndDate;

    @NotNull
    private Boolean popup;

    @NotNull
    private ProductAnnouncementDisplayStatus displayStatus;

    @Schema(description = "지정 노출(SPECIFIC) 시 대상 상품 ID 목록 (기존 매핑 전체 교체)")
    private List<Long> targetProductIds;
}
