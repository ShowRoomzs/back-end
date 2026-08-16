package showroomz.domain.history.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 동의 일시를 남겨야 하는 항목. */
@Getter
@AllArgsConstructor
public enum ConsentType {
    /** 광고성 정보 수신 — C15 설정에서 켜고 끄며, 철회 시 일시가 통지 근거가 된다 */
    MARKETING("광고성 정보 수신"),
    /** 본인확인 재인증 — C15-2에서 이름·생년월일·성별·휴대폰번호를 다시 수집할 때마다 새로 받는다 */
    IDENTITY_VERIFICATION("본인확인을 위한 개인정보 수집·이용");

    private final String description;
}
