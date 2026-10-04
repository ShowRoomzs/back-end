package showroomz.domain.payment.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 간편결제 3종 — 토스페이먼츠 허브형이라 채널 하나로 열린다(2-2). */
@Getter
@RequiredArgsConstructor
public enum EasyPayProvider {
    KAKAOPAY("카카오페이"),
    NAVERPAY("네이버페이"),
    TOSSPAY("토스페이");

    private final String label;
}
