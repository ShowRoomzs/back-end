package showroomz.api.app.post.controller;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostDto;
import showroomz.api.app.post.docs.UserFeedControllerDocs;
import showroomz.api.app.post.service.UserPostService;
import showroomz.domain.post.type.LikedPostSort;
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
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(postService.getFollowingFeed(userPrincipal.getUsername(), pagingRequest));
    }

    @Override
    @GetMapping("/feed/recommended")
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getRecommendedFeed(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(postService.getRecommendedFeed(userPrincipal.getUsername(), pagingRequest));
    }

    /** 경로({@code /wishlist/contents})는 앱이 쓰는 계약이라 그대로 두고 용어만 좋아요로 맞췄다 */
    @Override
    @GetMapping("/wishlist/contents")
    public ResponseEntity<PageResponse<PostDto.FeedItemResponse>> getLikedPosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(name = "sort", required = false, defaultValue = "DEFAULT") LikedPostSort sort,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(postService.getLikedPosts(userPrincipal.getUsername(), sort, pagingRequest));
    }
}
