package showroomz.Market.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MarketImageStatus {
    APPROVED("검수 완료"),
    UNDER_REVIEW("검수 중"),
    REJECTED("반려됨");

    private final String description;
}

