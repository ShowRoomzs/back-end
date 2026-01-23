package showroomz.api.app.market.controller;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import showroomz.api.app.docs.UserMarketControllerDocs;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.market.DTO.MarketListResponse;
import showroomz.api.app.market.service.MarketFollowService;
import showroomz.api.app.market.service.UserMarketService;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/user/markets")
@RequiredArgsConstructor
public class UserMarketController implements UserMarketControllerDocs {

    private final UserMarketService userMarketService;
    private final MarketFollowService marketFollowService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<MarketListResponse>> getMarkets(
            @RequestParam(required = false) String mainCategory,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        
        PageResponse<MarketListResponse> response = userMarketService.getMarkets(mainCategory, keyword, pageable);
        
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{marketId}")
    public ResponseEntity<MarketDetailResponse> getMarketDetail(
        @PathVariable("marketId") Long marketId) {
        
        String username = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            username = ((User) authentication.getPrincipal()).getUsername();
        }

        MarketDetailResponse response = userMarketService.getMarketDetail(marketId, username);
        return ResponseEntity.ok(response);
    }

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

