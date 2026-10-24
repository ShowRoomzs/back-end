package showroomz.api.creator.basicinfo.controller;

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
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.basicinfo.docs.CreatorBasicInfoControllerDocs;
import showroomz.api.creator.basicinfo.dto.CreatorBasicInfoDto;
import showroomz.api.creator.basicinfo.service.CreatorAccountTabService;
import showroomz.api.creator.basicinfo.service.CreatorBusinessTypeChangeService;
import showroomz.api.creator.basicinfo.service.CreatorSettlementInfoService;

@RestController
@RequestMapping("/v1/creator/basic-info")
@RequiredArgsConstructor
public class CreatorBasicInfoController implements CreatorBasicInfoControllerDocs {

    private final CreatorAccountTabService accountTabService;
    private final CreatorSettlementInfoService settlementInfoService;
    private final CreatorBusinessTypeChangeService businessTypeChangeService;

    // ------------------------------------------------------------------ 계정 탭

    @Override
    @GetMapping("/account")
    public ResponseEntity<CreatorBasicInfoDto.AccountResponse> getAccount(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(accountTabService.getAccount(principal.getUserId()));
    }

    @Override
    @PatchMapping("/account/business-email")
    public ResponseEntity<CreatorBasicInfoDto.AccountResponse> updateBusinessEmail(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatorBasicInfoDto.UpdateBusinessEmailRequest request) {
        return ResponseEntity.ok(accountTabService.updateBusinessEmail(principal.getUserId(), request));
    }

    @Override
    @PostMapping("/withdrawal-requests")
    public ResponseEntity<CreatorBasicInfoDto.WithdrawalRequestResponse> requestWithdrawal(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreatorBasicInfoDto.WithdrawalRequestBody request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountTabService.requestWithdrawal(principal.getUserId(), request.getReason()));
    }

    @Override
    @PostMapping("/withdrawal-requests/{requestId}/cancel")
    public ResponseEntity<Void> cancelWithdrawal(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable Long requestId) {
        accountTabService.cancelWithdrawal(principal.getUserId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/withdrawal-requests/{requestId}/acknowledge")
    public ResponseEntity<Void> acknowledgeWithdrawalRejection(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable Long requestId) {
        accountTabService.acknowledgeWithdrawalRejection(principal.getUserId(), requestId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ 정산 정보 탭

    @Override
    @GetMapping("/settlement")
    public ResponseEntity<CreatorBasicInfoDto.SettlementInfoResponse> getSettlement(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(settlementInfoService.getSettlement(principal.getUserId()));
    }

    @Override
    @GetMapping("/settlement-account/change-impact")
    public ResponseEntity<CreatorBasicInfoDto.AccountChangeImpact> getAccountChangeImpact(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(settlementInfoService.getAccountChangeImpact(principal.getUserId()));
    }

    @Override
    @PutMapping("/settlement-account")
    public ResponseEntity<CreatorBasicInfoDto.SettlementInfoResponse> changeSettlementAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatorBasicInfoDto.ChangeSettlementAccountRequest request) {
        return ResponseEntity.ok(settlementInfoService.changeAccount(principal.getUserId(), request));
    }

    @Override
    @org.springframework.web.bind.annotation.DeleteMapping("/settlement-account/change-request")
    public ResponseEntity<CreatorBasicInfoDto.SettlementInfoResponse> cancelSettlementAccountChange(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(settlementInfoService.cancelAccountChange(principal.getUserId()));
    }

    // ------------------------------------------------------------------ 사업자 구분 변경

    @Override
    @PostMapping("/business-type-change-requests")
    public ResponseEntity<CreatorBasicInfoDto.BusinessTypeChangeCreated> requestBusinessTypeChange(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreatorBasicInfoDto.BusinessTypeChangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(businessTypeChangeService.create(principal.getUserId(), request));
    }

    @Override
    @PostMapping("/business-type-change-requests/{requestId}/cancel")
    public ResponseEntity<Void> cancelBusinessTypeChange(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long requestId) {
        businessTypeChangeService.cancel(principal.getUserId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/business-type-change-requests/{requestId}/acknowledge")
    public ResponseEntity<Void> acknowledgeBusinessTypeChange(@AuthenticationPrincipal UserPrincipal principal,
                                                              @PathVariable Long requestId) {
        businessTypeChangeService.acknowledge(principal.getUserId(), requestId);
        return ResponseEntity.noContent().build();
    }
}
