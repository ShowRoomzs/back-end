package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.api.creator.showroom.type.StatsPeriod;
import showroomz.api.creator.showroom.type.TopContentSort;

import java.time.LocalDateTime;
import java.util.List;

/**
 * §22-4 쇼룸 현황 — 쇼룸이라는 공개 채널의 반응 지표.
 *
 * <p>개별 팔로워 목록과 언팔로우 수는 어떤 형태로도 담기지 않는다. 전자는 나열해도 인플루언서가
 * 할 수 있는 행동이 없고, 후자는 표시하지 않기로 못 박은 값이다.
 *
 * <p>빈 상태에서도 모든 카드가 그대로 내려간다 — 카드가 사라지면 그 기능이 없는 줄 알기 때문에,
 * 수치는 0으로, 분포는 빈 배열로 채운다.
 */
@Getter
@Builder
@Schema(description = "쇼룸 현황 조회 응답")
public class ShowroomStatsResponse {

    @Schema(description = "적용된 기간", example = "DAYS_30")
    private final StatsPeriod period;

    @Schema(description = "기간 라벨", example = "최근 30일")
    private final String periodLabel;

    @Schema(description = "집계 구간 시작(포함)", example = "2026-07-16T00:00:00")
    private final LocalDateTime from;

    @Schema(description = "집계 구간 종료(미포함)", example = "2026-08-15T00:00:00")
    private final LocalDateTime to;

    @Schema(description = "팔로워")
    private final FollowerStats follower;

    @Schema(description = "쇼룸 도달")
    private final ReachStats reach;

    @Schema(description = "팔로워 구성")
    private final CompositionStats composition;

    @Schema(description = "지역 분포")
    private final RegionStats region;

    @Schema(description = "팔로워 행동")
    private final BehaviorStats behavior;

    @Schema(description = "인기 콘텐츠 정렬 기준", example = "LIKES")
    private final TopContentSort topContentSort;

    @Schema(description = "인기 콘텐츠 TOP 5 — 없으면 빈 배열")
    private final List<TopContentItem> topContents;

    @Schema(description = "유입 경로 — 방문이 없으면 빈 배열")
    private final List<TrafficSourceItem> sources;
}
