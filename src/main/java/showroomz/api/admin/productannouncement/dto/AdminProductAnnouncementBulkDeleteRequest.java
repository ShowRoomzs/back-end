package showroomz.api.admin.productannouncement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "상품 공지 일괄 삭제 요청")
public class AdminProductAnnouncementBulkDeleteRequest {

    @NotEmpty
    @Schema(description = "삭제할 공지 ID 목록", example = "[1, 2, 3]")
    private List<Long> announcementIds;
}
