package showroomz.api.admin.creator.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CreatorRejectionReasonType {
    CHANNEL_PERFORMANCE_UNVERIFIABLE("채널 실적 확인 불가"),
    IDENTITY_INFO_MISMATCH("본인 인증 정보 불일치"),
    SUSPECTED_FAKE_FOLLOWERS("허위 팔로워 의심"),
    OTHER("기타");

    private final String description;
}
