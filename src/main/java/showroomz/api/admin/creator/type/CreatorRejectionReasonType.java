package showroomz.api.admin.creator.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CreatorRejectionReasonType {
    CHANNEL_INFO_MISMATCH("채널 정보 미일치 또는 확인 불가"),
    FOLLOWER_COUNT_SHORTFALL("팔로워 수 기준 미달"),
    OTHER("기타");

    private final String description;
}
