package showroomz.api.app.notice.dto;

import lombok.Getter;
import showroomz.domain.notice.entity.Notice;

import java.time.LocalDateTime;

@Getter
public class NoticeResponse {

    private final Long id;
    private final String title;
    private final LocalDateTime createdDate;

    public NoticeResponse(Notice notice) {
        this.id = notice.getId();
        this.title = notice.getTitle();
        this.createdDate = notice.getCreatedAt();
    }

    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(notice);
    }
}
