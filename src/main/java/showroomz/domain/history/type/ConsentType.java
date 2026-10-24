package showroomz.domain.history.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 동의 일시를 남겨야 하는 항목. */
@Getter
@AllArgsConstructor
public enum ConsentType {
    /** 광고성 정보 수신 — C15 설정에서 켜고 끄며, 철회 시 일시가 통지 근거가 된다 */
    MARKETING("광고성 정보 수신"),
    /** 1017 C15 — 광고성 토글 3종(혜택 · 이벤트는 위 MARKETING 이다). 켜고 끌 때마다 남긴다. */
    MARKETING_FOLLOW_GROUP_BUY("광고성 정보 수신 · 팔로우 쇼룸 새 공구"),
    MARKETING_FOLLOW_POST("광고성 정보 수신 · 팔로우 쇼룸 새 게시물"),
    MARKETING_GROUP_BUY_ENDING("광고성 정보 수신 · 공구 마감 임박"),
    /** 본인확인 재인증 — C15-2에서 이름·생년월일·성별·휴대폰번호를 다시 수집할 때마다 새로 받는다 */
    IDENTITY_VERIFICATION("본인확인을 위한 개인정보 수집·이용");

    private final String description;
}
