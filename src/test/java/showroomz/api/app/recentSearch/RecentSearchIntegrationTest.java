package showroomz.api.app.recentSearch;

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
import showroomz.support.IntegrationTestSupport;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C14 최근 검색 — 통합 테스트.
 *
 * <p>검색어 행(TERM)과 쇼룸 행(SHOWROOM)이 <b>한 목록에 시간순으로 섞여</b> 내려간다. 두 종류를
 * 섞어 정렬하는 것은 쿼리가 하는 일이라 단위 테스트로는 확인할 수 없어 실제 순서를 여기서 본다.
 *
 * <p>통합 하네스가 {@code open-in-view=false}라, 쇼룸 행의 아바타·핸들을 지연 로딩에 맡겼다면
 * 응답 직렬화에서 터진다 — 조회 쿼리의 페치 조인({@code @EntityGraph})도 이 테스트가 함께 지킨다.
 */
@DisplayName("[통합] C14 최근 검색")
class RecentSearchIntegrationTest extends IntegrationTestSupport {

    private static final String PATH = "/v1/user/recent-searches";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Users consumer;
    private String userToken;
    private Long showroomId;

    @BeforeEach
    void setUpConsumer() {
        consumer = createUser("mia", "미아", RoleType.USER);
        userToken = bearerToken(consumer.getUsername(), RoleType.USER, consumer.getId());
        showroomId = createShowroom("소연 뷰티", "soyeon").getId();
    }

    @Nested
    @DisplayName("저장 (upsert)")
    class Save {

        @Test
        @DisplayName("검색어를 저장하면 목록에 검색어 행으로 잡힌다")
        void keywordIsStoredAsTermRow() throws Exception {
            saveKeyword("토너");

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.content[0].term").value("토너"))
                    .andExpect(jsonPath("$.content[0].type").value("TERM"));
        }

        /** 쇼룸 행은 아바타·핸들을 함께 내려줘야 목록에서 쇼룸으로 바로 들어갈 수 있다. */
        @Test
        @DisplayName("쇼룸을 저장하면 쇼룸 행으로 잡히고 아바타·핸들이 함께 내려간다")
        void showroomIsStoredWithProfile() throws Exception {
            saveShowroom(showroomId);

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].type").value("SHOWROOM"))
                    .andExpect(jsonPath("$.content[0].term").value("소연 뷰티"))
                    .andExpect(jsonPath("$.content[0].showroom.showroomId").value(showroomId))
                    .andExpect(jsonPath("$.content[0].showroom.showroomName").value("소연 뷰티"))
                    .andExpect(jsonPath("$.content[0].showroom.showroomAddress").value("soyeon"));
        }

        /** 같은 검색어가 여러 행으로 쌓이면 목록이 한 단어로 도배된다. */
        @Test
        @DisplayName("같은 검색어를 다시 저장해도 행이 늘지 않는다")
        void repeatedKeywordDoesNotGrowList() throws Exception {
            saveKeyword("토너");
            saveKeyword("토너");
            saveKeyword("토너");

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        }

        @Test
        @DisplayName("같은 쇼룸을 다시 저장해도 행이 늘지 않는다")
        void repeatedShowroomDoesNotGrowList() throws Exception {
            saveShowroom(showroomId);
            saveShowroom(showroomId);

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        }

