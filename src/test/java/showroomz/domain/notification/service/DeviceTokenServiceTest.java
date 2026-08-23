package showroomz.domain.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.notification.entity.DeviceToken;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.type.DevicePlatform;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 기기 토큰 등록의 핵심은 <b>기기 하나에 주인 한 명</b>이다.
 *
 * <p>같은 폰에서 A로 로그아웃하고 B로 로그인했는데 A의 행이 남아 있으면, A 앞으로 가는 알림이
 * 그 폰으로 계속 간다. 알림 오배송은 개인정보 문제로 번진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceTokenServiceTest {

    private static final String TOKEN = "fcm-token-abc";

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    private DeviceTokenService deviceTokenService;

    @Test
    @DisplayName("처음 보는 토큰은 새로 등록한다")
    void registersNewToken() {
        given(deviceTokenRepository.findByToken(TOKEN)).willReturn(Optional.empty());

        deviceTokenService.register(user(1L), TOKEN, "ANDROID");

        then(deviceTokenRepository).should().save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("이미 다른 계정에 붙어 있던 토큰은 새 행을 만들지 않고 주인을 옮긴다")
    void reassignsExistingTokenToNewOwner() {
        Users previousOwner = user(1L);
        Users newOwner = user(2L);
        DeviceToken existing = new DeviceToken(previousOwner, TOKEN, DevicePlatform.ANDROID, LocalDateTime.now());
        given(deviceTokenRepository.findByToken(TOKEN)).willReturn(Optional.of(existing));

        deviceTokenService.register(newOwner, TOKEN, "ANDROID");

        assertThat(existing.getUser()).isSameAs(newOwner);
        then(deviceTokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("앞뒤 공백은 잘라서 저장한다 — 같은 기기가 두 행이 되면 알림이 두 번 간다")
    void trimsToken() {
        given(deviceTokenRepository.findByToken(TOKEN)).willReturn(Optional.empty());

        deviceTokenService.register(user(1L), "  " + TOKEN + "  ", "IOS");

        then(deviceTokenRepository).should().findByToken(TOKEN);
    }

    @Test
    @DisplayName("모르는 platform 값이 와도 등록은 된다 — 알림보다 값 검증이 앞설 이유가 없다")
    void unknownPlatformStillRegisters() {
        given(deviceTokenRepository.findByToken(TOKEN)).willReturn(Optional.empty());

        deviceTokenService.register(user(1L), TOKEN, "탭북");

        then(deviceTokenRepository).should().save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("토큰이 없는 로그인은 조용히 넘어간다 — 웹은 FCM 토큰을 보내지 않는다")
    void blankTokenIsIgnored() {
        deviceTokenService.register(user(1L), "   ", "WEB");

        then(deviceTokenRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("길이 상한을 넘는 토큰은 저장하지 않는다 — 잘라 넣으면 아무 데도 가지 않는 값이 남는다")
    void oversizedTokenIsRejected() {
        deviceTokenService.register(user(1L), "x".repeat(DeviceToken.MAX_TOKEN_LENGTH + 1), "ANDROID");

        then(deviceTokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("로그아웃은 그 기기 하나만 뺀다")
    void unregisterRemovesOnlyThatDevice() {
        deviceTokenService.unregister(TOKEN);

        then(deviceTokenRepository).should().deleteByToken(TOKEN);
        then(deviceTokenRepository).should(never()).deleteByUser(any());
    }

    private static Users user(Long id) {
        Users user = new Users();
        user.setId(id);
        return user;
    }
}
