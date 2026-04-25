package showroomz.api.admin.notice.dto;

import lombok.Builder;
import lombok.Getter;
import showroomz.domain.notice.entity.Notice;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminNoticeListResponse {

    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static AdminNoticeListResponse from(Notice notice) {
        return AdminNoticeListResponse.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .createdAt(notice.getCreatedAt())
                .modifiedAt(notice.getModifiedAt())
                .build();
    }
}
