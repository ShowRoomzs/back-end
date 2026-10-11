package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이슈 스레드 유형(C5) — <b>폐기 이력 · 신규 생성 없음</b>(2026-10-06 · 44 정산조정 이슈스레드 설계서 7절). 이슈는 정산 조정 요청으로만
 * 열린다({@code domain.settlement.adjustment}). 기존 행 때문에 보존한다.
 */
@Getter
@RequiredArgsConstructor
public enum GroupBuyIssueType {
    CONTENT_FULFILLMENT("콘텐츠 이행 문제"),
    TERMS_INTERPRETATION("계약 조건 해석 이견"),
    SETTLEMENT_AMOUNT("정산 금액 이견"),
    ETC("기타");

    private final String label;
}
