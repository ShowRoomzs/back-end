package showroomz.api.creator.post.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.post.DTO.PostDto;
import showroomz.api.creator.post.docs.PostControllerDocs;
import showroomz.api.creator.post.service.ShowroomPostService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/creator/posts")
@RequiredArgsConstructor
public class ShowroomPostController implements PostControllerDocs {

    private final ShowroomPostService postService;

    private String getCurrentUserEmail() {
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
        String userEmail = getCurrentUserEmail();
        PostDto.CreatePostResponse response = postService.createPost(userEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/{postId}")
    public ResponseEntity<PostDto.PostDetailResponse> getPostById(
            @PathVariable("postId") Long postId) {
        String userEmail = getCurrentUserEmail();
        PostDto.PostDetailResponse response = postService.getPostById(userEmail, postId);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<PostDto.PostListItem>> getPostList(
            PagingRequest pagingRequest) {
        String userEmail = getCurrentUserEmail();
        PageResponse<PostDto.PostListItem> response = postService.getPostList(userEmail, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @PutMapping("/{postId}")
    public ResponseEntity<PostDto.UpdatePostResponse> updatePost(
            @PathVariable("postId") Long postId,
            @Valid @RequestBody PostDto.UpdatePostRequest request) {
        String userEmail = getCurrentUserEmail();
        PostDto.UpdatePostResponse response = postService.updatePost(userEmail, postId, request);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable("postId") Long postId) {
        String userEmail = getCurrentUserEmail();
        postService.deletePost(userEmail, postId);
        return ResponseEntity.noContent().build();
    }
}
