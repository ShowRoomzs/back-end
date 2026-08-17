package showroomz.api.app.setting.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.service.IdentityVerificationService;
import showroomz.api.app.auth.service.IdentityVerificationService.IdentityVerification;
import showroomz.api.app.setting.DTO.AccountInfoResponse;
import showroomz.api.app.setting.DTO.IdentityReverifyRequest;
import showroomz.api.app.setting.DTO.NotificationSettingRequest;
import showroomz.api.app.setting.DTO.NotificationSettingResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.history.entity.UserConsentHistory;
import showroomz.domain.history.repository.UserConsentHistoryRepository;
import showroomz.domain.history.type.ConsentType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * C15 설정 — 알림 설정과 회원정보(C15-2).
 *
 * <p>여기서 지키려는 것은 두 가지다. 광고성 정보 수신은 <b>값이 실제로 바뀔 때만</b> 이력이
 * 남아야 한다(철회 일시가 통지 근거가 되므로 같은 값 재전송에 갱신되면 근거가 어긋난다).
 * 그리고 본인인증 결과인 이름·생년월일·휴대폰번호는 <b>서버에서</b> 마스킹돼 나가야 한다.
 */
@ExtendWith(MockitoExtension.class)
class SettingServiceTest {

    private static final String USERNAME = "mia";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserConsentHistoryRepository userConsentHistoryRepository;
    @Mock
    private IdentityVerificationService identityVerificationService;

    @InjectMocks
    private SettingService settingService;

    private Users user;

