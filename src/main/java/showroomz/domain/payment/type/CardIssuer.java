package showroomz.domain.payment.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** C9 카드사 목록 10개. 포트원 {@code CardCompany} 코드는 게이트웨이가 변환한다. 할부·카드번호는 받지 않는다(일시불 고정). */
@Getter
@RequiredArgsConstructor
public enum CardIssuer {
    SHINHAN("신한"),
    SAMSUNG("삼성"),
    HYUNDAI("현대"),
    KB("KB국민"),
    LOTTE("롯데"),
    HANA("하나"),
    BC("BC"),
    NH("NH농협"),
    WOORI("우리"),
    KAKAOBANK("카카오뱅크");

    private final String label;
}
