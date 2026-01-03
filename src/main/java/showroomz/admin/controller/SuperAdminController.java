package showroomz.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.Market.DTO.MarketDto;
import showroomz.Market.service.MarketService;
import showroomz.Market.type.MarketImageStatus;
import showroomz.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.swaggerDocs.SuperAdminControllerDocs;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class SuperAdminController implements SuperAdminControllerDocs {

    private final MarketService marketService;

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
}