    private Users newUser() {
        LocalDateTime now = LocalDateTime.now();
        Users created = new Users(USERNAME, "미아", "mia@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        ReflectionTestUtils.setField(created, "id", 7L);
        return created;
    }

    private void givenUser() {
        user = newUser();
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
    }

    private NotificationSettingRequest request(Boolean followPostPushAgree, Boolean marketingAgree) {
        NotificationSettingRequest request = new NotificationSettingRequest();
        ReflectionTestUtils.setField(request, "followPostPushAgree", followPostPushAgree);
        ReflectionTestUtils.setField(request, "marketingAgree", marketingAgree);
        return request;
    }

    private IdentityReverifyRequest reverifyRequest(boolean agreeConsent) {
        IdentityReverifyRequest request = new IdentityReverifyRequest();
        ReflectionTestUtils.setField(request, "agreeConsent", agreeConsent);
        return request;
    }

    @Nested
    @DisplayName("알림 설정 (C15)")
    class NotificationSettings {

        @Test
        @DisplayName("가입 직후 팔로우 알림은 켜진 상태로 내려간다 — 설정을 만진 적 없는 사용자도 기본값이 있다")
        void defaultsToEnabled() {
            givenUser();

            NotificationSettingResponse response = settingService.getNotificationSettings(USERNAME);

            assertThat(response.isFollowPostPushAgree()).isTrue();
            assertThat(response.isMarketingAgree()).isFalse();
            assertThat(response.getMarketingAgreeChangedAt()).isNull();
        }

        /** 구 데이터에는 임베디드 값이 비어 있을 수 있다 — 그때도 화면이 꺼진 토글을 그리면 안 된다. */
        @Test
        @DisplayName("알림 설정 값이 비어 있어도 팔로우 알림은 켜진 것으로 본다")
        void nullSettingIsTreatedAsEnabled() {
            givenUser();
            ReflectionTestUtils.setField(user, "notificationSetting", null);

            assertThat(settingService.getNotificationSettings(USERNAME).isFollowPostPushAgree()).isTrue();
        }

        @Test
        @DisplayName("보내지 않은 토글은 건드리지 않는다 — 하나만 눌러도 그것만 보내면 된다")
        void omittedToggleIsLeftAlone() {
            givenUser();
            user.updateNotificationSettings(false);

            settingService.updateNotificationSettings(USERNAME, request(null, true));

            assertThat(user.getNotificationSetting().isFollowPostPushAgree()).isFalse();
            assertThat(user.isMarketingAgree()).isTrue();
        }

        @Test
        @DisplayName("광고성 정보 수신을 철회하면 동의 이력이 남는다 — 철회 일시가 통지 근거다")
        void marketingWithdrawalIsRecorded() {
            givenUser();
            user.updateMarketingAgree(true);

            settingService.updateNotificationSettings(USERNAME, request(true, false));

            ArgumentCaptor<UserConsentHistory> captor = ArgumentCaptor.forClass(UserConsentHistory.class);
            verify(userConsentHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getConsentType()).isEqualTo(ConsentType.MARKETING);
            assertThat(captor.getValue().isAgreed()).isFalse();
            assertThat(user.isMarketingAgree()).isFalse();
        }

        /**
         * 같은 값을 다시 보냈다고 이력이 쌓이면 "마지막으로 철회한 시각"이 실제 철회 시점과 달라진다.
         * 앱이 설정 화면을 저장할 때마다 전체 값을 보내는 구현이면 매번 걸리는 경로다.
         */
        @Test
        @DisplayName("같은 값을 다시 보내면 이력을 남기지 않는다")
        void unchangedMarketingValueLeavesNoHistory() {
            givenUser();
            user.updateMarketingAgree(true);
            LocalDateTime changedAt = user.getMarketingAgreeChangedAt();

            settingService.updateNotificationSettings(USERNAME, request(true, true));

            verify(userConsentHistoryRepository, never()).save(any());
            assertThat(user.getMarketingAgreeChangedAt()).isEqualTo(changedAt);
        }

        @Test
        @DisplayName("광고성 정보 수신 값을 아예 보내지 않으면 동의 상태도 이력도 그대로다")
        void omittedMarketingValueChangesNothing() {
            givenUser();
            user.updateMarketingAgree(true);

            settingService.updateNotificationSettings(USERNAME, request(false, null));

            verify(userConsentHistoryRepository, never()).save(any());
            assertThat(user.isMarketingAgree()).isTrue();
        }
    }

    @Nested
    @DisplayName("회원정보 (C15-2)")
    class AccountInfo {

        @Test
        @DisplayName("이름·생년월일·휴대폰번호는 마스킹해서 내려간다 — 원본은 응답에 실리지 않는다")
        void personalDataIsMasked() {
            givenUser();
            user.setName("김수민");
            user.setBirthday("1998-04-12");
            user.setPhoneNumber("010-1234-5678");

            AccountInfoResponse response = settingService.getAccountInfo(USERNAME);

            assertThat(response.getName()).isEqualTo("김수*");
            assertThat(response.getBirthday()).isEqualTo("1998.04.**");
            assertThat(response.getPhoneNumber()).isEqualTo("010-****-5678");
        }

        @Test
        @DisplayName("본인인증 전이면 마스킹할 값이 없어 null로 내려가고 인증 시각도 비어 있다")
        void unverifiedUserHasNoMaskedValues() {
            givenUser();

            AccountInfoResponse response = settingService.getAccountInfo(USERNAME);

            assertThat(response.getName()).isNull();
            assertThat(response.getBirthday()).isNull();
            assertThat(response.getPhoneNumber()).isNull();
            assertThat(response.getIdentityVerifiedAt()).isNull();
        }

        @Test
        @DisplayName("재인증은 PASS 결과로 회원정보를 덮어쓰고 마스킹한 값을 돌려준다")
        void reverifyOverwritesIdentityFromVerificationResult() {
            givenUser();
            LocalDateTime verifiedAt = LocalDateTime.now();
            given(identityVerificationService.verify(USERNAME)).willReturn(
                    new IdentityVerification("홍길동", "1998-04-12", "FEMALE", "01000000000", verifiedAt));

            AccountInfoResponse response = settingService.reverifyIdentity(USERNAME, reverifyRequest(true));

            assertThat(user.getName()).isEqualTo("홍길동");
            assertThat(user.getPhoneNumber()).isEqualTo("01000000000");
            assertThat(user.getIdentityVerifiedAt()).isEqualTo(verifiedAt);
            assertThat(response.getName()).isEqualTo("홍길*");
            assertThat(response.getPhoneNumber()).isEqualTo("010-****-0000");
        }

        /** 가입 시 동의와 별개의 새 수집 행위라 매번 이력에 남는다 — 같은 값이어도 누적된다. */
        @Test
        @DisplayName("재인증 동의는 매번 이력에 남는다")
        void reverifyConsentIsRecordedEveryTime() {
            givenUser();
            given(identityVerificationService.verify(USERNAME)).willReturn(
                    new IdentityVerification("홍길동", "1998-04-12", "FEMALE", "01000000000", LocalDateTime.now()));

            settingService.reverifyIdentity(USERNAME, reverifyRequest(true));

            ArgumentCaptor<UserConsentHistory> captor = ArgumentCaptor.forClass(UserConsentHistory.class);
            verify(userConsentHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getConsentType()).isEqualTo(ConsentType.IDENTITY_VERIFICATION);
            assertThat(captor.getValue().isAgreed()).isTrue();
        }

        @Test
        @DisplayName("동의하지 않으면 인증도 이력도 남기지 않고 거절한다")
        void reverifyWithoutConsentIsRejected() {
            givenUser();

            assertThatThrownBy(() -> settingService.reverifyIdentity(USERNAME, reverifyRequest(false)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDENTITY_CONSENT_REQUIRED);

            verify(userConsentHistoryRepository, never()).save(any());
            verify(identityVerificationService, never()).verify(any());
        }
    }

    @Nested
    @DisplayName("접근 조건")
    class Access {

        @Test
        @DisplayName("탈퇴한 회원의 설정은 열리지 않는다")
        void withdrawnUserIsRejected() {
            givenUser();
            user.updateStatus(UserStatus.WITHDRAWN);

            assertThatThrownBy(() -> settingService.getNotificationSettings(USERNAME))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_WITHDRAWN);
        }

        @Test
        @DisplayName("정지된 회원은 설정을 볼 수 있다 — 막는 것은 탈퇴뿐이다")
        void suspendedUserIsAllowed() {
            givenUser();
            user.updateStatus(UserStatus.SUSPENDED);

            assertThat(settingService.getNotificationSettings(USERNAME)).isNotNull();
        }

        @Test
        @DisplayName("없는 회원이면 404를 낸다")
        void unknownUserIsRejected() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

            assertThatThrownBy(() -> settingService.getAccountInfo(USERNAME))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }
}
