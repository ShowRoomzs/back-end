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
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/user/showrooms")
@RequiredArgsConstructor
public class UserPostController implements PostControllerDocs {

    private final UserPostService postService;

    @Override
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId) {
        String username = userPrincipal != null ? userPrincipal.getUsername() : null;
        PostDto.PostDetailResponse response = postService.getPostById(username, postId);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    @Hidden 
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            PagingRequest pagingRequest) {
        String username = userPrincipal != null ? userPrincipal.getUsername() : null;
        PageResponse<PostDto.FeedItemResponse> response = postService.getPostList(username, pagingRequest, null);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{showroomId}/posts")
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostListByShowroom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("showroomId") Long showroomId,
            PagingRequest pagingRequest) {
        String username = userPrincipal != null ? userPrincipal.getUsername() : null;
        PageResponse<PostDto.FeedItemResponse> response = postService.getPostList(username, pagingRequest, showroomId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/posts/{postId}/wishlist")
    public ResponseEntity<Void> addPostToWishlist(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId) {
        postService.addPostToWishlist(userPrincipal.getUsername(), postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/posts/{postId}/wishlist")
    public ResponseEntity<Void> removePostFromWishlist(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId) {
        postService.removePostFromWishlist(userPrincipal.getUsername(), postId);
        return ResponseEntity.noContent().build();
    }
}
