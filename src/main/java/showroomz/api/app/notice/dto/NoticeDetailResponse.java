package showroomz.api.app.notice.dto;

import lombok.Getter;
import showroomz.domain.notice.entity.Notice;

import java.time.LocalDateTime;

@Getter
public class NoticeDetailResponse {

    private final Long id;
    private final String title;
    private final String content;
    private final LocalDateTime createdDate;

    public NoticeDetailResponse(Notice notice) {
        this.id = notice.getId();
        this.title = notice.getTitle();
        this.content = notice.getContent();
        this.createdDate = notice.getCreatedAt();
    }

    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(notice);
    }
}
