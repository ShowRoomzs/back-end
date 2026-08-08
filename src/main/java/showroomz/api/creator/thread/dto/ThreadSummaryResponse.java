package showroomz.api.creator.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class ThreadSummaryResponse {

    @Schema(description = "전체 스레드 안 읽은 수 합계")
    private final long unreadCount;

    @Schema(description = "미처리 연결 요청 건수 — 0건이면 `요청함` 탭 배지 숨김(§14-3)")
    private final long pendingRequestCount;

    public ThreadSummaryResponse(long unreadCount, long pendingRequestCount) {
        this.unreadCount = unreadCount;
        this.pendingRequestCount = pendingRequestCount;
    }
}
