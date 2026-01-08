package showroomz.api.app.market.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.market.DTO.MarketFollowResponse;
import showroomz.api.app.market.service.MarketFollowService;
import showroomz.api.app.market.service.UserMarketService;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Tag(name = "User - Market", description = "사용자용 마켓 API (조회/팔로우)")
@RestController
@RequestMapping("/v1/user/markets")
@RequiredArgsConstructor
public class UserMarketController {

    private final UserMarketService userMarketService;
    private final MarketFollowService marketFollowService;

    @Operation(summary = "마켓 상세 조회", description = "마켓 정보와 팔로워 수를 조회합니다. (비로그인 상태에서도 조회 가능)")
    @GetMapping("/{marketId}")
    public ResponseEntity<MarketDetailResponse> getMarketDetail(
            @Parameter(description = "마켓 ID", required = true)
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

    @Operation(summary = "마켓 팔로우/언팔로우 토글", description = "마켓을 팔로우하거나 취소합니다.")
    @PostMapping("/{marketId}/follow")
    public ResponseEntity<MarketFollowResponse> toggleFollow(
            @Parameter(description = "마켓 ID", required = true)
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

