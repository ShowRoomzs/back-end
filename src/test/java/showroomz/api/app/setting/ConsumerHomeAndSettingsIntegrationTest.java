package showroomz.api.app.setting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C1 홈 요약 · C15 설정 — 로그인 직후 앱이 부르는 "내 정보" 창구 통합 테스트.
 *
 * <p>설정 쪽에서 지키는 핵심은 <b>마스킹이 서버에서 끝난다</b>는 것이다. 원본을 내려보내고 앱이
 * 가리는 방식이면 가린 의미가 없으므로, 응답 본문에 원본 문자열이 <b>한 글자도 없는지</b>를 확인한다 —
 * 필드별 값만 비교하면 다른 필드로 원본이 새는 회귀를 놓친다.
 *
 * <p>홈 쪽은 배지 숫자가 다른 도메인(장바구니·팔로우)을 세는 값이라, 실제로 담고 팔로우한 뒤
 * 숫자가 따라 움직이는지를 본다.
 */
@DisplayName("[통합] C1 홈 요약 · C15 설정")
class ConsumerHomeAndSettingsIntegrationTest extends IntegrationTestSupport {

    private static final String HOME_PATH = "/v1/user/home/summary";
    private static final String NOTIFICATIONS_PATH = "/v1/user/settings/notifications";
    private static final String ACCOUNT_PATH = "/v1/user/settings/account";
    private static final String REVERIFY_PATH = "/v1/user/settings/account/verifications";
    private static final String FOLLOW_PATH = "/v1/user/showrooms/%d/follow";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Users consumer;
    private String userToken;

    @BeforeEach
    void setUpConsumer() {
        consumer = createUser("mia", "미아", RoleType.USER);
        userToken = bearerToken(consumer.getUsername(), RoleType.USER, consumer.getId());
    }

    @Nested
    @DisplayName("홈 요약 (C1)")
    class HomeSummary {

