package showroomz.global.payment.portone;

import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 게이트웨이 추상화(결제 계획서 6-1). 서비스 계층은 이 인터페이스만 본다 — 포트원 코드·채널키·HTTP는 구현이 안다.
 *
 * <p>모든 메서드는 <b>트랜잭션 밖</b>에서 불린다(4-7). 결과를 모르는 실패는 {@link PaymentGatewayException},
 * 명시적 거절은 {@link PaymentGatewayRejectedException}이다.
 */
public interface PortOnePaymentGateway {

    /** 이 게이트웨이의 상점 ID — 조회 응답의 {@code storeId}와 대조한다(2-3 ②). */
    String storeId();

    /** 결제창을 열기 전 금액 고정(2-1). 포트원이 결제창의 totalAmount를 이 값과 대조해 다르면 거절한다. */
    void preRegister(String paymentId, long totalAmount, String currency);

    /** {@code GET /payments/{paymentId}} — 없으면 empty(404). */
    Optional<PortOnePayment> getPayment(String paymentId);

    /** 전액 취소. 「이미 취소됨」은 {@link PortOneCancelResult.Outcome#ALREADY_CANCELLED}로 돌려준다. */
    PortOneCancelResult cancel(String paymentId, long amount, String reason);

    /** 일일 대사용 결제 목록 — 구간의 결제 전량(페이지네이션은 구현이 한다). */
    List<PortOnePayment> listPayments(LocalDateTime from, LocalDateTime until, List<PortOneStatus> statuses);

    /** 결제수단에 맞는 채널키. 닫힌 채널이면 empty — 주문서의 결제수단 목록에서 빠진다(5-2). */
    Optional<String> channelKeyFor(PaymentMethod method, EasyPayProvider easyPayProvider);

    /** 포트원 SDK가 받는 결제수단 코드 — 앱이 포트원 코드를 알 필요가 없다(2-2). */
    default String payMethodCode(PaymentMethod method) {
        return PortOneCodes.payMethod(method);
    }
}
