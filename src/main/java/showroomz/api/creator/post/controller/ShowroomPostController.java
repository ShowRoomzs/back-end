package showroomz.api.creator.post.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.post.DTO.PostDto;
import showroomz.api.creator.post.DTO.PostInsightDto;
import showroomz.api.creator.post.docs.PostControllerDocs;
import showroomz.api.creator.post.service.PostInsightService;
import showroomz.api.creator.post.service.ShowroomPostService;
import showroomz.api.creator.showroom.type.StatsPeriod;
import showroomz.domain.post.type.PostStatus;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 쇼룸 스튜디오 GNB #5 「게시물」 (§24) — 파트너센터에 짝이 없는 단독 화면이다.
 *
 * <p>게시물은 <b>인플루언서 소유 콘텐츠</b>이고 브랜드는 열람만 한다. 그래서 이 컨트롤러에는
 * 브랜드·운영자용 경로가 하나도 없다. 운영자 조치는 {@code /v1/admin/posts}가 따로 맡는다.
 *
 * <p>노출 여부를 켜고 끄는 엔드포인트도 없다 — 자율 숨김이 없고, 스스로 내리는 방법은 삭제뿐이다(§24-1).
 */
@RestController
@RequestMapping("/v1/creator/posts")
@RequiredArgsConstructor
public class ShowroomPostController implements PostControllerDocs {

    private final ShowroomPostService postService;
    private final PostInsightService postInsightService;

    @Override
    @PostMapping
    public ResponseEntity<PostDto.PostIdResponse> createPost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PostDto.SavePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.createPost(getUserId(userPrincipal), request));
    }

    @Override
    @GetMapping
    public ResponseEntity<PostDto.PostPageResponse> getPostList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "status", required = false) PostStatus status,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(postService.getPostList(getUserId(userPrincipal), status, pagingRequest));
    }

    @Override
    @GetMapping("/{postId}")
    public ResponseEntity<PostDto.PostDetailResponse> getPost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId) {
        return ResponseEntity.ok(postService.getPost(getUserId(userPrincipal), postId));
    }

    @Override
    @PutMapping("/{postId}")
    public ResponseEntity<PostDto.PostIdResponse> updatePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody PostDto.SavePostRequest request) {
        return ResponseEntity.ok(postService.updatePost(getUserId(userPrincipal), postId, request));
    }

    @Override
    @PostMapping("/{postId}/publish")
    public ResponseEntity<PostDto.PostIdResponse> publish(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId) {
        return ResponseEntity.ok(postService.publish(getUserId(userPrincipal), postId));
    }

    @Override
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId) {
        postService.deletePost(getUserId(userPrincipal), postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{postId}/appeal")
    public ResponseEntity<PostDto.AppealResponse> submitAppeal(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody PostDto.AppealRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.submitAppeal(getUserId(userPrincipal), postId, request));
    }

    @Override
    @GetMapping("/{postId}/originals")
    public ResponseEntity<PostDto.OriginalImagesResponse> getOriginalImages(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId) {
        return ResponseEntity.ok(postService.getOriginalImages(getUserId(userPrincipal), postId));
    }

    @Override
    @GetMapping("/{postId}/insights")
    public ResponseEntity<PostInsightDto.PostInsightResponse> getInsights(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId,
            @RequestParam(value = "period", defaultValue = "DAYS_30") StatsPeriod period) {
        return ResponseEntity.ok(postInsightService.getInsights(getUserId(userPrincipal), postId, period));
    }

    private Long getUserId(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUserId();
    }
}
