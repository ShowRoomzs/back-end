package showroomz.api.creator.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.creator.post.DTO.PostInsightDto;
import showroomz.api.creator.showroom.dto.DistributionItem;
import showroomz.api.creator.showroom.service.Demographics;
import showroomz.api.creator.showroom.type.StatsPeriod;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * §24-7 게시물 인사이트 3단 — 단위 테스트.
 *
 * <p>이 서비스는 저장하는 것이 없다. 하는 일은 <b>집계 창(from~to)을 정하고</b>, 원천 로그에서 받은
 * 수치를 비율로 바꾸고, 표본이 적을 때 비율을 가리는 것뿐이다. 그래서 여기서 지키는 것도 그 세 가지다.
 *
 * <p>가장 무너지기 쉬운 자리가 <b>분모</b>와 <b>상한 시각</b>이다. 노출이 0인데 0%를 내려보내면
 * 화면은 "반응이 없었다"로 읽지만 사실은 "잰 적이 없다"이고, 중지된 게시물의 상한을 지금으로 두면
 * 중지 이후의 빈 구간이 분모에 섞여 성과가 실제보다 낮게 보인다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("[단위] §24-7 게시물 인사이트")
class PostInsightServiceTest {

    private static final long USER_ID = 42L;
    private static final long CREATOR_ID = 5L;
    private static final long OTHER_CREATOR_ID = 9L;
    private static final long POST_ID = 301L;

    /** 서비스의 {@code MINIMUM_SAMPLE_SIZE}와 같은 값 — 이 수치가 바뀌면 테스트도 함께 깨져야 한다. */
    private static final int MINIMUM_SAMPLE_SIZE = 30;

    private static final AtomicInteger VIEWER_SEQUENCE = new AtomicInteger();

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostImpressionRepository postImpressionRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private PostSuspensionRepository postSuspensionRepository;
    @Mock
    private ShowroomVisitRepository showroomVisitRepository;
    @Mock
    private CreatorFollowRepository creatorFollowRepository;
    @Mock
    private CreatorRepository creatorRepository;

    @InjectMocks
    private PostInsightService postInsightService;

    private Creator me;

    @BeforeEach
    void setUp() {
        me = creator(CREATOR_ID);
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(publishedPost(me)));
        given(postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(POST_ID))
                .willReturn(Optional.empty());

