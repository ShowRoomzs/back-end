package showroomz.api.seller.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class ThreadSummaryResponse {

    @Schema(description = "전체 스레드 안 읽은 수 합계 — GNB 배지용(폴링 대상)")
    private final long unreadCount;

    public ThreadSummaryResponse(long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
