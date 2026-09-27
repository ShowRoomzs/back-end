package showroomz.global.payment.portone;

import java.time.LocalDateTime;

/**
 * 포트원 조회 응답 중 확정에 쓰는 값(결제 계획서 2-3 ②). {@code rawJson}은 원문이다 — 분쟁 근거로 {@code payment.raw_response}에 남긴다.
 *
 * @param totalAmount {@code amount.total} — 주문 금액과 대조한다
 * @param methodJson  실제 처리된 결제수단 원문({@code method}) — 사용자가 고른 것과 다를 수 있다
 * @param failCode    {@code failure.pgCode}(없으면 reason)
 */
public record PortOnePayment(
        String paymentId,
        PortOneStatus status,
        String storeId,
        String currency,
        Long totalAmount,
        String transactionId,
        String pgProvider,
        String methodJson,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt,
        String failCode,
        String failMessage,
        String rawJson
) {

    public static PortOnePayment of(String paymentId, PortOneStatus status, String storeId, long totalAmount) {
        return new PortOnePayment(paymentId, status, storeId, "KRW", totalAmount, "tx-" + paymentId, "TOSSPAYMENTS",
                null, status == PortOneStatus.PAID ? LocalDateTime.now() : null,
                status == PortOneStatus.CANCELLED ? LocalDateTime.now() : null, null, null, null);
    }

    public PortOnePayment withFailure(String code, String message) {
        return new PortOnePayment(paymentId, status, storeId, currency, totalAmount, transactionId, pgProvider, methodJson,
                paidAt, cancelledAt, code, message, rawJson);
    }
}
