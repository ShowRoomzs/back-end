package showroomz.api.admin.post.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.post.docs.AdminPostControllerDocs;
import showroomz.api.admin.post.dto.AdminPostDto;
import showroomz.api.admin.post.service.AdminPostService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

/**
 * 운영자 게시물 조치 (§24-5 · §24-6).
 *
 * <p><b>수정·삭제 엔드포인트가 없다.</b> 운영자가 할 수 있는 것은 내리는 일과 이의 신청을 심사하는
 * 일뿐이고, 게시물은 인플루언서 소유 콘텐츠다. 삭제는 심사 결과로만 일어난다.
 *
 * <p>신고 접수 화면은 §24 범위 밖이라 <b>진입은 운영자 수동 조작</b>으로 시작한다.
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminPostController implements AdminPostControllerDocs {

    private final AdminPostService adminPostService;

    @Override
    @GetMapping("/posts")
    public ResponseEntity<PageResponse<AdminPostDto.AdminPostListItem>> getPosts(
            @RequestParam(value = "showroomId", required = false) Long showroomId,
            @RequestParam(value = "status", required = false) PostStatus status,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(adminPostService.getPosts(showroomId, status, pagingRequest));
    }

    @Override
    @GetMapping("/posts/{postId}")
    public ResponseEntity<AdminPostDto.AdminPostDetailResponse> getPost(@PathVariable("postId") Long postId) {
        return ResponseEntity.ok(adminPostService.getPost(postId));
    }

    @Override
    @GetMapping("/posts/suspension-reasons")
    public ResponseEntity<List<AdminPostDto.SuspensionReasonItem>> getSuspensionReasons() {
        return ResponseEntity.ok(adminPostService.getSuspensionReasons());
    }

    @Override
    @PostMapping("/posts/{postId}/suspend")
    public ResponseEntity<AdminPostDto.AdminPostActionResponse> suspend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody AdminPostDto.SuspendRequest request) {
        return ResponseEntity.ok(adminPostService.suspend(requireOperatorId(principal), postId, request));
    }

    @Override
    @GetMapping("/post-appeals")
    public ResponseEntity<PageResponse<AdminPostDto.AppealItem>> getAppeals(
            @RequestParam(value = "status", required = false) PostAppealStatus status,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(adminPostService.getAppeals(status, pagingRequest));
    }

    @Override
    @PostMapping("/post-appeals/{appealId}/approve")
    public ResponseEntity<AdminPostDto.AdminPostActionResponse> approveAppeal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("appealId") Long appealId,
            @Valid @RequestBody AdminPostDto.AppealReviewRequest request) {
        return ResponseEntity.ok(adminPostService.approveAppeal(requireOperatorId(principal), appealId, request));
    }

    @Override
    @PostMapping("/post-appeals/{appealId}/reject")
    public ResponseEntity<AdminPostDto.AdminPostActionResponse> rejectAppeal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("appealId") Long appealId,
            @Valid @RequestBody AdminPostDto.AppealReviewRequest request) {
        return ResponseEntity.ok(adminPostService.rejectAppeal(requireOperatorId(principal), appealId, request));
    }

    /** 처리자는 조치 시점에 고정된다 — 누가 내렸는지가 화면에 남아야 한다(§24-5) */
    private Long requireOperatorId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