        /** 쇼룸명과 같은 검색어를 쳤을 때 두 행이 하나로 합쳐지면 "검색어" 기록이 사라진다. */
        @Test
        @DisplayName("쇼룸명과 같은 검색어는 별개 행으로 남는다 — 타입까지 좁혀 합치기 때문이다")
        void sameTextDifferentTypeStaysSeparate() throws Exception {
            saveShowroom(showroomId);
            saveKeyword("소연 뷰티");

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(2));
        }

        @Test
        @DisplayName("없는 쇼룸은 최근 검색에 넣지 않는다")
        void unknownShowroomIsRejected() throws Exception {
            mockMvc.perform(post(PATH).param("showroomId", "999999")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("빈 검색어는 저장하지 않는다")
        void blankKeywordIsIgnored() throws Exception {
            mockMvc.perform(post(PATH).param("keyword", "   ")
                            .header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        }
    }

    @Nested
    @DisplayName("목록 정렬")
    class Ordering {

        /** 검색어와 쇼룸이 한 목록에 섞이므로, 정렬 기준은 타입이 아니라 시간이어야 한다. */
        @Test
        @DisplayName("검색어 행과 쇼룸 행이 최신순으로 섞여 내려간다")
        void mixedRowsAreOrderedByRecency() throws Exception {
            saveKeyword("가장 오래된 검색어");
            saveShowroom(showroomId);
            saveKeyword("가장 최근 검색어");

            backdateTerm("가장 오래된 검색어", Instant.now().minus(3, ChronoUnit.DAYS));
            backdateShowroom(showroomId, Instant.now().minus(1, ChronoUnit.DAYS));

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].term").value("가장 최근 검색어"))
                    .andExpect(jsonPath("$.content[1].type").value("SHOWROOM"))
                    .andExpect(jsonPath("$.content[2].term").value("가장 오래된 검색어"));
        }

        /** 다시 검색하면 맨 위로 올라와야 한다 — upsert가 시각을 갱신하는 이유다. */
        @Test
        @DisplayName("오래된 검색어를 다시 검색하면 맨 위로 올라온다")
        void researchingMovesRowToTop() throws Exception {
            saveKeyword("토너");
            saveKeyword("세럼");
            backdateTerm("토너", Instant.now().minus(5, ChronoUnit.DAYS));

            saveKeyword("토너");

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                    .andExpect(jsonPath("$.content[0].term").value("토너"));
        }

        @Test
        @DisplayName("페이지를 잘라도 전체 개수는 그대로다")
        void totalSurvivesPaging() throws Exception {
            saveKeyword("하나");
            saveKeyword("둘");
            saveKeyword("셋");

            mockMvc.perform(get(PATH).param("size", "2").header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                    .andExpect(jsonPath("$.pageInfo.hasNext").value(true));
        }
    }

    @Nested
    @DisplayName("동기화")
    class Sync {

        /** 로컬 기록은 쌓인 시각이 의미를 가지므로 서버 시각으로 덮어쓰면 순서가 뒤집힌다. */
        @Test
        @DisplayName("보내온 시각 순서대로 목록에 자리를 잡는다")
        void syncedItemsKeepClientOrder() throws Exception {
            Instant older = Instant.now().minus(5, ChronoUnit.DAYS);
            Instant newer = Instant.now().minus(1, ChronoUnit.DAYS);

            mockMvc.perform(post(PATH + "/sync")
                            .header(HttpHeaders.AUTHORIZATION, userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("keywords", List.of(
                                    Map.of("keyword", "오래된 것", "createdAt", older.toString()),
                                    Map.of("keyword", "최근 것", "createdAt", newer.toString()))))))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                    .andExpect(jsonPath("$.content[0].term").value("최근 것"))
                    .andExpect(jsonPath("$.content[1].term").value("오래된 것"));
        }

        @Test
        @DisplayName("빈 목록을 보내도 오류 없이 지나간다")
        void emptySyncIsAccepted() throws Exception {
            mockMvc.perform(post(PATH + "/sync")
                            .header(HttpHeaders.AUTHORIZATION, userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("keywords", List.of()))))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("개별 삭제는 그 행만 지운다")
        void deleteOneRemovesOnlyThatRow() throws Exception {
            saveKeyword("남을 것");
            saveKeyword("지울 것");
            Long targetId = recentSearchIdOf("지울 것");

            mockMvc.perform(delete(PATH + "/" + targetId).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.content[0].term").value("남을 것"));
        }

        /** 조회를 (id, user)로 좁히므로 남의 기록은 애초에 잡히지 않는다. */
        @Test
        @DisplayName("남의 기록은 지울 수 없고 그 사람 목록은 그대로다")
        void othersRowIsNotDeletable() throws Exception {
            Users other = createUser("other", "다른사람", RoleType.USER);
            String otherToken = bearerToken(other.getUsername(), RoleType.USER, other.getId());
            mockMvc.perform(post(PATH).param("keyword", "남의 검색어")
                    .header(HttpHeaders.AUTHORIZATION, otherToken));
            Long othersId = recentSearchIdOf("남의 검색어");

            mockMvc.perform(delete(PATH + "/" + othersId).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, otherToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        }

        @Test
        @DisplayName("전체 삭제는 내 기록만 비우고 남의 기록은 남긴다")
        void clearAllIsScopedToMe() throws Exception {
            saveKeyword("내 것 하나");
            saveShowroom(showroomId);

            Users other = createUser("other", "다른사람", RoleType.USER);
            String otherToken = bearerToken(other.getUsername(), RoleType.USER, other.getId());
            mockMvc.perform(post(PATH).param("keyword", "남의 검색어")
                    .header(HttpHeaders.AUTHORIZATION, otherToken));

            mockMvc.perform(delete(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, otherToken))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        }
    }

    /** 최근 검색은 "내" 기록이라 비로그인에 열 수 없다 — 검색 자체와 경계가 갈리는 지점이다. */
    @Test
    @DisplayName("비로그인은 401")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH).param("keyword", "토너")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 단계 · 픽스처

    private void saveKeyword(String keyword) throws Exception {
        mockMvc.perform(post(PATH).param("keyword", keyword).header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isNoContent());
    }

    private void saveShowroom(Long id) throws Exception {
        mockMvc.perform(post(PATH).param("showroomId", String.valueOf(id))
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isNoContent());
    }

    private Long recentSearchIdOf(String term) {
        return jdbc.queryForObject(
                "SELECT recent_search_id FROM recent_search WHERE term = ?", Long.class, term);
    }

    /** 목록 순서를 검증하려면 시각을 손으로 벌려야 한다 — 같은 밀리초에 몰리면 순서 검증이 무의미해진다. */
    private void backdateTerm(String term, Instant createdAt) {
        jdbc.update("UPDATE recent_search SET created_at = ? WHERE term = ? AND entry_type = 'TERM'",
                java.sql.Timestamp.from(createdAt), term);
    }

    private void backdateShowroom(Long creatorId, Instant createdAt) {
        jdbc.update("UPDATE recent_search SET created_at = ? WHERE creator_id = ? AND entry_type = 'SHOWROOM'",
                java.sql.Timestamp.from(createdAt), creatorId);
    }

    private Users createUser(String username, String nickname, RoleType roleType) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(
                username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, roleType, now, now));
    }

    private Creator createShowroom(String showroomName, String showroomAddress) {
        Users owner = createUser("creator-" + showroomAddress, showroomName, RoleType.CREATOR);
        Creator creator = Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/" + showroomAddress)
                .accountId(showroomAddress)
                .followerCount(1000)
                .businessEmail("biz@showroomz.test")
                .showroomName(showroomName)
                .build();
        creator.assignShowroomAddressIfAbsent(showroomAddress);
        return creatorRepository.save(creator);
    }
}
