package showroomz.api.app.claim.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.claim.docs.UserClaimControllerDocs;
import showroomz.api.app.claim.dto.UserClaimDto;
import showroomz.api.app.claim.service.UserClaimCommandService;
import showroomz.api.app.claim.service.UserClaimQueryService;
import showroomz.domain.order.type.ClaimType;

@RestController
@RequestMapping("/v1/user/claims")
@RequiredArgsConstructor
public class UserClaimController implements UserClaimControllerDocs {

    private final UserClaimQueryService queryService;
    private final UserClaimCommandService commandService;

    @Override
    @GetMapping("/form")
    public ResponseEntity<UserClaimDto.FormResponse> getForm(@AuthenticationPrincipal UserPrincipal principal,
                                                             @RequestParam("orderProductId") Long orderProductId,
                                                             @RequestParam("type") ClaimType type) {
        return ResponseEntity.ok(queryService.getForm(principal.getUserId(), orderProductId, type));
    }

    @Override
    @PostMapping
    public ResponseEntity<UserClaimDto.CreateResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                              @Valid @RequestBody UserClaimDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.create(principal.getUserId(), request));
    }

    @Override
    @GetMapping("/{claimId}")
    public ResponseEntity<UserClaimDto.DetailResponse> getDetail(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable("claimId") Long claimId) {
        return ResponseEntity.ok(queryService.getDetail(principal.getUserId(), claimId));
    }

    @Override
    @PutMapping("/{claimId}/collection-invoice")
    public ResponseEntity<UserClaimDto.DetailResponse> putCollectionInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("claimId") Long claimId,
            @Valid @RequestBody UserClaimDto.InvoiceRequest request) {
        return ResponseEntity.ok(commandService.putCollectionInvoice(principal.getUserId(), claimId, request));
    }

    @Override
    @PostMapping("/payments/{paymentId}/complete")
    public ResponseEntity<UserClaimDto.PaymentCompleteResponse> completePayment(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable("paymentId") String paymentId) {
        return ResponseEntity.ok(commandService.completePayment(principal.getUserId(), paymentId));
    }

    @Override
    @PatchMapping("/{claimId}/reship-address")
    public ResponseEntity<UserClaimDto.DetailResponse> changeReshipAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("claimId") Long claimId,
            @Valid @RequestBody UserClaimDto.ReshipAddressRequest request) {
        return ResponseEntity.ok(
                commandService.changeReshipAddress(principal.getUserId(), claimId, request.getAddressId()));
    }

    @Override
    @PostMapping("/{claimId}/withdraw")
    public ResponseEntity<UserClaimDto.WithdrawResponse> withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                                                  @PathVariable("claimId") Long claimId) {
        return ResponseEntity.ok(commandService.withdraw(principal.getUserId(), claimId));
    }
}
