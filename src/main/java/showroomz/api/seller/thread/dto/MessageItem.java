package showroomz.api.seller.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.message.type.ParticipantType;

import java.time.LocalDateTime;

@Getter
public class MessageItem {

    @Schema(description = "메시지 ID")
    private final Long messageId;

    @Schema(description = "발신자 구분")
    private final ParticipantType senderType;

    @Schema(description = "내가 보낸 메시지인지 여부 — FE 말풍선 좌우 정렬에 사용")
    private final boolean mine;

    @Schema(description = "본문 — 첨부만 전송된 경우 null(§13-11, P3)")
    private final String content;

    @Schema(description = "전송 시각")
    private final LocalDateTime createdAt;

    public MessageItem(Long messageId, ParticipantType senderType, boolean mine, String content, LocalDateTime createdAt) {
        this.messageId = messageId;
        this.senderType = senderType;
        this.mine = mine;
        this.content = content;
        this.createdAt = createdAt;
    }
}
