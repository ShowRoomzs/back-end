package showroomz.api.app.recommendation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.docs.RecommendationControllerDocs;
import showroomz.api.app.recommendation.DTO.RecommendationDto;
import showroomz.api.app.recommendation.service.RecommendationService;

@RestController
@RequestMapping("/v1/user/recommendations")
@RequiredArgsConstructor
public class RecommendationController implements RecommendationControllerDocs {

    private final RecommendationService recommendationService;

    @Override
    @GetMapping
    public ResponseEntity<RecommendationDto.UnifiedRecommendationResponse> getRecommendations(
            @AuthenticationPrincipal User principal,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        String username = principal.getUsername();

        RecommendationDto.UnifiedRecommendationResponse response = recommendationService.getUnifiedRecommendations(
                username, categoryId, page, limit);
        return ResponseEntity.ok(response);
    }
}
