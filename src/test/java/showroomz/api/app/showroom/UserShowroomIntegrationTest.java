package showroomz.api.app.showroom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.repository.PostRepository;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쇼룸 조회 — 통합 테스트.
 *
 * <p>구 샵 API({@code /v1/user/shops})가 쇼룸 API({@code /v1/user/showrooms})로 옮겨 왔다.
 * 이름만 바뀐 것이 아니라 <b>조회 뿌리가 마켓에서 쇼룸으로</b> 바뀌었으므로, 여기서 지키는 것은
 * 세 가지다 — 마켓이 섞여 나오지 않는다, 노출 조건이 검색과 같다, 그리고 경로가 이웃한
 * {@code /following}·{@code /posts}를 삼키지 않는다.
 *
 * <p>마지막 항목이 특히 조용한 종류의 사고다. {@code /{showroomId}} 패턴은 문자열
 * "following"에도 걸릴 수 있고, 보안 화이트리스트가 그 경로를 함께 열어 버리면 남의 팔로잉
 * 목록 요청이 401 대신 다른 응답을 받게 된다.
 */
@DisplayName("[통합] 쇼룸 조회 (구 샵 API)")
class UserShowroomIntegrationTest extends IntegrationTestSupport {

    private static final String LIST_PATH = "/v1/user/showrooms";
    private static final String DETAIL_PATH = "/v1/user/showrooms/%d";
    private static final String FOLLOW_PATH = "/v1/user/showrooms/%d/follow";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private PostRepository postRepository;

    private Users consumer;
    private String userToken;

    @BeforeEach
    void setUpConsumer() {
        consumer = createUser("mia", "미아", RoleType.USER);
        userToken = bearerToken(consumer.getUsername(), RoleType.USER, consumer.getId());
    }

    @Nested
    @DisplayName("쇼룸 목록")
    class ShowroomList {

