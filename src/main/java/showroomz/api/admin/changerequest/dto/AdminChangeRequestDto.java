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
        @Schema(description = "요청 ID", example = "12")
        private Long requestId;
        @Schema(description = "요청 코드", example = "CHG-2026-0001")
        private String requestCode;
        @Schema(description = "브랜드명", example = "코코브라운")
        private String brandName;
        @Schema(description = "요청 유형", example = "BUSINESS_INFO")
        private ChangeRequestType type;
        @Schema(description = "요청 시각", example = "2026-08-07T10:00:00")
        private LocalDateTime requestedAt;
        @Schema(description = "처리 시각 — PENDING이면 null", example = "2026-08-10T11:05:00")
        private LocalDateTime processedAt;
        @Schema(description = "< 24h → 18h · >= 24h → 2일 3h · 처리 완료 건은 null", example = "2일 6h")
        private String elapsedText;
        @Schema(description = "PENDING && 경과 > 48h", example = "true")
        private boolean slaExceeded;
        @Schema(description = "상태", example = "PENDING")
        private ChangeRequestStatus status;
    }

    @Getter
    @Builder
    @Schema(description = "상태별 건수(§16-1) — all은 CANCELED를 포함한 네 상태의 합")
    public static class StatusCounts {
        @Schema(example = "2")
        private long pending;
        @Schema(example = "10")
        private long approved;
        @Schema(example = "3")
        private long rejected;
        @Schema(example = "1")
        private long canceled;
        @Schema(example = "16")
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
        @Schema(description = "검토 대기 건수", example = "2")
        private long pendingCount;
    }

    @Getter
    @Builder
    @Schema(description = "대조표 1행 (A-8)")
    public static class DiffRow {
        @Schema(example = "REPRESENTATIVE_NAME")
        private String fieldKey;
        @Schema(example = "대표자명")
        private String label;
        @Schema(example = "김대표")
        private String currentValue;
        @Schema(example = "이대표")
        private String requestedValue;
        @Schema(example = "true")
        private boolean changed;
        @Schema(description = "사업자등록번호만 true — '변경 요청 불가' 상수 표시용", example = "false")
        private boolean locked;
    }

    @Getter
    @Builder
    @Schema(description = "증빙 미리보기 (A-8)")
    public static class Evidence {
        @Schema(description = "요청 유형에서 파생되는 상수 — 사업자등록증 / 통장 사본", example = "사업자등록증")
        private String documentLabel;
        @Schema(example = "사업자등록증_변경.jpg")
        private String fileName;
        @Schema(example = "1258291")
        private Long fileSizeBytes;
        @Schema(example = "jpg")
        private String extension;
        @Schema(example = "https://s3.ap-northeast-2.amazonaws.com/bucket/change-request/biz.jpg")
        private String fileUrl;
        @Schema(example = "2026-08-07T10:00:00")
        private LocalDateTime uploadedAt;
    }

    @Getter
    @Builder
    @Schema(description = "참고 항목(변경 대상 아님)")
    public static class ReferenceItem {
        @Schema(example = "사업자등록번호")
        private String label;
        @Schema(example = "123-45-67890")
        private String value;
    }

    @Getter
    @Builder
    @Schema(description = "정산 계좌 예금주 대조(§16-3)")
    public static class HolderCheck {
        @Schema(example = "(주)코코브라운")
        private String requestedHolder;
        @Schema(example = "(주)코코브라운")
        private String companyName;
        @Schema(description = "예금주 ≠ 상호이면 true", example = "false")
        private boolean mismatch;
    }

    @Getter
    @Builder
    @Schema(description = "처리 이력 1건 — 접수/승인/반려/취소 최대 2건")
    public static class HistoryEvent {
        @Schema(description = "이벤트 — REQUESTED / APPROVED / REJECTED / CANCELED", example = "REQUESTED")
        private String event;
        @Schema(example = "2026-08-07T10:00:00")
        private LocalDateTime occurredAt;
        @Schema(example = "김담당")
        private String actorLabel;
    }

    @Getter
    @Builder
    @Schema(description = "상세 응답 (A-8)")
    public static class DetailResponse {
        @Schema(example = "12")
        private Long requestId;
        @Schema(example = "CHG-2026-0001")
        private String requestCode;
        @Schema(example = "코코브라운")
        private String brandName;
        @Schema(example = "7")
        private Long marketId;
        @Schema(example = "BUSINESS_INFO")
        private ChangeRequestType type;
        @Schema(example = "PENDING")
        private ChangeRequestStatus status;
        @Schema(example = "true")
        private boolean slaExceeded;
        @Schema(example = "2026-08-07T10:00:00")
        private LocalDateTime requestedAt;
        private LocalDateTime processedAt;
        @Schema(example = "김담당")
        private String requesterName;
        @Schema(example = "2일 6h")
        private String elapsedText;
        @Schema(example = "대표자 변경 및 사업장 이전")
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
        @Schema(description = "정형 반려 사유 코드", example = "REASON_INSUFFICIENT")
        private ChangeRequestRejectReason reasonType;

        @Size(max = 500, message = "상세 사유는 500자 이내로 입력해주세요.")
        @Schema(description = "OTHER 선택 시에만 필수(최대 500자)", example = "제출하신 서류의 발급일이 6개월을 초과했습니다.")
        private String reasonDetail;
    }

    @Getter
    @Builder
    @Schema(description = "승인·반려 응답 (A-9, C10 토스트)")
    public static class ProcessResponse {
        @Schema(example = "12")
        private Long requestId;
        @Schema(example = "CHG-2026-0001")
        private String requestCode;
        @Schema(example = "코코브라운")
        private String brandName;
        @Schema(example = "BUSINESS_INFO")
        private ChangeRequestType type;
        @Schema(example = "APPROVED")
        private ChangeRequestStatus status;
        @Schema(example = "2026-08-10T11:05:00")
        private LocalDateTime processedAt;
        @Schema(description = "정형 반려 사유 문구 — 승인이면 null", example = "변경 사유 불충분")
        private String rejectReason;
        @Schema(description = "기타 상세 사유 — 없으면 null")
        private String rejectReasonDetail;
    }

    @Getter
    @Builder
    @Schema(description = "반려 사유 드롭다운 옵션")
    public static class RejectReasonOption {
        @Schema(example = "REASON_INSUFFICIENT")
        private ChangeRequestRejectReason code;
        @Schema(example = "변경 사유 불충분")
        private String label;
        @Schema(description = "OTHER만 true", example = "false")
        private boolean detailRequired;
    }
}
