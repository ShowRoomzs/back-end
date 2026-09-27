package showroomz.global.payment.portone;

import showroomz.domain.payment.type.CardIssuer;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;

/**
 * 서버 enum ↔ 포트원 V2 코드(결제 계획서 2-2). 변환은 여기 한 곳이다 — 앱은 포트원 코드를 모르고, PG를 바꿔도 앱 스펙이 안 바뀐다.
 * 카드사 코드는 포트원 {@code CardCompany}(브라우저 SDK 타입 정의)다 — 실 채널 검증 때 다시 확인한다(2-2 구현 시 확인 항목).
 */
public final class PortOneCodes {

    private PortOneCodes() {
    }

    public static String payMethod(PaymentMethod method) {
        return switch (method) {
            case CARD -> "CARD";
            case EASY_PAY -> "EASY_PAY";
        };
    }

    public static String cardCompany(CardIssuer issuer) {
        if (issuer == null) {
            return null;
        }
        return switch (issuer) {
            case SHINHAN -> "SHINHAN_CARD";
            case SAMSUNG -> "SAMSUNG_CARD";
            case HYUNDAI -> "HYUNDAI_CARD";
            case KB -> "KOOKMIN_CARD";
            case LOTTE -> "LOTTE_CARD";
            case HANA -> "HANA_CARD";
            case BC -> "BC_CARD";
            case NH -> "NH_CARD";
            case WOORI -> "WOORI_CARD";
            case KAKAOBANK -> "KAKAO_BANK";
        };
    }

    public static String easyPayProvider(EasyPayProvider provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case KAKAOPAY -> "KAKAOPAY";
            case NAVERPAY -> "NAVERPAY";
            case TOSSPAY -> "TOSSPAY";
        };
    }
}
