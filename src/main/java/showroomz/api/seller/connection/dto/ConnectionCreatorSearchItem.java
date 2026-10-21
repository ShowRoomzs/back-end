package showroomz.api.seller.connection.dto;

import showroomz.domain.member.creator.type.CreatorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.connection.type.ConnectionStatus;

@Getter
public class ConnectionCreatorSearchItem {

    @Schema(description = "인플루언서(크리에이터) ID", example = "12")
    private final Long creatorId;

    @Schema(description = "쇼룸명", example = "민지의 쇼룸")
    private final String showroomName;

    @Schema(description = "쇼룸(SNS) 팔로워 수", example = "12000")
    private final Integer followerCount;

    @Schema(description = "프로필 이미지 URL — 없으면 null", example = "https://cdn.example.com/profiles/12.png", nullable = true)
    private final String profileImageUrl;

    @Schema(description = "현재 연결 상태 — null이면 요청 가능([요청] 버튼), REQUESTED/CONNECTED면 " +
            "[요청] 버튼 대신 상태 배지 표시(§13-6). 재요청이 허용되는 REJECTED/DISCONNECTED 이력은 " +
            "연결 이력이 없는 것과 동일하게 null로 내려간다",
            example = "REQUESTED", nullable = true)
    private final ConnectionStatus connectionStatus;

    @Schema(description = "지금 연결을 요청할 수 있는 쇼룸인가 — false 면 [요청] 비활성 + 「현재 연결 요청을 받을 수 없는 쇼룸」"
            + "(정지된 쇼룸 · ui-partner-15 rev.2 · 1017 설계서 8-6). 정지 사실 · 사유는 내리지 않는다", example = "true")
    private final boolean connectable;

    @Schema(description = "connectable=false 의 이유 코드 — UNAVAILABLE(상대 계정 사정). 요청 가능하면 null", nullable = true)
    private final String unavailableReason;

    public ConnectionCreatorSearchItem(
            Long creatorId,
            String showroomName,
            Integer followerCount,
            String profileImageUrl,
            ConnectionStatus connectionStatus) {
        this(creatorId, showroomName, followerCount, profileImageUrl, connectionStatus, CreatorStatus.ACTIVE);
    }

    /** JPQL 생성자 — 인플루언서 상태를 함께 받아 요청 가능 여부를 정한다. */
    public ConnectionCreatorSearchItem(
            Long creatorId,
            String showroomName,
            Integer followerCount,
            String profileImageUrl,
            ConnectionStatus connectionStatus,
            CreatorStatus creatorStatus) {
        this.creatorId = creatorId;
        this.showroomName = showroomName;
        this.followerCount = followerCount;
        this.profileImageUrl = profileImageUrl;
        this.connectionStatus = connectionStatus;
        this.connectable = creatorStatus != CreatorStatus.SUSPENDED;
        this.unavailableReason = this.connectable ? null : UNAVAILABLE;
    }

    public static final String UNAVAILABLE = "UNAVAILABLE";
}
