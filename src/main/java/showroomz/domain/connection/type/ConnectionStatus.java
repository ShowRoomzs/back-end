package showroomz.domain.connection.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConnectionStatus {
    REQUESTED("연결요청"),
    CONNECTED("연결됨"),
    DISCONNECTED("해제");

    private final String description;
}
