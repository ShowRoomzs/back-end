package showroomz.api.admin.changerequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.global.dto.PaginationInfo;

import java.time.LocalDateTime;
import java.util.List;

/** 부록 A(§16) 어드민 응답·요청 스키마. */
public class AdminChangeRequestDto {

    @Getter
    @Builder
    @Schema(description = "목록 행 (A-7)")
    public static class ListItem {
        private Long requestId;
        private String requestCode;
        private String brandName;
        private ChangeRequestType type;
        private LocalDateTime requestedAt;
        private LocalDateTime processedAt;
        @Schema(description = "< 24h → 18h · >= 24h → 2일 3h · 처리 완료 건은 null")
        private String elapsedText;
        @Schema(description = "PENDING && 경과 > 48h")
        private boolean slaExceeded;
        private ChangeRequestStatus status;
    }

    @Getter
    @Builder
    @Schema(description = "상태별 건수(§16-1) — all은 CANCELED를 포함한 네 상태의 합")
    public static class StatusCounts {
        private long pending;
        private long approved;
        private long rejected;
        private long canceled;
        private long all;
    }

    @Getter
    @Builder
    @Schema(description = "목록 응답 (A-7)")
    public static class ListResponse {
        private List<ListItem> content;
        private PaginationInfo pageInfo;
        private StatusCounts statusCounts;
    }

    @Getter
    @Builder
    @Schema(description = "GNB 배지용 검토 대기 건수")
    public static class SummaryResponse {
        private long pendingCount;
    }

    @Getter
    @Builder
    @Schema(description = "대조표 1행 (A-8)")
    public static class DiffRow {
        private String fieldKey;
        private String label;
        private String currentValue;
        private String requestedValue;
        private boolean changed;
        @Schema(description = "사업자등록번호만 true — '변경 요청 불가' 상수 표시용")
        private boolean locked;
    }

    @Getter
    @Builder
    @Schema(description = "증빙 미리보기 (A-8)")
    public static class Evidence {
        @Schema(description = "요청 유형에서 파생되는 상수 — 사업자등록증 / 통장 사본")
        private String documentLabel;
        private String fileName;
        private Long fileSizeBytes;
        private String extension;
        private String fileUrl;
        private LocalDateTime uploadedAt;
    }

    @Getter
    @Builder
    @Schema(description = "참고 항목(변경 대상 아님)")
    public static class ReferenceItem {
        private String label;
        private String value;
    }

    @Getter
    @Builder
    @Schema(description = "정산 계좌 예금주 대조(§16-3)")
    public static class HolderCheck {
        private String requestedHolder;
        private String companyName;
        private boolean mismatch;
    }

    @Getter
    @Builder
    @Schema(description = "처리 이력 1건 — 접수/승인/반려/취소 최대 2건")
    public static class HistoryEvent {
        private String event;
        private LocalDateTime occurredAt;
        private String actorLabel;
    }

    @Getter
    @Builder
    @Schema(description = "상세 응답 (A-8)")
    public static class DetailResponse {
        private Long requestId;
        private String requestCode;
        private String brandName;
        private Long marketId;
        private ChangeRequestType type;
        private ChangeRequestStatus status;
        private boolean slaExceeded;
        private LocalDateTime requestedAt;
        private LocalDateTime processedAt;
        private String requesterName;
        private String elapsedText;
        private String reason;
        private List<DiffRow> diff;
        private List<String> changedFieldLabels;
        private Evidence evidence;
        private List<ReferenceItem> referenceItems;
        private HolderCheck holderCheck;
        private List<HistoryEvent> history;
        private Long prevRequestId;
        private Long nextRequestId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "반려 요청")
    public static class RejectRequest {

        @NotNull(message = "반려 사유는 필수입니다.")
        private ChangeRequestRejectReason reasonType;

        @Size(max = 500, message = "상세 사유는 500자 이내로 입력해주세요.")
        private String reasonDetail;
    }

    @Getter
    @Builder
    @Schema(description = "승인·반려 응답 (A-9, C10 토스트)")
    public static class ProcessResponse {
        private Long requestId;
        private String requestCode;
        private String brandName;
        private ChangeRequestType type;
        private ChangeRequestStatus status;
        private LocalDateTime processedAt;
        private String rejectReason;
        private String rejectReasonDetail;
    }

    @Getter
    @Builder
    @Schema(description = "반려 사유 드롭다운 옵션")
    public static class RejectReasonOption {
        private ChangeRequestRejectReason code;
        private String label;
        private boolean detailRequired;
    }
}
