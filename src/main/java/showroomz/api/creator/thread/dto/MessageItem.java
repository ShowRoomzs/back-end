package showroomz.api.creator.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.thread.dto.MessageCardResponse;
import showroomz.domain.message.type.MessageType;
import showroomz.domain.message.type.ParticipantType;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class MessageItem {

    @Schema(description = "메시지 ID", example = "1001")
    private final Long messageId;

    @Schema(description = "발신자 구분", example = "CREATOR")
    private final ParticipantType senderType;

    @Schema(description = "내가 보낸 메시지인지 여부 — FE 말풍선 좌우 정렬에 사용", example = "true")
    private final boolean mine;

    @Schema(description = "본문 — 첨부만 전송된 경우 null(§13-11)", example = "네, 확인했습니다", nullable = true)
    private final String content;

    @Schema(description = "첨부 목록 — sortOrder 순으로 정렬됨(§4-5)")
    private final List<AttachmentSummary> attachments;

    @Schema(description = "전송 시각 — 첨부만 있는 메시지는 마지막 첨부 기준(§13-11)", example = "2026-08-08T14:22:10")
    private final LocalDateTime createdAt;

    @Schema(description = "메시지 종류 — TEXT(말풍선) · SYSTEM(시스템 카드). 카드는 가운데 정렬 카드로 그린다. "
            + "카드를 그리지 않는 화면은 content(카드 제목)를 말풍선으로 보여도 된다", example = "TEXT")
    private final MessageType messageType;

    @Schema(description = "시스템 카드 — messageType = SYSTEM일 때만. 운영자 개인 정보는 담기지 않는다", nullable = true)
    private final MessageCardResponse card;

    public MessageItem(Long messageId, ParticipantType senderType, boolean mine, String content,
                        List<AttachmentSummary> attachments, LocalDateTime createdAt) {
        this(messageId, senderType, mine, content, attachments, createdAt, MessageType.TEXT, null);
    }

    public MessageItem(Long messageId, ParticipantType senderType, boolean mine, String content,
                        List<AttachmentSummary> attachments, LocalDateTime createdAt,
                        MessageType messageType, MessageCardResponse card) {
        this.messageId = messageId;
        this.senderType = senderType;
        this.mine = mine;
        this.content = content;
        this.attachments = attachments;
        this.createdAt = createdAt;
        this.messageType = messageType;
        this.card = card;
    }
}
