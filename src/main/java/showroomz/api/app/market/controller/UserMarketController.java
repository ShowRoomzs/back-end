package showroomz.api.app.market.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import showroomz.api.app.docs.UserMarketControllerDocs;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.market.DTO.MarketListResponse;
import showroomz.api.app.market.service.UserMarketService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/user/markets")
@RequiredArgsConstructor
public class UserMarketController implements UserMarketControllerDocs {

    private final UserMarketService userMarketService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<MarketListResponse>> getMarkets(
            @Parameter(name = "mainCategory", description = "카테고리 필터 (선택)", required = false, example = "패션/의류", in = ParameterIn.QUERY)
            @RequestParam(name = "mainCategory", required = false) String mainCategory,
            @Parameter(name = "keyword", description = "마켓명 검색 키워드 (선택)", required = false, example = "쇼룸즈", in = ParameterIn.QUERY)
            @RequestParam(name = "keyword", required = false) String keyword,
            @ParameterObject @org.springframework.web.bind.annotation.ModelAttribute PagingRequest pagingRequest) {
        
        PageResponse<MarketListResponse> response = userMarketService.getMarkets(
                mainCategory, keyword, pagingRequest.toPageable(Sort.by(Sort.Direction.DESC, "id")));
        
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
