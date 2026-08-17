package showroomz.api.admin.post.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostSuspensionReason;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 운영자 게시물 조치 API의 요청·응답 (§24-5 · §24-6).
 *
 * <p>이 화면은 <b>조치만</b> 한다. 신고 접수(소비자 → 운영자)는 §24 범위 밖이고 어드민 신고 모듈도
 * 아직 없어서, 진입은 운영자 수동 조작으로 시작한다.
 */
public class AdminPostDto {

    @Schema(description = "노출 중지 요청 — 사유·근거 규정을 반드시 남긴다(§24-5)")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspendRequest {

        @Schema(description = "사유 코드", requiredMode = Schema.RequiredMode.REQUIRED, example = "AD_DISCLOSURE")
        @NotNull(message = "중지 사유는 필수입니다.")
        private PostSuspensionReason reasonCode;

        @Schema(description = "사유 상세 — 기타(OTHER)를 고르면 필수", maxLength = 500)
        @Size(max = 500)
        private String reasonDetail;

        @Schema(description = "근거 규정 조항", example = "운영정책 제12조 3항", maxLength = 200)
        @Size(max = 200)
        private String policyRef;
    }

    @Schema(description = "이의 신청 심사 요청")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppealReviewRequest {

        @Schema(description = "심사 의견 — 통지 문구에 그대로 실린다", maxLength = 500)
        @Size(max = 500)
        private String comment;
    }

    @Schema(description = "운영자 게시물 목록 항목 — 삭제·보관분을 포함한다(§24-6)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminPostListItem {
        @Schema(description = "게시물 ID", example = "301")
        private Long postId;
        @Schema(description = "쇼룸(인플루언서) ID", example = "10")
        private Long showroomId;
        @Schema(description = "쇼룸명")
        private String showroomName;
        @Schema(description = "상태", example = "SUSPENDED")
        private PostStatus status;
        @Schema(description = "대표 사진 URL")
        private String thumbnailUrl;
        @Schema(description = "본문 미리보기")
        private String contentPreview;
        @Schema(description = "노출 수", example = "2840")
        private Long impressionCount;
        @Schema(description = "좋아요 수", example = "24")
        private Long likeCount;

        @Schema(description = "게시 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime publishedAt;

        @Schema(description = "삭제 시각 — 보관 중인 게시물에만 값이 있다")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime deletedAt;

        @Schema(description = "삭제 경로", example = "APPEAL_REJECTED")
        private PostDeleteReason deleteReason;

        @Schema(description = "파기 예정 시각 — 이 시각이 지나면 배치가 물리 삭제한다")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime purgeAt;
    }

    @Schema(description = "운영자 게시물 상세 — 조치 이력을 전부 보여준다")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminPostDetailResponse {
        @Schema(description = "게시물 ID", example = "301")
        private Long postId;
        @Schema(description = "쇼룸 ID", example = "10")
        private Long showroomId;
        @Schema(description = "쇼룸명")
        private String showroomName;
        @Schema(description = "상태")
        private PostStatus status;
        @Schema(description = "본문")
        private String content;
        @Schema(description = "사진 URL 목록 (순서대로)")
        private List<String> imageUrls;
        @Schema(description = "노출 수")
        private Long impressionCount;
        @Schema(description = "좋아요 수")
        private Long likeCount;

        @Schema(description = "게시 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime publishedAt;

        @Schema(description = "삭제 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime deletedAt;

        @Schema(description = "파기 예정 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime purgeAt;

        @Schema(description = "조치 이력 — 재게시 후 재조치가 가능하므로 여러 건일 수 있다")
        private List<SuspensionHistoryItem> suspensions;

        @Schema(description = "이의 신청 — 게시물당 1회")
        private AppealItem appeal;
    }

    @Schema(description = "조치 이력 한 줄")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspensionHistoryItem {
        @Schema(description = "조치 ID", example = "5")
        private Long suspensionId;
        @Schema(description = "사유 코드")
        private PostSuspensionReason reasonCode;
        @Schema(description = "사유 표시명")
        private String reasonLabel;
        @Schema(description = "사유 상세")
        private String reasonDetail;
        @Schema(description = "근거 규정")
        private String policyRef;
        @Schema(description = "처리자(운영자) ID", example = "3")
        private Long suspendedBy;

        @Schema(description = "조치 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime suspendedAt;

        @Schema(description = "이의 신청 기한")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime appealDeadline;

        @Schema(description = "종결 결과 — null이면 진행 중인 조치다", example = "REPUBLISHED")
        private String resolution;

        @Schema(description = "종결 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime resolvedAt;
    }

    @Schema(description = "이의 신청 한 줄")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppealItem {
        @Schema(description = "신청 ID", example = "2")
        private Long appealId;
        @Schema(description = "게시물 ID", example = "301")
        private Long postId;
        @Schema(description = "쇼룸 ID", example = "10")
        private Long showroomId;
        @Schema(description = "쇼룸명")
        private String showroomName;
        @Schema(description = "심사 상태")
        private PostAppealStatus status;
        @Schema(description = "신청 내용")
        private String content;
        @Schema(description = "중지 사유 코드 — 무엇에 대한 이의인지")
        private PostSuspensionReason reasonCode;

        @Schema(description = "신청 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime submittedAt;

        @Schema(description = "심사 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime reviewedAt;

        @Schema(description = "심사 의견")
        private String reviewComment;

        @Schema(description = "원본 내려받기 유예 만료 — 반려된 건에만")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime graceUntil;
    }

    @Schema(description = "조치 결과 응답")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminPostActionResponse {
        @Schema(description = "게시물 ID", example = "301")
        private Long postId;
        @Schema(description = "조치 후 상태", example = "SUSPENDED")
        private PostStatus status;

        @Schema(description = "이의 신청 기한 — 중지 조치에만")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime appealDeadline;
    }

    /** 사유 코드 목록 — 어드민 드롭다운이 서버 값을 그대로 쓰도록 내려준다 */
    @Schema(description = "중지 사유 코드")
    @Getter
    @AllArgsConstructor
    public static class SuspensionReasonItem {
        @Schema(description = "코드", example = "AD_DISCLOSURE")
        private PostSuspensionReason code;
        @Schema(description = "표시명", example = "대가관계 미표시")
        private String label;
        @Schema(description = "상세 설명 필수 여부", example = "false")
        private boolean detailRequired;
    }
}
