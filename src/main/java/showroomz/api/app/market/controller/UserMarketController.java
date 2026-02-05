package showroomz.api.app.market.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Hidden;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.docs.UserMarketControllerDocs;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.market.DTO.MarketListResponse;
import showroomz.api.app.market.service.UserMarketService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/user/shops")
@RequiredArgsConstructor
public class UserMarketController implements UserMarketControllerDocs {

    private final UserMarketService userMarketService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<MarketListResponse>> getMarkets(
            @Parameter(name = "mainCategoryId", description = "카테고리 ID 필터 (선택)", required = false, example = "1", in = ParameterIn.QUERY)
            @RequestParam(name = "mainCategoryId", required = false) Long mainCategoryId,
            @Parameter(name = "keyword", description = "마켓명 검색 키워드 (선택)", required = false, example = "쇼룸즈", in = ParameterIn.QUERY)
            @RequestParam(name = "keyword", required = false) String keyword,
            @ParameterObject @org.springframework.web.bind.annotation.ModelAttribute PagingRequest pagingRequest) {
        
        PageResponse<MarketListResponse> response = userMarketService.getMarkets(
                mainCategoryId, keyword, pagingRequest.toPageable(Sort.by(Sort.Direction.DESC, "id")));
        
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{shopId}")
    public ResponseEntity<MarketDetailResponse> getMarketDetail(
        @PathVariable("shopId") Long shopId) {
        
        String username = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            username = ((UserPrincipal) authentication.getPrincipal()).getUsername();
        }

        MarketDetailResponse response = userMarketService.getMarketDetail(shopId, username);
        return ResponseEntity.ok(response);
    }
}
