package showroomz.api.creator.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.connection.type.ConnectionStatus;

import java.time.LocalDateTime;

@Getter
public class ConnectionRequestItem {

    @Schema(description = "연결 ID", example = "101")
    private final Long connectionId;

    @Schema(description = "요청 브랜드의 마켓 ID", example = "7")
    private final Long marketId;

    @Schema(description = "요청 브랜드명", example = "쇼룸즈")
    private final String marketName;

    @Schema(description = "브랜드 대표 이미지 URL", example = "https://s3.ap-northeast-2.amazonaws.com/bucket/market/7.jpg")
    private final String marketImageUrl;

    @Schema(description = "연결 상태 — REQUESTED(요청 도착)·CONNECTED(수락)·REJECTED(거절)",
            example = "REQUESTED")
    private final ConnectionStatus status;

    @Schema(description = "요청 시각 — 화면에서 상대 시간(\"2일 전\")으로 변환해 표시", example = "2026-08-06T14:22:10")
    private final LocalDateTime requestedAt;

    @Schema(description = "지금 수락할 수 있는 요청인가 — false 면 [수락] 비활성 + 「현재 수락할 수 없는 요청」(요청 브랜드 정지 · "
            + "ui-studio-13 rev.1 · 1017 설계서 8-6). 상대의 정지 사실은 내리지 않는다 · [거절]은 그대로", example = "true")
    private final boolean acceptable;

    public ConnectionRequestItem(Long connectionId, Long marketId, String marketName,
                                  String marketImageUrl, ConnectionStatus status, LocalDateTime requestedAt,
                                  boolean acceptable) {
        this.connectionId = connectionId;
        this.marketId = marketId;
        this.marketName = marketName;
        this.marketImageUrl = marketImageUrl;
        this.status = status;
        this.requestedAt = requestedAt;
        this.acceptable = acceptable;
    }
}
