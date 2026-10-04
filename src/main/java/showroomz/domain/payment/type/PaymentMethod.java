package showroomz.domain.payment.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** C9 결제수단 둘 — 카드(카드사 선택) · 간편결제(카카오·네이버·토스). 포트원 코드 변환은 게이트웨이 한 곳에서만 한다(2-2). */
@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
    CARD("카드"),
    EASY_PAY("간편결제");

    private final String label;
}
