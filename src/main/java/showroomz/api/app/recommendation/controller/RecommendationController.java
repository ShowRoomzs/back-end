package showroomz.api.app.recommendation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.docs.RecommendationControllerDocs;
import showroomz.api.app.recommendation.DTO.RecommendationDto;
import showroomz.api.app.recommendation.service.RecommendationService;

@RestController
@RequestMapping("/v1/common/recommendations")
@RequiredArgsConstructor
public class RecommendationController implements RecommendationControllerDocs {

    private final RecommendationService recommendationService;

    @Override
    @GetMapping
    public ResponseEntity<RecommendationDto.UnifiedRecommendationPageResponse> getRecommendations(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        RecommendationDto.UnifiedRecommendationPageResponse response = recommendationService.getUnifiedRecommendations(
                categoryId, page, limit);
        return ResponseEntity.ok(response);
    }
}
