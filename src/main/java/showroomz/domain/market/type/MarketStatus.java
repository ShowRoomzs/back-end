package showroomz.domain.market.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MarketStatus {
    ACTIVE("활성"),
    SUSPENDED("정지");

    private final String description;
}
