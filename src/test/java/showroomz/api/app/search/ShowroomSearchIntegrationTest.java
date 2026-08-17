package showroomz.api.app.search;

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
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C14 쇼룸 검색 — 통합 테스트.
 *
 * <p>검색 대상은 <b>쇼룸 이름과 아이디(@handle)뿐</b>이고, 정렬은 "왜 걸렸는지"가 위로 오도록
 * 이름 앞부분 → 이름 부분 → 아이디 앞부분 → 아이디 부분 순이다. 이 순위는 QueryDSL의
 * {@code CaseBuilder}가 만드는 값이라 단위 테스트로는 확인할 수 없어 실제 순서를 여기서 본다.
 *
 * <p>노출 조건(등록 완료 + 계정 정상 + 크리에이터)도 함께 지킨다 — 탈퇴·정지한 쇼룸이 검색에
 * 남는 것은 조용히 새는 종류의 버그다.
 */
@DisplayName("[통합] C14 쇼룸 검색")
class ShowroomSearchIntegrationTest extends IntegrationTestSupport {

    private static final String SEARCH_PATH = "/v1/user/search/showrooms";
    private static final String ACTIVE_PATH = "/v1/user/search/showrooms/active";
    private static final String AUTOCOMPLETE_PATH = "/v1/user/search/autocomplete";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;

    private String userToken;

    @BeforeEach
    void setUpViewer() {
        Users viewer = createUser("viewer", "미아", RoleType.USER);
        userToken = bearerToken(viewer.getUsername(), RoleType.USER, viewer.getId());
    }

    @Nested
    @DisplayName("검색 대상과 정렬")
    class Ranking {

        /**
         * 같은 키워드에 이름과 핸들이 동시에 걸리는 상황을 한 번에 세워 순위를 확인한다.
         * "소연"으로 검색했을 때 이름이 소연으로 시작하는 쇼룸이 이름 중간에 낀 쇼룸보다 위에 와야 한다.
         */
        @Test
        @DisplayName("이름 앞부분 일치가 이름 중간 일치보다 위에 온다")
        void namePrefixOutranksNameContains() throws Exception {
            createShowroom("소연 뷰티", "aaa");
            createShowroom("뷰티 소연", "bbb");

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "소연")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                    .andExpect(jsonPath("$.content[0].showroomName").value("소연 뷰티"))
                    .andExpect(jsonPath("$.content[1].showroomName").value("뷰티 소연"));
        }

