package showroomz.domain.member.user.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C15-3 회원 탈퇴 1단계에서 고르는 이유.
 * 선택하지 않고도 탈퇴할 수 있으므로 저장 시 null이 될 수 있다.
 */
@Getter
@AllArgsConstructor
public enum WithdrawalReason {
    NO_GROUP_BUY("원하는 공구가 없어요"),
    TOO_MANY_NOTIFICATIONS("알림이 너무 많아요"),
    INCONVENIENT_APP("앱이 사용하기 불편해요"),
    PRIVACY_CONCERN("개인정보가 걱정돼요"),
    REJOIN_OTHER_ACCOUNT("다른 계정으로 다시 가입할 거예요"),
    ETC("기타");

    private final String description;
}
