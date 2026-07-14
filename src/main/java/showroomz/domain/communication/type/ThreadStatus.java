package showroomz.domain.communication.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ThreadStatus {
    OPEN("열림"),
    DORMANT("휴면");

    private final String description;
}
