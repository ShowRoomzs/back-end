package showroomz.domain.history.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DeviceType {
    ANDROID("안드로이드"),
    IPHONE("아이폰"),
    DESKTOP_CHROME("PC (크롬)"),
    DESKTOP_EDGE("PC (엣지)"),
    UNKNOWN("알 수 없음");

    private final String description;
}
