package showroomz.api.app.order.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.order.docs.PaymentControllerDocs;
import showroomz.api.app.order.dto.PaymentDto;
import showroomz.api.app.order.service.PaymentConfirmService;

@RestController
@RequestMapping("/v1/user/payments")
@RequiredArgsConstructor
@Tag(name = "User - Payment", description = "결제 결과 확정 (C9 → 완료)")
public class PaymentController implements PaymentControllerDocs {

    private final PaymentConfirmService paymentConfirmService;

    @Override
    @PostMapping("/{paymentId}/complete")
    public ResponseEntity<PaymentDto.CompleteResponse> complete(@AuthenticationPrincipal UserPrincipal principal,
                                                                @PathVariable("paymentId") String paymentId,
                                                                @RequestBody(required = false) PaymentDto.CompleteRequest request) {
        PaymentDto.ClientResult clientResult = request != null ? request.getClientResult() : null;
        return ResponseEntity.ok(paymentConfirmService.complete(principal.getUserId(), paymentId, clientResult));
    }
}
