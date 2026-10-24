package showroomz.api.admin.post.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostMonitoringStatus;
import showroomz.domain.post.type.PostPolicyClause;
import showroomz.domain.post.type.PostReportReason;
import showroomz.domain.post.type.PostReportStatus;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.domain.post.type.SuspensionResolution;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 일반 게시물 모니터링 — 목록 · 요약 · 상세 응답 (§54-2 · §54-3 · §54-4).
 *
 * <p>기한은 <b>절대 시각</b>만 내린다. D-n을 서버가 계산하면 응답을 캐시한 화면이 날을 넘길 때 틀린다(54 설계 3-3).
 * 카드 배치(신고 카드가 원문 위인지 아래인지 등)는 FE가 {@code monitoringStatus}로 정한다 — 서버는 재료를 한 번에 내린다.
 */
public class AdminPostMonitoringDto {

    private static final String DATETIME = "yyyy-MM-dd'T'HH:mm:ss";

    // ------------------------------------------------------------------ 목록 · 요약

    @Schema(description = "목록 한 줄 (C1)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
        @Schema(description = "게시물 ID", example = "342")
        private Long postId;
        @Schema(description = "게시물 번호 — 잠정 포맷 POST-{게시일 yyyyMMdd}-{id 4자리}", example = "POST-20260811-0342")
        private String postNo;
        @Schema(description = "모니터링 상태 — 저장하지 않는 파생값", example = "REPORTED")
        private PostMonitoringStatus monitoringStatus;
        @Schema(description = "모니터링 상태 표시명", example = "신고 접수")
        private String monitoringStatusLabel;
        @Schema(description = "저장 상태", example = "PUBLISHED")
        private PostStatus status;
        @Schema(description = "쇼룸(인플루언서) ID", example = "10")
        private Long showroomId;
        @Schema(description = "쇼룸명 — 없으면 닉네임", example = "김소연")
        private String showroomName;
        @Schema(description = "대표 사진(첫 장)")
        private String thumbnailUrl;
        @Schema(description = "본문 미리보기 한 줄(80자) — 본문 없는 게시물은 null")
        private String contentPreview;
        @Schema(description = "사진 장수", example = "14")
        private Integer imageCount;

        @Schema(description = "등록(게시) 일시")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime publishedAt;
        @Schema(description = "수정일시 — 게시 후 수정이 없으면 null(시안 「—」)")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime editedAt;
        @Schema(description = "최초 대기 신고 뒤에 본문이 고쳐졌는가 — 조치 판단 재료(§54-2)", example = "false")
        private boolean editedAfterFirstReport;

        @Schema(description = "「신고」 열 — 대기 + 조치로 이어진 신고. 신고 종결로 닫힌 신고는 빠진다(종결 뒤 0)", example = "3")
        private long reportCount;
        @Schema(description = "대기 신고 — 「신고 접수」 판정 근거", example = "3")
        private long pendingReportCount;

        @Schema(description = "노출 중지 — 이의 신청 기한(「기한 D-n」)")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime appealDeadline;
        @Schema(description = "이의 신청 심사 중 — 심사 기한(「심사 D-n」)")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime reviewDueAt;
        @Schema(description = "영구 삭제 — 삭제 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime deletedAt;
        @Schema(description = "영구 삭제 — 삭제 경로")
        private PostDeleteReason deleteReason;
        @Schema(description = "영구 삭제 — 파기 예정(「보관 ~YYYY.MM」)")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime purgeAt;
    }

    @Schema(description = "탭 건수 · GNB 배지")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryResponse {
        @Schema(description = "전체 — 작성중 제외", example = "6")
        private long all;
        @Schema(description = "신고 접수", example = "2")
        private long reported;
        @Schema(description = "노출 중지(이의 신청 전)", example = "1")
        private long suspended;
        @Schema(description = "이의 신청 심사 중", example = "1")
        private long underReview;
        @Schema(description = "종결(영구 삭제)", example = "1")
        private long closed;
        @Schema(description = "조치 필요 = 신고 접수 + 이의 신청 심사 중 — GNB 배지", example = "3")
        private long actionRequired;
    }

    // ------------------------------------------------------------------ 상세

    @Schema(description = "게시물 상세 (C2 · C3 · C5 · C6 · C8 · C10 · C11)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailResponse {
        private Long postId;
        private String postNo;
        private PostMonitoringStatus monitoringStatus;
        private String monitoringStatusLabel;
        private PostStatus status;
        @Schema(description = "본문 — 조회 전용")
        private String content;
        @Schema(description = "게시물 비율(가로/세로) — 첫 사진 기준", example = "0.8000")
        private BigDecimal aspectRatio;
        @Schema(description = "게시물 비율 표기", example = "4:5")
        private String aspectRatioLabel;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime publishedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime editedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime deletedAt;
        private PostDeleteReason deleteReason;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime purgeAt;

        private Author author;
        @Schema(description = "사진 — sortOrder 0이 대표. 원본 뷰어(C11) 메타 포함")
        private List<Image> images;
        private Metrics metrics;
        private Reports reports;
        @Schema(description = "조치 이력 — 최신 우선. open=true가 진행 중인 조치")
        private List<Suspension> suspensions;
        @Schema(description = "이의 신청 — 게시물당 1건 또는 null")
        private Appeal appeal;
        @Schema(description = "삭제 경위 — 영구 삭제일 때만")
        private Deletion deletion;
        @Schema(description = "처리 이력 — 최신 우선")
        private List<TimelineItem> timeline;
        @Schema(description = "이전 · 다음 — 상세를 연 목록(tab · sort · keyword · showroomId)의 순서 기준")
        private Navigation navigation;
    }

    /** 브랜드 상세의 Navigation과 같은 모양이다 — 기준만 id 순이 아니라 목록 순서다(55 계획서 A7) */
    @Schema(name = "AdminPostNavigation",
            description = "목록 순서의 바로 앞 · 뒤 게시물 — 첫 건 · 끝 건이면 그쪽이 null, 그 목록에 없는 게시물이면 둘 다 null")
    @Getter
    @AllArgsConstructor
    public static class Navigation {
        @Schema(description = "이전(목록에서 위) 게시물 ID", example = "341", nullable = true)
        private Long previousPostId;
        @Schema(description = "다음(목록에서 아래) 게시물 ID", example = "343", nullable = true)
        private Long nextPostId;
    }

    @Schema(description = "작성자 · 레일 「작성자 조치 이력」")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Author {
        private Long showroomId;
        private String showroomName;
        @Schema(description = "인플루언서 상세 딥링크 재료")
        private Long userId;
        private String profileImageUrl;
        @Schema(description = "앱 내 팔로우 수 — 인스타 팔로워 아님", example = "51400")
        private long followerCount;
        @Schema(description = "노출 중지 횟수 — 운영자가 거둔 조치는 뺀다 · 횟수만", example = "1")
        private long suspensionCount;
        @Schema(description = "제재로 끝난 삭제 횟수 — 이의 반려 + 기한 경과 · 본인 삭제 제외", example = "0")
        private long sanctionDeleteCount;
    }

    @Schema(description = "사진 한 장")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Image {
        private Long imageId;
        private Integer sortOrder;
        @Schema(description = "표시용(크롭본) — 격자는 FE가 4:5 센터 크롭")
        private String imageUrl;
        @Schema(description = "원본 — 뷰어는 게시 비율 그대로")
        private String originalUrl;
        @Schema(description = "파일명 — 원본 URL의 마지막 경로 조각", example = "IMG_3302.jpg")
        private String fileName;
        private Integer width;
        private Integer height;
        private Integer fileSize;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime uploadedAt;
        @Schema(description = "「신고 지목」 표시 — 운영자 수동")
        private boolean reportFlagged;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime reportFlaggedAt;
        private String reportFlaggedByName;
    }

    @Schema(description = "지표 — 스튜디오 인사이트와 같은 집계")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metrics {
        private long impressions;
        private long likes;
        @Schema(description = "좋아요율(%) — 노출 0이면 null", example = "11.8")
        private Double likeRate;
        private long showroomVisits;
        private long follows;
        @Schema(description = "집계 상한 — 노출 중지면 조치 시각, 삭제면 삭제 시각, 게시중이면 null")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime frozenAt;
    }

    @Schema(description = "신고 내역")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reports {
        private long pendingCount;
        @Schema(description = "처리분 포함 전체")
        private long totalCount;
        @Schema(description = "레일 「최초 신고」 — 대기 신고 기준")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime firstPendingAt;
        @Schema(description = "사유별 건수 — 메타 「3건 · 허위 · 과장 정보 2」. 대기 신고 기준, 대기가 없으면 전체 기준")
        private List<ReasonCount> countsByReason;
        @Schema(description = "전부 · 접수 순")
        private List<Report> items;
    }

    @Getter
    @AllArgsConstructor
    public static class ReasonCount {
        private PostReportReason reasonCode;
        private String reasonLabel;
        private long count;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Report {
        private Long reportId;
        private PostReportReason reasonCode;
        private String reasonLabel;
        private String reasonDetail;
        private PostReportStatus status;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime reportedAt;
        @Schema(description = "신고자 — 회원 상세 링크 재료")
        private Long reporterUserId;
        @Schema(description = "신고자 마스킹 이름 — 소비자에게 「상대에게 알리지 않는다」를 고지했으므로 어드민에서도 가린다",
                example = "한*준")
        private String reporterMaskedName;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime handledAt;
        private String handledByName;
    }

    @Schema(description = "운영자 조치 한 건 — 조치 기록 5항목")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Suspension {
        private Long suspensionId;
        @Schema(description = "진행 중인 조치인가")
        private boolean open;
        private PostSuspensionReason reasonCode;
        private String reasonLabel;
        @Schema(description = "작성자에게 전달한 설명")
        private String reasonDetail;
        private PostPolicyClause policyClauseCode;
        @Schema(description = "근거 규정 문구 — 조치 시점 스냅샷")
        private String policyRef;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime suspendedAt;
        private Long suspendedBy;
        private String suspendedByName;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime appealDeadline;
        @Schema(description = "이의 신청 접수로 기한이 정지됐는가 — 「(신청 접수로 정지)」")
        private boolean deadlinePaused;
        private SuspensionResolution resolution;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime resolvedAt;
        @Schema(description = "조치 취소한 운영자 — CANCELED_BY_ADMIN만")
        private String resolvedByName;
        @Schema(description = "작성자 알림 적재 시각")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime notifiedAt;
        @Schema(description = "실제 발송 성공 여부 — 발송 인프라 미설정이면 false")
        private Boolean notificationDelivered;
    }

    @Schema(description = "이의 신청")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Appeal {
        private Long appealId;
        private PostAppealStatus status;
        private String content;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime submittedAt;
        @Schema(description = "심사 기한 — 접수 + 3영업일")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime reviewDueAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime reviewedAt;
        private String reviewedByName;
        private String reviewComment;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime graceUntil;
    }

    @Schema(description = "삭제 경위 (C8 · C10)")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Deletion {
        @Schema(description = "삭제 유형 — SELF(작성자 삭제) · APPEAL_REJECTED · APPEAL_EXPIRED")
        private PostDeleteReason type;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime deletedAt;
        @Schema(description = "처리자 — 반려는 운영자 이름, 작성자 삭제는 쇼룸명, 기한 경과는 「시스템」")
        private String deletedByName;
        private boolean deletedBySelf;
        @Schema(description = "원 조치 — 게시중에 본인이 지웠다면 null")
        private Long originSuspensionId;
        @Schema(description = "반려 사유 — 반려 삭제만")
        private String rejectReason;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime graceFrom;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime graceUntil;
        @Schema(description = "유예 중 원본 내려받기 기록")
        private List<OriginalDownload> originalDownloads;
        @Schema(description = "작성자 삭제 — 중지 후 며칠 만에 지웠나")
        private Long daysAfterSuspension;
        @Schema(description = "작성자 삭제 — 그 조치의 이의 신청 기한(삭제 시각 기준 D-n은 FE)")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime appealDeadlineAtDeletion;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime purgeAt;
    }

    @Getter
    @AllArgsConstructor
    public static class OriginalDownload {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime downloadedAt;
        private int imageCount;
    }

    @Schema(description = "처리 이력 한 줄")
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineItem {
        @Schema(description = "종류", example = "REPORTED")
        private TimelineType type;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATETIME)
        private LocalDateTime at;
        @Schema(description = "주체 — ADMIN · CREATOR · USER · SYSTEM")
        private TimelineActor actorType;
        @Schema(description = "주체 표기 — 운영자 이름 · 쇼룸명 · 신고자 마스킹 · null(시스템)", example = "임*현")
        private String actorName;
        @Schema(description = "문구", example = "신고 접수 (3건째)")
        private String label;
        @Schema(description = "부가 설명 — 사유 표시명 등")
        private String detail;
        @Schema(description = "점 색 — INFO · SUCCESS · WARNING · DANGER · NEUTRAL")
        private String tone;
        @Schema(description = "참조 id — 신고 · 조치 · 신청 · 알림")
        private Long refId;
    }

    public enum TimelineType {
        PUBLISHED, EDITED, REPORTED, REPORTS_CLOSED, SUSPENDED, SUSPENSION_CANCELED, NOTIFIED,
        APPEAL_SUBMITTED, APPEAL_APPROVED, APPEAL_REJECTED, ORIGINAL_DOWNLOADED, DELETED
    }

    public enum TimelineActor {
        ADMIN, CREATOR, USER, SYSTEM
    }
}
