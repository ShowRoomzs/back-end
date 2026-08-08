package showroomz.api.seller.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class ConnectionCodeCheckResponse {

    @Schema(description = "일치하는 연결코드 존재 여부")
    private final boolean found;

    @Schema(description = "일치하는 인플루언서 ID (found=false면 null)")
    private final Long creatorId;

    @Schema(description = "일치하는 쇼룸명 (found=false면 null)")
    private final String showroomName;

    private ConnectionCodeCheckResponse(boolean found, Long creatorId, String showroomName) {
        this.found = found;
        this.creatorId = creatorId;
        this.showroomName = showroomName;
    }

    public static ConnectionCodeCheckResponse found(Long creatorId, String showroomName) {
        return new ConnectionCodeCheckResponse(true, creatorId, showroomName);
    }

    public static ConnectionCodeCheckResponse notFound() {
        return new ConnectionCodeCheckResponse(false, null, null);
    }
}
