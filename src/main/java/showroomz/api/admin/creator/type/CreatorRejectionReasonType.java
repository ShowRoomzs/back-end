package showroomz.api.admin.creator.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 인플루언서 입점 반려 정형 사유 4종 — 1021 결정 노트 기-36.
 *
 * <p>사유 코드 하나에 라벨이 둘이다. {@link #description} 은 운영자 · 내부 기록용, {@link #noticeLabel} 은 신청자에게 통지하는
 * 라벨이다 — 「허위 팔로워 의심」은 통지에서 「채널 운영 기준 미충족」으로 보낸다. 상세 사유는 내부 기록에만 남고 신청자에게 가지 않는다.
 */
@Getter
@AllArgsConstructor
public enum CreatorRejectionReasonType {
    CHANNEL_PERFORMANCE_UNVERIFIABLE("채널 실적 확인 불가", "채널 실적 확인 불가"),
    IDENTITY_INFO_MISMATCH("본인 인증 정보 불일치", "본인 인증 정보 불일치"),
    SUSPECTED_FAKE_FOLLOWERS("허위 팔로워 의심", "채널 운영 기준 미충족"),
    OTHER("기타", "기타");

    private final String description;
    private final String noticeLabel;

    public static Optional<CreatorRejectionReasonType> fromCode(String code) {
        return code == null ? Optional.empty()
                : Arrays.stream(values()).filter(v -> v.name().equals(code)).findFirst();
    }
}
