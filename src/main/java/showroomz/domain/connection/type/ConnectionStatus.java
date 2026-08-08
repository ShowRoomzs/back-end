package showroomz.domain.connection.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ConnectionStatus {
    REQUESTED("요청중"),
    CONNECTED("연결됨"),
    REJECTED("거절"),
    DISCONNECTED("해제");

    private final String description;
}
