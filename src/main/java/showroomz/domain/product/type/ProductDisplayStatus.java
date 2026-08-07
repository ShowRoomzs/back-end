package showroomz.domain.product.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductDisplayStatus {
    DISPLAY("진열"),
    HIDDEN("미진열"),
    PENDING_REVIEW("재검토 대기"),
    HIDE_REQUEST("미진열 요청");

    private final String description;

    /** 앱/유저에게 노출 가능한 상태인지 */
    public boolean isVisible() {
        return this == DISPLAY;
    }
}
