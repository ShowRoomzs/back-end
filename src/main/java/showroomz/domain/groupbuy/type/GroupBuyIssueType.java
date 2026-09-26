package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이슈 스레드 유형(C5). */
@Getter
@RequiredArgsConstructor
public enum GroupBuyIssueType {
    CONTENT_FULFILLMENT("콘텐츠 이행 문제"),
    TERMS_INTERPRETATION("계약 조건 해석 이견"),
    SETTLEMENT_AMOUNT("정산 금액 이견"),
    ETC("기타");

    private final String label;
}
