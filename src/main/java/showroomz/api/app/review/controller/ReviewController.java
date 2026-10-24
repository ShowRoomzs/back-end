package showroomz.api.app.review.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.review.dto.ReviewDto;
import showroomz.api.app.review.dto.ReviewRegisterRequest;
import showroomz.api.app.review.dto.ReviewRegisterResponse;
import showroomz.api.app.review.dto.ReviewUpdateRequest;
import showroomz.api.app.review.docs.ReviewControllerDocs;
import showroomz.api.app.review.service.ReviewService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/user/reviews")
@RequiredArgsConstructor
public class ReviewController implements ReviewControllerDocs {

    private final ReviewService reviewService;

    @Override
    @GetMapping("/writable")
    public ResponseEntity<PageResponse<ReviewDto.WritableItem>> getWritableList(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        PageResponse<ReviewDto.WritableItem> response = reviewService.getWritableList(userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping
    public ResponseEntity<ReviewRegisterResponse> registerReview(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ReviewRegisterRequest request) {
        ReviewRegisterResponse response = reviewService.registerReview(userPrincipal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<ReviewDto.ReviewItem>> getMyReviews(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        PageResponse<ReviewDto.ReviewItem> response = reviewService.getMyReviews(
                userPrincipal.getUserId(),
                pagingRequest.toPageable());
        return ResponseEntity.ok(response);
    }

    @Override
    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewDto.UpdateResponse> updateReview(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("reviewId") Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request) {
        ReviewDto.UpdateResponse response = reviewService.updateReview(userPrincipal.getUserId(), reviewId, request);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ReviewDto.DeleteResponse> deleteReview(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("reviewId") Long reviewId) {
        ReviewDto.DeleteResponse response = reviewService.deleteReview(userPrincipal.getUserId(), reviewId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{reviewId}/likes")
    public ResponseEntity<ReviewDto.LikeToggleResponse> toggleLike(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("reviewId") Long reviewId) {
        ReviewDto.LikeToggleResponse response = reviewService.toggleLike(userPrincipal.getUserId(), reviewId);
        return ResponseEntity.ok(response);
    }
}