        @Test
        @DisplayName("아무것도 없는 신규 사용자는 두 값이 0이다 — 앱이 빈 상태를 그리는 기준이다")
        void newUserGetsZeros() throws Exception {
            mockMvc.perform(get(HOME_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cartCount").value(0))
                    .andExpect(jsonPath("$.followingCount").value(0));
        }

        @Test
        @DisplayName("팔로우하면 팔로잉 수가 따라 올라간다")
        void followingCountFollowsActualFollows() throws Exception {
            Long first = createShowroom("소연 뷰티", "soyeon").getId();
            Long second = createShowroom("지민 뷰티", "jimin").getId();

            mockMvc.perform(post(FOLLOW_PATH.formatted(first)).header(HttpHeaders.AUTHORIZATION, userToken));
            mockMvc.perform(post(FOLLOW_PATH.formatted(second)).header(HttpHeaders.AUTHORIZATION, userToken));

            mockMvc.perform(get(HOME_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.followingCount").value(2))
                    .andExpect(jsonPath("$.cartCount").value(0));
        }

        @Test
        @DisplayName("비로그인은 401")
        void anonymousIsRejected() throws Exception {
            mockMvc.perform(get(HOME_PATH)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("알림 설정 (C15)")
    class Notifications {

        @Test
        @DisplayName("가입 직후 팔로우 알림은 켜져 있고 광고 수신은 꺼져 있다")
        void defaultsAreReturned() throws Exception {
            mockMvc.perform(get(NOTIFICATIONS_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.followPostPushAgree").value(true))
                    .andExpect(jsonPath("$.marketingAgree").value(false))
                    .andExpect(jsonPath("$.marketingAgreeChangedAt").doesNotExist());
        }

        @Test
        @DisplayName("토글을 끄면 다음 조회에 반영된다")
        void toggleIsPersisted() throws Exception {
            patchNotifications(Map.of("followPostPushAgree", false));

            mockMvc.perform(get(NOTIFICATIONS_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.followPostPushAgree").value(false));
        }

        /** Boolean Wrapper라 보내지 않은 값은 건드리지 않는다 — 토글 하나만 눌러도 그것만 보내면 된다. */
        @Test
        @DisplayName("보내지 않은 토글은 그대로 남는다")
        void omittedToggleIsPreserved() throws Exception {
            patchNotifications(Map.of("followPostPushAgree", false));

            patchNotifications(Map.of("marketingAgree", true));

            mockMvc.perform(get(NOTIFICATIONS_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.followPostPushAgree").value(false))
                    .andExpect(jsonPath("$.marketingAgree").value(true));
        }

        /** 철회 일시가 통지 근거라, 값이 실제로 바뀔 때만 이력이 쌓여야 한다. */
        @Test
        @DisplayName("광고 수신을 켜고 끄면 그때마다 동의 이력이 쌓인다")
        void marketingChangesLeaveHistory() throws Exception {
            patchNotifications(Map.of("marketingAgree", true));
            assertThat(marketingHistoryCount()).isEqualTo(1);

            patchNotifications(Map.of("marketingAgree", false));
            assertThat(marketingHistoryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 값을 다시 보내면 이력이 쌓이지 않는다")
        void unchangedMarketingValueLeavesNoHistory() throws Exception {
            patchNotifications(Map.of("marketingAgree", true));
            assertThat(marketingHistoryCount()).isEqualTo(1);

            patchNotifications(Map.of("marketingAgree", true));
            patchNotifications(Map.of("marketingAgree", true));

            assertThat(marketingHistoryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("광고 수신을 바꾸면 변경 시각이 함께 내려간다")
        void marketingChangeStampsTimestamp() throws Exception {
            patchNotifications(Map.of("marketingAgree", true));

            mockMvc.perform(get(NOTIFICATIONS_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.marketingAgreeChangedAt").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("회원정보 (C15-2)")
    class AccountInfo {

        @Test
        @DisplayName("본인인증 전이면 값이 비어 있다")
        void unverifiedUserHasEmptyValues() throws Exception {
            mockMvc.perform(get(ACCOUNT_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").doesNotExist())
                    .andExpect(jsonPath("$.identityVerifiedAt").doesNotExist());
        }

        /**
         * 필드별 값만 비교하면 다른 필드로 원본이 새는 회귀를 놓친다 —
         * 응답 본문 <b>전체</b>에 원본 문자열이 없어야 한다.
         */
        @Test
        @DisplayName("이름·생년월일·휴대폰번호는 마스킹돼 나가고 원본은 본문 어디에도 없다")
        void personalDataNeverLeavesUnmasked() throws Exception {
            jdbc.update("UPDATE users SET name = ?, birthday = ?, phone_number = ? WHERE user_id = ?",
                    "김수민", "1998-04-12", "01012345678", consumer.getId());

            mockMvc.perform(get(ACCOUNT_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("김수*"))
                    .andExpect(jsonPath("$.birthday").value("1998.04.**"))
                    .andExpect(jsonPath("$.phoneNumber").value("010-****-5678"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("김수민"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("01012345678"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("1998-04-12"))));
        }

        @Test
        @DisplayName("재인증에 동의하면 인증 결과로 회원정보가 채워지고 마스킹돼 내려온다")
        void reverifyFillsIdentity() throws Exception {
            mockMvc.perform(post(REVERIFY_PATH)
                            .header(HttpHeaders.AUTHORIZATION, userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("agreeConsent", true))))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.name").value("홍길*"))
                    .andExpect(jsonPath("$.identityVerifiedAt").isNotEmpty());

            mockMvc.perform(get(ACCOUNT_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.name").value("홍길*"));
        }

        @Test
        @DisplayName("동의하지 않으면 거절되고 회원정보는 그대로다")
        void reverifyWithoutConsentIsRejected() throws Exception {
            mockMvc.perform(post(REVERIFY_PATH)
                            .header(HttpHeaders.AUTHORIZATION, userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("agreeConsent", false))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDENTITY_CONSENT_REQUIRED"));

            mockMvc.perform(get(ACCOUNT_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.identityVerifiedAt").doesNotExist());
        }

        /** 가입 시 동의와 별개의 새 수집 행위라 매번 이력에 남는다 — 같은 값이어도 누적된다. */
        @Test
        @DisplayName("재인증 동의는 부를 때마다 이력에 쌓인다")
        void reverifyConsentAccumulates() throws Exception {
            reverify();
            assertThat(identityHistoryCount()).isEqualTo(1);

            reverify();
            assertThat(identityHistoryCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("접근 조건")
    class Access {

        @Test
        @DisplayName("탈퇴한 회원의 설정은 열리지 않는다")
        void withdrawnUserIsRejected() throws Exception {
            consumer.updateStatus(UserStatus.WITHDRAWN);
            userRepository.save(consumer);

            mockMvc.perform(get(NOTIFICATIONS_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"));
        }

        /** 막는 것은 탈퇴뿐이다 — 정지된 회원도 자기 설정은 볼 수 있어야 문의로 이어진다. */
        @Test
        @DisplayName("정지된 회원은 설정을 볼 수 있다")
        void suspendedUserIsAllowed() throws Exception {
            consumer.updateStatus(UserStatus.SUSPENDED);
            userRepository.save(consumer);

            mockMvc.perform(get(NOTIFICATIONS_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("비로그인은 설정 어느 창구에도 들어오지 못한다")
        void anonymousIsRejected() throws Exception {
            mockMvc.perform(get(NOTIFICATIONS_PATH)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(ACCOUNT_PATH)).andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ 단계 · 픽스처

    private void patchNotifications(Map<String, Object> body) throws Exception {
        mockMvc.perform(patch(NOTIFICATIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new HashMap<>(body))))
                .andExpect(status().is2xxSuccessful());
    }

    private void reverify() throws Exception {
        mockMvc.perform(post(REVERIFY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("agreeConsent", true))))
                .andExpect(status().is2xxSuccessful());
    }

    private int consentHistoryCount(String consentType) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_consent_history WHERE user_id = ? AND consent_type = ?",
                Integer.class, consumer.getId(), consentType);
        return count == null ? 0 : count;
    }

    private int marketingHistoryCount() {
        return consentHistoryCount("MARKETING");
    }

    private int identityHistoryCount() {
        return consentHistoryCount("IDENTITY_VERIFICATION");
    }

    private Users createUser(String username, String nickname, RoleType roleType) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(
                username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, roleType, now, now));
    }

    private Creator createShowroom(String showroomName, String handle) {
        Users owner = createUser("creator-" + handle, showroomName, RoleType.CREATOR);
        Creator creator = Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + handle)
                .accountId(handle)
                .followerCount(1000)
                .businessEmail("biz@showroomz.test")
                .showroomName(showroomName)
                .build();
        creator.assignShowroomAddressIfAbsent(handle);
        return creatorRepository.save(creator);
    }
}
