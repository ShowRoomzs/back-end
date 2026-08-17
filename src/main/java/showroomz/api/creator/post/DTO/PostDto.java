package showroomz.api.creator.post.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.creator.post.type.PostSaveAction;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.global.dto.PaginationInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 쇼룸 스튜디오 게시물 API의 요청·응답 (§24).
 *
 * <p>구버전 대비 사라진 것 — {@code title}(§24-3 제목이 없다), {@code productIds}(상품은 공구 게시물의
 * 몫이다), {@code isDisplay}(자율 숨김이 없으므로 노출 여부를 인플루언서가 켜고 끄지 않는다).
 * 새로 생긴 것 — 사진 순서·원본 URL, 비율, 상태 5종, 운영자 조치·이의 신청 정보.
 */
public class PostDto {

    // ------------------------------------------------------------------ 요청

    @Schema(description = "게시물 사진 한 장 — 업로드 API가 돌려준 값을 그대로 싣는다")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostImageRequest {

        @Schema(description = "표시용 URL — 크롭 결과", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "사진 URL은 필수입니다.")
        @Size(max = 512)
        private String imageUrl;

        @Schema(description = "원본 URL — 크롭 전 파일. 유예 기간 내려받기에 쓰인다(§24-6)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "원본 사진 URL은 필수입니다.")
        @Size(max = 512)
        private String originalUrl;

        @Schema(description = "가로 픽셀 — 첫 장의 값이 게시물 비율을 결정한다(§24-2)",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "1080")
        @NotNull(message = "사진 가로 크기는 필수입니다.")
        @Positive
        private Integer width;

        @Schema(description = "세로 픽셀", requiredMode = Schema.RequiredMode.REQUIRED, example = "1350")
        @NotNull(message = "사진 세로 크기는 필수입니다.")
        @Positive
        private Integer height;

        @Schema(description = "파일 크기(byte)", example = "2048000")
        private Integer fileSize;
    }

    @Schema(description = "게시물 저장 요청 — 임시저장과 게시하기가 같은 본문을 쓰고 action으로 갈린다")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavePostRequest {

        @Schema(description = "본문 — 선택. 사진만 있는 게시물을 허용한다(§24-3)", maxLength = 2000)
        @Size(max = 2000, message = "본문은 최대 2,000자까지 입력할 수 있습니다.")
        private String content;

        @Schema(description = "사진 목록 — 배열 순서가 곧 노출 순서이고 첫 장이 대표 사진이다(§24-2). 최대 20장")
        @Valid
        private List<PostImageRequest> images;

        @Schema(description = "DRAFT(임시저장) 또는 PUBLISH(게시하기)",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "PUBLISH")
        @NotNull(message = "저장 방식(action)은 필수입니다.")
        private PostSaveAction action;
    }

    @Schema(description = "이의 신청 요청 (§24-5) — 게시물당 1회")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppealRequest {

        @Schema(description = "신청 사유", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 1000)
        @NotBlank(message = "이의 신청 내용을 입력해 주세요.")
        @Size(max = 1000, message = "이의 신청 내용은 최대 1,000자까지 입력할 수 있습니다.")
        private String content;
    }

    // ------------------------------------------------------------------ 응답

    @Schema(description = "게시물 ID 응답")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostIdResponse {
        @Schema(description = "게시물 ID", example = "1")
        private Long postId;

        public static PostIdResponse of(Post post) {
            return PostIdResponse.builder().postId(post.getId()).build();
        }
    }

    @Schema(description = "게시물 사진 응답")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostImageResponse {
        @Schema(description = "노출 순서 — 0이 대표 사진", example = "0")
        private Integer sortOrder;
        @Schema(description = "표시용 URL")
        private String imageUrl;
        @Schema(description = "원본 URL")
        private String originalUrl;
        @Schema(description = "가로 픽셀", example = "1080")
        private Integer width;
        @Schema(description = "세로 픽셀", example = "1350")
        private Integer height;
    }

    @Schema(description = "상태 탭 건수 (§24-1) — 탭에 개수가 함께 보여야 조치가 필요한 게시물을 바로 찾는다")
    @Getter
    @AllArgsConstructor
    public static class StatusCount {
        @Schema(description = "탭 코드. null이면 전체 탭", example = "PUBLISHED")
        private PostStatus status;
        @Schema(description = "탭 표시명", example = "게시중")
        private String label;
        @Schema(description = "건수", example = "12")
        private long count;
    }

    @Schema(description = "게시물 목록 항목 — 제목이 없으므로 대표 사진과 본문 앞부분으로 식별한다")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostListItem {
        @Schema(description = "게시물 ID", example = "1")
        private Long postId;
        @Schema(description = "상태", example = "PUBLISHED")
        private PostStatus status;
        @Schema(description = "대표 사진 — 목록 격자는 균일 4:5 센터 크롭으로 그린다(§24-2)")
        private String thumbnailUrl;
        @Schema(description = "사진 장수", example = "5")
        private Integer imageCount;
        @Schema(description = "본문 미리보기(앞 40자)")
        private String contentPreview;
        @Schema(description = "노출 수 — 게시물에 누적된 값", example = "2840")
        private Long impressionCount;
        @Schema(description = "좋아요 수 — 게시물에 누적된 값", example = "24")
        private Long likeCount;

        @Schema(description = "게시 시각. 작성중이면 null", example = "2026-08-10T09:12:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime publishedAt;

        @Schema(description = "작성 시각", example = "2026-08-09T21:30:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;

        @Schema(description = "이의 신청 기한 — 노출 중지 상태에서만 값이 있다", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime appealDeadline;
    }

    @Schema(description = "게시물 목록 응답 — 목록 + 상태 탭 건수를 한 번에 준다")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostPageResponse {
        @Schema(description = "게시물 목록 (최신순)")
        private List<PostListItem> content;
        @Schema(description = "페이징 정보")
        private PaginationInfo pageInfo;
        @Schema(description = "상태 탭 건수 — 전체·게시중·노출 중지·작성중")
        private List<StatusCount> statusCounts;
    }

    @Schema(description = "운영자 조치 정보 (§24-5) — 사유·근거 규정·조치 시각·처리자·기한을 그대로 내려준다")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspensionResponse {
        @Schema(description = "조치 ID", example = "5")
        private Long suspensionId;
        @Schema(description = "사유 코드", example = "AD_DISCLOSURE")
        private PostSuspensionReason reasonCode;
        @Schema(description = "사유 표시명", example = "대가관계 미표시")
        private String reasonLabel;
        @Schema(description = "사유 상세")
        private String reasonDetail;
        @Schema(description = "근거 규정 조항", example = "운영정책 제12조 3항")
        private String policyRef;

        @Schema(description = "조치 시각", example = "2026-08-12T10:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime suspendedAt;

        @Schema(description = "처리자(운영자) ID", example = "3")
        private Long suspendedBy;

        @Schema(description = "이의 신청 기한", example = "2026-08-19T10:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime appealDeadline;

        @Schema(description = "지금 이의 신청이 가능한지 — 기한 내 미신청 상태일 때만 true", example = "true")
        private Boolean appealable;
    }

    @Schema(description = "이의 신청 정보 (§24-5)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppealResponse {
        @Schema(description = "신청 ID", example = "2")
        private Long appealId;
        @Schema(description = "심사 상태", example = "PENDING")
        private PostAppealStatus status;
        @Schema(description = "신청 내용")
        private String content;

        @Schema(description = "신청 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime submittedAt;

        @Schema(description = "심사 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime reviewedAt;

        @Schema(description = "심사 의견")
        private String reviewComment;

        @Schema(description = "원본 내려받기 유예 만료 — 반려된 건에만 값이 있다(§24-6)")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime graceUntil;

        @Schema(description = "심사 예상 소요(영업일) — 표시용", example = "3")
        private Integer expectedReviewBusinessDays;
    }

    @Schema(description = "게시물 상세 응답")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostDetailResponse {
        @Schema(description = "게시물 ID", example = "1")
        private Long postId;
        @Schema(description = "상태", example = "SUSPENDED")
        private PostStatus status;
        @Schema(description = "본문")
        private String content;
        @Schema(description = "게시물 비율(가로/세로) — 1.9100 ~ 0.8000", example = "0.8000")
        private BigDecimal aspectRatio;
        @Schema(description = "사진 목록 (순서대로)")
        private List<PostImageResponse> images;
        @Schema(description = "노출 수", example = "2840")
        private Long impressionCount;
        @Schema(description = "좋아요 수", example = "24")
        private Long likeCount;

        @Schema(description = "게시 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime publishedAt;

        @Schema(description = "작성 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;

        @Schema(description = "수정 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime modifiedAt;

        @Schema(description = "수정 가능 여부 — 중지·심사 중에는 false(§24-5)", example = "false")
        private Boolean editable;

        @Schema(description = "삭제 가능 여부 — 심사 중에만 false(§24-5)", example = "true")
        private Boolean deletable;

        @Schema(description = "진행 중인 운영자 조치. 없으면 null")
        private SuspensionResponse suspension;

        @Schema(description = "이의 신청. 없으면 null")
        private AppealResponse appeal;
    }

    @Schema(description = "원본 사진 내려받기 응답 (§24-6) — 반려 통지 후 유예 기간 동안 본인만 받을 수 있다")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OriginalImagesResponse {
        @Schema(description = "유예 만료 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime graceUntil;

        @Schema(description = "원본 URL 목록 (순서대로)")
        private List<PostImageResponse> images;
    }
}
