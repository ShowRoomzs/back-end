package showroomz.api.common.market.controller;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.common.market.docs.CommonMarketControllerDocs;
import showroomz.api.common.market.dto.MarketRecommendationResponse;
import showroomz.api.common.market.dto.PopularProductResponse;
import showroomz.api.common.market.service.CommonMarketService;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/common/markets")
@RequiredArgsConstructor
public class CommonMarketController implements CommonMarketControllerDocs {

    private final CommonMarketService commonMarketService;

    @Override
    @GetMapping("/recommendations")
    public ResponseEntity<MarketRecommendationResponse> getRecommendedMarkets(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest
    ) {
        MarketRecommendationResponse response = commonMarketService.getRecommendedMarkets(
                categoryId, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{marketId}/products/popular")
    public ResponseEntity<PopularProductResponse> getPopularProducts(
            @PathVariable("marketId") Long marketId
    ) {
        PopularProductResponse response = commonMarketService.getPopularProducts(marketId);
        return ResponseEntity.ok(response);
    }
}
