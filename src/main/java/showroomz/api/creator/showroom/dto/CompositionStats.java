package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

/**
 * §22-4 팔로워 구성 카드 — 연령대·성별의 집계 비율.
 *
 * <p>§22-5 수집 한계 두 가지가 응답 형태에 그대로 들어 있다.
 * ① 소셜 로그인 동의자만 값이 있어 동의하지 않은 팔로워는 "미확인"으로 남는다 —
 *    비율을 숨기지 않고 미확인 항목으로 드러내야 인플루언서가 표본의 한계를 안다.
 * ② 팔로워가 적으면 비율이 개인을 특정할 수 있어, 표본이 기준 인원에 못 미치면
 *    {@code ratioSuppressed=true}로 비율을 비운다.
 */
@Getter
@Schema(description = "팔로워 구성 지표 — 집계값만, 개인 식별 정보 없음")
public class CompositionStats {

    @Schema(description = "연령대 분포 — 표본 부족 시 빈 배열")
    private final List<DistributionItem> ageGroups;

    @Schema(description = "성별 분포 — 표본 부족 시 빈 배열")
    private final List<DistributionItem> genders;

    @Schema(description = "집계 표본(팔로워) 수", example = "1240")
    private final Long sampleSize;

    @Schema(description = "표본 최소치 미달로 비율을 비공개했는지 여부", example = "false")
    private final Boolean ratioSuppressed;

    @Schema(description = "비율 공개에 필요한 최소 표본 수", example = "30")
    private final Integer minimumSampleSize;

    public CompositionStats(List<DistributionItem> ageGroups, List<DistributionItem> genders,
                            Long sampleSize, Boolean ratioSuppressed, Integer minimumSampleSize) {
        this.ageGroups = ageGroups;
        this.genders = genders;
        this.sampleSize = sampleSize;
        this.ratioSuppressed = ratioSuppressed;
        this.minimumSampleSize = minimumSampleSize;
    }
}
