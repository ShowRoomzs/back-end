package showroomz.api.common.review.controller;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.common.review.docs.CommonProductReviewControllerDocs;
import showroomz.api.common.review.dto.ProductReviewResponse;
import showroomz.api.common.review.dto.ProductReviewSortType;
import showroomz.api.common.review.service.CommonProductReviewService;

import java.util.List;

@RestController
@RequestMapping("/v1/common/products")
@RequiredArgsConstructor
public class CommonProductReviewController implements CommonProductReviewControllerDocs {

    private final CommonProductReviewService commonProductReviewService;

    @Override
    @GetMapping("/{productId}/reviews")
    public ResponseEntity<ProductReviewResponse> getProductReviews(
            @PathVariable("productId") Long productId,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
            @RequestParam(value = "sortType", required = false, defaultValue = "LATEST") ProductReviewSortType sortType,
            @RequestParam(value = "optionIds", required = false) List<Long> optionIds,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long currentUserId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
        ProductReviewResponse response = commonProductReviewService.getProductReviews(
                productId, page, size, sortType, optionIds, currentUserId);
        return ResponseEntity.ok(response);
    }
}