        // 기본값은 전부 0 — 각 테스트는 자기가 보는 지표만 덮어쓴다.
        givenMetrics(0L, 0L, 0L, 0L);
        given(postImpressionRepository.findViewerDemographics(anyLong(), any(), any())).willReturn(List.of());
    }

    // ------------------------------------------------------------------ 열람 자격

    @Nested
    @DisplayName("열람 자격")
    class Access {

        @Test
        @DisplayName("크리에이터로 등록되지 않은 사용자는 인사이트를 열 수 없다")
        void nonCreatorIsRejected() {
            given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> insights())
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CREATOR_NOT_FOUND);
        }

        @Test
        @DisplayName("없는 게시물은 404다")
        void unknownPostIsRejected() {
            given(postRepository.findById(POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> insights())
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }

        /** 인사이트는 남의 성과를 들여다보는 창구가 되면 안 된다 — 소유자가 아니면 수치를 한 줄도 계산하지 않는다. */
        @Test
        @DisplayName("남의 게시물은 열 수 없고, 집계 쿼리도 돌지 않는다")
        void othersPostIsRejectedBeforeAnyAggregation() {
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(publishedPost(creator(OTHER_CREATOR_ID))));

            assertThatThrownBy(() -> insights())
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_ACCESS_DENIED);

            verify(postImpressionRepository, never()).countByPostIdInPeriod(anyLong(), any(), any());
        }

        /**
         * 삭제는 인플루언서 기준의 삭제라 행이 남는다(§24-6). 인사이트에서는 <b>없는 것과 같아야</b> 하므로
         * "권한 없음"이 아니라 "없음"으로 답한다 — 본인이 지운 글이 목록에서 사라진 뒤 인사이트만
         * 열리면 삭제가 반쯤만 된 것으로 보인다.
         */
        @Test
        @DisplayName("삭제한 내 게시물은 권한 오류가 아니라 없는 게시물로 답한다")
        void deletedPostLooksAbsent() {
            Post deleted = publishedPost(me);
            deleted.softDelete(PostDeleteReason.SELF, LocalDateTime.now(), LocalDateTime.now().plusDays(30));
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(deleted));

            assertThatThrownBy(() -> insights())
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }

        /** 소유권을 먼저 본다 — 남의 게시물의 삭제 여부까지 알려 줄 이유가 없다. */
        @Test
        @DisplayName("남이 삭제한 게시물은 삭제 여부를 알려주지 않고 권한으로 막는다")
        void othersDeletedPostIsRejectedByOwnership() {
            Post deleted = publishedPost(creator(OTHER_CREATOR_ID));
            deleted.softDelete(PostDeleteReason.SELF, LocalDateTime.now(), LocalDateTime.now().plusDays(30));
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(deleted));

            assertThatThrownBy(() -> insights())
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_ACCESS_DENIED);
        }

        /** 노출 중지된 게시물의 인사이트는 계속 열린다 — 중지 시점까지의 누적을 보여 주는 화면이다(§24-7). */
        @Test
        @DisplayName("노출 중지된 게시물도 인사이트는 열린다")
        void suspendedPostStaysReadable() {
            Post suspended = publishedPost(me);
            suspended.suspend();
            given(postRepository.findById(POST_ID)).willReturn(Optional.of(suspended));

            assertThat(insights().getPostId()).isEqualTo(POST_ID);
        }
    }

    // ------------------------------------------------------------------ ① 반응

    @Nested
    @DisplayName("① 반응 — 노출 · 좋아요 · 좋아요율")
    class Reaction {

        @Test
        @DisplayName("좋아요율은 좋아요 ÷ 노출이고 소수점 1자리로 접는다")
        void likeRateIsLikesOverImpressions() {
            givenMetrics(2840L, 24L, 0L, 0L);

            PostInsightDto.ReactionStats reaction = insights().getReaction();

            assertThat(reaction.getImpressions()).isEqualTo(2840L);
            assertThat(reaction.getLikes()).isEqualTo(24L);
            // 24 / 2840 = 0.845…% → 0.8
            assertThat(reaction.getLikeRate()).isEqualTo(0.8);
        }

        /**
         * 0%로 내려보내면 화면은 "반응이 없었다"로 읽는다. 사실은 "잰 적이 없다"이므로 비운다.
         */
        @Test
        @DisplayName("노출이 0이면 좋아요율은 0%가 아니라 null이다")
        void likeRateIsNullWithoutImpressions() {
            givenMetrics(0L, 0L, 0L, 0L);

            PostInsightDto.ReactionStats reaction = insights().getReaction();

            assertThat(reaction.getImpressions()).isZero();
            assertThat(reaction.getLikeRate()).isNull();
        }

        /**
         * 노출은 30분 세션으로 접히고 좋아요는 접히지 않으므로, 좋아요가 노출보다 많은 구간이 생길 수 있다.
         * 100%를 넘는 비율을 깎아 감추지 않는다 — 감추면 정의가 어긋난 것을 아무도 모른다.
         */
        @Test
        @DisplayName("좋아요가 노출보다 많아도 비율을 100%로 깎지 않는다")
        void likeRateIsNotCappedAtHundred() {
            givenMetrics(10L, 12L, 0L, 0L);

            assertThat(insights().getReaction().getLikeRate()).isEqualTo(120.0);
        }

        @Test
        @DisplayName("노출은 있는데 좋아요가 없으면 0%다 — 이때는 실제로 잰 결과다")
        void zeroLikesWithImpressionsIsZeroPercent() {
            givenMetrics(500L, 0L, 0L, 0L);

            assertThat(insights().getReaction().getLikeRate()).isEqualTo(0.0);
        }
    }

    // ------------------------------------------------------------------ ② 행동

    @Nested
    @DisplayName("② 이 게시물을 보고 한 행동")
    class Behavior {

        @Test
        @DisplayName("방문·팔로우 전환율의 분모는 노출이다")
        void conversionRatesUseImpressionsAsDenominator() {
            givenMetrics(2840L, 24L, 180L, 12L);

            PostInsightDto.BehaviorStats behavior = insights().getBehavior();

            assertThat(behavior.getShowroomVisits()).isEqualTo(180L);
            // 180 / 2840 = 6.33…% → 6.3
            assertThat(behavior.getVisitRate()).isEqualTo(6.3);
            assertThat(behavior.getFollows()).isEqualTo(12L);
            // 12 / 2840 = 0.42…% → 0.4
            assertThat(behavior.getFollowRate()).isEqualTo(0.4);
        }

        @Test
        @DisplayName("노출이 0이면 두 전환율 모두 null이고 건수는 그대로 내려간다")
        void ratesAreNullButCountsSurvive() {
            givenMetrics(0L, 0L, 3L, 1L);

            PostInsightDto.BehaviorStats behavior = insights().getBehavior();

            assertThat(behavior.getShowroomVisits()).isEqualTo(3L);
            assertThat(behavior.getVisitRate()).isNull();
            assertThat(behavior.getFollows()).isEqualTo(1L);
            assertThat(behavior.getFollowRate()).isNull();
        }

        /**
         * 언팔로우하면 귀속 행이 사라져 과거의 팔로우 수가 줄어든다. 화면이 이 한계를 고지할 수 있도록
         * 서버가 플래그로 알려 준다 — 값이 줄어든 것을 버그로 신고받는 쪽이 더 비싸다.
         */
        @Test
        @DisplayName("팔로우 수가 줄어들 수 있다는 사실을 항상 함께 내려준다")
        void followCountMayDecreaseIsAlwaysTrue() {
            givenMetrics(2840L, 24L, 180L, 12L);
            assertThat(insights().getBehavior().getFollowCountMayDecrease()).isTrue();

            givenMetrics(0L, 0L, 0L, 0L);
            assertThat(insights().getBehavior().getFollowCountMayDecrease()).isTrue();
        }
    }

    // ------------------------------------------------------------------ 집계 창

    @Nested
    @DisplayName("집계 창")
    class Window {

        @Test
        @DisplayName("기본 30일 — 시작은 종료에서 30일 거슬러 올라간 시각이고 라벨도 함께 내려간다")
        void defaultWindowIsThirtyDays() {
            PostInsightDto.PostInsightResponse response = insights(StatsPeriod.DAYS_30);

            assertThat(response.getPeriod()).isEqualTo(StatsPeriod.DAYS_30);
            assertThat(response.getPeriodLabel()).isEqualTo("최근 30일");
            assertThat(response.getTo()).isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS));
            assertThat(response.getFrom()).isEqualTo(response.getTo().minusDays(30));
        }

        /**
         * 창이 지표별로 갈리면 "노출은 30일, 좋아요는 7일" 같은 조합이 한 화면에 섞인다 —
         * 응답에 실린 from·to가 <b>모든</b> 집계 쿼리에 그대로 들어갔는지 확인한다.
         */
        @Test
        @DisplayName("응답에 실린 창을 다섯 개 집계 쿼리가 모두 똑같이 쓴다")
        void everyQuerySharesOneWindow() {
            PostInsightDto.PostInsightResponse response = insights(StatsPeriod.DAYS_7);

            verifyAllQueriesUsed(response.getFrom(), response.getTo());
        }

        @Test
        @DisplayName("기간을 바꾸면 창이 함께 움직인다")
        void windowFollowsRequestedPeriod() {
            assertThat(windowLengthOf(StatsPeriod.DAYS_7).toDays()).isEqualTo(7);
            assertThat(windowLengthOf(StatsPeriod.DAYS_14).toDays()).isEqualTo(14);
            assertThat(windowLengthOf(StatsPeriod.DAYS_60).toDays()).isEqualTo(60);
            assertThat(windowLengthOf(StatsPeriod.DAYS_90).toDays()).isEqualTo(90);
        }

        /** 달·해는 일수가 고정이 아니다 — 180일·365일로 못박지 않고 캘린더 단위로 거슬러 올라간다. */
        @Test
        @DisplayName("6개월·1년은 일수가 아니라 캘린더 기준으로 거슬러 올라간다")
        void calendarPeriodsAreNotFixedDayCounts() {
            PostInsightDto.PostInsightResponse halfYear = insights(StatsPeriod.MONTHS_6);
            assertThat(halfYear.getFrom()).isEqualTo(halfYear.getTo().minusMonths(6));
            assertThat(halfYear.getPeriodLabel()).isEqualTo("최근 6개월");

            PostInsightDto.PostInsightResponse year = insights(StatsPeriod.YEAR_1);
            assertThat(year.getFrom()).isEqualTo(year.getTo().minusYears(1));
            assertThat(year.getPeriodLabel()).isEqualTo("최근 1년");
        }

        @Test
        @DisplayName("중지되지 않은 게시물은 절단 표시가 꺼진다")
        void livePostIsNotTruncated() {
            assertThat(insights().getTruncatedBySuspension()).isFalse();
        }

        private Duration windowLengthOf(StatsPeriod period) {
            PostInsightDto.PostInsightResponse response = insights(period);
            return Duration.between(response.getFrom(), response.getTo());
        }
    }

    // ------------------------------------------------------------------ 중지 절단

    /**
     * §24-7 화면의 "중지 시점까지 누적" — 중지된 게시물은 상한을 중지 시각으로 내린다.
     *
     * <p>상한을 지금으로 두면 중지 이후의 <b>빈 구간</b>이 창에 섞인다. 노출이 멈춘 뒤의 시간은
     * 분모를 늘리지 않지만(분모는 노출이다) 창 안의 좋아요·방문·팔로우는 계속 쌓일 수 있어
     * 중지된 게시물의 성과가 시간이 갈수록 흔들린다.
     */
    @Nested
    @DisplayName("중지 시점 절단")
    class SuspensionTruncation {

        @Test
        @DisplayName("진행 중인 중지가 있으면 상한이 중지 시각으로 내려가고 절단 표시가 켜진다")
        void openSuspensionCapsTheWindow() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusDays(2);
            givenOpenSuspension(suspendedAt);

            PostInsightDto.PostInsightResponse response = insights(StatsPeriod.DAYS_30);

            assertThat(response.getTruncatedBySuspension()).isTrue();
            assertThat(response.getTo()).isEqualTo(suspendedAt);
            assertThat(response.getFrom()).isEqualTo(suspendedAt.minusDays(30));
            verifyAllQueriesUsed(suspendedAt.minusDays(30), suspendedAt);
        }

        /** 재게시되면 다시 지금까지 쌓인다 — 종결된 조치는 조회 자체에 걸리지 않는다. */
        @Test
        @DisplayName("종결된 조치는 절단하지 않는다")
        void resolvedSuspensionDoesNotTruncate() {
            given(postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(POST_ID))
                    .willReturn(Optional.empty());

            PostInsightDto.PostInsightResponse response = insights();

            assertThat(response.getTruncatedBySuspension()).isFalse();
            assertThat(response.getTo()).isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS));
        }

        /** 시각이 앞당겨 기록된 조치로 창이 미래까지 벌어지면 안 된다 — 상한은 결코 지금을 넘지 않는다. */
        @Test
        @DisplayName("중지 시각이 미래로 적혀 있으면 절단하지 않는다")
        void futureSuspensionTimeIsIgnored() {
            givenOpenSuspension(LocalDateTime.now().plusDays(1));

            PostInsightDto.PostInsightResponse response = insights();

            assertThat(response.getTruncatedBySuspension()).isFalse();
            assertThat(response.getTo()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        /** 중지가 아무리 오래됐어도 기간 선택은 그대로 먹는다 — 창의 길이는 기간이, 상한은 중지가 정한다. */
        @Test
        @DisplayName("절단된 창의 길이도 선택한 기간을 따른다")
        void truncatedWindowKeepsPeriodLength() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusDays(100);
            givenOpenSuspension(suspendedAt);

            PostInsightDto.PostInsightResponse response = insights(StatsPeriod.DAYS_7);

            assertThat(response.getTo()).isEqualTo(suspendedAt);
            assertThat(response.getFrom()).isEqualTo(suspendedAt.minusDays(7));
        }
    }

    // ------------------------------------------------------------------ ③ 본 사람

    /**
     * §24-7 ③ 본 사람 — 규칙을 쇼룸 관리(§22-4)와 공유한다. 같은 사람이 한쪽에서 "25–34세",
     * 다른 쪽에서 "20대"로 세어지면 두 화면을 나란히 놓을 수 없다.
     */
    @Nested
    @DisplayName("③ 본 사람")
    class Viewers {

        @Test
        @DisplayName("표본이 최소치에 못 미치면 분포를 비우고 비공개 표시를 켠다")
        void smallSampleIsSuppressed() {
            givenDemographics(demographics(MINIMUM_SAMPLE_SIZE - 1, "FEMALE", yearsAgo(30)));

            PostInsightDto.ViewerStats viewers = insights().getViewers();

            assertThat(viewers.getRatioSuppressed()).isTrue();
            assertThat(viewers.getAgeGroups()).isEmpty();
            assertThat(viewers.getGenders()).isEmpty();
            assertThat(viewers.getSampleSize()).isEqualTo(29L);
            assertThat(viewers.getMinimumSampleSize()).isEqualTo(MINIMUM_SAMPLE_SIZE);
        }

        /** 경계는 "미달"만 가린다 — 최소치와 같으면 공개한다. */
        @Test
        @DisplayName("표본이 정확히 최소치면 공개한다")
        void exactlyMinimumSampleIsPublished() {
            givenDemographics(demographics(MINIMUM_SAMPLE_SIZE, "FEMALE", yearsAgo(30)));

            PostInsightDto.ViewerStats viewers = insights().getViewers();

            assertThat(viewers.getRatioSuppressed()).isFalse();
            assertThat(viewers.getSampleSize()).isEqualTo(30L);
            assertThat(ratioOf(viewers.getGenders(), "여성")).isEqualTo(100.0);
        }

        @Test
        @DisplayName("조회자가 아무도 없어도 카드는 사라지지 않는다")
        void emptySampleStillReturnsCard() {
            givenDemographics(List.of());

            PostInsightDto.ViewerStats viewers = insights().getViewers();

            assertThat(viewers).isNotNull();
            assertThat(viewers.getSampleSize()).isZero();
            assertThat(viewers.getRatioSuppressed()).isTrue();
        }

        /**
         * 비로그인 조회는 연령·성별을 알 수 없다. 이 표본을 빼고 비율을 내면 남은 표본이 전체처럼 보이므로
         * "미확인"을 항목으로 드러내고 분모에도 포함한다.
         */
        @Test
        @DisplayName("비로그인 조회는 미확인 항목으로 드러나고 분모에도 들어간다")
        void unknownViewersStayVisible() {
            List<Object[]> rows = new ArrayList<>(demographics(30, "FEMALE", yearsAgo(30)));
            rows.addAll(demographics(10, null, null));
            givenDemographics(rows);

            PostInsightDto.ViewerStats viewers = insights().getViewers();

            assertThat(viewers.getSampleSize()).isEqualTo(40L);
            assertThat(ratioOf(viewers.getGenders(), Demographics.UNKNOWN_LABEL)).isEqualTo(25.0);
            assertThat(ratioOf(viewers.getAgeGroups(), Demographics.UNKNOWN_LABEL)).isEqualTo(25.0);
            assertThat(ratioOf(viewers.getGenders(), "여성")).isEqualTo(75.0);
        }

        /** 형식이 깨진 생년월일도 표본에서 빼지 않는다 — 빼면 분모가 조용히 줄어든다. */
        @Test
        @DisplayName("생년월일이 깨진 표본은 미확인으로 분류하되 분모에서 빼지 않는다")
        void malformedBirthdayBecomesUnknown() {
            List<Object[]> rows = new ArrayList<>(demographics(20, "FEMALE", yearsAgo(30)));
            rows.addAll(demographics(20, "FEMALE", "1996년 3월 2일"));
            givenDemographics(rows);

            PostInsightDto.ViewerStats viewers = insights().getViewers();

            assertThat(viewers.getSampleSize()).isEqualTo(40L);
            assertThat(ratioOf(viewers.getAgeGroups(), Demographics.UNKNOWN_LABEL)).isEqualTo(50.0);
            // 성별은 알고 있으므로 성별 쪽 미확인은 늘지 않는다.
            assertThat(ratioOf(viewers.getGenders(), Demographics.UNKNOWN_LABEL)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("분포 항목은 쇼룸 관리와 같은 라벨·같은 순서로 내려간다 — 빈 구간도 0%로 남는다")
        void labelsAndOrderMatchShowroomStats() {
            givenDemographics(demographics(40, "MALE", yearsAgo(20)));

            PostInsightDto.ViewerStats viewers = insights().getViewers();

            assertThat(viewers.getAgeGroups()).extracting(DistributionItem::getLabel)
                    .containsExactlyElementsOf(Demographics.AGE_LABELS);
            assertThat(viewers.getGenders()).extracting(DistributionItem::getLabel)
                    .containsExactlyElementsOf(Demographics.GENDER_LABELS);
            assertThat(ratioOf(viewers.getAgeGroups(), Demographics.AGE_LABELS.get(1))).isEqualTo(0.0);
        }

        @Test
        @DisplayName("연령 구간 경계 — 24세는 아래 구간, 25세는 위 구간이다")
        void ageBoundariesFollowSharedRule() {
            List<Object[]> rows = new ArrayList<>(demographics(15, "FEMALE", yearsAgo(24)));
            rows.addAll(demographics(15, "FEMALE", yearsAgo(25)));
            givenDemographics(rows);

            PostInsightDto.ViewerStats viewers = insights().getViewers();

            assertThat(ratioOf(viewers.getAgeGroups(), Demographics.AGE_LABELS.get(0))).isEqualTo(50.0);
            assertThat(ratioOf(viewers.getAgeGroups(), Demographics.AGE_LABELS.get(1))).isEqualTo(50.0);
        }

        @Test
        @DisplayName("네 구간에 흩어져 있어도 각 비율이 제대로 나뉜다")
        void distributionSpreadsAcrossAllBuckets() {
            List<Object[]> rows = new ArrayList<>(demographics(10, "FEMALE", yearsAgo(20)));
            rows.addAll(demographics(10, "MALE", yearsAgo(30)));
            rows.addAll(demographics(10, "female", yearsAgo(40)));
            rows.addAll(demographics(10, "male", yearsAgo(50)));
            givenDemographics(rows);

            PostInsightDto.ViewerStats viewers = insights().getViewers();

            assertThat(viewers.getAgeGroups()).extracting(DistributionItem::getRatio)
                    .containsExactly(25.0, 25.0, 25.0, 25.0, 0.0);
            // 대소문자가 섞여 들어와도 같은 항목으로 센다.
            assertThat(ratioOf(viewers.getGenders(), "여성")).isEqualTo(50.0);
            assertThat(ratioOf(viewers.getGenders(), "남성")).isEqualTo(50.0);
        }

        /** 표본은 쿼리가 {@code viewerKey}로 중복을 제거한 뒤의 행 수다 — 서비스는 행 수를 그대로 센다. */
        @Test
        @DisplayName("표본 수는 노출 수가 아니라 조회자 행 수다")
        void sampleSizeIsRowCountNotImpressions() {
            givenMetrics(1000L, 0L, 0L, 0L);
            givenDemographics(demographics(35, "FEMALE", yearsAgo(30)));

            PostInsightDto.PostInsightResponse response = insights();

            assertThat(response.getReaction().getImpressions()).isEqualTo(1000L);
            assertThat(response.getViewers().getSampleSize()).isEqualTo(35L);
        }

        @Test
        @DisplayName("성별을 알 수 없는 표본만 있으면 미확인이 100%다")
        void allUnknownIsHundredPercent() {
            givenDemographics(demographics(30, null, null));

            PostInsightDto.ViewerStats viewers = insights().getViewers();

            assertThat(ratioOf(viewers.getGenders(), Demographics.UNKNOWN_LABEL)).isEqualTo(100.0);
            assertThat(ratioOf(viewers.getAgeGroups(), Demographics.UNKNOWN_LABEL)).isEqualTo(100.0);
        }
    }

    // ------------------------------------------------------------------ 단계 · 픽스처

    private PostInsightDto.PostInsightResponse insights() {
        return insights(StatsPeriod.DEFAULT);
    }

    private PostInsightDto.PostInsightResponse insights(StatsPeriod period) {
        return postInsightService.getInsights(USER_ID, POST_ID, period);
    }

    private void givenMetrics(long impressions, long likes, long visits, long follows) {
        given(postImpressionRepository.countByPostIdInPeriod(anyLong(), any(), any())).willReturn(impressions);
        given(postLikeRepository.countByPostIdInPeriod(anyLong(), any(), any())).willReturn(likes);
        given(showroomVisitRepository.countAttributedVisits(anyLong(), any(), any())).willReturn(visits);
        given(creatorFollowRepository.countAttributedFollows(anyLong(), any(), any())).willReturn(follows);
    }

    private void givenDemographics(List<Object[]> rows) {
        given(postImpressionRepository.findViewerDemographics(anyLong(), any(), any())).willReturn(rows);
    }

    private void givenOpenSuspension(LocalDateTime suspendedAt) {
        PostSuspension suspension = new PostSuspension(
                publishedPost(me), PostSuspensionReason.AD_DISCLOSURE, null, "운영정책 제12조",
                9L, suspendedAt, suspendedAt.plusDays(7));
        given(postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(POST_ID))
                .willReturn(Optional.of(suspension));
    }

    /** 응답에 실린 창이 모든 집계 쿼리에 그대로 전달됐는지 — 지표별로 창이 갈리면 여기서 걸린다. */
    private void verifyAllQueriesUsed(LocalDateTime from, LocalDateTime to) {
        verify(postImpressionRepository).countByPostIdInPeriod(POST_ID, from, to);
        verify(postLikeRepository).countByPostIdInPeriod(POST_ID, from, to);
        verify(showroomVisitRepository).countAttributedVisits(POST_ID, from, to);
        verify(creatorFollowRepository).countAttributedFollows(POST_ID, from, to);
        verify(postImpressionRepository).findViewerDemographics(POST_ID, from, to);
    }

    private static Creator creator(long id) {
        return Creator.builder().id(id).showroomName("뷰티 소연").build();
    }

    private static Post publishedPost(Creator owner) {
        Post post = Post.published(owner, "3주 루틴 기록", new BigDecimal("0.8000"), LocalDateTime.now());
        ReflectionTestUtils.setField(post, "id", POST_ID);
        return post;
    }

    /**
     * {viewerKey, 성별, 생년월일} — 쿼리가 내려주는 행 모양 그대로 만든다.
     * {@code viewerKey}는 쿼리가 이미 중복을 제거한 뒤의 값이라 행마다 달라야 한다.
     */
    private static List<Object[]> demographics(int count, String gender, String birthday) {
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[]{"u:" + VIEWER_SEQUENCE.incrementAndGet(), gender, birthday});
        }
        return rows;
    }

    /** 생년월일을 고정 문자열로 적으면 해가 바뀔 때 구간이 옮겨간다 — 나이로 적는다. */
    private static String yearsAgo(int age) {
        return LocalDate.now().minusYears(age).toString();
    }

    private static Double ratioOf(List<DistributionItem> items, String label) {
        return items.stream()
                .filter(item -> label.equals(item.getLabel()))
                .map(DistributionItem::getRatio)
                .findFirst()
                .orElseThrow(() -> new AssertionError("분포에 항목이 없다: " + label));
    }
}
