package showroomz.api.app.order.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.order.dto.PaymentDto;

@Tag(name = "User - Payment", description = "결제 결과 확정 (C9 → 완료)")
public interface PaymentControllerDocs {

    @Operation(
            summary = "결제 결과 확정",
            description = "포트원 RN SDK `onComplete` 를 받은 앱이 부른다. 서버는 앱이 보낸 값을 믿지 않고 **포트원을 다시 조회**해 " +
                    "상태·금액·통화·상점을 주문과 대조한 뒤 주문을 `PAID` 로 올린다. 웹훅과 같은 함수를 타므로 어느 쪽이 먼저 와도 결과는 하나다.\n\n" +
                    "**응답 해석:**\n" +
                    "- `orderStatus: PAID` → 완료 화면\n" +
                    "- `paymentStatus: FAILED` → C9 로 돌아가 재시도(`POST /orders/{orderId}/payments`)\n" +
                    "- `orderStatus: PAYMENT_PENDING` + `paymentStatus: READY` → 아직 결제되지 않았다. `onComplete` 에 `code` 가 있었으면" +
                    "(창 닫음·인증 실패) 결제 안 된 것으로 보고 C9 유지. `code` 가 없는데 READY 면 승인 반영 지연이니 1~2초 간격으로 " +
                    "몇 번(3회 정도) 다시 부르고, 그래도 READY 면 주문 상세로 보낸다(웹훅이 뒤이어 완료시킨다)\n" +
                    "- `paymentStatus: CANCELLED_MISMATCH` / `CANCEL_REQUESTED` → 금액 불일치·만료 후 결제 등으로 자동 취소됨(`failReason`)\n\n" +
                    "멱등이다 — 완료 화면에서 반복해 불러도 종결된 결제는 포트원으로 나가지 않는다. `clientResult` 는 힌트일 뿐이라 없어도 된다.\n\n" +
                    "**권한:** USER (본인 주문의 결제만)"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(mediaType = "application/json",
            examples = @ExampleObject(name = "SDK onComplete 값", value = "{ \"clientResult\": { \"code\": null, \"message\": null, \"txId\": \"0195…\" } }")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 주문·결제 상태",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentDto.CompleteResponse.class),
                            examples = {
                                    @ExampleObject(name = "결제 완료", value = "{ \"orderId\": 1147, \"orderNumber\": \"20260927-000123\", \"orderStatus\": \"PAID\", \"paymentStatus\": \"PAID\", \"failReason\": null }"),
                                    @ExampleObject(name = "결제 실패", value = "{ \"orderId\": 1147, \"orderNumber\": \"20260927-000123\", \"orderStatus\": \"PAYMENT_PENDING\", \"paymentStatus\": \"FAILED\", \"failReason\": \"한도 초과\" }")
                            })),
            @ApiResponse(responseCode = "403", description = "ORDER_ACCESS_DENIED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "포트원 조회 실패(PAYMENT_GATEWAY_ERROR) — 상태 불변, 잠시 뒤 다시 부른다",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PaymentDto.CompleteResponse> complete(@AuthenticationPrincipal UserPrincipal principal,
                                                         @Parameter(description = "결제 ID — 주문 생성 응답의 payment.paymentId", example = "20260927-000123-1")
                                                         @PathVariable("paymentId") String paymentId,
                                                         @RequestBody(required = false) PaymentDto.CompleteRequest request);
}
