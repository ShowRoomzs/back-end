package showroomz.domain.product.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductInspectionStatus {
    WAITING("검수 대기"),
    HOLD("보류"),
    APPROVED("승인"),
    REJECTED("반려"),
    REAPPLIED("재신청");

    private final String description;
}
