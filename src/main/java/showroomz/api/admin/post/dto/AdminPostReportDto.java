package showroomz.api.admin.post.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.post.type.PostReportReason;
import showroomz.domain.post.type.PostReportStatus;
import showroomz.domain.post.type.PostSuspensionReason;

import java.time.LocalDateTime;

/**
 * 운영자 신고 대기열의 응답.
 *
 * <p><b>신고자를 싣지 않는다.</b> 운영자가 조치 여부를 판단하는 데 필요한 것은 무엇이 문제라고
 * 지목됐는지이지 누가 눌렀는지가 아니고, 어드민 화면에 뜬 값은 문의·분쟁 과정에서 인플루언서
 * 쪽으로 옮겨 갈 수 있다. 허위 신고 반복 판단이 필요해지면 그때는 이 목록이 아니라 계정 조회
 * 경로로 따로 만든다.
 */
public class AdminPostReportDto {

    @Schema(description = "신고 항목")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportItem {

        @Schema(description = "신고 ID", example = "17")
        private Long reportId;

        @Schema(description = "게시물 ID", example = "301")
        private Long postId;

        @Schema(description = "쇼룸(인플루언서) ID", example = "10")
        private Long showroomId;

        @Schema(description = "쇼룸명", example = "제니의 뷰티룸")
        private String showroomName;

        @Schema(description = "본문 미리보기 — 목록에서 무엇이 지목됐는지 바로 읽히도록 함께 내려준다")
        private String contentPreview;

        @Schema(description = "신고 사유 코드", example = "AD_DISCLOSURE")
        private PostReportReason reasonCode;

        @Schema(description = "신고 사유 문구 — 신고자가 본 그대로", example = "광고인데 표시가 없어요")
        private String reasonLabel;

        @Schema(description = "노출 중지 사유 코드 — 이 신고를 받아 내릴 때 그대로 쓰는 값",
                example = "AD_DISCLOSURE")
        private PostSuspensionReason suspensionReasonCode;

        @Schema(description = "상세 사유 — 기타일 때만 채워진다", nullable = true)
        private String reasonDetail;

        @Schema(description = "처리 상태", example = "PENDING")
        private PostReportStatus status;

        @Schema(description = "이 게시물에 걸린 대기 신고 수 — 같은 게시물을 몇 명이 지목했는지", example = "3")
        private Long pendingCountOnPost;

        @Schema(description = "접수 시각", example = "2026-08-17T12:34:56")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime reportedAt;

        @Schema(description = "처리 시각 — 미처리면 null", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime handledAt;

        @Schema(description = "처리자(운영자) ID — 미처리면 null", nullable = true)
        private Long handledBy;
    }
}
