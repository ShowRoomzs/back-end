package showroomz.api.creator.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ThreadListItem {

    @Schema(description = "스레드 ID", example = "55")
    private final Long threadId;

    @Schema(description = "상대 표시명 — 운영자 채널이면 \"SHOWROOMZ 운영팀\" 고정, 아니면 상대 브랜드명",
            example = "쇼룸즈")
    private final String counterpartName;

    @Schema(description = "상대 프로필 이미지 URL",
            example = "https://s3.ap-northeast-2.amazonaws.com/bucket/market/7.jpg", nullable = true)
    private final String counterpartImageUrl;

    @Schema(description = "운영자 고정 채널 여부 — true면 목록 최상단 고정(§14-6)", example = "false")
    private final boolean operatorChannel;

    @Schema(description = "계약이 존재하는지 여부 — [계약 확인] 버튼 노출 게이트(§14-2). 계약 도메인 미구현 구간은 항상 false",
            example = "false")
    private final boolean hasContract;

    @Schema(description = "최근 메시지 1줄 미리보기", example = "계약서 확인 부탁드립니다", nullable = true)
    private final String lastMessagePreview;

    @Schema(description = "최근 메시지 시각", example = "2026-08-08T14:22:10", nullable = true)
    private final LocalDateTime lastMessageAt;

    @Schema(description = "안 읽은 메시지 수", example = "2")
    private final long unreadCount;

    public ThreadListItem(Long threadId, String counterpartName, String counterpartImageUrl,
                           boolean operatorChannel, boolean hasContract,
                           String lastMessagePreview, LocalDateTime lastMessageAt, long unreadCount) {
        this.threadId = threadId;
        this.counterpartName = counterpartName;
        this.counterpartImageUrl = counterpartImageUrl;
        this.operatorChannel = operatorChannel;
        this.hasContract = hasContract;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
    }
}
