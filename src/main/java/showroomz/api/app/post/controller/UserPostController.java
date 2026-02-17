package showroomz.api.app.post.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.api.app.post.docs.PostControllerDocs;
import showroomz.api.app.post.service.UserPostService;
import showroomz.global.dto.PageResponse;

@RestController
@RequestMapping("/v1/user/posts")
@RequiredArgsConstructor
public class UserPostController implements PostControllerDocs {

    private final UserPostService postService;

    @Override
    @GetMapping("/{postId}")
    public ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId) {
        String username = userPrincipal != null ? userPrincipal.getUsername() : null;
        PostDto.PostDetailResponse response = postService.getPostById(username, postId);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<PostDto.PostListItem>> getPostList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "marketId", required = false) Long marketId) {
        String username = userPrincipal != null ? userPrincipal.getUsername() : null;
        PageResponse<PostDto.PostListItem> response = postService.getPostList(username, page, limit, marketId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{postId}/wishlist")
    public ResponseEntity<Void> addPostToWishlist(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId) {
        postService.addPostToWishlist(userPrincipal.getUsername(), postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{postId}/wishlist")
    public ResponseEntity<Void> removePostFromWishlist(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId) {
        postService.removePostFromWishlist(userPrincipal.getUsername(), postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/wishlist")
    public ResponseEntity<PageResponse<PostDto.PostListItem>> getWishlistedPosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit) {
        PageResponse<PostDto.PostListItem> response = postService.getWishlistedPosts(
                userPrincipal.getUsername(), page, limit);
        return ResponseEntity.ok(response);
    }
}
