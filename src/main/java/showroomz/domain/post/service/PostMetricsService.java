package showroomz.domain.post.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;

import java.time.LocalDateTime;

/**
 * 게시물 지표 5종 — 노출 · 좋아요 · 좋아요율 · 쇼룸 방문 · 팔로우 (§24-7 · §54-3).
 *
 * <p>어드민 모니터링이 「스튜디오 인사이트와 같은 집계」를 보여야 하므로 스튜디오 {@code PostInsightService}와
 * <b>같은 원천 로그 쿼리</b>를 같은 방식(기간 · 상한)으로 부른다. 누적 카운터({@code impression_count})는 쓰지 않는다 —
 * 중지 · 삭제 시점으로 상한을 자르는 「동결」이 카운터로는 안 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostMetricsService {

    /** 전체 기간의 하한 — 서비스 개시 이전 */
    public static final LocalDateTime ALL_TIME_FROM = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final PostImpressionRepository postImpressionRepository;
    private final PostLikeRepository postLikeRepository;
    private final ShowroomVisitRepository showroomVisitRepository;
    private final CreatorFollowRepository creatorFollowRepository;

    /**
     * @param to 집계 상한(미포함) — 게시중이면 지금, 노출 중지면 조치 시각, 삭제면 삭제 시각
     */
    public PostMetrics measure(Long postId, LocalDateTime from, LocalDateTime to) {
        long impressions = postImpressionRepository.countByPostIdInPeriod(postId, from, to);
        long likes = postLikeRepository.countByPostIdInPeriod(postId, from, to);
        long visits = showroomVisitRepository.countAttributedVisits(postId, from, to);
        long follows = creatorFollowRepository.countAttributedFollows(postId, from, to);
        return new PostMetrics(impressions, likes, rateOf(likes, impressions), visits, follows);
    }

    /**
     * 스튜디오 인사이트({@code PostInsightService.rateOf} → {@code Demographics.round})와 같은 식 — 소수 첫째 자리
     * 반올림 백분율 · 노출 0이면 null. 도메인이 api 패키지를 참조하지 않도록 식을 옮겨 적었다.
     */
    private static Double rateOf(long value, long total) {
        if (total == 0) {
            return null;
        }
        double percent = value * 100.0 / total;
        return Math.round(percent * 10) / 10.0;
    }

    public record PostMetrics(long impressions, long likes, Double likeRate, long showroomVisits, long follows) {
    }
}
