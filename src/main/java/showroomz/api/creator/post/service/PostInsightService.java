package showroomz.api.creator.post.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.post.DTO.PostInsightDto;
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
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 게시물 인사이트 3단 (§24-7).
 *
 * <p>지표를 <b>누적 카운터가 아니라 원천 로그에서</b> 계산한다. {@code impression_count}·{@code like_count}는
 * 목록의 빠른 표시를 위한 값이고, "최근 30일"·"연령 분포"·"이 게시물을 보고 한 행동"은 카운터로는
 * 만들 수 없다.
 *
 * <p>롤업 집계 테이블은 아직 만들지 않는다. 로그만 있으면 언제든 소급 생성되고, 초기 트래픽에서는
 * 인덱스 조회로 충분하다. 도입 기준선은 <b>{@code post_impression} 1천만 행</b> 또는
 * <b>이 API 응답 500ms 초과</b>다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostInsightService {

    /**
     * §22-5와 같은 표본 최소치 — 조회자가 적을 때 구성 비율은 개인을 특정할 수 있다.
     * 기준 인원은 아직 확정되지 않았고 30명은 확정 전까지 쓰는 잠정값이다.
     */
    private static final int MINIMUM_SAMPLE_SIZE = 30;

    private final PostRepository postRepository;
    private final PostImpressionRepository postImpressionRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostSuspensionRepository postSuspensionRepository;
    private final ShowroomVisitRepository showroomVisitRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final CreatorRepository creatorRepository;

    public PostInsightDto.PostInsightResponse getInsights(Long userId, Long postId, StatsPeriod period) {
        Creator creator = creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.isOwnedBy(creator.getId())) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }
        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime suspendedAt = openSuspensionTime(postId);
        // "중지 시점까지 누적"(S8/S11 화면)은 별도 저장 없이 상한만 바꾸면 같은 쿼리로 나온다.
        boolean truncated = suspendedAt != null && suspendedAt.isBefore(now);
        LocalDateTime to = truncated ? suspendedAt : now;
        LocalDateTime from = period.startOf(to);

        long impressions = postImpressionRepository.countByPostIdInPeriod(postId, from, to);
        long likes = postLikeRepository.countByPostIdInPeriod(postId, from, to);
        long visits = showroomVisitRepository.countAttributedVisits(postId, from, to);
        long follows = creatorFollowRepository.countAttributedFollows(postId, from, to);

        return PostInsightDto.PostInsightResponse.builder()
                .postId(postId)
                .period(period)
                .periodLabel(period.getLabel())
                .from(from)
                .to(to)
                .truncatedBySuspension(truncated)
                .reaction(PostInsightDto.ReactionStats.builder()
                        .impressions(impressions)
                        .likes(likes)
                        .likeRate(rateOf(likes, impressions))
                        .build())
                .behavior(PostInsightDto.BehaviorStats.builder()
                        .showroomVisits(visits)
                        .visitRate(rateOf(visits, impressions))
                        .follows(follows)
                        .followRate(rateOf(follows, impressions))
                        .followCountMayDecrease(true)
                        .build())
                .viewers(buildViewerStats(postId, from, to))
                .build();
    }

    /**
     * ③ 본 사람 — 같은 사람이 여러 번 본 것을 {@code viewerKey}로 접은 뒤 분포를 낸다.
     *
     * <p>비로그인 조회는 연령·성별을 알 수 없어 "미확인"으로 분류된다(§24-7 표본 한계).
     * 미확인을 숨기지 않고 항목으로 드러내야 인플루언서가 표본의 한계를 안다.
     */
    private PostInsightDto.ViewerStats buildViewerStats(Long postId, LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = postImpressionRepository.findViewerDemographics(postId, from, to);
        long sampleSize = rows.size();

        if (sampleSize < MINIMUM_SAMPLE_SIZE) {
            return PostInsightDto.ViewerStats.builder()
                    .ageGroups(List.of())
                    .genders(List.of())
                    .sampleSize(sampleSize)
                    .ratioSuppressed(true)
                    .minimumSampleSize(MINIMUM_SAMPLE_SIZE)
                    .build();
        }

        Map<String, Long> ageGroups = Demographics.newCountMap(Demographics.AGE_LABELS);
        Map<String, Long> genders = Demographics.newCountMap(Demographics.GENDER_LABELS);
        LocalDate today = LocalDate.now();

        for (Object[] row : rows) {
            String gender = (String) row[1];
            String birthday = (String) row[2];
            Demographics.increment(ageGroups, Demographics.ageGroupOf(birthday, today));
            Demographics.increment(genders, Demographics.genderLabelOf(gender));
        }

        return PostInsightDto.ViewerStats.builder()
                .ageGroups(Demographics.toDistribution(ageGroups, sampleSize))
                .genders(Demographics.toDistribution(genders, sampleSize))
                .sampleSize(sampleSize)
                .ratioSuppressed(false)
                .minimumSampleSize(MINIMUM_SAMPLE_SIZE)
                .build();
    }

    private LocalDateTime openSuspensionTime(Long postId) {
        return postSuspensionRepository.findFirstByPost_IdAndResolutionIsNullOrderBySuspendedAtDesc(postId)
                .map(PostSuspension::getSuspendedAt)
                .orElse(null);
    }

    /** 분모가 0이면 {@code null}이다 — 0%로 표시하면 "반응이 없었다"로 읽히지만 사실은 "잰 적이 없다"다 */
    private static Double rateOf(long value, long total) {
        return total == 0 ? null : Demographics.round(value * 100.0 / total);
    }
}
