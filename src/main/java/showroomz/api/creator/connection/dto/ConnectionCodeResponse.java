package showroomz.api.creator.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ConnectionCodeResponse {

    @Schema(description = "연결코드")
    private final String connectionCode;

    @Schema(description = "(재)발급 시각")
    private final LocalDateTime issuedAt;

    public ConnectionCodeResponse(String connectionCode, LocalDateTime issuedAt) {
        this.connectionCode = connectionCode;
        this.issuedAt = issuedAt;
    }
}
