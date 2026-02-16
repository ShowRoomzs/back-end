package showroomz.api.seller.market.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.market.DTO.MarketDto;
import showroomz.api.seller.market.docs.MarketControllerDocs;
import showroomz.api.seller.market.service.MarketService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/seller/markets")
@RequiredArgsConstructor
public class MarketController implements MarketControllerDocs {

    private final MarketService marketService;

    // 현재 로그인한 Admin의 Email 가져오기 (SecurityContext의 username은 email)
    private String getCurrentAdminEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }

    @Override
    @GetMapping("/check-name")
    public ResponseEntity<MarketDto.CheckMarketNameResponse> checkMarketName(
            @RequestParam("marketName") String marketName) {
        // 정규식 검증은 DTO Validation이나 프론트에서 1차로 하고, 여기선 DB 중복만 체크
        return ResponseEntity.ok(marketService.checkMarketName(marketName));
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<MarketDto.MarketProfileResponse> getMyMarket() {
        String email = getCurrentAdminEmail();
        return ResponseEntity.ok(marketService.getMyMarket(email));
    }

    @Override
    @PatchMapping("/me")
    public ResponseEntity<Void> updateMarketProfile(
            @RequestBody @Valid MarketDto.UpdateMarketProfileRequest request) {
        // @Valid를 통해 마켓명 한글, 길이 제한, URL 형식 등 검증 수행
        // 검증 실패 시 GlobalExceptionHandler에서 처리되어 400 Bad Request 반환
        
        String email = getCurrentAdminEmail();
        marketService.updateMarketProfile(email, request);
        
        return ResponseEntity.noContent().build();
    }
}

