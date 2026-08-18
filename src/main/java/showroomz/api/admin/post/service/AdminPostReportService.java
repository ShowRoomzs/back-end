package showroomz.api.admin.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.post.dto.AdminPostReportDto;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.type.PostReportStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 운영자 신고 대기열.
 *
 * <p>지금까지 조치의 진입은 운영자 수동 조작뿐이었다. 이 목록이 그 앞단을 채우고, 여기서 고른
 * 게시물을 {@code POST /v1/admin/posts/{postId}/suspend}로 내리면 대기 중이던 신고가 함께 닫힌다
 * ({@link AdminPostService#suspend}).
 *
 * <p>기본 정렬이 <b>오래 기다린 순</b>이다. 신고가 방치되면 위반 게시물이 계속 노출된다는 뜻이라
 * 순서가 곧 형평이고, 이의 신청 대기열(§24-5)과 같은 규칙이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostReportService {

    private static final int CONTENT_PREVIEW_LENGTH = 40;

    private final PostReportRepository postReportRepository;

    /**
     * @param postId null이면 전체 대기열, 값이 있으면 그 게시물에 걸린 신고만
     * @param status null이면 처리분까지 최신순, 값이 있으면 그 상태만
     */
    public PageResponse<AdminPostReportDto.ReportItem> getReports(Long postId, PostReportStatus status,
                                                                  PagingRequest pagingRequest) {
        // 정렬 키가 reported_at이라 PagingRequest의 기본 정렬(createdAt)을 쓰지 않는다 — 쿼리가 직접 순서를 잡는다
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());
        Page<PostReport> page = findPage(postId, status, pageable);
        Map<Long, Long> pendingCounts = pendingCountsByPost(page.getContent());

        return new PageResponse<>(page.map(report -> toItem(report, pendingCounts)));
    }

    /**
     * 조치하지 않기로 판단 — 대기열에서만 내린다. 게시물에는 아무 일도 일어나지 않는다.
     *
     * <p>신고자에게 알리지 않는다. §24-5의 "알리지 않고 사라지는 경우는 없다"는 <b>조치를 당하는
     * 쪽</b>에 대한 약속이고, 신고자에게 처리 결과를 돌려주면 그 값이 곧 "저 게시물이 지금 어떤
     * 상태인가"를 캐는 창구가 된다.
     */
    @Transactional
    public void dismiss(Long operatorId, Long reportId) {
        PostReport report = postReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_REPORT_NOT_FOUND));
        if (!report.isPending()) {
            throw new BusinessException(ErrorCode.POST_REPORT_ALREADY_HANDLED);
        }
        report.dismiss(operatorId, LocalDateTime.now());
    }

    // ------------------------------------------------------------------ 내부

    private Page<PostReport> findPage(Long postId, PostReportStatus status, Pageable pageable) {
        if (postId != null) {
            return status == null
                    ? postReportRepository.findByPost_IdOrderByReportedAtDesc(postId, pageable)
                    : postReportRepository.findByPost_IdAndStatusOrderByReportedAtDesc(postId, status, pageable);
        }
        return status == null
                ? postReportRepository.findAllByOrderByReportedAtDesc(pageable)
                : postReportRepository.findByStatusOrderByReportedAtAsc(status, pageable);
    }

    /** 페이지에 실린 게시물만 센다 — 같은 게시물의 신고가 여러 줄 실려도 대조는 한 번이다 */
    private Map<Long, Long> pendingCountsByPost(List<PostReport> reports) {
        if (reports.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> postIds = reports.stream().map(report -> report.getPost().getId()).distinct().toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : postReportRepository.countByPostIdsAndStatus(postIds, PostReportStatus.PENDING)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static AdminPostReportDto.ReportItem toItem(PostReport report, Map<Long, Long> pendingCounts) {
        Creator creator = report.getPost().getCreator();
        return AdminPostReportDto.ReportItem.builder()
                .reportId(report.getId())
                .postId(report.getPost().getId())
                .showroomId(creator.getId())
                .showroomName(showroomName(creator))
                .contentPreview(preview(report.getPost().getContent()))
                .reasonCode(report.getReasonCode())
                .reasonLabel(report.getReasonCode().getLabel())
                .suspensionReasonCode(report.getReasonCode().toSuspensionReason())
                .reasonDetail(report.getReasonDetail())
                .status(report.getStatus())
                .pendingCountOnPost(pendingCounts.getOrDefault(report.getPost().getId(), 0L))
                .reportedAt(report.getReportedAt())
                .handledAt(report.getHandledAt())
                .handledBy(report.getHandledBy())
                .build();
    }

    private static String showroomName(Creator creator) {
        return creator.getShowroomName() != null ? creator.getShowroomName() : creator.getUser().getNickname();
    }

    private static String preview(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String flattened = content.replaceAll("\s+", " ").trim();
        return flattened.length() <= CONTENT_PREVIEW_LENGTH
                ? flattened
                : flattened.substring(0, CONTENT_PREVIEW_LENGTH) + "…";
    }
}
