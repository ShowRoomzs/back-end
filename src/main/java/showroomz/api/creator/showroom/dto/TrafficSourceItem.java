package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.showroom.type.ShowroomVisitSource;

/** §22-4 유입 경로 한 줄 — 방문 횟수 기준(사람 수가 아니라 도달 카드의 순방문과 같은 단위). */
@Getter
@Schema(description = "유입 경로 항목")
public class TrafficSourceItem {

    @Schema(description = "유입 경로 코드", example = "INSTAGRAM_LINK")
    private final ShowroomVisitSource source;

    @Schema(description = "유입 경로명", example = "인스타그램 링크")
    private final String label;

    @Schema(description = "비율(%) — 소수점 1자리", example = "62.0")
    private final Double ratio;

    @Schema(description = "방문 횟수", example = "1972")
    private final Long visits;

    public TrafficSourceItem(ShowroomVisitSource source, Double ratio, Long visits) {
        this.source = source;
        this.label = source.getLabel();
        this.ratio = ratio;
        this.visits = visits;
    }
}
