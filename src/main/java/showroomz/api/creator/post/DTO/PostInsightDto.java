package showroomz.api.creator.post.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.creator.showroom.dto.DistributionItem;
import showroomz.api.creator.showroom.type.StatsPeriod;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시물 인사이트 3단 (§24-7).
 *
 * <p>용어를 쇼룸 관리(§22-4)와 맞춘다 — 와이어의 "조회수"는 <b>노출</b>이고,
 * 좋아요율은 좋아요 ÷ 노출이다. 매출·구매는 판매 현황(#6) 소관이라 여기 없다.
 */
public class PostInsightDto {

    @Schema(description = "① 반응 — 노출 · 좋아요 · 좋아요율")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionStats {
        @Schema(description = "노출 — 기간 내 원천 로그 행 수", example = "2840")
        private Long impressions;

        @Schema(description = "좋아요 — 기간 내 누른 수", example = "24")
        private Long likes;

        @Schema(description = "좋아요율(%) — 좋아요 ÷ 노출. <b>노출이 0이면 null</b>이다(0%로 표시하지 않는다)",
                example = "0.8", nullable = true)
        private Double likeRate;
    }

    @Schema(description = "② 이 게시물을 보고 한 행동 — 게시물을 본 뒤 24시간 이내, 마지막으로 본 게시물에 귀속한다")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorStats {
        @Schema(description = "쇼룸 방문", example = "180")
        private Long showroomVisits;

        @Schema(description = "방문 전환율(%) — 쇼룸 방문 ÷ 노출", example = "6.3", nullable = true)
        private Double visitRate;

        @Schema(description = "팔로우", example = "12")
        private Long follows;

        @Schema(description = "팔로우 전환율(%) — 팔로우 ÷ 노출", example = "0.4", nullable = true)
        private Double followRate;

        @Schema(description = "팔로우 수치가 과거로 갈수록 줄어들 수 있는지 — 언팔로우 시 귀속 행이 사라진다",
                example = "true")
        private Boolean followCountMayDecrease;
    }

    @Schema(description = "③ 본 사람 — 집계값만. 개인 식별 정보·개별 목록은 어떤 화면에도 두지 않는다(§24-7)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViewerStats {
        @Schema(description = "연령대 분포 — 쇼룸 관리와 같은 구간을 쓴다")
        private List<DistributionItem> ageGroups;

        @Schema(description = "성별 분포")
        private List<DistributionItem> genders;

        @Schema(description = "집계 표본(중복 제거한 조회자) 수", example = "1830")
        private Long sampleSize;

        @Schema(description = "표본 최소치 미달로 비율을 비공개했는지", example = "false")
        private Boolean ratioSuppressed;

        @Schema(description = "비율 공개에 필요한 최소 표본 수", example = "30")
        private Integer minimumSampleSize;
    }

    @Schema(description = "게시물 인사이트 응답 (§24-7)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostInsightResponse {
        @Schema(description = "게시물 ID", example = "301")
        private Long postId;

        @Schema(description = "조회 기간", example = "DAYS_30")
        private StatsPeriod period;

        @Schema(description = "기간 표시명", example = "최근 30일")
        private String periodLabel;

        @Schema(description = "집계 시작")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime from;

        @Schema(description = "집계 종료 — 노출 중지된 게시물은 <b>중지 시각</b>이 상한이다(§24-7 화면의 '중지 시점까지 누적')")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime to;

        @Schema(description = "집계가 중지 시점에서 멈췄는지", example = "false")
        private Boolean truncatedBySuspension;

        private ReactionStats reaction;
        private BehaviorStats behavior;
        private ViewerStats viewers;
    }
}
