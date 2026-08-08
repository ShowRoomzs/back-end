package showroomz.api.seller.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.domain.message.type.ParticipantType;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class MessageItem {

    @Schema(description = "메시지 ID", example = "1001")
    private final Long messageId;

    @Schema(description = "발신자 구분", example = "SELLER")
    private final ParticipantType senderType;

    @Schema(description = "내가 보낸 메시지인지 여부 — FE 말풍선 좌우 정렬에 사용", example = "true")
    private final boolean mine;

    @Schema(description = "본문 — 첨부만 전송된 경우 null(§13-11)", example = "촬영본 보내드렸습니다", nullable = true)
    private final String content;

    @Schema(description = "첨부 목록 — sortOrder 순으로 정렬됨(§4-5)")
    private final List<AttachmentSummary> attachments;

    @Schema(description = "전송 시각 — 첨부만 있는 메시지는 마지막 첨부 기준(§13-11)", example = "2026-08-08T14:22:10")
    private final LocalDateTime createdAt;

    public MessageItem(Long messageId, ParticipantType senderType, boolean mine, String content,
                        List<AttachmentSummary> attachments, LocalDateTime createdAt) {
        this.messageId = messageId;
        this.senderType = senderType;
        this.mine = mine;
        this.content = content;
        this.attachments = attachments;
        this.createdAt = createdAt;
    }
}
