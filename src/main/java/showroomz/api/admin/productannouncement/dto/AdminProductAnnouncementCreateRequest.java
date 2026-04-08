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
@Schema(description = "상품 공지 등록 요청")
public class AdminProductAnnouncementCreateRequest {

    @NotBlank
    @Schema(description = "카테고리", example = "배송")
    private String category;

    @NotBlank
    @Schema(description = "제목", example = "설 연휴 배송 안내")
    private String title;

    @NotBlank
    @Schema(description = "본문", example = "1/25~1/27 출고 지연...")
    private String content;

    @NotNull
    @Schema(description = "노출 범위", example = "ALL")
    private ExposureType exposureType;

    @NotNull
    @Schema(description = "노출 기간 사용 여부", example = "false")
    private Boolean displayPeriodSet;

    @Schema(description = "노출 시작 일시 (displayPeriodSet=true일 때 권장)")
    private LocalDateTime displayStartDate;

    @Schema(description = "노출 종료 일시 (displayPeriodSet=true일 때 권장)")
    private LocalDateTime displayEndDate;

    @NotNull
    @Schema(description = "팝업 여부", example = "false")
    private Boolean popup;

    @NotNull
    @Schema(description = "노출 상태", example = "DISPLAY")
    private ProductAnnouncementDisplayStatus displayStatus;

    @Schema(description = "지정 노출(SPECIFIC) 시 대상 상품 ID 목록")
    private List<Long> targetProductIds;
}
