package showroomz.api.app.showroom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.repository.PostRepository;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C2 팔로잉 — 통합 테스트.
 *
 * <p>기본 정렬이 "최근 게시물을 올린 쇼룸 순"인데 <b>정렬 기준이 다른 테이블에 있다</b>(게시물).
 * 서비스가 게시물 최신 시각을 따로 조회해 DB 밖에서 정렬하고 손으로 페이징하므로, 실제 순서와
 * 슬라이싱 경계는 여기서만 확인된다.
 *
 * <p>통합 하네스가 {@code open-in-view=false}라, 카드의 쇼룸명·아바타를 지연 로딩에 맡겼다면
 * 응답 직렬화에서 터진다 — 조회 쿼리의 페치 조인도 이 테스트가 함께 지킨다.
 */
@DisplayName("[통합] C2 팔로잉 목록")
class FollowingShowroomsIntegrationTest extends IntegrationTestSupport {

    private static final String FOLLOWING_PATH = "/v1/user/showrooms/following";
    private static final String FOLLOW_PATH = "/v1/user/showrooms/%d/follow";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private PostRepository postRepository;
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
    @DisplayName("팔로우 토글")
    class Toggle {

        @Test
        @DisplayName("팔로우하면 목록에 잡힌다")
        void followedShowroomAppearsInList() throws Exception {
            Long showroomId = createShowroom("소연 뷰티", "soyeon").getId();

            follow(showroomId);

            mockMvc.perform(get(FOLLOWING_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.content[0].showroomId").value(showroomId))
                    .andExpect(jsonPath("$.content[0].showroomName").value("소연 뷰티"))
                    .andExpect(jsonPath("$.content[0].followedAt").isNotEmpty());
        }

        /** 앱이 낙관적 토글이라 같은 요청이 두 번 오는 일이 흔하다 — 목록이 중복되면 안 된다. */
        @Test
        @DisplayName("두 번 팔로우해도 목록에 한 번만 잡힌다")
        void duplicateFollowIsIdempotent() throws Exception {
            Long showroomId = createShowroom("소연 뷰티", "soyeon").getId();

            follow(showroomId);
            follow(showroomId);

            mockMvc.perform(get(FOLLOWING_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        }

        @Test
        @DisplayName("팔로우를 취소하면 목록에서 빠진다")
        void unfollowRemovesFromList() throws Exception {
            Long showroomId = createShowroom("소연 뷰티", "soyeon").getId();
            follow(showroomId);

            mockMvc.perform(delete(FOLLOW_PATH.formatted(showroomId))
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().is2xxSuccessful());

            mockMvc.perform(get(FOLLOWING_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        }

        @Test
        @DisplayName("팔로우하지 않은 쇼룸을 취소해도 오류가 아니다")
        void unfollowWithoutFollowIsAccepted() throws Exception {
            Long showroomId = createShowroom("소연 뷰티", "soyeon").getId();

            mockMvc.perform(delete(FOLLOW_PATH.formatted(showroomId))
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("없는 쇼룸은 팔로우할 수 없다")
        void unknownShowroomIsRejected() throws Exception {
            mockMvc.perform(post(FOLLOW_PATH.formatted(999999L))
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("목록 정렬")
    class Ordering {

        private Long noPost;
        private Long oldPost;
        private Long freshPost;

        @BeforeEach
        void followThree() throws Exception {
            // 팔로우 순서: 게시물 없음 → 오래된 게시물 → 최신 게시물
            noPost = createShowroom("게시물 없는 쇼룸", "nopost").getId();
            oldPost = createShowroom("오래된 게시물 쇼룸", "oldpost").getId();
            freshPost = createShowroom("최신 게시물 쇼룸", "freshpost").getId();

            follow(noPost);
            follow(oldPost);
            follow(freshPost);

            backdateFollow(noPost, LocalDateTime.now().minusDays(30));
            backdateFollow(oldPost, LocalDateTime.now().minusDays(20));
            backdateFollow(freshPost, LocalDateTime.now().minusDays(10));

            publishPost(oldPost, LocalDateTime.now().minusDays(15));
            publishPost(freshPost, LocalDateTime.now().minusHours(2));
        }

        /** 팔로우 시각이 아니라 쇼룸의 최근 게시물 시각이 기준이다. */
        @Test
        @DisplayName("기본 정렬은 최근에 게시물을 올린 쇼룸 순이고 게시물 없는 쇼룸은 뒤로 밀린다")
        void defaultSortIsLatestPost() throws Exception {
            mockMvc.perform(get(FOLLOWING_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].showroomId").value(freshPost))
                    .andExpect(jsonPath("$.content[1].showroomId").value(oldPost))
                    .andExpect(jsonPath("$.content[2].showroomId").value(noPost));
        }

        @Test
        @DisplayName("팔로우 최신순은 가장 최근에 팔로우한 쇼룸부터다")
        void followLatestSort() throws Exception {
            mockMvc.perform(get(FOLLOWING_PATH).param("sort", "FOLLOW_LATEST")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].showroomId").value(freshPost))
                    .andExpect(jsonPath("$.content[2].showroomId").value(noPost));
        }

        @Test
        @DisplayName("팔로우 오래된순은 가장 먼저 팔로우한 쇼룸부터다")
        void followOldestSort() throws Exception {
            mockMvc.perform(get(FOLLOWING_PATH).param("sort", "FOLLOW_OLDEST")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].showroomId").value(noPost))
                    .andExpect(jsonPath("$.content[2].showroomId").value(freshPost));
        }

        @Test
        @DisplayName("페이지를 잘라도 전체 개수는 그대로다")
        void totalSurvivesPaging() throws Exception {
            mockMvc.perform(get(FOLLOWING_PATH).param("sort", "FOLLOW_OLDEST").param("size", "2")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                    .andExpect(jsonPath("$.pageInfo.hasNext").value(true));
        }

        @Test
        @DisplayName("마지막 페이지는 남은 만큼만 준다")
        void lastPageReturnsRemainder() throws Exception {
            mockMvc.perform(get(FOLLOWING_PATH).param("sort", "FOLLOW_OLDEST")
                            .param("size", "2").param("page", "2")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].showroomId").value(freshPost));
        }

        /** 손으로 자르는 코드라 범위를 넘는 페이지에서 터지기 쉽다. */
        @Test
        @DisplayName("범위를 넘는 페이지는 빈 목록을 준다 — 예외로 터지지 않는다")
        void pageBeyondRangeIsEmpty() throws Exception {
            mockMvc.perform(get(FOLLOWING_PATH).param("size", "2").param("page", "9")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(3));
        }
    }

    @Test
    @DisplayName("팔로우한 쇼룸이 없으면 빈 목록을 준다 — 앱이 발견 피드를 그리는 기준이다")
    void emptyWhenNothingFollowed() throws Exception {
        mockMvc.perform(get(FOLLOWING_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
    }

    @Test
    @DisplayName("비로그인은 401 — 팔로잉은 내 목록이다")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get(FOLLOWING_PATH)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 단계 · 픽스처

    private void follow(Long showroomId) throws Exception {
        mockMvc.perform(post(FOLLOW_PATH.formatted(showroomId)).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().is2xxSuccessful());
    }

    /** 팔로우 시각 정렬을 검증하려면 시각을 손으로 벌려야 한다 — 같은 밀리초에 몰리면 순서가 무의미해진다. */
    private void backdateFollow(Long creatorId, LocalDateTime followedAt) {
        jdbc.update("UPDATE creator_follow SET created_at = ? WHERE user_id = ? AND creator_id = ?",
                followedAt, consumer.getId(), creatorId);
    }

    private void publishPost(Long creatorId, LocalDateTime createdAt) {
        Creator showroom = creatorRepository.findById(creatorId).orElseThrow();
        Post post = Post.published(showroom, "본문", new BigDecimal("0.8000"), createdAt);
        Long postId = postRepository.save(post).getId();
        jdbc.update("UPDATE post SET created_at = ? WHERE post_id = ?", createdAt, postId);
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
