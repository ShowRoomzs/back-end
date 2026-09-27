package showroomz.api.app.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.type.PaymentStatus;

/** 결제 확정 API DTO(결제 계획서 5-4). */
public class PaymentDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "RN SDK onComplete 가 돌려준 값 — 힌트일 뿐이다. 없어도 된다(앱 재진입 후 재확인)")
    public static class ClientResult {
        @Schema(description = "결제창 오류 코드 — 성공이면 null", nullable = true)
        private String code;
        @Schema(nullable = true)
        private String message;
        @Schema(nullable = true)
        private String txId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "결제 결과 확정 요청")
    public static class CompleteRequest {
        private ClientResult clientResult;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "결제 확정 응답 — orderStatus 가 PAID 면 완료 화면, paymentStatus 가 FAILED 면 재시도, "
            + "PAYMENT_PENDING + READY 면 결제창이 아직 열린 것으로 보고 잠시 뒤 다시 부른다")
    public static class CompleteResponse {
        private Long orderId;
        private String orderNumber;
        private OrderStatus orderStatus;
        private PaymentStatus paymentStatus;
        @Schema(description = "실패·자동 취소 사유", nullable = true)
        private String failReason;
    }
}
