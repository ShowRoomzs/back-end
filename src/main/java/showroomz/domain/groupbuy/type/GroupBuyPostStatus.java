package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GroupBuyPostStatus {
    DRAFT("작성중"),
    SCHEDULED("예약"),
    VISIBLE("노출중"),
    HIDDEN("숨김"),
    CLOSED("종료");

    private final String description;
}
