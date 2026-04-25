package showroomz.api.admin.faq.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqReorderRequest {

    @NotEmpty(message = "FAQ ID 목록은 비어있을 수 없습니다.")
    private List<@NotNull(message = "FAQ ID는 null일 수 없습니다.") Long> faqIds;

    public FaqReorderRequest(List<Long> faqIds) {
        this.faqIds = faqIds;
    }
}
