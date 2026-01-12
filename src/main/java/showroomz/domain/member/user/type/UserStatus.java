package showroomz.domain.member.user.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatus {
    NORMAL("정상"),
    DORMANT("휴면"),
    WITHDRAWN("탈퇴");

    private final String description;
}
