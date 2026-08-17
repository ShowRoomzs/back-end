package showroomz.api.creator.showroom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §22-1 쇼룸 관리 — 통합 테스트.
 *
 * <p>여기서 지키는 핵심은 <b>쇼룸 주소(@handle)의 불변성</b>이다. 주소는 등록 완료 시점에 자동 생성되고
 * 그 뒤로 바뀌지 않는다 — 소비자가 공유한 링크와 최근 검색에 남은 쇼룸이 주소로 이어져 있어서,
 * 쇼룸명을 바꿀 때 주소가 따라 바뀌면 그 링크가 전부 끊긴다.
 *
 * <p>중복 검사는 <b>본인 이름을 자기 것으로 알아보는지</b>가 관건이다. 소개글만 고치려고 저장했는데
 * 자기 쇼룸명이 중복이라고 거부되면 화면에서 아무것도 수정할 수 없게 된다.
 */
@DisplayName("[통합] §22-1 쇼룸 관리")
class CreatorShowroomIntegrationTest extends IntegrationTestSupport {

    private static final String PROFILE_PATH = "/v1/creator/showroom/profile";
    private static final String CHECK_NAME_PATH = "/v1/creator/showroom/profile/check-name";
    private static final String STATS_PATH = "/v1/creator/showroom/stats";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;

    private Creator myShowroom;
    private String creatorToken;

