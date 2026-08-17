package showroomz.api.creator.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.creator.showroom.service.Demographics;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImpression;
import showroomz.domain.post.entity.PostLike;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.domain.post.type.SuspensionResolution;
import showroomz.domain.showroom.entity.ShowroomVisit;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §24-7 게시물 인사이트 — 통합 테스트.
 *
 * <p>이 화면의 계약은 "지표를 <b>누적 카운터가 아니라 원천 로그에서</b> 계산한다"는 것이다. 단위 테스트는
 * 리포지토리가 돌려준 숫자를 어떻게 비율로 바꾸는지만 볼 수 있고, <b>그 숫자가 맞는지는 볼 수 없다</b> —
 * 기간 경계·귀속 조건·조회자 중복 제거는 전부 JPQL 안에 있다. 그래서 여기서는 로그를 실제로 깔고
 * HTTP로 열어 확인한다.
 *
 * <p>{@code open-in-view=false} 하네스라 응답 직렬화가 지연 로딩에 기대면 여기서 터진다 —
 * 인사이트가 집계값만 내려보내는지도 이 테스트가 함께 지킨다.
 */
@DisplayName("[통합] §24-7 게시물 인사이트")
class PostInsightIntegrationTest extends IntegrationTestSupport {

    private static final DateTimeFormatter RESPONSE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** §22-5와 같은 표본 최소치 — 미달이면 구성 비율을 비공개한다. */
    private static final int MINIMUM_SAMPLE_SIZE = 30;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostImpressionRepository postImpressionRepository;
    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private PostSuspensionRepository postSuspensionRepository;
    @Autowired
    private ShowroomVisitRepository showroomVisitRepository;
    @Autowired
    private CreatorFollowRepository creatorFollowRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Creator myShowroom;
    private String creatorToken;
    private Long myPostId;

