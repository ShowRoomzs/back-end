package showroomz.api.seller.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.connection.type.ConnectionStatus;

import java.time.LocalDateTime;

@Getter
public class ThreadListItem {

    @Schema(description = "스레드 ID", example = "55")
    private final Long threadId;

    @Schema(description = "상대 표시명 — 운영자 채널이면 \"SHOWROOMZ 운영팀\" 고정, 아니면 상대 쇼룸명",
            example = "민지의 쇼룸")
    private final String counterpartName;

    @Schema(description = "상대 프로필 이미지 URL",
            example = "https://s3.ap-northeast-2.amazonaws.com/bucket/creator/12.jpg", nullable = true)
    private final String counterpartImageUrl;

    @Schema(description = "운영자 고정 채널 여부 — true면 목록 최상단 고정(§13-3)", example = "false")
    private final boolean operatorChannel;

    @Schema(description = "현재 연결 상태 — [계약 작성] 버튼 게이트(§13-5)에 사용", example = "CONNECTED")
    private final ConnectionStatus connectionStatus;

    @Schema(description = "최근 메시지 1줄 미리보기", example = "촬영본 보내드렸습니다", nullable = true)
    private final String lastMessagePreview;

    @Schema(description = "최근 메시지 시각", example = "2026-08-08T14:22:10", nullable = true)
    private final LocalDateTime lastMessageAt;

    @Schema(description = "안 읽은 메시지 수", example = "3")
    private final long unreadCount;

    public ThreadListItem(Long threadId, String counterpartName, String counterpartImageUrl,
                           boolean operatorChannel, ConnectionStatus connectionStatus,
                           String lastMessagePreview, LocalDateTime lastMessageAt, long unreadCount) {
        this.threadId = threadId;
        this.counterpartName = counterpartName;
        this.counterpartImageUrl = counterpartImageUrl;
        this.operatorChannel = operatorChannel;
        this.connectionStatus = connectionStatus;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
    }
}
