package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

/**
 * §22-4 지역 분포 카드 — 시·도 단위 비율.
 *
 * <p>§22-5 수집 한계: 별도 수집 항목이 없어 팔로워의 배송지 시·도로만 추정한다.
 * 배송지가 없는 팔로워는 표본에서 빠지므로 {@code sampleSize}를 함께 내려 편향의 크기를 드러낸다.
 * 표본이 기준 인원에 못 미치면 구성과 같은 이유로 비율을 비공개한다.
 */
@Getter
@Schema(description = "지역 분포 지표 — 배송지 시·도 기준 추정")
public class RegionStats {

    @Schema(description = "시·도 분포 — 상위 5개 + 기타. 표본 부족 시 빈 배열")
    private final List<DistributionItem> items;

    @Schema(description = "배송지가 있어 집계에 잡힌 팔로워 수", example = "780")
    private final Long sampleSize;

    @Schema(description = "표본 최소치 미달로 비율을 비공개했는지 여부", example = "false")
    private final Boolean ratioSuppressed;

    @Schema(description = "비율 공개에 필요한 최소 표본 수", example = "30")
    private final Integer minimumSampleSize;

    public RegionStats(List<DistributionItem> items, Long sampleSize,
                       Boolean ratioSuppressed, Integer minimumSampleSize) {
        this.items = items;
        this.sampleSize = sampleSize;
        this.ratioSuppressed = ratioSuppressed;
        this.minimumSampleSize = minimumSampleSize;
    }
}
