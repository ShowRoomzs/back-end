package showroomz.api.seller.basicinfo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.basicinfo.docs.SellerBasicInfoControllerDocs;
import showroomz.api.seller.basicinfo.dto.SellerBasicInfoDto;
import showroomz.api.seller.basicinfo.service.SellerBasicInfoService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/seller/basic-info")
@RequiredArgsConstructor
public class SellerBasicInfoController implements SellerBasicInfoControllerDocs {

    private final SellerBasicInfoService sellerBasicInfoService;

    @Override
    @GetMapping("/business")
    public ResponseEntity<SellerBasicInfoDto.BusinessInfoResponse> getBusinessInfo() {
        return ResponseEntity.ok(sellerBasicInfoService.getBusinessInfo(getCurrentSellerEmail()));
    }

    @Override
    @PatchMapping("/business")
    public ResponseEntity<Void> updateBusinessInfo(@Valid @RequestBody SellerBasicInfoDto.UpdateBusinessInfoRequest request) {
        sellerBasicInfoService.updateBusinessInfo(getCurrentSellerEmail(), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/settlement")
    public ResponseEntity<SellerBasicInfoDto.SettlementInfoResponse> getSettlementInfo() {
        return ResponseEntity.ok(sellerBasicInfoService.getSettlementInfo(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/manager")
    public ResponseEntity<SellerBasicInfoDto.ManagerInfoResponse> getManagerInfo() {
        return ResponseEntity.ok(sellerBasicInfoService.getManagerInfo(getCurrentSellerEmail()));
    }

    @Override
    @PutMapping("/manager")
    public ResponseEntity<Void> updateManagerInfo(@Valid @RequestBody SellerBasicInfoDto.UpdateManagerInfoRequest request) {
        sellerBasicInfoService.updateManagerInfo(getCurrentSellerEmail(), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/account")
    public ResponseEntity<SellerBasicInfoDto.AccountInfoResponse> getAccountInfo() {
        return ResponseEntity.ok(sellerBasicInfoService.getAccountInfo(getCurrentSellerEmail()));
    }

    @Override
    @PatchMapping("/account/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody SellerBasicInfoDto.ChangePasswordRequest request) {
        sellerBasicInfoService.changePassword(getCurrentSellerEmail(), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/account/email")
    public ResponseEntity<SellerBasicInfoDto.AccountInfoResponse> changeEmail(@Valid @RequestBody SellerBasicInfoDto.ChangeEmailRequest request) {
        return ResponseEntity.ok(sellerBasicInfoService.changeEmail(getCurrentSellerEmail(), request));
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}
