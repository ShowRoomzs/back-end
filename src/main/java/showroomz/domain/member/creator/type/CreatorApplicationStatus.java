package showroomz.domain.member.creator.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CreatorApplicationStatus {
    PENDING("검수 대기"),
    APPROVED("승인"),
    REJECTED("반려");

    private final String description;
}
