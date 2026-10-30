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
import showroomz.domain.post.type.PostMonitoringStatus;
import showroomz.domain.post.type.PostPolicyClause;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostSuspensionReason;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 운영자 게시물 조치 API의 요청 · 응답 (§24-5 · §54-5).
 *
 * <p>목록 · 요약 · 상세 응답은 {@link AdminPostMonitoringDto}에 있다.
 */
public class AdminPostDto {

    @Schema(description = "노출 중지 요청 — 사유 · 근거 규정 2개가 필수다(§54-5)")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspendRequest {

        @Schema(description = "사유 코드", requiredMode = Schema.RequiredMode.REQUIRED, example = "MISLEADING_INFO")
        @NotNull(message = "중지 사유는 필수입니다.")
        private PostSuspensionReason reasonCode;

        @Schema(description = "작성자에게 전달할 설명 — 선택. 기타(OTHER)를 고르면 필수", maxLength = 500,
                example = "효과를 단정하고 환불을 보장하는 표현이 있어 노출을 중지합니다. 이의가 있으면 7일 안에 신청해 주세요.")
        @Size(max = 500)
        private String reasonDetail;

        /**
         * {@code @NotNull}로 막지 않는다 — 공구 게시물이면 근거 규정보다 「이 화면에서 처리할 수 없다」(409)가 먼저다.
         * 누락은 서비스가 400 {@code POST_POLICY_CLAUSE_REQUIRED}로 답한다.
         */
        @Schema(description = "근거 규정 조항 — 필수(이의 · 반려 심사의 판단 기준)", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "ARTICLE_3_2")
        private PostPolicyClause policyClause;
    }

    @Schema(description = "이의 신청 심사 요청")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppealReviewRequest {

        @Schema(description = "심사 의견 — 작성자에게 그대로 전달되고 알림 이력에 영구 보존된다. **반려는 필수**", maxLength = 500)
        @Size(max = 500)
        private String comment;
    }

    @Schema(description = "신고 종결 · 조치 취소 요청 — 메모는 선택")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionNoteRequest {

        @Schema(description = "운영 메모 — 신고 종결은 저장하지 않고, 조치 취소는 작성자 알림에 실린다", maxLength = 200,
                example = "근거 규정 적용 오류 — 개인 사용감 표현으로 재검토")
        @Size(max = 200)
        private String note;
    }

    @Schema(description = "조치 결과 응답")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminPostActionResponse {
        @Schema(description = "게시물 ID", example = "301")
        private Long postId;
        @Schema(description = "조치 후 저장 상태", example = "SUSPENDED")
        private PostStatus status;
        @Schema(description = "조치 후 모니터링 상태", example = "SUSPENDED")
        private PostMonitoringStatus monitoringStatus;

        @Schema(description = "이의 신청 기한 — 중지 조치에만")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime appealDeadline;

        @Schema(description = "닫은 신고 수 — 신고 종결 · 노출 중지에만", example = "3")
        private Integer closedReportCount;
    }

    /** 사유 코드 목록 — 어드민 드롭다운이 서버 값을 그대로 쓰도록 내려준다 */
    @Schema(description = "중지 사유 코드")
    @Getter
    @AllArgsConstructor
    public static class SuspensionReasonItem {
        @Schema(description = "코드", example = "MISLEADING_INFO")
        private PostSuspensionReason code;
        @Schema(description = "표시명", example = "허위 · 과장 정보")
        private String label;
        @Schema(description = "상세 설명 필수 여부", example = "false")
        private boolean detailRequired;
        @Schema(description = "운영자 전용 — 소비자 신고 사유에 없다. FE가 「(운영자 전용)」을 붙인다", example = "false")
        private boolean operatorOnly;
    }

    @Schema(description = "근거 규정 조항 — 서비스 운영정책 커뮤니티 장. 조항 번호 · 문구는 운영정책 문서 확정 전 가정값")
    @Getter
    @AllArgsConstructor
    public static class PolicyClauseItem {
        @Schema(description = "코드", example = "ARTICLE_3_2")
        private PostPolicyClause code;
        @Schema(description = "조항 문구 — 조치에는 선택 시점 문구가 굳는다(라벨이 바뀌어도 과거 조치는 그대로)",
                example = "서비스 운영정책 — 커뮤니티 장 3조 2항 — 효과 · 가격 오인 표현")
        private String label;
        @Schema(description = "기본 선택 추천 사유 — 서버는 조합을 검증하지 않는다")
        private Set<PostSuspensionReason> suggestedFor;
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
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime submittedAt;

        @Schema(description = "심사 기한 — 접수 + 3영업일(주말 · 공휴일 제외). D-n은 FE가 센다")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime reviewDueAt;

        @Schema(description = "심사 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime reviewedAt;

        @Schema(description = "심사 운영자 이름")
        private String reviewedByName;

        @Schema(description = "심사 의견 · 반려 사유")
        private String reviewComment;

        @Schema(description = "원본 내려받기 유예 만료 — 반려된 건에만")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime graceUntil;
    }
}
