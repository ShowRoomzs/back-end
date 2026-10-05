package showroomz.api.admin.thread.type;

import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.user.type.UserStatus;

/**
 * 채널 상대 회원의 상태 — 브랜드({@code MarketStatus})와 인플루언서({@code UserStatus})의 값을 한 축으로 맞춘다.
 * 스레드 상태가 아니라 이 값으로 쓰기 가능 여부를 가른다(36 설계 2-3) — 운영팀 채널은 휴면에 빠지지 않는다.
 */
public enum AdminChannelMemberStatus {
    ACTIVE,
    DORMANT,
    SUSPENDED,
    WITHDRAWN;

    public static AdminChannelMemberStatus of(MarketStatus status) {
        if (status == null) {
            return ACTIVE;
        }
        return switch (status) {
            case ACTIVE -> ACTIVE;
            case DORMANT -> DORMANT;
            case SUSPENDED -> SUSPENDED;
            case WITHDRAWN -> WITHDRAWN;
        };
    }

    public static AdminChannelMemberStatus of(UserStatus status) {
        if (status == null) {
            return ACTIVE;
        }
        return switch (status) {
            case NORMAL -> ACTIVE;
            case DORMANT -> DORMANT;
            case SUSPENDED -> SUSPENDED;
            case WITHDRAWN -> WITHDRAWN;
        };
    }

    /** 탈퇴 회원은 받을 사람이 로그인할 수 없다 — 열람만 된다. 정지는 사유를 이 채널에서 설명할 수 있어야 해 쓰기를 둔다. */
    public boolean isWritable() {
        return this != WITHDRAWN;
    }
}
