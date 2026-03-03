package showroomz.api.common.market.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.common.market.docs.CommonMarketControllerDocs;
import showroomz.api.common.market.dto.MarketRecommendationResponse;
import showroomz.api.common.market.service.CommonMarketService;

@RestController
@RequestMapping("/v1/common/markets")
@RequiredArgsConstructor
public class CommonMarketController implements CommonMarketControllerDocs {

    private final CommonMarketService commonMarketService;

    @Override
    @GetMapping("/recommendations")
    public ResponseEntity<MarketRecommendationResponse> getRecommendedMarkets(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        MarketRecommendationResponse response = commonMarketService.getRecommendedMarkets(
                categoryId, page, limit);
        return ResponseEntity.ok(response);
    }
}
