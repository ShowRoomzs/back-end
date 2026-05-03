package showroomz.api.seller.productannouncement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;

import java.util.List;

@Getter
@Setter
@Schema(description = "상품 공지 노출 상태 일괄 변경 요청")
public class SellerProductAnnouncementBulkStatusRequest {

    @NotEmpty
    @Schema(description = "대상 공지 ID 목록", example = "[1, 2]")
    private List<Long> announcementIds;

    @NotNull
    @Schema(description = "변경할 노출 상태", example = "HIDDEN")
    private ProductAnnouncementDisplayStatus displayStatus;
}
