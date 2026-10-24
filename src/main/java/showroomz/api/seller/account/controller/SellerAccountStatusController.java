package showroomz.api.seller.account.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.seller.account.docs.SellerAccountStatusControllerDocs;
import showroomz.api.common.account.dto.MemberAccountStatusResponse;
import showroomz.api.seller.account.service.SellerAccountStatusService;
import showroomz.api.seller.common.SellerMarketResolver;

@RestController
@RequestMapping("/v1/seller/account-status")
@RequiredArgsConstructor
public class SellerAccountStatusController implements SellerAccountStatusControllerDocs {

    private final SellerAccountStatusService accountStatusService;

    @Override
    @GetMapping
    public ResponseEntity<MemberAccountStatusResponse> getAccountStatus() {
        return ResponseEntity.ok(accountStatusService.getStatus(SellerMarketResolver.currentEmail()));
    }
}
