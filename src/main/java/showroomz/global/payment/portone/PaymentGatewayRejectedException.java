package showroomz.global.payment.portone;

import lombok.Getter;

/** 포트원이 <b>명시적으로</b> 거절한 호출(4xx) — 결과가 확정됐다. {@code type}은 포트원 오류 코드({@code PAYMENT_NOT_PAID} 등)다. */
@Getter
public class PaymentGatewayRejectedException extends RuntimeException {

    private final int httpStatus;
    private final String type;

    public PaymentGatewayRejectedException(int httpStatus, String type, String message) {
        super("포트원 거절 " + httpStatus + " " + type + (message != null ? " - " + message : ""));
        this.httpStatus = httpStatus;
        this.type = type;
    }
}
