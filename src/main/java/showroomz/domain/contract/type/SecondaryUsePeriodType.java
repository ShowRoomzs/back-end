package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 2차 활용 기간 유형. */
@Getter
@RequiredArgsConstructor
public enum SecondaryUsePeriodType {
    FIXED("기간 지정"),
    UNLIMITED("무기한");

    private final String label;
}
