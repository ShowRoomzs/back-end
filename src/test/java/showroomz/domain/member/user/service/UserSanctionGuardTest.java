package showroomz.domain.member.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserBlockedAction;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정지 중 차단 가드(51-6 설계서 4절) — {@code users.status}만 본다. 정지만 막고, 정상 · 탈퇴(탈퇴는 각 서비스가 따로 막는다) · 휴면은 지나간다.
 * 409 메시지가 시안 안내 줄과 같은 말투인지도 본다 — 앱이 이 문구를 그대로 띄울 수 있다.
 */
class UserSanctionGuardTest {

    private static Users user(UserStatus status) {
        Users user = new Users();
        user.setStatus(status);
        return user;
    }

    @Test
    @DisplayName("정지 회원은 USER_SUSPENDED 로 막히고 메시지에 막힌 행위가 실린다")
    void suspendedUserIsBlocked() {
        assertThatThrownBy(() -> UserSanctionGuard.requireNotSuspended(user(UserStatus.SUSPENDED), UserBlockedAction.LIKE))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_SUSPENDED)
                .hasMessage("이용 정지 중에는 좋아요를 누를 수 없어요.");
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = "SUSPENDED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("정지가 아니면 지나간다")
    void nonSuspendedPasses(UserStatus status) {
        assertThatCode(() -> UserSanctionGuard.requireNotSuspended(user(status), UserBlockedAction.PURCHASE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("회원을 못 읽은 경우(null)는 가드의 일이 아니다 — 지나간다")
    void nullUserPasses() {
        assertThatCode(() -> UserSanctionGuard.requireNotSuspended(null, UserBlockedAction.FOLLOW))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(UserBlockedAction.class)
    @DisplayName("모든 차단 행위의 문구는 「이용 정지 중에는 … 수 없어요.」 꼴이다")
    void everyActionHasMessage(UserBlockedAction action) {
        assertThatThrownBy(() -> UserSanctionGuard.requireNotSuspended(user(UserStatus.SUSPENDED), action))
                .hasMessageStartingWith("이용 정지 중에는 ")
                .hasMessageEndingWith(" 수 없어요.");
    }
}
