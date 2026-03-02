package showroomz.api.app.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

@Tag(name = "User - Review")
@RestController
@RequestMapping("/v1/user/reviews")
@RequiredArgsConstructor
public class ReviewController implements ReviewControllerDocs {

    private final ReviewService reviewService;

    @Operation(summary = "사용자 리뷰 작성 가능한 상품 목록 조회")
    @GetMapping("/writable")
    public ResponseEntity<PageResponse<ReviewDto.WritableItem>> getWritableList(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal) {
        PageResponse<ReviewDto.WritableItem> response = reviewService.getWritableList(userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사용자 리뷰 작성")
    @PostMapping
    public ResponseEntity<ReviewRegisterResponse> registerReview(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ReviewRegisterRequest request) {
        ReviewRegisterResponse response = reviewService.registerReview(userPrincipal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "사용자 리뷰 목록 조회")
    @GetMapping
    public ResponseEntity<PageResponse<ReviewDto.ReviewItem>> getMyReviews(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1") @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @Parameter(description = "페이지당 항목 수", example = "20") @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        int pageNumber = page != null && page > 0 ? page - 1 : 0;
        int pageSize = size != null && size > 0 ? size : 20;
        var pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<ReviewDto.ReviewItem> response = reviewService.getMyReviews(userPrincipal.getUserId(), pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사용자 리뷰 수정", parameters = {
            @Parameter(name = "reviewId", description = "리뷰 ID", required = true, example = "1", in = ParameterIn.PATH)
    })
    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewDto.UpdateResponse> updateReview(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("reviewId") Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request) {
        ReviewDto.UpdateResponse response = reviewService.updateReview(userPrincipal.getUserId(), reviewId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사용자 리뷰 삭제", parameters = {
            @Parameter(name = "reviewId", description = "리뷰 ID", required = true, example = "1", in = ParameterIn.PATH)
    })
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ReviewDto.DeleteResponse> deleteReview(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("reviewId") Long reviewId) {
        ReviewDto.DeleteResponse response = reviewService.deleteReview(userPrincipal.getUserId(), reviewId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사용자 리뷰 좋아요 토글", parameters = {
            @Parameter(name = "reviewId", description = "리뷰 ID", required = true, example = "1", in = ParameterIn.PATH)
    })
    @PostMapping("/{reviewId}/likes")
    public ResponseEntity<ReviewDto.LikeToggleResponse> toggleLike(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("reviewId") Long reviewId) {
        ReviewDto.LikeToggleResponse response = reviewService.toggleLike(userPrincipal.getUserId(), reviewId);
        return ResponseEntity.ok(response);
    }
}
