package showroomz.api.app.recommendation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.recommendation.docs.RecommendationControllerDocs;
import showroomz.api.app.recommendation.service.RecommendationService;
import showroomz.global.dto.PageResponse;

@RestController
@RequestMapping("/v1/common/products/recommendations")
@RequiredArgsConstructor
public class RecommendationController implements RecommendationControllerDocs {

    private final RecommendationService recommendationService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<ProductDto.ProductItem>> getRecommendations(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        PageResponse<ProductDto.ProductItem> response = recommendationService.getRecommendedProducts(
                categoryId, page, limit);
        return ResponseEntity.ok(response);
    }
}
