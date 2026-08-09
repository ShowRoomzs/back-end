package showroomz.api.seller.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.connection.type.ConnectionStatus;

@Getter
public class ConnectResponse {

    @Schema(description = "연결 ID", example = "101")
    private final Long connectionId;

    @Schema(description = "연결 상태 — 신규/재요청 모두 REQUESTED", example = "REQUESTED")
    private final ConnectionStatus status;

    public ConnectResponse(Long connectionId, ConnectionStatus status) {
        this.connectionId = connectionId;
        this.status = status;
    }
}
