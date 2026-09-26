package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 종결 3종(§29-2). 조기 마감도 상태는 ENDED다 — 이 값이 둘을 가른다. */
@Getter
@RequiredArgsConstructor
public enum GroupBuyCloseType {
    COMPLETED("기간 종료"),
    EARLY_CLOSED("조기 마감"),
    SUSPENDED("중단");

    private final String label;
}
