package showroomz.api.admin.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import showroomz.api.admin.notice.type.AdminNoticeStatusFilter;

@Getter
@Setter
public class AdminNoticeListRequest {

    @Schema(description = "상태 탭 (미입력 시 전체)", example = "PUBLISHED",
            allowableValues = {"ALL", "PUBLISHED", "ENDED"})
    private AdminNoticeStatusFilter status = AdminNoticeStatusFilter.ALL;

    @Schema(description = "제목 키워드 검색 (제목 단일 대상)", example = "점검")
    private String keyword;

    /** 미입력·잘못된 값이면 기본 진입 탭인 전체로 본다 (기획 §20-3) */
    public AdminNoticeStatusFilter getStatus() {
        return status == null ? AdminNoticeStatusFilter.ALL : status;
    }
}
