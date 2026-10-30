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
import showroomz.domain.post.type.PostType;

import java.time.LocalDateTime;

/**
 * 운영자 신고 대기열의 응답.
 *
 * <p><b>신고자는 마스킹해서 싣는다</b>(§54-3 rev.3 — 54 설계 5-2에서 이전 결정을 뒤집었다). 소비자에게 「상대에게 알리지
 * 않는다」를 고지했으므로 이름은 가리고(한*준), 허위 신고 누적 판단(§54-7 ⓕ)을 위해 회원 상세로 가는 id만 함께 준다.
 * 스튜디오 · 앱 응답과 조치 통지에는 여전히 신고자 정보가 없다.
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

        /**
         * 공구 게시물 신고는 이 큐의 조치(노출 중지)로 처리할 수 없다 — 공구 게시물은 어드민 공구관리의 숨김으로 내린다
         * (공구 게시물 설계 7-2 · 31 설계 S0). 운영자가 그쪽으로 가도록 타입을 싣는다. 연동은 미결 ⑤.
         */
        @Schema(description = "게시물 타입 — GROUP_BUY면 이 화면이 아니라 어드민 공구관리의 게시물 노출 중지으로 처리한다",
                example = "GENERAL")
        private PostType postType;

        @Schema(description = "쇼룸명", example = "제니의 뷰티룸")
        private String showroomName;

        @Schema(description = "본문 미리보기 — 목록에서 무엇이 지목됐는지 바로 읽히도록 함께 내려준다")
        private String contentPreview;

        @Schema(description = "신고 사유 코드", example = "MISLEADING_INFO")
        private PostReportReason reasonCode;

        @Schema(description = "신고 사유 문구 — 신고자가 본 그대로", example = "허위 · 과장 정보")
        private String reasonLabel;

        @Schema(description = "노출 중지 사유 코드 — 이 신고를 받아 내릴 때 그대로 쓰는 값",
                example = "MISLEADING_INFO")
        private PostSuspensionReason suspensionReasonCode;

        @Schema(description = "상세 사유 — 기타일 때만 채워진다", nullable = true)
        private String reasonDetail;

        @Schema(description = "처리 상태", example = "PENDING")
        private PostReportStatus status;

        @Schema(description = "이 게시물에 걸린 대기 신고 수 — 같은 게시물을 몇 명이 지목했는지", example = "3")
        private Long pendingCountOnPost;

        @Schema(description = "접수 시각", example = "2026-08-17T12:34:56")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime reportedAt;

        @Schema(description = "처리 시각 — 미처리면 null", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime handledAt;

        @Schema(description = "처리자(운영자) ID — 미처리면 null", nullable = true)
        private Long handledBy;

        @Schema(description = "처리 운영자 이름 — 미처리면 null", nullable = true)
        private String handledByName;

        @Schema(description = "신고자 회원 ID — 회원 상세 링크 재료", example = "5521")
        private Long reporterUserId;

        @Schema(description = "신고자 마스킹 이름 — 가운데를 가린다", example = "한*준")
        private String reporterMaskedName;
    }
}
