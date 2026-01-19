package showroomz.domain.history.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LoginStatus {
    SUCCESS("성공"),
    ABNORMAL("이상");

    private final String description;
}
