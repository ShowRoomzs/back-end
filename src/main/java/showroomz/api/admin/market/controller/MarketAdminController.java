package showroomz.api.admin.market.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.docs.MarketAdminControllerDocs;
import showroomz.api.admin.market.service.AdminService;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.seller.auth.DTO.SellerDto;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.api.seller.market.DTO.MarketDto;
import showroomz.api.seller.market.service.MarketService;
import showroomz.api.seller.market.type.MarketImageStatus;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class MarketAdminController implements MarketAdminControllerDocs {

    private final MarketService marketService;
    private final AdminService adminService;

    @Override
    @PatchMapping("/markets/{marketId}/image-status")
    public ResponseEntity<Void> updateMarketImageStatus(
            @PathVariable Long marketId,
            @RequestBody MarketDto.UpdateImageStatusRequest request) {

        MarketImageStatus status;
        try {
            status = MarketImageStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        marketService.updateMarketImageStatus(marketId, status);
        return ResponseEntity.noContent().build();
    }

    /**
     * 판매자 계정 승인/반려 API
     */
    @PatchMapping("/sellers/{sellerId}/status")
    public ResponseEntity<Void> updateSellerStatus(
            @PathVariable Long sellerId,
            @RequestBody SellerDto.UpdateStatusRequest request) {

        SellerStatus status;
        try {
            status = SellerStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        adminService.updateAdminStatus(sellerId, status);
        return ResponseEntity.noContent().build();
    }
}


