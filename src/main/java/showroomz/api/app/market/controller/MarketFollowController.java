package showroomz.api.app.market.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Hidden;
import showroomz.api.app.docs.MarketFollowControllerDocs;
import showroomz.api.app.market.service.MarketFollowService;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/user/markets")
@RequiredArgsConstructor
public class MarketFollowController implements MarketFollowControllerDocs {

    private final MarketFollowService marketFollowService;

    // 찜 하기 (추가) - 성공 시 204 No Content
    @Override
    @PostMapping("/{marketId}/follow")
    public ResponseEntity<Void> followMarket(
        @PathVariable("marketId") Long marketId) {
        
        String username = getUsername();
        marketFollowService.followMarket(username, marketId);
        
        return ResponseEntity.noContent().build();
    }

    // 찜 취소 (삭제) - 성공 시 204 No Content
    @Override
    @DeleteMapping("/{marketId}/follow")
    public ResponseEntity<Void> unfollowMarket(
        @PathVariable("marketId") Long marketId) {
        
        String username = getUsername();
        marketFollowService.unfollowMarket(username, marketId);
        
        return ResponseEntity.noContent().build();
    }

    private String getUsername() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((User) principal).getUsername();
    }
}

