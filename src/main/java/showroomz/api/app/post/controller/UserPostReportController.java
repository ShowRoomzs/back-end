package showroomz.api.app.post.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostReportReasonItem;
import showroomz.api.app.post.DTO.PostReportRequest;
import showroomz.api.app.post.docs.UserPostReportControllerDocs;
import showroomz.api.app.post.service.UserPostReportService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

/**
 * 게시물 신고 (C4 게시물 헤더 ⋯ 시트 · C4 하단 고지 "게시물 신고").
 *
 * <p>경로를 좋아요 옆({@code /v1/user/showrooms/posts/...})이 아니라 {@code /v1/user/posts} 아래에
 * 둔다. 좋아요가 그 자리에 있는 것은 앱이 이미 쓰고 있는 계약을 유지하기 위해서지 그쪽이 옳은
 * 자리라서가 아니다 — 신고는 쇼룸이 아니라 게시물에 걸리는 동작이고, 노출 적재
 * ({@code /v1/user/posts/impressions})와 같은 뿌리를 쓴다.
 */
@RestController
@RequestMapping("/v1/user/posts")
@RequiredArgsConstructor
public class UserPostReportController implements UserPostReportControllerDocs {

    private final UserPostReportService userPostReportService;

    @Override
    @GetMapping("/report-reasons")
    public ResponseEntity<List<PostReportReasonItem>> getReportReasons() {
        return ResponseEntity.ok(userPostReportService.getReportReasons());
    }

    @Override
    @PostMapping("/{postId}/reports")
    public ResponseEntity<Void> reportPost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody PostReportRequest request) {

        userPostReportService.reportPost(requireUsername(userPrincipal), postId, request);
        return ResponseEntity.noContent().build();
    }

    private static String requireUsername(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
