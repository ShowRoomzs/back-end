package showroomz.api.admin.thread.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.connection.type.ConnectionType;

/**
 * 어드민 소통 스레드의 좌측 목록 탭(36 설계 3-1) — 운영팀 1:1 채널 두 종류 + 이슈 스레드(44 이슈 스레드 설계서 4-1).
 * {@link #ISSUE}는 PAIR 연결에 붙는 정산 조정 3자 스레드라 운영팀 채널 종류가 없다({@code connectionType = null}) —
 * {@link #of(ConnectionType)}는 운영팀 채널만 다루고 이슈 탭은 조회 서비스가 분기한다.
 */
@Getter
@RequiredArgsConstructor
public enum AdminChannelTab {
    BRAND(ConnectionType.OPERATOR_MARKET),
    INFLUENCER(ConnectionType.OPERATOR_CREATOR),
    ISSUE(null);

    private final ConnectionType connectionType;

    public static AdminChannelTab of(ConnectionType type) {
        return type == ConnectionType.OPERATOR_MARKET ? BRAND : INFLUENCER;
    }
}
