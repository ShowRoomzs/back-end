package showroomz.api.creator.showroom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.showroom.dto.BehaviorStats;
import showroomz.api.creator.showroom.dto.CompositionStats;
import showroomz.api.creator.showroom.dto.DistributionItem;
import showroomz.api.creator.showroom.dto.FollowerStats;
import showroomz.api.creator.showroom.dto.ReachStats;
import showroomz.api.creator.showroom.dto.RegionStats;
import showroomz.api.creator.showroom.dto.ShowroomStatsResponse;
import showroomz.api.creator.showroom.dto.TopContentItem;
import showroomz.api.creator.showroom.dto.TrafficSourceItem;
import showroomz.api.creator.showroom.type.StatsPeriod;
import showroomz.api.creator.showroom.type.TopContentSort;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §22-4 쇼룸 현황 — 쇼룸이라는 공개 채널의 반응 지표를 집계한다.
 *
 * <p>이 서비스를 관통하는 규칙 하나: <b>개인 단위 정보는 어떤 카드에도 담기지 않는다.</b>
 * 방문 로그와 팔로우 관계에서 사람을 식별할 수 있는 값(사용자 ID·배송지 원문)은 여기서 비율로 접히고
 * 응답 밖으로 나가지 않는다. 개별 팔로워 목록과 언팔로우 수는 아예 계산하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowroomStatsService {

    private static final int TOP_CONTENT_SIZE = 5;
    private static final int REGION_VISIBLE_SIZE = 5;
    private static final String UNKNOWN_LABEL = "미확인";
    private static final String OTHER_REGION_LABEL = "기타";

    /**
     * §22-5 표본 최소치 — 팔로워가 적을 때 구성 비율은 개인을 특정할 수 있다.
     * 기준 인원은 아직 확정되지 않았고, 30명은 확정 전까지 쓰는 잠정값이다.
     */
    private static final int MINIMUM_SAMPLE_SIZE = 30;

    /** 시·도 표기가 도로명·지번·구주소마다 제각각이라(서울/서울시/서울특별시) 접두사로 맞춘다. */
    private static final Map<String, String> SIDO_PREFIXES = new LinkedHashMap<>();

    static {
        SIDO_PREFIXES.put("서울", "서울");
        SIDO_PREFIXES.put("부산", "부산");
        SIDO_PREFIXES.put("대구", "대구");
        SIDO_PREFIXES.put("인천", "인천");
        SIDO_PREFIXES.put("광주", "광주");
        SIDO_PREFIXES.put("대전", "대전");
        SIDO_PREFIXES.put("울산", "울산");
        SIDO_PREFIXES.put("세종", "세종");
        SIDO_PREFIXES.put("경기", "경기");
        SIDO_PREFIXES.put("강원", "강원");
        SIDO_PREFIXES.put("충북", "충북");
        SIDO_PREFIXES.put("충청북", "충북");
        SIDO_PREFIXES.put("충남", "충남");
        SIDO_PREFIXES.put("충청남", "충남");
        SIDO_PREFIXES.put("전북", "전북");
        SIDO_PREFIXES.put("전라북", "전북");
        SIDO_PREFIXES.put("전남", "전남");
        SIDO_PREFIXES.put("전라남", "전남");
        SIDO_PREFIXES.put("경북", "경북");
        SIDO_PREFIXES.put("경상북", "경북");
        SIDO_PREFIXES.put("경남", "경남");
        SIDO_PREFIXES.put("경상남", "경남");
        SIDO_PREFIXES.put("제주", "제주");
    }

    private final CreatorRepository creatorRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final ShowroomVisitRepository showroomVisitRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final PostRepository postRepository;

    public ShowroomStatsResponse getStats(Long userId, StatsPeriod period, TopContentSort sort) {
        Creator creator = getMyCreator(userId);
        Long creatorId = creator.getId();

        // 기간은 화면 전체에 하나로 적용된다 — 카드마다 다른 구간을 쓰면 카드 사이 비교가 깨진다.
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = period.startOf(to);
        LocalDateTime previousFrom = period.previousStartOf(to);

        FollowerStats follower = buildFollowerStats(creatorId, from, to, previousFrom);
        ReachStats reach = buildReachStats(creatorId, from, to, follower.getNewFollowers());

        return ShowroomStatsResponse.builder()
                .period(period)
                .periodLabel(period.getLabel())
                .from(from)
                .to(to)
                .follower(follower)
                .reach(reach)
                .composition(buildCompositionStats(creatorId))
                .region(buildRegionStats(creatorId))
                .behavior(buildBehaviorStats(creatorId, from, to, reach.getVisitors()))
                .topContentSort(sort)
                .topContents(buildTopContents(creatorId, from, to, sort))
                .sources(buildSources(creatorId, from, to))
                .build();
    }

    /**
     * 팔로워 — 세 번째 지표는 게시물 총 개수가 아니라 증감률이다.
     * 절대 수치만으로는 좋은지 나쁜지 판단할 수 없어 직전 동일 기간을 비교 기준으로 둔다.
     */
    private FollowerStats buildFollowerStats(Long creatorId, LocalDateTime from, LocalDateTime to,
                                             LocalDateTime previousFrom) {
        long total = creatorFollowRepository.countByCreator_Id(creatorId);
        long newFollowers = creatorFollowRepository.countNewFollowers(creatorId, from, to);
        long previousNewFollowers = creatorFollowRepository.countNewFollowers(creatorId, previousFrom, from);

        // 직전 기간이 0이면 증감률이 정의되지 않는다 — 숫자를 지어내지 않고 비운다.
        Double changeRate = previousNewFollowers == 0
                ? null
                : round((newFollowers - previousNewFollowers) * 100.0 / previousNewFollowers);

        return new FollowerStats(total, newFollowers, changeRate);
    }

    /**
     * 쇼룸 도달 — 전환율의 분모는 방문 횟수가 아니라 방문자 수다.
     * 재방문이 많은 사람이 분모를 키우면 전환율이 실제보다 낮게 나온다.
     */
    private ReachStats buildReachStats(Long creatorId, LocalDateTime from, LocalDateTime to, long newFollowers) {
        long visits = showroomVisitRepository.countVisits(creatorId, from, to);
        long visitors = showroomVisitRepository.countVisitors(creatorId, from, to);
        Double conversionRate = visitors == 0 ? null : round(newFollowers * 100.0 / visitors);
        return new ReachStats(visits, visitors, conversionRate);
    }

    /**
     * 팔로워 구성 — 연령대·성별.
     * 소셜 로그인 동의 항목이라 값이 없는 팔로워가 생기는데, 그 몫을 빼지 않고 "미확인"으로 남긴다.
     * 미확인을 감추면 동의율이 낮을 때 남은 표본이 전체를 대표하는 것처럼 보인다.
     */
    private CompositionStats buildCompositionStats(Long creatorId) {
        List<Object[]> demographics = creatorFollowRepository.findFollowerDemographics(creatorId);
        long sampleSize = demographics.size();

        if (sampleSize < MINIMUM_SAMPLE_SIZE) {
            return new CompositionStats(List.of(), List.of(), sampleSize, true, MINIMUM_SAMPLE_SIZE);
        }

        Map<String, Long> ageGroups = newCountMap("18–24세", "25–34세", "35–44세", "45세 이상", UNKNOWN_LABEL);
        Map<String, Long> genders = newCountMap("여성", "남성", UNKNOWN_LABEL);
        LocalDate today = LocalDate.now();

        for (Object[] row : demographics) {
            String gender = (String) row[0];
            String birthday = (String) row[1];
            increment(ageGroups, toAgeGroup(birthday, today));
            increment(genders, toGenderLabel(gender));
        }

        return new CompositionStats(
                toDistribution(ageGroups, sampleSize),
                toDistribution(genders, sampleSize),
                sampleSize,
                false,
                MINIMUM_SAMPLE_SIZE);
    }

    /**
     * 지역 분포 — 팔로워의 배송지 시·도.
     * 별도 수집 항목이 없어 배송지로만 추정하므로 배송지가 없는 팔로워는 애초에 표본에 들어오지 않는다.
     * 전체 팔로워가 아니라 <b>집계에 잡힌 인원</b>을 분모로 쓰고, 그 수를 함께 내려 편향의 크기를 드러낸다.
     */
    private RegionStats buildRegionStats(Long creatorId) {
        List<String> addresses = deliveryAddressRepository.findDefaultAddressesOfFollowers(creatorId);
        long sampleSize = addresses.size();

        if (sampleSize < MINIMUM_SAMPLE_SIZE) {
            return new RegionStats(List.of(), sampleSize, true, MINIMUM_SAMPLE_SIZE);
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String address : addresses) {
            increment(counts, toSido(address));
        }

        // 상위 5개만 이름을 두고 나머지는 "기타"로 접는다 — 시·도를 전부 나열하면 꼬리가 화면을 잡아먹는다.
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Long>comparingByValue().reversed());

        List<DistributionItem> items = new ArrayList<>();
        long othersCount = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            if (i < REGION_VISIBLE_SIZE && !OTHER_REGION_LABEL.equals(entry.getKey())) {
                items.add(new DistributionItem(entry.getKey(), ratio(entry.getValue(), sampleSize)));
            } else {
                othersCount += entry.getValue();
            }
        }
        if (othersCount > 0) {
            items.add(new DistributionItem(OTHER_REGION_LABEL, ratio(othersCount, sampleSize)));
        }

        return new RegionStats(items, sampleSize, false, MINIMUM_SAMPLE_SIZE);
    }

    /** 팔로워 행동 — 팔로워 1인당 방문 횟수 목록 하나로 세 지표를 모두 낸다. */
    private BehaviorStats buildBehaviorStats(Long creatorId, LocalDateTime from, LocalDateTime to, long visitors) {
        List<Long> visitsPerFollower = showroomVisitRepository.countVisitsPerFollower(creatorId, from, to);

        long followerVisitors = visitsPerFollower.size();
        if (followerVisitors == 0) {
            // 방문 자체가 없으면 "0%"도 사실이 아니다 — 방문이 있는데 팔로워가 없었을 때만 0으로 센다.
            return new BehaviorStats(null, null, visitors == 0 ? null : 0.0);
        }

        long totalFollowerVisits = 0;
        long revisitedFollowers = 0;
        for (Long visits : visitsPerFollower) {
            totalFollowerVisits += visits;
            if (visits >= 2) {
                revisitedFollowers++;
            }
        }

        return new BehaviorStats(
                round((double) totalFollowerVisits / followerVisitors),
                ratio(revisitedFollowers, followerVisitors),
                visitors == 0 ? null : ratio(followerVisitors, visitors));
    }

    /**
     * 인기 콘텐츠 TOP 5 — 기간은 게시일에 적용된다.
     * 노출·좋아요는 게시물에 누적된 값이라 기간 내 증가분이 아니다(시계열 적재가 없다).
     */
    private List<TopContentItem> buildTopContents(Long creatorId, LocalDateTime from, LocalDateTime to,
                                                  TopContentSort sort) {
        PageRequest limit = PageRequest.of(0, TOP_CONTENT_SIZE);
        List<Post> posts = sort == TopContentSort.VIEWS
                ? postRepository.findTopContentsByViews(creatorId, from, to, limit)
                : postRepository.findTopContentsByLikes(creatorId, from, to, limit);

        List<TopContentItem> items = new ArrayList<>(posts.size());
        for (int i = 0; i < posts.size(); i++) {
            items.add(new TopContentItem(i + 1, posts.get(i)));
        }
        return items;
    }

    /**
     * 유입 경로 — 인스타그램 <b>링크 클릭</b>은 여기에 없다.
     * 쇼룸 밖으로 나가는 행동이라 쇼룸 도달 지표와 성격이 다르다.
     */
    private List<TrafficSourceItem> buildSources(Long creatorId, LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = showroomVisitRepository.countVisitsBySource(creatorId, from, to);

        long total = 0;
        for (Object[] row : rows) {
            total += (Long) row[1];
        }
        if (total == 0) {
            return List.of();
        }

        List<TrafficSourceItem> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            ShowroomVisitSource source = (ShowroomVisitSource) row[0];
            long visits = (Long) row[1];
            items.add(new TrafficSourceItem(source, ratio(visits, total), visits));
        }
        items.sort(Comparator.comparing(TrafficSourceItem::getVisits).reversed());
        return items;
    }

    private static String toAgeGroup(String birthday, LocalDate today) {
        if (birthday == null || birthday.isBlank()) {
            return UNKNOWN_LABEL;
        }
        try {
            int age = Period.between(LocalDate.parse(birthday), today).getYears();
            if (age <= 24) {
                // 만 14세 이상만 가입하므로 최저 구간 아래는 구간을 늘리지 않고 여기에 합친다.
                return "18–24세";
            }
            if (age <= 34) {
                return "25–34세";
            }
            if (age <= 44) {
                return "35–44세";
            }
            return "45세 이상";
        } catch (DateTimeParseException e) {
            return UNKNOWN_LABEL;
        }
    }

    private static String toGenderLabel(String gender) {
        if ("FEMALE".equalsIgnoreCase(gender)) {
            return "여성";
        }
        if ("MALE".equalsIgnoreCase(gender)) {
            return "남성";
        }
        return UNKNOWN_LABEL;
    }

    /** 배송지 원문에서 시·도만 떼어 낸다 — 시·군·구 이하는 개인을 좁히므로 서비스 밖으로 내보내지 않는다. */
    private static String toSido(String address) {
        if (address == null || address.isBlank()) {
            return OTHER_REGION_LABEL;
        }
        String head = address.trim().split("\\s+")[0];
        for (Map.Entry<String, String> entry : SIDO_PREFIXES.entrySet()) {
            if (head.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return OTHER_REGION_LABEL;
    }

    private static Map<String, Long> newCountMap(String... labels) {
        // 빈 상태에서도 항목이 사라지지 않도록 라벨을 미리 깔아 둔다(화면 순서도 그대로 유지된다).
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String label : labels) {
            counts.put(label, 0L);
        }
        return counts;
    }

    private static void increment(Map<String, Long> counts, String key) {
        counts.merge(key, 1L, Long::sum);
    }

    private static List<DistributionItem> toDistribution(Map<String, Long> counts, long total) {
        List<DistributionItem> items = new ArrayList<>(counts.size());
        counts.forEach((label, count) -> items.add(new DistributionItem(label, ratio(count, total))));
        return items;
    }

    private static Double ratio(long value, long total) {
        return total == 0 ? 0.0 : round(value * 100.0 / total);
    }

    private static Double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private Creator getMyCreator(Long userId) {
        return creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }
}