    @BeforeEach
    void setUpShowroomAndPost() {
        myShowroom = createShowroom("소연 뷰티", "soyeon");
        Users owner = myShowroom.getUser();
        creatorToken = bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());
        myPostId = createPost(myShowroom, "3주 루틴 기록");
    }

    // ------------------------------------------------------------------ 열람 자격

    @Nested
    @DisplayName("열람 자격")
    class Access {

        @Test
        @DisplayName("비로그인은 401")
        void anonymousIsRejected() throws Exception {
            mockMvc.perform(get(insightPath(myPostId)))
                    .andExpect(status().isUnauthorized());
        }

        /** 인사이트는 크리에이터 창구다 — 소비자 앱에 같은 경로를 열어 두지 않는다. */
        @Test
        @DisplayName("소비자 토큰으로는 열 수 없다")
        void consumerIsForbidden() throws Exception {
            Users consumer = createConsumer("mia", null, null);

            mockMvc.perform(get(insightPath(myPostId))
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearerToken(consumer.getUsername(), RoleType.USER, consumer.getId())))
                    .andExpect(status().isForbidden());
        }

        /** 남의 성과를 들여다보는 창구가 되면 안 된다. */
        @Test
        @DisplayName("다른 쇼룸의 게시물은 403")
        void othersPostIsForbidden() throws Exception {
            Creator otherShowroom = createShowroom("지민 뷰티", "jimin");
            Long othersPostId = createPost(otherShowroom, "남의 게시물");

            insights(othersPostId, null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("POST_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("없는 게시물은 404")
        void unknownPostIsNotFound() throws Exception {
            insights(999_999L, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        }

        /**
         * 삭제는 행을 지우지 않고 비공개 보관으로 남긴다(§24-6). 목록에서 사라진 게시물의 인사이트만
         * 계속 열리면 삭제가 반쯤만 된 것으로 보이므로, 여기서도 없는 게시물로 답해야 한다.
         */
        @Test
        @DisplayName("삭제한 게시물은 404 — 행은 남아 있어도 인사이트에서는 없는 게시물이다")
        void deletedPostIsNotFound() throws Exception {
            impression(myPostId, null, "d:anon-1", LocalDateTime.now().minusHours(1));
            Post post = postRepository.findById(myPostId).orElseThrow();
            post.softDelete(PostDeleteReason.SELF, LocalDateTime.now(), LocalDateTime.now().plusDays(30));
            postRepository.save(post);

            insights(myPostId, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        }

        /** 권한은 통과했지만 쇼룸이 없는 계정 — 인사이트는 쇼룸 단위 집계라 성립하지 않는다. */
        @Test
        @DisplayName("쇼룸이 없는 크리에이터 토큰은 404")
        void tokenWithoutShowroomIsNotFound() throws Exception {
            Users stranger = createUser("stranger", "낯선이", RoleType.CREATOR, null, null);

            mockMvc.perform(get(insightPath(myPostId))
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearerToken(stranger.getUsername(), RoleType.CREATOR, stranger.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        }

        /** 노출 중지된 게시물의 인사이트는 계속 열린다 — 중지 시점까지의 누적을 보는 화면이다. */
        @Test
        @DisplayName("노출 중지된 게시물도 열린다")
        void suspendedPostStaysReadable() throws Exception {
            Post post = postRepository.findById(myPostId).orElseThrow();
            post.suspend();
            postRepository.save(post);
            suspend(myPostId, LocalDateTime.now().minusDays(1), null);

            insights(myPostId, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.postId").value(myPostId));
        }
    }

    // ------------------------------------------------------------------ 지표 계산

    @Nested
    @DisplayName("원천 로그에서 계산")
    class Metrics {

        /** 활동이 없는 게시물도 화면이 그려져야 한다 — 카드가 사라지는 대신 0과 null이 내려간다. */
        @Test
        @DisplayName("로그가 하나도 없으면 수치는 0, 비율은 null이다")
        void emptyLogsGiveZerosAndNulls() throws Exception {
            insights(myPostId, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reaction.impressions").value(0))
                    .andExpect(jsonPath("$.reaction.likes").value(0))
                    .andExpect(jsonPath("$.reaction.likeRate").value(nullValue()))
                    .andExpect(jsonPath("$.behavior.showroomVisits").value(0))
                    .andExpect(jsonPath("$.behavior.visitRate").value(nullValue()))
                    .andExpect(jsonPath("$.behavior.followRate").value(nullValue()))
                    .andExpect(jsonPath("$.behavior.followCountMayDecrease").value(true))
                    .andExpect(jsonPath("$.viewers.sampleSize").value(0))
                    .andExpect(jsonPath("$.viewers.ratioSuppressed").value(true));
        }

        @Test
        @DisplayName("노출·좋아요·방문·팔로우를 각 로그에서 세고 비율은 노출로 나눈다")
        void everyMetricComesFromItsOwnLog() throws Exception {
            LocalDateTime recent = LocalDateTime.now().minusHours(2);
            for (int i = 0; i < 8; i++) {
                impression(myPostId, null, "d:anon-" + i, recent);
            }
            like(createConsumer("liker-1", null, null), myPostId, recent);
            like(createConsumer("liker-2", null, null), myPostId, recent);
            visit(myShowroom, null, "d:anon-1", myPostId, recent);
            follow(createConsumer("follower-1", null, null), myShowroom, myPostId, recent);

            insights(myPostId, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reaction.impressions").value(8))
                    .andExpect(jsonPath("$.reaction.likes").value(2))
                    .andExpect(jsonPath("$.reaction.likeRate").value(25.0))
                    .andExpect(jsonPath("$.behavior.showroomVisits").value(1))
                    .andExpect(jsonPath("$.behavior.visitRate").value(12.5))
                    .andExpect(jsonPath("$.behavior.follows").value(1))
                    .andExpect(jsonPath("$.behavior.followRate").value(12.5));
        }

        /** 누적 카운터를 읽었다면 기간과 무관한 값이 나온다 — 창 밖의 로그가 빠지는지가 이 화면의 핵심이다. */
        @Test
        @DisplayName("기간 밖의 로그는 빠지고, 기간을 늘리면 다시 들어온다")
        void windowBoundaryIsEnforcedByTheQuery() throws Exception {
            impression(myPostId, null, "d:recent", LocalDateTime.now().minusHours(1));
            impression(myPostId, null, "d:old", LocalDateTime.now().minusDays(40));
            Users liker = createConsumer("liker-old", null, null);
            like(liker, myPostId, LocalDateTime.now().minusDays(40));

            insights(myPostId, "DAYS_30")
                    .andExpect(jsonPath("$.reaction.impressions").value(1))
                    .andExpect(jsonPath("$.reaction.likes").value(0));

            insights(myPostId, "DAYS_60")
                    .andExpect(jsonPath("$.reaction.impressions").value(2))
                    .andExpect(jsonPath("$.reaction.likes").value(1));
        }

        @Test
        @DisplayName("같은 쇼룸의 다른 게시물 로그는 섞이지 않는다")
        void otherPostsLogsDoNotLeakIn() throws Exception {
            Long anotherPostId = createPost(myShowroom, "다른 게시물");
            LocalDateTime recent = LocalDateTime.now().minusHours(1);
            impression(myPostId, null, "d:anon-1", recent);
            impression(anotherPostId, null, "d:anon-2", recent);
            impression(anotherPostId, null, "d:anon-3", recent);

            insights(myPostId, null).andExpect(jsonPath("$.reaction.impressions").value(1));
            insights(anotherPostId, null).andExpect(jsonPath("$.reaction.impressions").value(2));
        }

        /**
         * ②는 "이 게시물을 보고 한 행동"이다. 귀속되지 않은 방문(귀속 불명)과 다른 게시물의 몫으로
         * 기록된 방문이 섞이면 게시물별 성과가 전부 같아진다.
         */
        @Test
        @DisplayName("귀속되지 않은 방문과 다른 게시물에 귀속된 방문은 세지 않는다")
        void onlyAttributedBehaviorIsCounted() throws Exception {
            Long anotherPostId = createPost(myShowroom, "다른 게시물");
            LocalDateTime recent = LocalDateTime.now().minusHours(1);
            impression(myPostId, null, "d:anon-1", recent);

            visit(myShowroom, null, "d:anon-1", myPostId, recent);        // 이 게시물의 몫
            visit(myShowroom, null, "d:anon-2", anotherPostId, recent);   // 다른 게시물의 몫
            visit(myShowroom, null, "d:anon-3", null, recent);            // 귀속 불명

            follow(createConsumer("follower-1", null, null), myShowroom, myPostId, recent);
            follow(createConsumer("follower-2", null, null), myShowroom, anotherPostId, recent);
            follow(createConsumer("follower-3", null, null), myShowroom, null, recent);

            insights(myPostId, null)
                    .andExpect(jsonPath("$.behavior.showroomVisits").value(1))
                    .andExpect(jsonPath("$.behavior.follows").value(1));
        }

        /** 좋아요를 취소하면 행이 사라진다 — 카운터가 아니라 로그를 세므로 기간 집계도 함께 줄어든다. */
        @Test
        @DisplayName("좋아요를 취소하면 기간 집계에서도 빠진다")
        void canceledLikeLeavesTheAggregate() throws Exception {
            impression(myPostId, null, "d:anon-1", LocalDateTime.now().minusHours(1));
            Users liker = createConsumer("liker-1", null, null);
            like(liker, myPostId, LocalDateTime.now().minusHours(1));

            insights(myPostId, null).andExpect(jsonPath("$.reaction.likes").value(1));

            inTransaction(() -> {
                postLikeRepository.deleteByUserIdAndPostId(liker.getId(), myPostId);
                return null;
            });

            insights(myPostId, null).andExpect(jsonPath("$.reaction.likes").value(0));
        }
    }

    // ------------------------------------------------------------------ 기간

    @Nested
    @DisplayName("기간 선택")
    class Period {

        @Test
        @DisplayName("기간을 보내지 않으면 최근 30일이다")
        void defaultPeriodIsThirtyDays() throws Exception {
            insights(myPostId, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value("DAYS_30"))
                    .andExpect(jsonPath("$.periodLabel").value("최근 30일"))
                    .andExpect(jsonPath("$.truncatedBySuspension").value(false));
        }

        @Test
        @DisplayName("보낸 기간이 라벨과 집계 창에 함께 반영된다")
        void requestedPeriodIsReflected() throws Exception {
            insights(myPostId, "DAYS_7")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value("DAYS_7"))
                    .andExpect(jsonPath("$.periodLabel").value("최근 7일"));

            insights(myPostId, "YEAR_1")
                    .andExpect(jsonPath("$.periodLabel").value("최근 1년"));
        }
    }

    // ------------------------------------------------------------------ 중지 절단

    /**
     * §24-7 화면의 "중지 시점까지 누적" — 상한을 지금으로 두면 노출이 멈춘 뒤의 빈 구간이 창에 섞여
     * 중지된 게시물의 성과가 시간이 갈수록 흔들린다.
     */
    @Nested
    @DisplayName("중지 시점 절단")
    class SuspensionTruncation {

        @Test
        @DisplayName("진행 중인 중지가 있으면 상한이 중지 시각으로 내려가고 그 뒤의 노출은 세지 않는다")
        void openSuspensionCapsTheWindow() throws Exception {
            LocalDateTime suspendedAt = LocalDateTime.now().minusDays(2).withNano(0);
            impression(myPostId, null, "d:before", suspendedAt.minusDays(1));
            impression(myPostId, null, "d:after", suspendedAt.plusHours(1));
            suspend(myPostId, suspendedAt, null);

            insights(myPostId, "DAYS_30")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.truncatedBySuspension").value(true))
                    .andExpect(jsonPath("$.to").value(suspendedAt.format(RESPONSE_TIME)))
                    .andExpect(jsonPath("$.from").value(suspendedAt.minusDays(30).format(RESPONSE_TIME)))
                    .andExpect(jsonPath("$.reaction.impressions").value(1));
        }

        /** 재게시되면 다시 지금까지 쌓인다 — 종결된 조치는 상한을 내리지 않는다. */
        @Test
        @DisplayName("종결된 조치는 절단하지 않는다")
        void resolvedSuspensionDoesNotTruncate() throws Exception {
            LocalDateTime suspendedAt = LocalDateTime.now().minusDays(2).withNano(0);
            impression(myPostId, null, "d:before", suspendedAt.minusDays(1));
            impression(myPostId, null, "d:after", suspendedAt.plusHours(1));
            suspend(myPostId, suspendedAt, SuspensionResolution.REPUBLISHED);

            insights(myPostId, "DAYS_30")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.truncatedBySuspension").value(false))
                    .andExpect(jsonPath("$.reaction.impressions").value(2));
        }

        /** 재조치가 있으면 가장 최근의 진행 중인 조치가 상한이다. */
        @Test
        @DisplayName("조치가 여러 번이면 진행 중인 마지막 조치를 상한으로 쓴다")
        void latestOpenSuspensionWins() throws Exception {
            LocalDateTime first = LocalDateTime.now().minusDays(10).withNano(0);
            LocalDateTime second = LocalDateTime.now().minusDays(3).withNano(0);
            suspend(myPostId, first, SuspensionResolution.REPUBLISHED);
            suspend(myPostId, second, null);

            insights(myPostId, "DAYS_30")
                    .andExpect(jsonPath("$.truncatedBySuspension").value(true))
                    .andExpect(jsonPath("$.to").value(second.format(RESPONSE_TIME)));
        }
    }

    // ------------------------------------------------------------------ ③ 본 사람

    @Nested
    @DisplayName("③ 본 사람")
    class Viewers {

        /**
         * 표본이 최소치에 닿으면 비율을 공개한다. 라벨·구간은 쇼룸 관리(§22-4)와 같은 것을 쓰고,
         * 성별·연령을 알 수 없는 비로그인 조회는 숨기지 않고 "미확인"으로 드러난다.
         */
        @Test
        @DisplayName("표본이 최소치에 닿으면 연령·성별 비율을 공개하고 미확인도 항목으로 남긴다")
        void distributionIsPublishedAtMinimumSample() throws Exception {
            LocalDateTime recent = LocalDateTime.now().minusHours(1);
            for (int i = 0; i < 12; i++) {
                impression(myPostId, createConsumer("f-" + i, "FEMALE", yearsAgo(30)), "u:f-" + i, recent);
            }
            for (int i = 0; i < 6; i++) {
                impression(myPostId, createConsumer("m-" + i, "MALE", yearsAgo(20)), "u:m-" + i, recent);
            }
            for (int i = 0; i < 12; i++) {
                impression(myPostId, null, "d:anon-" + i, recent);
            }

            insights(myPostId, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.viewers.sampleSize").value(MINIMUM_SAMPLE_SIZE))
                    .andExpect(jsonPath("$.viewers.ratioSuppressed").value(false))
                    .andExpect(jsonPath("$.viewers.minimumSampleSize").value(MINIMUM_SAMPLE_SIZE))
                    // 여성 12 · 남성 6 · 미확인 12 (비로그인)
                    .andExpect(jsonPath("$.viewers.genders[0].label").value(Demographics.GENDER_LABELS.get(0)))
                    .andExpect(jsonPath("$.viewers.genders[0].ratio").value(40.0))
                    .andExpect(jsonPath("$.viewers.genders[1].ratio").value(20.0))
                    .andExpect(jsonPath("$.viewers.genders[2].label").value(Demographics.UNKNOWN_LABEL))
                    .andExpect(jsonPath("$.viewers.genders[2].ratio").value(40.0))
                    // 18–24세 6 · 25–34세 12 · 빈 구간 0 · 미확인 12
                    .andExpect(jsonPath("$.viewers.ageGroups.length()").value(Demographics.AGE_LABELS.size()))
                    .andExpect(jsonPath("$.viewers.ageGroups[0].label").value(Demographics.AGE_LABELS.get(0)))
                    .andExpect(jsonPath("$.viewers.ageGroups[0].ratio").value(20.0))
                    .andExpect(jsonPath("$.viewers.ageGroups[1].ratio").value(40.0))
                    .andExpect(jsonPath("$.viewers.ageGroups[2].ratio").value(0.0))
                    .andExpect(jsonPath("$.viewers.ageGroups[3].ratio").value(0.0))
                    .andExpect(jsonPath("$.viewers.ageGroups[4].ratio").value(40.0));
        }

        /** 조회자가 적을 때 구성 비율은 개인을 특정할 수 있다(§22-5) — 항목을 비우고 표시를 켠다. */
        @Test
        @DisplayName("표본이 최소치에 못 미치면 분포를 비운다 — 노출·좋아요는 그대로 내려간다")
        void smallSampleIsSuppressed() throws Exception {
            LocalDateTime recent = LocalDateTime.now().minusHours(1);
            for (int i = 0; i < MINIMUM_SAMPLE_SIZE - 1; i++) {
                impression(myPostId, createConsumer("f-" + i, "FEMALE", yearsAgo(30)), "u:f-" + i, recent);
            }

            insights(myPostId, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.viewers.sampleSize").value(MINIMUM_SAMPLE_SIZE - 1))
                    .andExpect(jsonPath("$.viewers.ratioSuppressed").value(true))
                    .andExpect(jsonPath("$.viewers.ageGroups").isEmpty())
                    .andExpect(jsonPath("$.viewers.genders").isEmpty())
                    .andExpect(jsonPath("$.reaction.impressions").value(MINIMUM_SAMPLE_SIZE - 1));
        }

        /**
         * 노출은 30분 세션 규칙으로 접히지만 세션을 넘긴 재조회는 노출을 새로 센다. 그래도 <b>본 사람</b>은
         * 한 명이다 — 표본을 접지 않으면 열심히 다시 본 소수가 분포를 통째로 끌고 간다.
         */
        @Test
        @DisplayName("같은 사람이 여러 번 봐도 표본은 한 명이다")
        void repeatViewsCollapseIntoOneSample() throws Exception {
            Users viewer = createConsumer("mia", "FEMALE", yearsAgo(30));
            impression(myPostId, viewer, "u:" + viewer.getId(), LocalDateTime.now().minusHours(5));
            impression(myPostId, viewer, "u:" + viewer.getId(), LocalDateTime.now().minusHours(1));

            insights(myPostId, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reaction.impressions").value(2))
                    .andExpect(jsonPath("$.viewers.sampleSize").value(1));
        }

        /** ③도 같은 창을 쓴다 — 창 밖의 조회자가 표본에 남으면 분포가 기간 선택을 따라오지 않는다. */
        @Test
        @DisplayName("기간 밖의 조회자는 표본에서도 빠진다")
        void sampleFollowsTheWindow() throws Exception {
            impression(myPostId, createConsumer("recent", "FEMALE", yearsAgo(30)), "u:recent",
                    LocalDateTime.now().minusHours(1));
            impression(myPostId, createConsumer("old", "MALE", yearsAgo(20)), "u:old",
                    LocalDateTime.now().minusDays(40));

            insights(myPostId, "DAYS_30").andExpect(jsonPath("$.viewers.sampleSize").value(1));
            insights(myPostId, "DAYS_60").andExpect(jsonPath("$.viewers.sampleSize").value(2));
        }
    }

    // ------------------------------------------------------------------ 단계

    private ResultActions insights(Long postId, String period) throws Exception {
        var request = get(insightPath(postId)).header(HttpHeaders.AUTHORIZATION, creatorToken);
        if (period != null) {
            request = request.param("period", period);
        }
        return mockMvc.perform(request);
    }

    private static String insightPath(Long postId) {
        return "/v1/creator/posts/" + postId + "/insights";
    }

    // ------------------------------------------------------------------ 픽스처

    private Users createUser(String username, String nickname, RoleType roleType, String gender, String birthday) {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users(username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, roleType, now, now);
        user.setGender(gender);
        user.setBirthday(birthday);
        return userRepository.save(user);
    }

    private Users createConsumer(String username, String gender, String birthday) {
        return createUser(username, username, RoleType.USER, gender, birthday);
    }

    private Creator createShowroom(String showroomName, String handle) {
        Users owner = createUser("creator-" + handle, showroomName, RoleType.CREATOR, null, null);
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

    private Long createPost(Creator owner, String content) {
        return postRepository.save(Post.published(owner, content, new BigDecimal("0.8000"), LocalDateTime.now()))
                .getId();
    }

    /** 노출 로그 — {@code viewedAt}을 직접 받으므로 기간 경계를 손으로 벌릴 수 있다. */
    private void impression(Long postId, Users viewer, String viewerKey, LocalDateTime viewedAt) {
        postImpressionRepository.save(new PostImpression(
                postRepository.getReferenceById(postId), myShowroom.getId(), viewer, viewerKey, viewedAt));
    }

    /** 좋아요 시각은 {@code @CreatedDate}가 지금으로 찍으므로, 기간 경계를 태우려면 손으로 되돌린다. */
    private void like(Users user, Long postId, LocalDateTime likedAt) {
        postLikeRepository.save(new PostLike(user, postRepository.getReferenceById(postId)));
        jdbc.update("UPDATE post_like SET created_at = ? WHERE user_id = ? AND post_id = ?",
                likedAt, user.getId(), postId);
    }

    private void visit(Creator showroom, Users user, String visitorKey, Long attributedPostId, LocalDateTime at) {
        ShowroomVisit visit = new ShowroomVisit(showroom, user, visitorKey, ShowroomVisitSource.DIRECT, at);
        visit.attributeTo(attributedPostId);
        showroomVisitRepository.save(visit);
    }

    private void follow(Users user, Creator showroom, Long attributedPostId, LocalDateTime at) {
        CreatorFollow follow = new CreatorFollow(user, showroom);
        follow.attributeTo(attributedPostId);
        creatorFollowRepository.save(follow);
        jdbc.update("UPDATE creator_follow SET created_at = ? WHERE user_id = ? AND creator_id = ?",
                at, user.getId(), showroom.getId());
    }

    /** 운영자 조치 1건 — {@code resolution}이 null인 행이 진행 중인 조치다(§24-5). */
    private void suspend(Long postId, LocalDateTime suspendedAt, SuspensionResolution resolution) {
        PostSuspension suspension = new PostSuspension(
                postRepository.getReferenceById(postId), PostSuspensionReason.AD_DISCLOSURE, null, "운영정책 제12조",
                1L, suspendedAt, suspendedAt.plusDays(7));
        if (resolution != null) {
            suspension.resolve(resolution, suspendedAt.plusDays(1));
        }
        postSuspensionRepository.save(suspension);
    }

    /** 생년월일을 고정 문자열로 적으면 해가 바뀔 때 구간이 옮겨간다 — 나이로 적는다. */
    private static String yearsAgo(int age) {
        return LocalDate.now().minusYears(age).toString();
    }
}
