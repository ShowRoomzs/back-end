package showroomz.api.app.market.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MarketFollowResponse {
    private boolean isFollowed; // 최종 상태 (true: 팔로우됨, false: 취소됨)
    private String message;
}

