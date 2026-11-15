package showroomz.api.app.showroom;

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
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.repository.PostRepository;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쇼룸 차단(App Store 심사 1.2) — 차단한 쇼룸은 피드 · 쇼룸 목록 · 검색에서 빠지고, 프로필은 열리며 {@code isBlocked}가 켜진다.
 */
@DisplayName("[통합] 쇼룸 차단")
class ShowroomBlockIntegrationTest extends IntegrationTestSupport {

    private static final String BLOCK_PATH = "/v1/user/showrooms/%d/block";
    private static final String FOLLOW_PATH = "/v1/user/showrooms/%d/follow";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private CreatorFollowRepository creatorFollowRepository;
    @Autowired
    private PostRepository postRepository;

    private Users consumer;
    private String userToken;
    private Creator blocked;
    private Creator other;

    @BeforeEach
    void setUp() {
        consumer = createUser("mia", "미아", RoleType.USER);
        userToken = bearerToken(consumer.getUsername(), RoleType.USER, consumer.getId());
        blocked = createShowroom("차단 뷰티", "blockme");
        other = createShowroom("다른 뷰티", "other");
        publishPost(blocked);
        publishPost(other);
    }

    @Test
    @DisplayName("차단하면 전체 · 추천 피드 · 쇼룸 목록 · 검색 · 자동완성에서 빠지고, 해제하면 돌아온다")
    void blockedShowroomIsHiddenEverywhere() throws Exception {
        perform(post(BLOCK_PATH.formatted(blocked.getId()))).andExpect(status().isNoContent());
        // 다시 눌러도 같다(멱등)
        perform(post(BLOCK_PATH.formatted(blocked.getId()))).andExpect(status().isNoContent());

        perform(get("/v1/user/showrooms/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].post.showroomId").value(other.getId()));
        perform(get("/v1/user/feed/recommended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        perform(get("/v1/user/showrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].showroomId").value(other.getId()));
        perform(get("/v1/user/search/showrooms").param("keyword", "뷰티"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        perform(get("/v1/user/search/autocomplete").param("keyword", "뷰티"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showrooms.length()").value(1));
        perform(get("/v1/user/search/showrooms/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // 프로필은 열린다 — 「차단 해제」 버튼용 isBlocked
        perform(get("/v1/user/showrooms/%d".formatted(blocked.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isBlocked").value(true));

        perform(get("/v1/user/showrooms/blocked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].showroomId").value(blocked.getId()))
                .andExpect(jsonPath("$.content[0].showroomName").value("차단 뷰티"));

        perform(delete(BLOCK_PATH.formatted(blocked.getId()))).andExpect(status().isNoContent());

        perform(get("/v1/user/showrooms")).andExpect(jsonPath("$.pageInfo.totalResults").value(2));
        perform(get("/v1/user/showrooms/posts")).andExpect(jsonPath("$.content.length()").value(2));
        perform(get("/v1/user/showrooms/%d".formatted(blocked.getId()))).andExpect(jsonPath("$.isBlocked").value(false));
    }

    @Test
    @DisplayName("차단하면 팔로우가 끊기고 팔로잉 피드에서도 빠진다")
    void blockUnfollows() throws Exception {
        perform(post(FOLLOW_PATH.formatted(blocked.getId()))).andExpect(status().is2xxSuccessful());
        perform(get("/v1/user/feed/following")).andExpect(jsonPath("$.content.length()").value(1));

        perform(post(BLOCK_PATH.formatted(blocked.getId()))).andExpect(status().isNoContent());

        assertThat(creatorFollowRepository.findCreatorIdsByUserId(consumer.getId())).isEmpty();
        perform(get("/v1/user/feed/following")).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("비로그인은 차단할 수 없고, 다른 사람의 차단은 비로그인 목록에 영향이 없다")
    void anonymous() throws Exception {
        perform(post(BLOCK_PATH.formatted(blocked.getId()))).andExpect(status().isNoContent());

        mockMvc.perform(post(BLOCK_PATH.formatted(blocked.getId()))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/user/showrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(2));
    }

    @Test
    @DisplayName("본인 쇼룸은 차단할 수 없다")
    void cannotBlockOwnShowroom() throws Exception {
        Users owner = blocked.getUser();
        String ownerToken = bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());

        mockMvc.perform(post(BLOCK_PATH.formatted(blocked.getId())).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 픽스처

    private org.springframework.test.web.servlet.ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, userToken));
    }

    private void publishPost(Creator showroom) {
        postRepository.save(Post.published(showroom, "본문", new BigDecimal("0.8000"), LocalDateTime.now()));
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
