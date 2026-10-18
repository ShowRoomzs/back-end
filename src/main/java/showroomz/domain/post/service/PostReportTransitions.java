package showroomz.domain.post.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.type.PostReportStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 신고 처리 전이 한 곳(§54-3 · §54-4 · 정합 계획서 R5-2) — 신고 1건 반려(신고 목록)와 게시물의 대기 신고 일괄 처리(노출 중지 · 신고 종결)가
 * 같은 전이를 쓴다. 신고자 · 작성자에게 결과를 알리지 않는다 — 신고자에게 결과를 돌려주면 그 값이 게시물 상태를 캐는 창구가 된다.
 * 호출자 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
public class PostReportTransitions {

    private final PostReportRepository postReportRepository;

    /** 신고 1건 반려 — 없는 신고 404 · 이미 처리된 신고 409. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PostReport dismiss(Long reportId, Long operatorId, LocalDateTime now) {
        PostReport report = postReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_REPORT_NOT_FOUND));
        if (!report.isPending()) {
            throw new BusinessException(ErrorCode.POST_REPORT_ALREADY_HANDLED);
        }
        report.dismiss(operatorId, now);
        return report;
    }

    /**
     * 게시물의 대기 신고를 모두 닫는다.
     *
     * @param accept true 면 조치로 이어짐(노출 중지), false 면 반려(신고 종결)
     * @return 닫은 신고 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int closePending(Long postId, Long operatorId, LocalDateTime now, boolean accept) {
        List<PostReport> pending = postReportRepository.findByPost_IdAndStatus(postId, PostReportStatus.PENDING);
        for (PostReport report : pending) {
            if (accept) {
                report.accept(operatorId, now);
            } else {
                report.dismiss(operatorId, now);
            }
        }
        return pending.size();
    }
}
