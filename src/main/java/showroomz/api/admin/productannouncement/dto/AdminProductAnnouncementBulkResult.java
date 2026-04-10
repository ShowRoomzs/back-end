package showroomz.api.admin.productannouncement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "일괄 처리 결과")
public class AdminProductAnnouncementBulkResult {

    @Schema(description = "처리된 행 수", example = "3")
    private final int affectedCount;
}
