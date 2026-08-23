package showroomz.api.app.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.repository.PostRepository;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C1·C4 소비자 피드 — 무엇이 보이고 무엇이 안 보이는지.
 *
 * <p>피드 카드에는 게시물뿐 아니라 <b>쇼룸명·프로필</b>이 함께 붙는다. 쇼룸(과 그 계정)을 조회
 * 쿼리가 함께 읽지 않으면 카드 수만큼 조회가 더 나가는데, 단위 테스트는 리포지토리를 흉내 내므로
 * 그 사실을 잡지 못한다. 여기서는 실제 쿼리로 돌려 쇼룸명이 응답에 실리는지까지 확인한다.
 *
 * <p>노출 조건도 함께 본다 — 작성중·노출 중지가 소비자 목록에 새면 §24-1이 무너진다.
 */
@DisplayName("[통합] C1 소비자 피드")
class UserFeedIntegrationTest extends IntegrationTestSupport {

    private static final String SHOWROOM_FEED = "/v1/user/showrooms/{showroomId}/posts";
    private static final String FOLLOWING_FEED = "/v1/user/feed/following";
    private static final String RECOMMENDED_FEED = "/v1/user/feed/recommended";
    private static final String POST_DETAIL = "/v1/user/showrooms/posts/{postId}";

    private static final String SUSPENDED_CONTENT = "내려간 게시물";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private CreatorFollowRepository creatorFollowRepository;
    @Autowired
    private PostRepository postRepository;

    private String userToken;
    private Creator followed;
    private Long followedPostId;
    private Long notFollowedPostId;
    private Long suspendedPostId;

    @BeforeEach
    void setUpFeed() {
        Users viewer = createUser("mia", "미아");
        userToken = bearerToken(viewer.getUsername(), RoleType.USER, viewer.getId());

        followed = createShowroom("제니의 뷰티룸");
        Creator notFollowed = createShowroom("소연의 살림");
        creatorFollowRepository.save(new CreatorFollow(viewer, followed));

        followedPostId = createPublishedPost(followed, "요즘 아침 루틴 정리했어요");
        notFollowedPostId = createPublishedPost(notFollowed, "주방 정리 3주 기록");

        // 소비자에게 보이면 안 되는 것들 — 작성중과 노출 중지
        postRepository.save(withImage(Post.draft(followed, "아직 초안", new BigDecimal("0.8000"))));
        Post suspended = postRepository.save(withImage(
                Post.published(followed, SUSPENDED_CONTENT, new BigDecimal("0.8000"), LocalDateTime.now())));
        suspended.suspend();
        suspendedPostId = postRepository.save(suspended).getId();
    }

    @Test
    @DisplayName("쇼룸 피드는 게시중만 내려주고 카드에 쇼룸명이 함께 실린다")
    void showroomFeedCarriesShowroomName() throws Exception {
        mockMvc.perform(get(SHOWROOM_FEED, followed.getId()).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].post.postId").value(followedPostId))
                .andExpect(jsonPath("$.content[0].post.showroomName").value("제니의 뷰티룸"))
                .andExpect(jsonPath("$.content[0].post.imageUrls.length()").value(1))
                .andExpect(jsonPath("$.content[0].post.isFollowing").value(true));
    }

    @Test
    @DisplayName("팔로잉 피드에는 팔로우한 쇼룸의 게시물만 나온다")
    void followingFeedShowsOnlyFollowedShowrooms() throws Exception {
        mockMvc.perform(get(FOLLOWING_FEED).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].post.postId").value(followedPostId))
                .andExpect(jsonPath("$.content[0].post.showroomName").value("제니의 뷰티룸"));
    }

    /** 추천은 팔로잉 피드의 여집합이다 — 겹치면 "새 게시물을 모두 확인했어요" 구분선이 거짓말이 된다. */
    @Test
    @DisplayName("추천 피드는 팔로우하지 않은 쇼룸만 내려주고 팔로우 버튼이 붙는다")
    void recommendedFeedExcludesFollowedShowrooms() throws Exception {
        mockMvc.perform(get(RECOMMENDED_FEED).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].post.postId").value(notFollowedPostId))
                .andExpect(jsonPath("$.content[0].post.showroomName").value("소연의 살림"))
                .andExpect(jsonPath("$.content[0].post.isFollowing").value(false));
    }

    /**
     * 비로그인 발견 피드 — 팔로우가 없으니 뺄 것도 없어 게시중 게시물이 전부 나온다.
     * 노출 조건은 로그인과 똑같이 걸린다(작성중·노출 중지는 여기서도 새면 안 된다).
     */
    @Test
    @DisplayName("추천 피드는 토큰 없이도 열린다 — 게시중 전체가 발견 피드가 된다")
    void recommendedFeedIsOpenToAnonymous() throws Exception {
        mockMvc.perform(get(RECOMMENDED_FEED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                .andExpect(jsonPath("$.content[*].post.postId",
                        containsInAnyOrder(followedPostId.intValue(), notFollowedPostId.intValue())))
                .andExpect(jsonPath("$.content[*].post.content", not(hasItem(SUSPENDED_CONTENT))))
                .andExpect(jsonPath("$.content[0].post.isFollowing").value(false))
                .andExpect(jsonPath("$.content[0].post.isLiked").value(false));
    }

    /** 팔로잉 피드는 정의상 내 팔로우 목록이라 비로그인이 열리면 안 된다 — 함께 새지 않았는지 본다. */
    @Test
    @DisplayName("팔로잉 피드는 여전히 비로그인 401이다")
    void followingFeedStillRequiresLogin() throws Exception {
        mockMvc.perform(get(FOLLOWING_FEED))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("상세도 쇼룸 정보를 함께 내려준다")
    void detailCarriesShowroomAndImages() throws Exception {
        mockMvc.perform(get(POST_DETAIL, followedPostId).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(followedPostId))
                .andExpect(jsonPath("$.showroomId").value(followed.getId()))
                .andExpect(jsonPath("$.showroomName").value("제니의 뷰티룸"))
                .andExpect(jsonPath("$.imageCount").value(1))
                .andExpect(jsonPath("$.isLiked").value(false));
    }

    /** 작성중·노출 중지·삭제를 구분해 알려주지 않는다 — 소비자에게는 전부 "없는 것"이다. */
    @Test
    @DisplayName("내려간 게시물의 상세는 404다")
    void suspendedPostDetailIsNotFound() throws Exception {
        mockMvc.perform(get(POST_DETAIL, suspendedPostId).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isNotFound());
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

    private Long createPublishedPost(Creator showroom, String content) {
        return postRepository.save(withImage(
                Post.published(showroom, content, new BigDecimal("0.8000"), LocalDateTime.now()))).getId();
    }

    private static Post withImage(Post post) {
        post.replaceImages(List.of(new PostImage(
                "https://cdn.example.com/posts/a.jpg", "https://cdn.example.com/posts/a-origin.jpg",
                1080, 1350, 2_048_000)));
        return post;
    }
}
