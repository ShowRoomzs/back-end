package showroomz.api.app.post.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.api.app.post.docs.PostControllerDocs;
import showroomz.api.app.post.service.UserPostService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

/**
 * 소비자 쇼룸 피드.
 *
 * <p>좋아요 경로({@code /posts/{postId}/wishlist})는 앱이 이미 쓰고 있는 계약이라 그대로 둔다.
 * 바뀐 것은 응답 필드 이름뿐이다 — {@code wishlistCount}/{@code isWishlisted} →
 * {@code likeCount}/{@code isLiked}(§24 용어 통일).
 */
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
        return ResponseEntity.ok(postService.getPostById(username, postId));
    }

    @Override
    @GetMapping
    @Hidden
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            PagingRequest pagingRequest) {
        String username = userPrincipal != null ? userPrincipal.getUsername() : null;
        return ResponseEntity.ok(postService.getPostList(username, pagingRequest, null));
    }

    @Override
    @GetMapping("/{showroomId}/posts")
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getPostListByShowroom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("showroomId") Long showroomId,
            PagingRequest pagingRequest) {
        String username = userPrincipal != null ? userPrincipal.getUsername() : null;
        return ResponseEntity.ok(postService.getPostList(username, pagingRequest, showroomId));
    }

    @Override
    @PostMapping("/posts/{postId}/wishlist")
    public ResponseEntity<Void> likePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId) {
        postService.likePost(userPrincipal.getUsername(), postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/posts/{postId}/wishlist")
    public ResponseEntity<Void> unlikePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId) {
        postService.unlikePost(userPrincipal.getUsername(), postId);
        return ResponseEntity.noContent().build();
    }
}
