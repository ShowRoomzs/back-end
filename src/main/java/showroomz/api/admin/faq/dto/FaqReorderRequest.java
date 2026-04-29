package showroomz.api.admin.faq.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqReorderRequest {

    @Valid
    @NotEmpty(message = "변경할 FAQ 순서 목록은 비어있을 수 없습니다.")
    private List<FaqOrderDto> reorderList;

    public FaqReorderRequest(List<FaqOrderDto> reorderList) {
        this.reorderList = reorderList;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class FaqOrderDto {
        @NotNull(message = "FAQ ID는 null일 수 없습니다.")
        private Long faqId;

        @NotNull(message = "표시 순서(displayOrder)는 null일 수 없습니다.")
        private Integer displayOrder;

        public FaqOrderDto(Long faqId, Integer displayOrder) {
            this.faqId = faqId;
            this.displayOrder = displayOrder;
        }
    }
}
