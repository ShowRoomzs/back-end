package showroomz.api.admin.thread.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.connection.type.ConnectionType;

/**
 * 어드민 소통 스레드의 좌측 목록 탭(36 설계 3-1) — 운영팀 1:1 채널 두 종류.
 * 「이슈 스레드」 탭은 추후 기획 예정이라 여기에 없다.
 */
@Getter
@RequiredArgsConstructor
public enum AdminChannelTab {
    BRAND(ConnectionType.OPERATOR_MARKET),
    INFLUENCER(ConnectionType.OPERATOR_CREATOR);

    private final ConnectionType connectionType;

    public static AdminChannelTab of(ConnectionType type) {
        return type == ConnectionType.OPERATOR_MARKET ? BRAND : INFLUENCER;
    }
}
