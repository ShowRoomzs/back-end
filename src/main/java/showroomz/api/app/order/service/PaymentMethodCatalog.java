package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.app.order.dto.OrderDto;
import showroomz.domain.payment.type.CardIssuer;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;
import showroomz.global.payment.portone.PortOnePaymentGateway;

import java.util.Arrays;
import java.util.List;

/**
 * 지금 열려 있는 결제수단 — 주문서(C9)와 반품·교환 요청 폼(C10-3 고객 귀책 교환의 재발송비)이 같은 목록을 내린다.
 * 채널키가 "-" 로 꺼진 간편결제는 빠져 앱이 고장 난 버튼을 그리지 않는다(5-2).
 */
@Component
@RequiredArgsConstructor
public class PaymentMethodCatalog {

    private final PortOnePaymentGateway gateway;

    public OrderDto.PaymentMethods available() {
        List<CardIssuer> cards = gateway.channelKeyFor(PaymentMethod.CARD, null).isPresent()
                ? Arrays.asList(CardIssuer.values()) : List.of();
        List<EasyPayProvider> providers = Arrays.stream(EasyPayProvider.values())
                .filter(provider -> gateway.channelKeyFor(PaymentMethod.EASY_PAY, provider).isPresent())
                .toList();
        return OrderDto.PaymentMethods.builder().cardIssuers(cards).easyPayProviders(providers).build();
    }
}
