package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * §22-4 비율 막대 한 줄 — 라벨과 비율만 담는다.
 * 인원 수를 내려보내지 않는 이유는 표본이 작을 때 비율보다 인원이 개인을 특정하기 쉽기 때문이다.
 */
@Getter
@Schema(description = "구성·지역 분포 항목")
public class DistributionItem {

    @Schema(description = "항목명", example = "25–34세")
    private final String label;

    @Schema(description = "비율(%) — 소수점 1자리", example = "41.0")
    private final Double ratio;

    public DistributionItem(String label, Double ratio) {
        this.label = label;
        this.ratio = ratio;
    }
}
