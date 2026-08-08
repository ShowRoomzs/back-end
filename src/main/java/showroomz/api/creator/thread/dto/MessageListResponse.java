package showroomz.api.creator.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
public class MessageListResponse {

    @Schema(description = "메시지 목록 — 최신순")
    private final List<MessageItem> content;

    @Schema(description = "다음 페이지 조회에 쓸 cursor(마지막 항목의 messageId) — hasNext=false면 null")
    private final Long nextCursor;

    @Schema(description = "다음 페이지 존재 여부")
    private final boolean hasNext;

    public MessageListResponse(List<MessageItem> content, Long nextCursor, boolean hasNext) {
        this.content = content;
        this.nextCursor = nextCursor;
        this.hasNext = hasNext;
    }
}
