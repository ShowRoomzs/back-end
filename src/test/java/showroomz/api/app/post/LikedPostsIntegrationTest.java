package showroomz.api.app.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import showroomz.domain.post.entity.PostLike;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C3 좋아요 — 목록 조회 통합 테스트.
 *
 * <p>정렬은 쿼리가 하는 일이라 단위 테스트로는 검증할 수 없어 여기서 실제 순서를 확인한다.
 * 통합 하네스가 {@code open-in-view=false}라, 카드에 붙는 쇼룸명·프로필을 지연 로딩에 맡겼다면
 * 응답 직렬화에서 터진다 — 조회 쿼리의 페치 조인도 이 테스트가 함께 지킨다.
 */
@DisplayName("[통합] C3 좋아요 목록")
class LikedPostsIntegrationTest extends IntegrationTestSupport {

    private static final String PATH = "/v1/user/wishlist/contents";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private String userToken;
    private Long oldestLikedPostId;   // 가장 먼저 좋아요 · 좋아요 5
    private Long middleLikedPostId;   // 두 번째로 좋아요 · 좋아요 40 (가장 인기)
    private Long newestLikedPostId;   // 가장 최근에 좋아요 · 좋아요 1

    @BeforeEach
    void setUpLikes() {
        Users viewer = createUser("mia", "미아");
        userToken = bearerToken(viewer.getUsername(), RoleType.USER, viewer.getId());

        Creator showroom = createShowroom("제니의 뷰티룸");

        oldestLikedPostId = createPublishedPost(showroom, "여름 끝 무너진 장벽", 5);
        middleLikedPostId = createPublishedPost(showroom, "가을 초입, 각질 정리부터", 40);
        newestLikedPostId = createPublishedPost(showroom, "요즘 아침 루틴 정리했어요", 1);

        like(viewer, oldestLikedPostId, LocalDateTime.now().minusDays(30));
        like(viewer, middleLikedPostId, LocalDateTime.now().minusDays(7));
        like(viewer, newestLikedPostId, LocalDateTime.now().minusHours(3));

        // 좋아요했지만 그 사이 내려간 게시물 — 목록에서 빠져야 한다
        Long suspendedPostId = createPublishedPost(showroom, "노출 중지된 게시물", 99);
        like(viewer, suspendedPostId, LocalDateTime.now().minusDays(1));
        suspend(suspendedPostId);

        // 좋아요하지 않은 게시물 — 목록에 없어야 한다
        createPublishedPost(showroom, "누르지 않은 게시물", 100);
    }

    @Test
    @DisplayName("기본 정렬은 최근에 좋아요한 순이고, 내려간 게시물과 누르지 않은 게시물은 빠진다")
    void defaultSortIsMostRecentlyLiked() throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                .andExpect(jsonPath("$.content[0].post.postId").value(newestLikedPostId))
                .andExpect(jsonPath("$.content[1].post.postId").value(middleLikedPostId))
                .andExpect(jsonPath("$.content[2].post.postId").value(oldestLikedPostId))
                .andExpect(jsonPath("$.content[0].contentType").value("GENERAL"))
                .andExpect(jsonPath("$.content[0].post.showroomName").value("제니의 뷰티룸"));
    }

    @Test
    @DisplayName("오래된순은 가장 먼저 좋아요한 게시물부터 나온다")
    void oldestSortStartsFromFirstLike() throws Exception {
        mockMvc.perform(get(PATH).param("sort", "LIKED_OLDEST").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].post.postId").value(oldestLikedPostId))
                .andExpect(jsonPath("$.content[2].post.postId").value(newestLikedPostId));
    }

    @Test
    @DisplayName("좋아요 많은순은 내가 누른 시각이 아니라 게시물의 총 좋아요 수를 본다")
    void mostLikedSortUsesPostLikeCount() throws Exception {
        mockMvc.perform(get(PATH).param("sort", "MOST_LIKED").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].post.postId").value(middleLikedPostId))
                .andExpect(jsonPath("$.content[0].post.likeCount").value(40))
                .andExpect(jsonPath("$.content[2].post.postId").value(newestLikedPostId));
    }

    @Test
    @DisplayName("공구 먼저 정렬은 공구 게시물이 없는 지금 기본 정렬과 같다 — 계약만 먼저 열어 둔다")
    void groupBuyFirstFallsBackToRecentWhileNoGroupBuyExists() throws Exception {
        mockMvc.perform(get(PATH).param("sort", "GROUP_BUY_FIRST").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].post.postId").value(newestLikedPostId))
                .andExpect(jsonPath("$.content[2].post.postId").value(oldestLikedPostId));
    }

    @Test
    @DisplayName("목록의 항목은 좋아요 상태이고 일반 게시물은 하트가 잠기지 않는다")
    void itemsAreLikedAndUnlocked() throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].post.isLiked").value(true))
                .andExpect(jsonPath("$.content[0].post.likeLocked").value(false));
    }

    @Test
    @DisplayName("페이지를 잘라도 전체 개수는 그대로다 — 상단 카운트가 스크롤에 흔들리면 안 된다")
    void totalResultsSurvivesPaging() throws Exception {
        mockMvc.perform(get(PATH).param("size", "2").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(true));

        mockMvc.perform(get(PATH).param("size", "2").param("page", "2")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].post.postId").value(oldestLikedPostId));
    }

    @Test
    @DisplayName("비로그인은 401 — 앱은 목록 대신 로그인 유도 화면을 그린다")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 픽스처

    private Users createUser(String username, String nickname) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(
                username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now));
    }

    private Creator createShowroom(String showroomName) {
        Users owner = createUser("creator-" + showroomName, showroomName);
        return creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/showroomz")
                .accountId("showroomz")
                .followerCount(1000)
                .businessEmail("biz@showroomz.test")
                .showroomName(showroomName)
                .build());
    }

    private Long createPublishedPost(Creator showroom, String content, int likeCount) {
        Post post = Post.published(showroom, content, new BigDecimal("0.8000"), LocalDateTime.now());
        for (int i = 0; i < likeCount; i++) {
            post.increaseLikeCount();
        }
        return postRepository.save(post).getId();
    }

    /** 좋아요한 시각으로 정렬하므로 시각을 손으로 벌려 둔다 — 같은 밀리초에 몰리면 순서 검증이 무의미해진다 */
    private void like(Users user, Long postId, LocalDateTime likedAt) {
        postLikeRepository.save(new PostLike(user, postRepository.getReferenceById(postId)));
        jdbc.update("UPDATE post_like SET created_at = ? WHERE user_id = ? AND post_id = ?",
                likedAt, user.getId(), postId);
    }

    private void suspend(Long postId) {
        Post post = postRepository.findById(postId).orElseThrow();
        post.suspend();
        postRepository.save(post);
    }
}
