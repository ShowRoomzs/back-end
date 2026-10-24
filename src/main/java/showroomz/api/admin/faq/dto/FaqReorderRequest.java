package showroomz.api.admin.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Schema(description = "FAQ 노출 순서 변경 요청")
public class FaqReorderRequest {

    @Valid
    @NotEmpty(message = "변경할 FAQ 순서 목록은 비어있을 수 없습니다.")
    @Schema(description = "변경할 FAQ와 노출 순서 목록 — 같은 카테고리 FAQ만 담는다. faqId · displayOrder 는 각각 중복 불가. 담긴 FAQ만 갱신")
    private List<FaqOrderDto> reorderList;

    public FaqReorderRequest(List<FaqOrderDto> reorderList) {
        this.reorderList = reorderList;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Schema(description = "FAQ 노출 순서 항목")
    public static class FaqOrderDto {
        @NotNull(message = "FAQ ID는 null일 수 없습니다.")
        @Schema(description = "FAQ ID", example = "5")
        private Long faqId;

        @NotNull(message = "표시 순서(displayOrder)는 null일 수 없습니다.")
        @Schema(description = "카테고리 안 노출 순서 — 1 이상, 해당 카테고리 FAQ 개수 이하. 작을수록 위", example = "2")
        private Integer displayOrder;

        public FaqOrderDto(Long faqId, Integer displayOrder) {
            this.faqId = faqId;
            this.displayOrder = displayOrder;
        }
    }
}
