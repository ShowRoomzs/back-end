package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GroupBuyStatus {
    PREPARING("준비중"),
    READY("준비완료"),
    IN_PROGRESS("진행중"),
    CLOSED("종료"),
    SETTLED("정산완료"),
    CANCELED("취소");

    private final String description;
}
