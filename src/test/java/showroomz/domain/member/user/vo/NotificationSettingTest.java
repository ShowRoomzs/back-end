package showroomz.domain.member.user.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.member.user.type.NotificationToggle;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C15 광고성 토글 3종(1017 설계서 2-1 · 2-2) — 값이 실제로 바뀔 때만 true(이력 근거) · 켤 때만 동의 일시 · 끄면 일시가 사라진다.
 * 혜택 · 이벤트는 기존 광고성 동의(users.marketing_agree)라 여기서 다루지 않는다.
 */
@DisplayName("NotificationSetting — 토글 · 동의 일시")
class NotificationSettingTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-22T10:00:00");

    @Test
    @DisplayName("기본값 — 새 게시물만 켜짐 · 새 공구 · 마감 임박은 꺼짐 · 동의 일시 없음")
    void defaults() {
        NotificationSetting setting = new NotificationSetting();

        assertThat(setting.isOn(NotificationToggle.FOLLOW_POST)).isTrue();
        assertThat(setting.isOn(NotificationToggle.FOLLOW_GROUP_BUY)).isFalse();
        assertThat(setting.isOn(NotificationToggle.GROUP_BUY_ENDING)).isFalse();
        assertThat(setting.agreedAt(NotificationToggle.FOLLOW_GROUP_BUY)).isNull();
    }

    @Test
    @DisplayName("켜면 true · 동의 일시 기록 / 같은 값 재전송은 false · 일시 그대로")
    void turningOnRecordsAgreedAtOnce() {
        NotificationSetting setting = new NotificationSetting();

        assertThat(setting.set(NotificationToggle.FOLLOW_GROUP_BUY, true, NOW)).isTrue();
        assertThat(setting.agreedAt(NotificationToggle.FOLLOW_GROUP_BUY)).isEqualTo(NOW);

        assertThat(setting.set(NotificationToggle.FOLLOW_GROUP_BUY, true, NOW.plusDays(1))).isFalse();
        assertThat(setting.agreedAt(NotificationToggle.FOLLOW_GROUP_BUY)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("끄면 true · 동의 일시가 지워진다(화면 「수신 동의」 줄이 사라진다)")
    void turningOffClearsAgreedAt() {
        NotificationSetting setting = new NotificationSetting();
        setting.set(NotificationToggle.GROUP_BUY_ENDING, true, NOW);

        assertThat(setting.set(NotificationToggle.GROUP_BUY_ENDING, false, NOW.plusHours(1))).isTrue();
        assertThat(setting.isOn(NotificationToggle.GROUP_BUY_ENDING)).isFalse();
        assertThat(setting.agreedAt(NotificationToggle.GROUP_BUY_ENDING)).isNull();
    }

    @Test
    @DisplayName("토글은 서로 독립이다")
    void togglesAreIndependent() {
        NotificationSetting setting = new NotificationSetting(false);
        setting.set(NotificationToggle.FOLLOW_POST, true, NOW);

        assertThat(setting.isOn(NotificationToggle.FOLLOW_POST)).isTrue();
        assertThat(setting.isOn(NotificationToggle.FOLLOW_GROUP_BUY)).isFalse();
        assertThat(setting.agreedAt(NotificationToggle.GROUP_BUY_ENDING)).isNull();
    }

    @Test
    @DisplayName("혜택 · 이벤트는 이 값 객체가 아니라 광고성 동의 열이 든다")
    void benefitIsNotHere() {
        NotificationSetting setting = new NotificationSetting();

        assertThatThrownBy(() -> setting.isOn(NotificationToggle.BENEFIT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> setting.set(NotificationToggle.BENEFIT, true, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