        @Test
        @DisplayName("노출 가능한 쇼룸을 신규 등록순으로 준다")
        void listsShowroomsNewestFirst() throws Exception {
            createShowroom("소연 뷰티", "soyeon");
            Creator newest = createShowroom("지민 뷰티", "jimin");

            mockMvc.perform(get(LIST_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                    .andExpect(jsonPath("$.content[0].showroomId").value(newest.getId()))
                    .andExpect(jsonPath("$.content[0].showroomName").value("지민 뷰티"))
                    .andExpect(jsonPath("$.content[0].showroomAddress").value("jimin"));
        }

        /** 마켓이 섞여 나오면 탭했을 때 갈 화면이 없다 — 소비자 앱에 브랜드 상세는 없다. */
        @Test
        @DisplayName("쇼룸이 하나도 없으면 빈 목록이다 — 마켓으로 채우지 않는다")
        void marketsAreNotListed() throws Exception {
            fixture.createBrand("brand@showroomz.test", "화이트 브랜드");

            mockMvc.perform(get(LIST_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0))
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("키워드는 쇼룸명과 아이디에 걸린다")
        void keywordMatchesNameAndHandle() throws Exception {
            createShowroom("소연 뷰티", "aaa");
            createShowroom("전혀 다른 이름", "soyeon_room");

            mockMvc.perform(get(LIST_PATH).param("keyword", "soyeon")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.content[0].showroomAddress").value("soyeon_room"));
        }

        /** 사용자는 화면에 보이는 대로 "@handle"을 붙여 넣는다. */
        @Test
        @DisplayName("@를 붙여도 아이디로 찾을 수 있다")
        void handleSearchIgnoresLeadingAt() throws Exception {
            createShowroom("소연의 방", "soyeon");

            mockMvc.perform(get(LIST_PATH).param("keyword", "@soyeon")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        }

        @Test
        @DisplayName("탈퇴한 쇼룸과 등록을 마치지 않은 쇼룸은 목록에 없다")
        void hiddenShowroomsAreExcluded() throws Exception {
            Creator withdrawn = createShowroom("탈퇴 쇼룸", "gone");
            Users owner = withdrawn.getUser();
            owner.updateStatus(UserStatus.WITHDRAWN);
            userRepository.save(owner);
            createShowroom("등록 중 쇼룸", null);

            mockMvc.perform(get(LIST_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        }

        @Test
        @DisplayName("팔로우한 쇼룸은 isFollowing이 켜진다 — 이때 팔로우 버튼을 그리지 않는다")
        void followStateIsReflected() throws Exception {
            Long showroomId = createShowroom("소연 뷰티", "soyeon").getId();
            follow(showroomId);

            mockMvc.perform(get(LIST_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].isFollowing").value(true));
        }

        @Test
        @DisplayName("비로그인도 목록을 볼 수 있고, 이때 isFollowing은 꺼져 있다")
        void anonymousCanBrowse() throws Exception {
            createShowroom("소연 뷰티", "soyeon");

            mockMvc.perform(get(LIST_PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.content[0].isFollowing").value(false));
        }

        @Test
        @DisplayName("페이지를 잘라도 전체 개수는 그대로다")
        void totalSurvivesPaging() throws Exception {
            createShowroom("쇼룸 A", "aaa");
            createShowroom("쇼룸 B", "bbb");
            createShowroom("쇼룸 C", "ccc");

            mockMvc.perform(get(LIST_PATH).param("size", "2")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                    .andExpect(jsonPath("$.pageInfo.hasNext").value(true));
        }
    }

    @Nested
    @DisplayName("쇼룸 상세 (C4 프로필)")
    class ShowroomDetail {

        @Test
        @DisplayName("프로필 한 벌을 준다 — 이름·아이디·소개·게시물 수·팔로워 수")
        void servesProfile() throws Exception {
            Creator showroom = createShowroom("소연 뷰티", "soyeon");
            showroom.updateShowroomProfile("소연 뷰티", "토너만 파는 쇼룸", "https://instagram.com/soyeon");
            creatorRepository.save(showroom);
            publishPost(showroom);
            follow(showroom.getId());

            mockMvc.perform(get(DETAIL_PATH.formatted(showroom.getId()))
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.showroomId").value(showroom.getId()))
                    .andExpect(jsonPath("$.showroomName").value("소연 뷰티"))
                    .andExpect(jsonPath("$.showroomAddress").value("soyeon"))
                    .andExpect(jsonPath("$.introduction").value("토너만 파는 쇼룸"))
                    .andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/soyeon"))
                    .andExpect(jsonPath("$.postCount").value(1))
                    .andExpect(jsonPath("$.followerCount").value(1))
                    .andExpect(jsonPath("$.isFollowing").value(true))
                    .andExpect(jsonPath("$.hasOngoingGroupBuy").value(false));
        }

        /** 내려간 게시물까지 세면 프로필의 "게시물 N"과 실제로 보이는 목록의 길이가 어긋난다. */
        @Test
        @DisplayName("게시물 수는 게시중인 것만 센다")
        void postCountCountsOnlyPublished() throws Exception {
            Creator showroom = createShowroom("소연 뷰티", "soyeon");
            publishPost(showroom);
            Post suspended = postRepository.save(
                    Post.published(showroom, "내려간 글", new BigDecimal("0.8000"), LocalDateTime.now()));
            suspended.suspend();
            postRepository.save(suspended);

            mockMvc.perform(get(DETAIL_PATH.formatted(showroom.getId()))
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.postCount").value(1));
        }

        @Test
        @DisplayName("비로그인도 열 수 있고, 이때 isFollowing은 꺼져 있다")
        void anonymousCanOpen() throws Exception {
            Creator showroom = createShowroom("소연 뷰티", "soyeon");

            mockMvc.perform(get(DETAIL_PATH.formatted(showroom.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isFollowing").value(false))
                    .andExpect(jsonPath("$.followerCount").value(0));
        }

        @Test
        @DisplayName("없는 쇼룸은 404다")
        void unknownShowroomIsNotFound() throws Exception {
            mockMvc.perform(get(DETAIL_PATH.formatted(999999L))
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isNotFound());
        }

        /** 탈퇴·정지는 조치 사실이라 없는 쇼룸과 같은 404로 덮는다. */
        @Test
        @DisplayName("탈퇴한 쇼룸도 같은 404다 — 상태를 구분해 알려주지 않는다")
        void withdrawnShowroomIsNotFound() throws Exception {
            Creator showroom = createShowroom("탈퇴 쇼룸", "gone");
            Users owner = showroom.getUser();
            owner.updateStatus(UserStatus.WITHDRAWN);
            userRepository.save(owner);

            mockMvc.perform(get(DETAIL_PATH.formatted(showroom.getId()))
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * {@code /v1/user/showrooms} 아래에는 이미 팔로우·방문·게시물 경로가 살고 있다.
     * 목록과 상세가 그 자리를 빼앗지 않는지 본다 — 라우팅과 보안 규칙이 함께 걸리는 지점이다.
     */
    @Nested
    @DisplayName("이웃 경로 침범 방지")
    class PathBoundaries {

        /** 상세 화이트리스트가 `{showroomId}`였다면 이 요청이 401 대신 통과해 버린다. */
        @Test
        @DisplayName("팔로잉 목록은 여전히 로그인이 필요하다")
        void followingStillRequiresLogin() throws Exception {
            mockMvc.perform(get("/v1/user/showrooms/following"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("전체 게시물 목록은 /showrooms/posts로 살아 있다")
        void internalPostListStillResolves() throws Exception {
            mockMvc.perform(get("/v1/user/showrooms/posts").header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        /** 구 경로가 살아 있으면 앱이 옛 계약을 계속 쓰게 된다. */
        @Test
        @DisplayName("구 샵 경로는 사라졌다")
        void legacyShopPathIsGone() throws Exception {
            mockMvc.perform(get("/v1/user/shops").header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------ 단계 · 픽스처

    private void follow(Long showroomId) throws Exception {
        mockMvc.perform(post(FOLLOW_PATH.formatted(showroomId)).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().is2xxSuccessful());
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

    /** {@code handle}이 null이면 등록을 마치지 않은 쇼룸이다(쇼룸 아이디 미발급). */
    private Creator createShowroom(String showroomName, String handle) {
        Users owner = createUser("creator-" + (handle == null ? showroomName : handle),
                showroomName, RoleType.CREATOR);
        Creator creator = Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + (handle == null ? "none" : handle))
                .accountId(handle == null ? "none" : handle)
                .followerCount(1000)
                .businessEmail("biz@showroomz.test")
                .showroomName(showroomName)
                .build();
        if (handle != null) {
            creator.assignShowroomAddressIfAbsent(handle);
        }
        return creatorRepository.save(creator);
    }
}
