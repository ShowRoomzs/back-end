package showroomz.api.admin.notice.dto;

import lombok.Builder;
import lombok.Getter;
import showroomz.domain.notice.entity.Notice;

@Getter
@Builder
public class AdminNoticeDetailResponse {

    private String title;
    private String content;

    public static AdminNoticeDetailResponse from(Notice notice) {
        return AdminNoticeDetailResponse.builder()
                .title(notice.getTitle())
                .content(notice.getContent())
                .build();
    }
}