    @BeforeEach
    void setUpShowroom() {
        myShowroom = createShowroom("소연 뷰티", "soyeon");
        Users owner = myShowroom.getUser();
        creatorToken = bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());
    }

    @Nested
    @DisplayName("프로필 조회")
    class GetProfile {

        @Test
        @DisplayName("쇼룸명과 주소·전체 URL을 함께 내려준다")
        void profileCarriesHandleAndUrl() throws Exception {
            mockMvc.perform(get(PROFILE_PATH).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.showroomName").value("소연 뷰티"))
                    .andExpect(jsonPath("$.showroomAddress").value("soyeon"))
                    .andExpect(jsonPath("$.showroomUrl").value(
                            org.hamcrest.Matchers.containsString("soyeon")));
        }

        @Test
        @DisplayName("소비자 토큰으로는 열 수 없다 — 쇼룸 관리는 크리에이터 창구다")
        void consumerCannotOpenShowroomManagement() throws Exception {
            Users consumer = createUser("consumer", "미아", RoleType.USER);
            String consumerToken = bearerToken(consumer.getUsername(), RoleType.USER, consumer.getId());

            mockMvc.perform(get(PROFILE_PATH).header(HttpHeaders.AUTHORIZATION, consumerToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("비로그인은 401")
        void anonymousIsRejected() throws Exception {
            mockMvc.perform(get(PROFILE_PATH)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("프로필 수정")
    class UpdateProfile {

        @Test
        @DisplayName("쇼룸명·소개글·인스타그램 URL을 함께 고칠 수 있다")
        void editableFieldsAreUpdated() throws Exception {
            updateProfile(body("소연 스킨케어", "토너 전문 쇼룸입니다", "https://instagram.com/soyeon", null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.showroomName").value("소연 스킨케어"))
                    .andExpect(jsonPath("$.introduction").value("토너 전문 쇼룸입니다"));

            mockMvc.perform(get(PROFILE_PATH).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(jsonPath("$.showroomName").value("소연 스킨케어"));
        }

        /**
         * 주소는 등록 완료 시점에 정해지고 그 뒤로 바뀌지 않는다 — 공유된 링크와 최근 검색에 남은
         * 쇼룸이 주소로 이어져 있어서, 쇼룸명을 바꿀 때 주소가 따라 바뀌면 그 링크가 전부 끊긴다.
         */
        @Test
        @DisplayName("쇼룸명을 바꿔도 쇼룸 주소는 그대로다")
        void handleNeverFollowsTheName() throws Exception {
            updateProfile(body("완전히 다른 이름", null, null, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.showroomAddress").value("soyeon"));
        }

        @Test
        @DisplayName("프로필 이미지를 빈 값으로 보내면 삭제되고 기본 이미지로 돌아간다")
        void blankImageClearsProfileImage() throws Exception {
            updateProfile(body("소연 뷰티", null, null, "https://cdn.showroomz.test/me.jpg"))
                    .andExpect(jsonPath("$.profileImageUrl").value("https://cdn.showroomz.test/me.jpg"));

            updateProfile(body("소연 뷰티", null, null, ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profileImageUrl").doesNotExist());
        }

        @Test
        @DisplayName("특수문자가 섞인 쇼룸명은 거절한다")
        void invalidNameIsRejected() throws Exception {
            updateProfile(body("소연_뷰티", null, null, null))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이미 다른 쇼룸이 쓰는 이름으로는 저장할 수 없다")
        void duplicateNameIsRejected() throws Exception {
            createShowroom("지민 뷰티", "jimin");

            updateProfile(body("지민 뷰티", null, null, null))
                    .andExpect(status().isBadRequest());
        }

        /** 소개글만 고치려고 저장했는데 자기 이름이 중복이라 거부되면 화면에서 아무것도 못 고친다. */
        @Test
        @DisplayName("쇼룸명을 그대로 두고 소개글만 고칠 수 있다 — 자기 이름은 중복이 아니다")
        void keepingOwnNameIsNotDuplicate() throws Exception {
            updateProfile(body("소연 뷰티", "소개글만 고칩니다", null, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.introduction").value("소개글만 고칩니다"));
        }

        @Test
        @DisplayName("https가 아닌 인스타그램 URL은 거절한다")
        void nonHttpsInstagramUrlIsRejected() throws Exception {
            updateProfile(body("소연 뷰티", null, "instagram.com/soyeon", null))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("소개글이 50자를 넘으면 거절한다")
        void tooLongIntroductionIsRejected() throws Exception {
            updateProfile(body("소연 뷰티", "가".repeat(51), null, null))
                    .andExpect(status().isBadRequest());
        }

        /** 거절된 요청이 일부만 반영되면 화면과 서버 값이 갈린다. */
        @Test
        @DisplayName("거절된 요청은 아무 값도 바꾸지 않는다")
        void rejectedRequestChangesNothing() throws Exception {
            updateProfile(body("소연_뷰티", "이 소개글도 저장되면 안 된다", null, null))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get(PROFILE_PATH).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(jsonPath("$.showroomName").value("소연 뷰티"))
                    .andExpect(jsonPath("$.introduction").doesNotExist());
        }
    }

    @Nested
    @DisplayName("쇼룸명 중복 확인")
    class CheckName {

        @Test
        @DisplayName("쓰이지 않는 이름은 사용 가능이다")
        void freeNameIsAvailable() throws Exception {
            mockMvc.perform(get(CHECK_NAME_PATH).param("showroomName", "아무도 안 쓰는 이름")
                            .header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("AVAILABLE"))
                    .andExpect(jsonPath("$.isAvailable").value(true));
        }

        @Test
        @DisplayName("다른 쇼룸이 쓰는 이름은 중복으로 알려준다")
        void takenNameIsDuplicate() throws Exception {
            createShowroom("지민 뷰티", "jimin");

            mockMvc.perform(get(CHECK_NAME_PATH).param("showroomName", "지민 뷰티")
                            .header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("DUPLICATE"))
                    .andExpect(jsonPath("$.isAvailable").value(false));
        }

        /** 저장 화면에서 이름을 건드리지 않은 채 중복 확인을 눌러도 사용 가능으로 나와야 한다. */
        @Test
        @DisplayName("자기 현재 이름은 사용 가능으로 나온다")
        void ownCurrentNameIsAvailable() throws Exception {
            mockMvc.perform(get(CHECK_NAME_PATH).param("showroomName", "소연 뷰티")
                            .header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("AVAILABLE"))
                    .andExpect(jsonPath("$.isAvailable").value(true));
        }

        @Test
        @DisplayName("형식이 어긋난 이름은 형식 오류로 알려준다")
        void invalidFormatIsReported() throws Exception {
            mockMvc.perform(get(CHECK_NAME_PATH).param("showroomName", "소연@뷰티")
                            .header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("INVALID_FORMAT"))
                    .andExpect(jsonPath("$.isAvailable").value(false));
        }
    }

    @Nested
    @DisplayName("쇼룸 통계")
    class Stats {

        /** 방문·게시물이 없는 신규 쇼룸도 화면이 그려져야 한다 — 0이 내려가면 빈 상태를 그릴 수 있다. */
        @Test
        @DisplayName("활동이 없는 쇼룸도 통계가 0으로 내려간다")
        void newShowroomGetsZeroStats() throws Exception {
            mockMvc.perform(get(STATS_PATH).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("비로그인은 통계도 볼 수 없다")
        void anonymousIsRejected() throws Exception {
            mockMvc.perform(get(STATS_PATH)).andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ 단계 · 픽스처

    private org.springframework.test.web.servlet.ResultActions updateProfile(Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(put(PROFILE_PATH)
                .header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    /** null 값도 담아 보내야 "보내지 않음"과 "빈 값"을 구분하는 동작을 태울 수 있다. */
    private Map<String, Object> body(String showroomName, String introduction,
                                     String instagramUrl, String profileImageUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("showroomName", showroomName);
        body.put("introduction", introduction);
        body.put("instagramUrl", instagramUrl);
        body.put("profileImageUrl", profileImageUrl);
        return body;
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
