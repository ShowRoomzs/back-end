package showroomz.api.seller.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.connection.type.ConnectionStatus;

@Getter
public class ConnectionCodeCheckResponse {

    @Schema(description = "일치하는 연결코드 존재 여부", example = "true")
    private final boolean found;

    @Schema(description = "일치하는 인플루언서 ID (found=false면 null)", example = "12", nullable = true)
    private final Long creatorId;

    @Schema(description = "일치하는 쇼룸명 (found=false면 null)", example = "민지의 쇼룸", nullable = true)
    private final String showroomName;

    @Schema(description = "쇼룸(SNS) 팔로워 수 — B5 정보 카드의 \"팔로워 8천\" 표기용 (found=false면 null)",
            example = "8000", nullable = true)
    private final Integer followerCount;

    @Schema(description = "프로필 이미지 URL — B5 정보 카드 아바타용. 없거나 found=false면 null",
            example = "https://cdn.example.com/profiles/12.png", nullable = true)
    private final String profileImageUrl;

    @Schema(description = "현재 연결 상태 — null이면 요청 가능([요청 보내기] 활성), " +
            "REQUESTED/CONNECTED면 이미 유효한 연결이 있어 중복 요청 불가(§13-6). " +
            "REJECTED/DISCONNECTED 이력은 재요청 가능하므로 null로 내려간다",
            example = "CONNECTED", nullable = true)
    private final ConnectionStatus connectionStatus;

    private ConnectionCodeCheckResponse(boolean found, Long creatorId, String showroomName,
                                        Integer followerCount, String profileImageUrl,
                                        ConnectionStatus connectionStatus) {
        this.found = found;
        this.creatorId = creatorId;
        this.showroomName = showroomName;
        this.followerCount = followerCount;
        this.profileImageUrl = profileImageUrl;
        this.connectionStatus = connectionStatus;
    }

    public static ConnectionCodeCheckResponse found(Long creatorId, String showroomName, Integer followerCount,
                                                    String profileImageUrl, ConnectionStatus connectionStatus) {
        return new ConnectionCodeCheckResponse(true, creatorId, showroomName, followerCount,
                profileImageUrl, connectionStatus);
    }

    public static ConnectionCodeCheckResponse notFound() {
        return new ConnectionCodeCheckResponse(false, null, null, null, null, null);
    }
}
