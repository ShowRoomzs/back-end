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

import showroomz.api.app.docs.CommonMarketControllerDocs;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.market.DTO.MarketListResponse;
import showroomz.api.app.market.service.UserMarketService;
import showroomz.global.dto.PageResponse;

@RestController
@RequestMapping("/v1/common/markets")
@RequiredArgsConstructor
public class CommonMarketController implements CommonMarketControllerDocs {

    private final UserMarketService userMarketService;

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
}
