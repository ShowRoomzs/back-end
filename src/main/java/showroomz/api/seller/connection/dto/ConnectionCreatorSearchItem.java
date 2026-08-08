package showroomz.api.seller.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.connection.type.ConnectionStatus;

@Getter
public class ConnectionCreatorSearchItem {

    @Schema(description = "인플루언서(크리에이터) ID", example = "12")
    private final Long creatorId;

    @Schema(description = "쇼룸명", example = "민지의 쇼룸")
    private final String showroomName;

    @Schema(description = "현재 연결 상태 — null이면 연결 이력 없음(요청 가능), REQUESTED/CONNECTED면 [요청] 버튼 대신 상태 배지 표시(§13-6)",
            example = "REQUESTED", nullable = true)
    private final ConnectionStatus connectionStatus;

    public ConnectionCreatorSearchItem(Long creatorId, String showroomName, ConnectionStatus connectionStatus) {
        this.creatorId = creatorId;
        this.showroomName = showroomName;
        this.connectionStatus = connectionStatus;
    }
}
