package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 조기 마감 요청 사유(C3). */
@Getter
@RequiredArgsConstructor
public enum EarlyCloseReasonCode {
    STOCK_OUT("재고 소진"),
    TARGET_REACHED("판매 목표 달성"),
    ETC("기타");

    private final String label;

    public boolean requiresMemo() {
        return this == ETC;
    }
}
