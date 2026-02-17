package showroomz.api.seller.post.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.post.DTO.PostDto;
import showroomz.api.seller.post.docs.PostControllerDocs;
import showroomz.api.seller.post.service.PostService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController("sellerPostController")
@RequestMapping("/v1/seller/posts")
@RequiredArgsConstructor
public class PostController implements PostControllerDocs {

    private final PostService postService;

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }

    @Override
    @PostMapping
    public ResponseEntity<PostDto.CreatePostResponse> createPost(
            @Valid @RequestBody PostDto.CreatePostRequest request) {
        String sellerEmail = getCurrentSellerEmail();
        PostDto.CreatePostResponse response = postService.createPost(sellerEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/{postId}")
    public ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @PathVariable Long postId) {
        String sellerEmail = getCurrentSellerEmail();
        PostDto.PostDetailResponse response = postService.getPostById(sellerEmail, postId);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<PostDto.PostListItem>> getPostList(
            PagingRequest pagingRequest) {
        String sellerEmail = getCurrentSellerEmail();
        PageResponse<PostDto.PostListItem> response = postService.getPostList(sellerEmail, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @PutMapping("/{postId}")
    public ResponseEntity<PostDto.UpdatePostResponse> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostDto.UpdatePostRequest request) {
        String sellerEmail = getCurrentSellerEmail();
        PostDto.UpdatePostResponse response = postService.updatePost(sellerEmail, postId, request);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable Long postId) {
        String sellerEmail = getCurrentSellerEmail();
        postService.deletePost(sellerEmail, postId);
        return ResponseEntity.noContent().build();
    }
}
