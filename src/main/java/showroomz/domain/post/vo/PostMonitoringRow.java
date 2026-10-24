package showroomz.domain.post.vo;

import showroomz.domain.post.type.PostMonitoringStatus;
import showroomz.domain.post.type.PostStatus;

import java.time.LocalDateTime;

/**
 * 어드민 모니터링 목록 한 줄의 <b>집계값</b> — 게시물 본체는 따로 읽는다.
 *
 * @param pendingReportCount   대기 신고 수 — 「신고 접수」 판정
 * @param reportCount          목록의 「신고」 열 — 대기 + 조치로 이어진 신고. 신고 종결(반려)분은 빠진다
 * @param firstPendingReportAt 가장 오래 기다린 대기 신고 — 「수정일시가 신고 뒤인가」 판정 · 정렬
 * @param openAppealDeadline   열린 조치의 이의 신청 기한
 * @param appealSubmittedAt    심사 대기 중인 이의 신청 접수 시각
 */
public record PostMonitoringRow(
        Long postId,
        PostStatus status,
        long pendingReportCount,
        long reportCount,
        LocalDateTime firstPendingReportAt,
        LocalDateTime openAppealDeadline,
        LocalDateTime appealSubmittedAt
) {
    public PostMonitoringStatus monitoringStatus() {
        return PostMonitoringStatus.of(status, pendingReportCount);
    }
}
