package showroomz.api.app.market.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.market.DTO.FollowingMarketResponse;
import showroomz.api.app.market.docs.MarketFollowControllerDocs;
import showroomz.api.app.market.service.MarketFollowService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
public class MarketFollowController implements MarketFollowControllerDocs {

    private final MarketFollowService marketFollowService;

    // 찜 하기 (추가) - 성공 시 204 No Content
    @Override
    @PostMapping("/shops/{shopId}/follow")
    public ResponseEntity<Void> followMarket(
        @PathVariable("shopId") Long shopId) {
        
        String username = getUsername();
        marketFollowService.followMarket(username, shopId);
        
        return ResponseEntity.noContent().build();
    }

    // 찜 취소 (삭제) - 성공 시 204 No Content
    @Override
    @DeleteMapping("/shops/{shopId}/follow")
    public ResponseEntity<Void> unfollowMarket(
        @PathVariable("shopId") Long shopId) {
        
        String username = getUsername();
        marketFollowService.unfollowMarket(username, shopId);
        
        return ResponseEntity.noContent().build();
    }

    // 팔로우 목록 조회
    @Override
    @GetMapping("/shops/following")
    public ResponseEntity<PageResponse<FollowingMarketResponse>> getFollowedMarkets(
            @ModelAttribute PagingRequest pagingRequest) {

        String username = getUsername();
        return ResponseEntity.ok(marketFollowService.getFollowedMarkets(username, pagingRequest));
    }

    /**
     * 크리에이터 쇼룸 팔로우 (찜 하기)
     */
    @Override
    @PostMapping("/showrooms/{showroomId}/follow")
    public ResponseEntity<Void> followShowroom(@PathVariable Long showroomId) {
        marketFollowService.followCreator(getUsername(), showroomId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 크리에이터 쇼룸 팔로우 취소 (찜 취소)
     */
    @Override
    @DeleteMapping("/showrooms/{showroomId}/follow")
    public ResponseEntity<Void> unfollowShowroom(@PathVariable Long showroomId) {
        marketFollowService.unfollowCreator(getUsername(), showroomId);
        return ResponseEntity.noContent().build();
    }

    // /**
    //  * 팔로우한 마켓 및 쇼룸 목록 조회 (통합 페이징)
    //  */
    // @GetMapping("/follows/shops")
    // public ResponseEntity<PageResponse<FollowingMarketResponse>> getFollowedShops(
    //         @ModelAttribute PagingRequest pagingRequest
    // ) {
    //     return ResponseEntity.ok(marketFollowService.getFollowedMarkets(getUsername(), pagingRequest));
    // }

    private String getUsername() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}

