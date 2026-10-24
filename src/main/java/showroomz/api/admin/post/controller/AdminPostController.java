package showroomz.api.admin.post.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
import showroomz.api.admin.post.docs.AdminPostControllerDocs;
import showroomz.api.admin.post.dto.AdminPostDto;
import showroomz.api.admin.post.dto.AdminPostMonitoringDto;
import showroomz.api.admin.post.dto.AdminPostReportDto;
import showroomz.api.admin.post.service.AdminPostQueryService;
import showroomz.api.admin.post.service.AdminPostReportService;
import showroomz.api.admin.post.service.AdminPostService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostMonitoringSort;
import showroomz.domain.post.type.PostMonitoringTab;
import showroomz.domain.post.type.PostReportStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

/**
 * 어드민 게시물 · 소통 모니터링 — 일반 게시물 (§24-5 · §54).
 *
 * <p><b>수정 · 삭제 엔드포인트가 없다.</b> 운영자가 할 수 있는 것은 노출 중지 · 신고 종결 · 조치 취소 · 이의 신청 심사뿐이고,
 * 게시물은 인플루언서 소유 콘텐츠다. 삭제는 심사 결과 · 기한 만료 · 작성자 삭제로만 일어난다.
 *
 * <p>신고는 접수일 뿐이라 <b>자동 조치로 이어지지 않는다</b>(§54-7 ⓐ 미결). 반려가 곧 영구 삭제인 절차라
 * 자동화하면 경쟁 쇼룸을 신고로 내리는 길이 열린다.
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminPostController implements AdminPostControllerDocs {

    private final AdminPostService adminPostService;
    private final AdminPostQueryService adminPostQueryService;
    private final AdminPostReportService adminPostReportService;

    // ------------------------------------------------------------------ 조회

    @Override
    @GetMapping("/posts")
    public ResponseEntity<PageResponse<AdminPostMonitoringDto.ListItem>> getPosts(
            @RequestParam(value = "tab", required = false) PostMonitoringTab tab,
            @RequestParam(value = "sort", required = false) PostMonitoringSort sort,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "showroomId", required = false) Long showroomId,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(adminPostQueryService.getPosts(tab, sort, keyword, showroomId, pagingRequest));
    }

    @Override
    @GetMapping("/posts/summary")
    public ResponseEntity<AdminPostMonitoringDto.SummaryResponse> getSummary() {
        return ResponseEntity.ok(adminPostQueryService.getSummary());
    }

    @Override
    @GetMapping("/posts/{postId}")
    public ResponseEntity<AdminPostMonitoringDto.DetailResponse> getPost(
            @PathVariable("postId") Long postId,
            @RequestParam(value = "tab", required = false) PostMonitoringTab tab,
            @RequestParam(value = "sort", required = false) PostMonitoringSort sort,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "showroomId", required = false) Long showroomId) {
        return ResponseEntity.ok(adminPostQueryService.getPost(postId, tab, sort, keyword, showroomId));
    }

    @Override
    @GetMapping("/posts/suspension-reasons")
    public ResponseEntity<List<AdminPostDto.SuspensionReasonItem>> getSuspensionReasons() {
        return ResponseEntity.ok(adminPostService.getSuspensionReasons());
    }

    @Override
    @GetMapping("/posts/policy-clauses")
    public ResponseEntity<List<AdminPostDto.PolicyClauseItem>> getPolicyClauses() {
        return ResponseEntity.ok(adminPostService.getPolicyClauses());
    }

    // ------------------------------------------------------------------ 조치

    @Override
    @PostMapping("/posts/{postId}/suspend")
    public ResponseEntity<AdminPostDto.AdminPostActionResponse> suspend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody AdminPostDto.SuspendRequest request) {
        return ResponseEntity.ok(adminPostService.suspend(requireOperatorId(principal), postId, request));
    }

    @Override
    @PostMapping("/posts/{postId}/reports/close")
    public ResponseEntity<AdminPostDto.AdminPostActionResponse> closeReports(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody(required = false) AdminPostDto.ActionNoteRequest request) {
        return ResponseEntity.ok(adminPostService.closeReports(requireOperatorId(principal), postId));
    }

    @Override
    @PostMapping("/posts/{postId}/suspension/cancel")
    public ResponseEntity<AdminPostDto.AdminPostActionResponse> cancelSuspension(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody(required = false) AdminPostDto.ActionNoteRequest request) {
        return ResponseEntity.ok(adminPostService.cancelSuspension(
                requireOperatorId(principal), postId, request == null ? null : request.getNote()));
    }

    @Override
    @PutMapping("/posts/{postId}/images/{imageId}/report-flag")
    public ResponseEntity<Void> flagImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("postId") Long postId,
            @PathVariable("imageId") Long imageId) {
        adminPostService.flagImage(requireOperatorId(principal), postId, imageId, true);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/posts/{postId}/images/{imageId}/report-flag")
    public ResponseEntity<Void> unflagImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("postId") Long postId,
            @PathVariable("imageId") Long imageId) {
        adminPostService.flagImage(requireOperatorId(principal), postId, imageId, false);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ 신고 대기열 · 이의 신청

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
