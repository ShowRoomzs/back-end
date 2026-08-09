package showroomz.domain.message.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ThreadStatus {
    OPEN("열림"),
    DORMANT("휴면");

    private final String description;
}
