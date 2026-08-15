package showroomz.api.creator.showroom.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import showroomz.api.creator.showroom.dto.ShowroomStatsResponse;
import showroomz.api.creator.showroom.type.StatsPeriod;
import showroomz.api.creator.showroom.type.TopContentSort;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShowroomStatsServiceTest {

    private static final long USER_ID = 42L;
    private static final long CREATOR_ID = 5L;

    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private CreatorFollowRepository creatorFollowRepository;
    @Mock
    private ShowroomVisitRepository showroomVisitRepository;
    @Mock
    private DeliveryAddressRepository deliveryAddressRepository;
    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private ShowroomStatsService showroomStatsService;

    @BeforeEach
    void setUp() {
        Creator me = Creator.builder().id(CREATOR_ID).showroomName("뷰티 소연").build();
        given(creatorRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(me));

        // 기본값 — 각 테스트는 자기가 보는 지표만 덮어쓴다.
        given(creatorFollowRepository.countByCreator_Id(CREATOR_ID)).willReturn(0L);
        given(creatorFollowRepository.countNewFollowers(anyLong(), any(), any())).willReturn(0L);
        given(creatorFollowRepository.findFollowerDemographics(CREATOR_ID)).willReturn(List.of());
        given(showroomVisitRepository.countVisits(anyLong(), any(), any())).willReturn(0L);
        given(showroomVisitRepository.countVisitors(anyLong(), any(), any())).willReturn(0L);
        given(showroomVisitRepository.countVisitsBySource(anyLong(), any(), any())).willReturn(List.of());
        given(showroomVisitRepository.countVisitsPerFollower(anyLong(), any(), any())).willReturn(List.of());
        given(deliveryAddressRepository.findDefaultAddressesOfFollowers(CREATOR_ID)).willReturn(List.of());
        given(postRepository.findTopContentsByLikes(anyLong(), any(), any(), any())).willReturn(List.of());
        given(postRepository.findTopContentsByViews(anyLong(), any(), any(), any())).willReturn(List.of());
    }

    private ShowroomStatsResponse stats() {
        return showroomStatsService.getStats(USER_ID, StatsPeriod.DAYS_30, TopContentSort.LIKES);
    }

    @Test
    @DisplayName("팔로우 전환율의 분모는 방문 횟수가 아니라 방문자 수다")
    void conversionRateUsesVisitorsNotVisits() {
        given(creatorFollowRepository.countNewFollowers(eq(CREATOR_ID), any(), any())).willReturn(42L);
        given(showroomVisitRepository.countVisits(eq(CREATOR_ID), any(), any())).willReturn(3180L);
        given(showroomVisitRepository.countVisitors(eq(CREATOR_ID), any(), any())).willReturn(2410L);

        ShowroomStatsResponse response = stats();

        assertThat(response.getReach().getVisits()).isEqualTo(3180L);
        assertThat(response.getReach().getVisitors()).isEqualTo(2410L);
        // 42 / 2410 = 1.74…% — 횟수(3180)로 나눴다면 1.3%가 나온다.
        assertThat(response.getReach().getFollowConversionRate()).isEqualTo(1.7);
    }

    @Test
    @DisplayName("증감률은 직전 동일 기간과 비교하고, 직전 기간이 0이면 비운다")
    void changeRateComparesPreviousPeriod() {
        // 이번 기간의 시작은 30일 전, 직전 동일 기간의 시작은 60일 전이다.
        given(creatorFollowRepository.countNewFollowers(eq(CREATOR_ID), any(), any()))
                .willAnswer(invocation -> {
                    LocalDateTime from = invocation.getArgument(1);
                    return from.isAfter(LocalDateTime.now().minusDays(45)) ? 42L : 40L;
                });

        ShowroomStatsResponse response = stats();

        assertThat(response.getFollower().getNewFollowers()).isEqualTo(42L);
        assertThat(response.getFollower().getChangeRate()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("직전 기간에 신규 팔로워가 없으면 증감률은 null이다(0에서 늘어난 비율은 정의되지 않는다)")
    void changeRateIsNullWhenPreviousPeriodIsEmpty() {
        given(creatorFollowRepository.countNewFollowers(eq(CREATOR_ID), any(), any())).willReturn(0L);

        assertThat(stats().getFollower().getChangeRate()).isNull();
    }

    @Test
    @DisplayName("표본이 최소치에 못 미치면 구성·지역 비율을 비공개한다")
    void ratiosAreSuppressedForSmallSamples() {
        given(creatorFollowRepository.findFollowerDemographics(CREATOR_ID))
                .willReturn(demographics(10, "FEMALE", "1996-03-02"));
        given(deliveryAddressRepository.findDefaultAddressesOfFollowers(CREATOR_ID))
                .willReturn(Collections.nCopies(10, "서울특별시 강남구 테헤란로 1"));

        ShowroomStatsResponse response = stats();

        assertThat(response.getComposition().getRatioSuppressed()).isTrue();
        assertThat(response.getComposition().getAgeGroups()).isEmpty();
        assertThat(response.getComposition().getSampleSize()).isEqualTo(10L);
        assertThat(response.getRegion().getRatioSuppressed()).isTrue();
        assertThat(response.getRegion().getItems()).isEmpty();
    }

    @Test
    @DisplayName("동의하지 않은 팔로워는 지워지지 않고 미확인 항목으로 남는다")
    void unknownDemographicsStayVisible() {
        List<Object[]> rows = new ArrayList<>(demographics(30, "FEMALE", "1996-03-02"));
        rows.addAll(demographics(10, null, null));
        given(creatorFollowRepository.findFollowerDemographics(CREATOR_ID)).willReturn(rows);

        ShowroomStatsResponse response = stats();

        assertThat(response.getComposition().getRatioSuppressed()).isFalse();
        assertThat(response.getComposition().getGenders())
                .anySatisfy(item -> {
                    assertThat(item.getLabel()).isEqualTo("미확인");
                    assertThat(item.getRatio()).isEqualTo(25.0);
                });
        assertThat(response.getComposition().getAgeGroups())
                .anySatisfy(item -> {
                    assertThat(item.getLabel()).isEqualTo("미확인");
                    assertThat(item.getRatio()).isEqualTo(25.0);
                });
    }

    @Test
    @DisplayName("지역 분포는 시·도로 접고 상위 5개 밖은 기타로 묶는다")
    void regionIsFoldedIntoSido() {
        List<String> addresses = new ArrayList<>();
        addresses.addAll(Collections.nCopies(20, "서울특별시 강남구 테헤란로 1"));
        addresses.addAll(Collections.nCopies(10, "경기도 성남시 분당구 판교로 2"));
        addresses.addAll(Collections.nCopies(5, "부산광역시 해운대구 센텀로 3"));
        addresses.addAll(Collections.nCopies(5, "제주특별자치도 제주시 첨단로 4"));
        addresses.addAll(Collections.nCopies(5, "대구광역시 중구 국채보상로 5"));
        addresses.addAll(Collections.nCopies(3, "인천광역시 연수구 송도과학로 6"));
        addresses.addAll(Collections.nCopies(2, "충청남도 천안시 서북구 불당대로 7"));
        given(deliveryAddressRepository.findDefaultAddressesOfFollowers(CREATOR_ID)).willReturn(addresses);

        ShowroomStatsResponse response = stats();

        assertThat(response.getRegion().getRatioSuppressed()).isFalse();
        assertThat(response.getRegion().getSampleSize()).isEqualTo(50L);
        assertThat(response.getRegion().getItems()).hasSize(6); // 상위 5개 + 기타
        assertThat(response.getRegion().getItems().get(0).getLabel()).isEqualTo("서울");
        assertThat(response.getRegion().getItems().get(0).getRatio()).isEqualTo(40.0);
        assertThat(response.getRegion().getItems().get(5).getLabel()).isEqualTo("기타");
        assertThat(response.getRegion().getItems().get(5).getRatio()).isEqualTo(10.0); // 인천 3 + 충남 2
    }

    @Test
    @DisplayName("팔로워 행동은 방문 횟수 목록 하나로 평균·재방문율·팔로워 비중을 낸다")
    void behaviorStatsAreDerivedFromVisitCounts() {
        given(showroomVisitRepository.countVisitors(eq(CREATOR_ID), any(), any())).willReturn(10L);
        // 팔로워 4명이 각각 1·2·3·4회 방문 → 평균 2.5회, 2회 이상은 3명(75%), 방문자 10명 중 4명(40%)
        given(showroomVisitRepository.countVisitsPerFollower(eq(CREATOR_ID), any(), any()))
                .willReturn(List.of(1L, 2L, 3L, 4L));

        ShowroomStatsResponse response = stats();

        assertThat(response.getBehavior().getAverageVisitsPerFollower()).isEqualTo(2.5);
        assertThat(response.getBehavior().getFollowerRevisitRate()).isEqualTo(75.0);
        assertThat(response.getBehavior().getFollowerShareOfVisitors()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("유입 경로는 방문이 많은 순으로 비율과 함께 내려간다")
    void sourcesAreRankedByVisits() {
        given(showroomVisitRepository.countVisitsBySource(eq(CREATOR_ID), any(), any()))
                .willReturn(List.of(
                        new Object[]{ShowroomVisitSource.APP_SEARCH, 20L},
                        new Object[]{ShowroomVisitSource.INSTAGRAM_LINK, 80L}));

        ShowroomStatsResponse response = stats();

        assertThat(response.getSources()).hasSize(2);
        assertThat(response.getSources().get(0).getSource()).isEqualTo(ShowroomVisitSource.INSTAGRAM_LINK);
        assertThat(response.getSources().get(0).getLabel()).isEqualTo("인스타그램 링크");
        assertThat(response.getSources().get(0).getRatio()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("데이터가 없어도 카드는 사라지지 않는다 — 수치는 0, 분포는 빈 배열")
    void emptyStateKeepsEveryCard() {
        ShowroomStatsResponse response = stats();

        assertThat(response.getFollower().getTotal()).isZero();
        assertThat(response.getReach().getVisits()).isZero();
        assertThat(response.getReach().getFollowConversionRate()).isNull();
        assertThat(response.getComposition()).isNotNull();
        assertThat(response.getRegion()).isNotNull();
        assertThat(response.getBehavior()).isNotNull();
        assertThat(response.getBehavior().getFollowerShareOfVisitors()).isNull();
        assertThat(response.getTopContents()).isEmpty();
        assertThat(response.getSources()).isEmpty();
        assertThat(response.getPeriodLabel()).isEqualTo("최근 30일");
    }

    private static List<Object[]> demographics(int count, String gender, String birthday) {
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[]{gender, birthday});
        }
        return rows;
    }
}
