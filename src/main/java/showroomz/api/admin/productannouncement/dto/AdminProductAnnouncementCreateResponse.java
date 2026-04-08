package showroomz.api.admin.productannouncement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "상품 공지 등록 응답")
public class AdminProductAnnouncementCreateResponse {

    @Schema(description = "생성된 공지 ID", example = "103")
    private final Long announcementId;

    @Schema(description = "응답 메시지", example = "상품 공지사항이 성공적으로 등록되었습니다.")
    private final String message;
}
