package showroomz.api.app.post.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Hidden;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.api.app.post.docs.PostControllerDocs;
import showroomz.api.app.post.service.UserPostService;
import showroomz.global.dto.PageResponse;

@RestController
@RequestMapping("/v1/user/showroom")
@RequiredArgsConstructor
public class UserPostController implements PostControllerDocs {

    private final UserPostService postService;

    @Override
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId) {
        String username = userPrincipal != null ? userPrincipal.getUsername() : null;
        PostDto.PostDetailResponse response = postService.getPostById(username, postId);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    @Hidden 
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit) {
        String username = userPrincipal != null ? userPrincipal.getUsername() : null;
        PageResponse<PostDto.FeedItemResponse> response = postService.getPostList(username, page, limit, null);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{showroomId}/posts")
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostListByShowroom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long showroomId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit) {
        String username = userPrincipal != null ? userPrincipal.getUsername() : null;
        PageResponse<PostDto.FeedItemResponse> response = postService.getPostList(username, page, limit, showroomId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/posts/{postId}/wishlist")
    public ResponseEntity<Void> addPostToWishlist(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId) {
        postService.addPostToWishlist(userPrincipal.getUsername(), postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/posts/{postId}/wishlist")
    public ResponseEntity<Void> removePostFromWishlist(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId) {
        postService.removePostFromWishlist(userPrincipal.getUsername(), postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/posts/wishlist")
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getWishlistedPosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit) {
        PageResponse<PostDto.FeedItemResponse> response = postService.getWishlistedPosts(
                userPrincipal.getUsername(), page, limit);
        return ResponseEntity.ok(response);
    }
}
