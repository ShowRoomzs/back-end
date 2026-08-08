package showroomz.api.creator.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ConnectionRequestItem {

    @Schema(description = "연결 ID")
    private final Long connectionId;

    @Schema(description = "요청 브랜드의 마켓 ID")
    private final Long marketId;

    @Schema(description = "요청 브랜드명")
    private final String marketName;

    @Schema(description = "브랜드 대표 이미지 URL")
    private final String marketImageUrl;

    @Schema(description = "요청 시각 — 화면에서 상대 시간(\"2일 전\")으로 변환해 표시")
    private final LocalDateTime requestedAt;

    public ConnectionRequestItem(Long connectionId, Long marketId, String marketName,
                                  String marketImageUrl, LocalDateTime requestedAt) {
        this.connectionId = connectionId;
        this.marketId = marketId;
        this.marketName = marketName;
        this.marketImageUrl = marketImageUrl;
        this.requestedAt = requestedAt;
    }
}