        @Test
        @DisplayName("이름에 걸린 쇼룸이 아이디에만 걸린 쇼룸보다 위에 온다")
        void nameMatchOutranksHandleMatch() throws Exception {
            createShowroom("전혀 다른 이름", "brainy");
            createShowroom("brain 뷰티", "zzz");

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "brain")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                    .andExpect(jsonPath("$.content[0].showroomName").value("brain 뷰티"))
                    .andExpect(jsonPath("$.content[1].showroomName").value("전혀 다른 이름"));
        }

        /** 같은 등급 안에서는 이름이 짧은 쪽이 먼저다 — 정확히 그 이름을 찾은 사람이 가장 많다. */
        @Test
        @DisplayName("같은 등급이면 이름이 짧은 쇼룸이 먼저 나온다")
        void shorterNameComesFirstWithinSameRank() throws Exception {
            createShowroom("토너 아카이브 스토어", "aaa");
            createShowroom("토너", "bbb");

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "토너")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].showroomName").value("토너"))
                    .andExpect(jsonPath("$.content[1].showroomName").value("토너 아카이브 스토어"));
        }

        /** 사용자는 화면에 보이는 대로 "@handle"을 그대로 붙여 넣는다 — @를 떼고 맞춰야 걸린다. */
        @Test
        @DisplayName("@를 붙여 입력해도 아이디로 찾을 수 있다")
        void handleSearchIgnoresLeadingAt() throws Exception {
            createShowroom("소연의 방", "soyeon");

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "@soyeon")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.content[0].showroomAddress").value("soyeon"));
        }

        @Test
        @DisplayName("아이디는 대소문자를 가리지 않는다")
        void handleSearchIsCaseInsensitive() throws Exception {
            createShowroom("소연의 방", "soyeon");

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "SoYeon")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        }

        /** 검색 범위는 이름과 아이디뿐이다 — 소개글까지 걸리면 결과가 설명 없이 늘어난다. */
        @Test
        @DisplayName("소개글은 검색 대상이 아니다")
        void introductionIsNotSearched() throws Exception {
            Creator creator = createShowroom("소연의 방", "soyeon");
            creator.updateShowroomProfile("소연의 방", "토너 전문 쇼룸입니다", null);
            creatorRepository.save(creator);

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "토너")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        }

        @Test
        @DisplayName("키워드가 비면 빈 목록을 준다 — 전체 쇼룸을 쏟지 않는다")
        void blankKeywordReturnsEmpty() throws Exception {
            createShowroom("소연 뷰티", "soyeon");

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "   ")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0))
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("페이지를 잘라도 전체 개수는 그대로다")
        void totalResultsSurvivesPaging() throws Exception {
            createShowroom("토너 A", "aaa");
            createShowroom("토너 B", "bbb");
            createShowroom("토너 C", "ccc");

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "토너").param("size", "2")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                    .andExpect(jsonPath("$.pageInfo.hasNext").value(true));
        }
    }

    @Nested
    @DisplayName("노출 조건")
    class Visibility {

        @Test
        @DisplayName("탈퇴한 쇼룸은 검색에 나오지 않는다")
        void withdrawnShowroomIsHidden() throws Exception {
            Creator creator = createShowroom("소연 뷰티", "soyeon");
            Users owner = creator.getUser();
            owner.updateStatus(UserStatus.WITHDRAWN);
            userRepository.save(owner);

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "소연")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        }

        @Test
        @DisplayName("정지된 쇼룸도 검색에 나오지 않는다")
        void suspendedShowroomIsHidden() throws Exception {
            Creator creator = createShowroom("소연 뷰티", "soyeon");
            Users owner = creator.getUser();
            owner.updateStatus(UserStatus.SUSPENDED);
            userRepository.save(owner);

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "소연")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        }

        /** 쇼룸명과 아이디는 등록 완료 시점에 함께 정해진다 — 둘 중 하나라도 없으면 등록 전이다. */
        @Test
        @DisplayName("등록을 마치지 않아 아이디가 없는 쇼룸은 검색에 나오지 않는다")
        void showroomWithoutHandleIsHidden() throws Exception {
            createShowroom("소연 뷰티", null);

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "소연")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        }
    }

    @Nested
    @DisplayName("결과 없음 화면의 활동 중인 쇼룸")
    class ActiveShowrooms {

        /** 게시물이 아직 없어도 목록이 비어선 안 된다 — 결과 없음 화면에서 다음 행동이 사라진다. */
        @Test
        @DisplayName("게시물이 없어도 신규 등록순으로 채워 준다")
        void fallsBackToNewestWhenNoPostsExist() throws Exception {
            createShowroom("소연 뷰티", "soyeon");
            createShowroom("지민 뷰티", "jimin");

            mockMvc.perform(get(ACTIVE_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("size로 개수를 줄일 수 있다")
        void sizeLimitsResult() throws Exception {
            createShowroom("소연 뷰티", "soyeon");
            createShowroom("지민 뷰티", "jimin");

            mockMvc.perform(get(ACTIVE_PATH).param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("노출할 수 없는 쇼룸은 이 목록에도 오르지 않는다")
        void hiddenShowroomIsExcluded() throws Exception {
            Creator creator = createShowroom("소연 뷰티", "soyeon");
            Users owner = creator.getUser();
            owner.updateStatus(UserStatus.WITHDRAWN);
            userRepository.save(owner);

            mockMvc.perform(get(ACTIVE_PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("자동완성")
    class Autocomplete {

        @Test
        @DisplayName("쇼룸은 이름으로 걸리고 상품 칸은 비어도 응답 형태는 유지된다")
        void showroomAppearsInAutocomplete() throws Exception {
            createShowroom("소연 뷰티", "soyeon");

            mockMvc.perform(get(AUTOCOMPLETE_PATH).param("keyword", "소연")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.showrooms.length()").value(1))
                    .andExpect(jsonPath("$.showrooms[0].name").value("소연 뷰티"))
                    .andExpect(jsonPath("$.products").isArray());
        }

        /**
         * 마켓 후보는 기획에서 빠졌다 — 소비자 앱에서 마켓(브랜드)은 조회되지 않는다.
         * 되살아나면 탭했을 때 갈 화면이 없는 후보가 자동완성에 다시 낀다.
         */
        @Test
        @DisplayName("마켓 후보는 응답에 없다")
        void marketCandidatesAreGone() throws Exception {
            createShowroom("소연 뷰티", "soyeon");

            mockMvc.perform(get(AUTOCOMPLETE_PATH).param("keyword", "소연")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.markets").doesNotExist());
        }

        /** 빈 키워드는 컨트롤러가 질의 없이 빈 응답으로 끊는다 — 전체 스캔이 돌면 안 된다. */
        @Test
        @DisplayName("키워드가 비면 질의하지 않고 빈 응답을 준다")
        void blankKeywordShortCircuits() throws Exception {
            createShowroom("소연 뷰티", "soyeon");

            mockMvc.perform(get(AUTOCOMPLETE_PATH).param("keyword", "  ")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.showrooms").isEmpty())
                    .andExpect(jsonPath("$.products").isEmpty());
        }
    }

    /**
     * 검색·추천·자동완성은 <b>일부러</b> 비로그인에 열려 있다 — 앱을 처음 켠 사람이 로그인 벽 앞에서
     * 쇼룸을 하나도 못 보면 가입할 이유가 생기지 않는다. 로그인이 필요한 것은 최근 검색 기록뿐이다.
     * 인증을 조이는 변경이 이 경로까지 쓸어가면 여기서 걸린다.
     */
    @Nested
    @DisplayName("비로그인 접근")
    class AnonymousAccess {

        @Test
        @DisplayName("비로그인도 쇼룸을 검색할 수 있다")
        void anonymousCanSearch() throws Exception {
            createShowroom("소연 뷰티", "soyeon");

            mockMvc.perform(get(SEARCH_PATH).param("keyword", "소연"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        }

        @Test
        @DisplayName("비로그인도 추천 목록과 자동완성을 볼 수 있다")
        void anonymousCanSeeActiveAndAutocomplete() throws Exception {
            createShowroom("소연 뷰티", "soyeon");

            mockMvc.perform(get(ACTIVE_PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            mockMvc.perform(get(AUTOCOMPLETE_PATH).param("keyword", "소연"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.showrooms.length()").value(1));
        }

        /** 최근 검색은 "내" 기록이라 비로그인에 열 수 없다 — 검색과 경계가 갈리는 지점이다. */
        @Test
        @DisplayName("최근 검색 기록은 비로그인에 열리지 않는다")
        void recentSearchStillRequiresLogin() throws Exception {
            mockMvc.perform(get("/v1/user/recent-searches")).andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ 픽스처

    private Users createUser(String username, String nickname, RoleType roleType) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(
                username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, roleType, now, now));
    }

    /**
     * 검색 노출 조건이 소유 계정(크리에이터 권한·정상 상태)에 걸려 있어 쇼룸마다 계정을 따로 만든다.
     * {@code showroomAddress}가 null이면 등록을 마치지 않은 쇼룸이다.
     */
    private Creator createShowroom(String showroomName, String showroomAddress) {
        Users owner = createUser("creator-" + (showroomAddress == null ? showroomName : showroomAddress),
                showroomName, RoleType.CREATOR);
        Creator creator = Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + (showroomAddress == null ? "none" : showroomAddress))
                .accountId(showroomAddress == null ? "none" : showroomAddress)
                .followerCount(1000)
                .businessEmail("biz@showroomz.test")
                .showroomName(showroomName)
                .build();
        if (showroomAddress != null) {
            creator.assignShowroomAddressIfAbsent(showroomAddress);
        }
        return creatorRepository.save(creator);
    }
}
