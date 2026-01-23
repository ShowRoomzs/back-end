package showroomz.api.app.market.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketListResponse {
    private Long marketId;
    private String marketName;
    private String marketImageUrl;
}
