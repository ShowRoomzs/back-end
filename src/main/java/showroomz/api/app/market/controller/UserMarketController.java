package showroomz.api.app.market.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.docs.UserMarketControllerDocs;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.market.DTO.MarketFollowResponse;
import showroomz.api.app.market.service.MarketFollowService;
import showroomz.api.app.market.service.UserMarketService;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/user/markets")
@RequiredArgsConstructor
public class UserMarketController implements UserMarketControllerDocs {

    private final UserMarketService userMarketService;
    private final MarketFollowService marketFollowService;

    @Override
    @GetMapping("/{marketId}")
    public ResponseEntity<MarketDetailResponse> getMarketDetail(
            @PathVariable Long marketId) {
        
        // 현재 로그인한 사용자 확인 (없으면 null 처리하여 비로그인 로직 수행)
        String username = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            username = ((User) authentication.getPrincipal()).getUsername();
        }

        MarketDetailResponse response = userMarketService.getMarketDetail(marketId, username);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{marketId}/follow")
    public ResponseEntity<MarketFollowResponse> toggleFollow(
            @PathVariable Long marketId) {
        
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        String username = ((User) principal).getUsername();

        boolean isFollowed = marketFollowService.toggleFollow(username, marketId);
        
        String message = isFollowed ? "마켓을 찜했습니다." : "마켓 찜을 취소했습니다.";
        return ResponseEntity.ok(new MarketFollowResponse(isFollowed, message));
    }
}

