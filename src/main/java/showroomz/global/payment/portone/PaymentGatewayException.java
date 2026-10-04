package showroomz.global.payment.portone;

/**
 * 결과를 <b>모르는</b> 외부 호출 실패 — 타임아웃·통신 오류·5xx. 취소가 됐을 수도, 안 됐을 수도 있다(2-3 ⑧).
 * 호출자는 상태를 실패로 적지 않고 수렴(웹훅·재조회)에 맡긴다. 명시적 거절은 {@link PaymentGatewayRejectedException}이다.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
