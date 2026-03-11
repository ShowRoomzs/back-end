package showroomz.api.app.post.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.api.app.post.docs.UserFeedControllerDocs;
import showroomz.api.app.post.service.UserPostService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
public class UserFeedController implements UserFeedControllerDocs {

    private final UserPostService postService;

    @Override
    @GetMapping("/feed/following")
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getFollowingFeed(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            PagingRequest pagingRequest) {
        PageResponse<PostDto.FeedItemResponse> response = postService.getFollowingFeed(
                userPrincipal.getUsername(), pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/wishlist/contents")
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getWishlistedPosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            PagingRequest pagingRequest) {
        PageResponse<PostDto.FeedItemResponse> response = postService.getWishlistedPosts(
                userPrincipal.getUsername(), pagingRequest);
        return ResponseEntity.ok(response);
    }
}
