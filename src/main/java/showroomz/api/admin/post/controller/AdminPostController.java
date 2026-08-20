package showroomz.api.admin.post.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
import showroomz.api.admin.post.dto.AdminPostReportDto;
import showroomz.api.admin.post.service.AdminPostReportService;
import showroomz.api.admin.post.service.AdminPostService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostReportStatus;
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
 * <p>진입은 두 갈래다 — 소비자 신고 대기열({@code GET /post-reports})에서 고르거나, 운영자가
 * 게시물 목록에서 직접 찾는다. 신고는 접수일 뿐이라 <b>자동 조치로 이어지지 않는다</b>. 반려가 곧
 * 영구 삭제인 절차라(§24-5) 자동화하면 경쟁 쇼룸을 신고로 내리는 길이 열린다.
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminPostController implements AdminPostControllerDocs {

    private final AdminPostService adminPostService;
    private final AdminPostReportService adminPostReportService;

    @Override
    @GetMapping("/posts")
    public ResponseEntity<PageResponse<AdminPostDto.AdminPostListItem>> getPosts(
            @RequestParam(value = "showroomId", required = false) Long showroomId,
            @RequestParam(value = "status", required = false) PostStatus status,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
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
    @GetMapping("/post-reports")
    public ResponseEntity<PageResponse<AdminPostReportDto.ReportItem>> getReports(
            @RequestParam(value = "postId", required = false) Long postId,
            @RequestParam(value = "status", required = false) PostReportStatus status,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(adminPostReportService.getReports(postId, status, pagingRequest));
    }

    @Override
    @PostMapping("/post-reports/{reportId}/dismiss")
    public ResponseEntity<Void> dismissReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("reportId") Long reportId) {
        adminPostReportService.dismiss(requireOperatorId(principal), reportId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/post-appeals")
    public ResponseEntity<PageResponse<AdminPostDto.AppealItem>> getAppeals(
            @RequestParam(value = "status", required = false) PostAppealStatus status,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
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
