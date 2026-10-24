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
import showroomz.domain.changerequest.type.ChangeRequesterType;
import showroomz.global.dto.PaginationInfo;

import java.time.LocalDateTime;
import java.util.List;

/** 부록 A(§16) 어드민 응답·요청 스키마. rev.7 — 요청자(브랜드 · 인플루언서) · 사업자 구분 변경(§16-2a · 16 설계서 3절). */
public class AdminChangeRequestDto {

    @Getter
    @Builder
    @Schema(description = "요청자(rev.7 「요청자」 열) — 브랜드면 marketId · 브랜드명, 인플루언서면 creatorId · 쇼룸명(현재값)")
    public static class Requester {
        @Schema(example = "CREATOR")
        private ChangeRequesterType type;
        @Schema(description = "BRAND → marketId([브랜드 상세 ↗]) · CREATOR → creatorId([인플루언서 상세 ↗])", example = "31")
        private Long id;
        @Schema(example = "예린 뷰티")
        private String name;
    }

    @Getter
    @Builder
    @Schema(description = "목록 행 (A-7)")
    public static class ListItem {
        @Schema(description = "요청 ID", example = "12")
        private Long requestId;
        @Schema(description = "요청 코드", example = "CHG-2026-0001")
        private String requestCode;
        private Requester requester;
        @Schema(description = "브랜드명 — 브랜드 요청만(인플루언서 요청은 null) · requester.name 을 쓴다", example = "코코브라운",
                deprecated = true)
        private String brandName;
        @Schema(description = "요청 유형 — BUSINESS_INFO · SETTLEMENT_ACCOUNT · TO_BUSINESS · TO_INDIVIDUAL · CREATOR_ACCOUNT(1021 기-22)",
                example = "BUSINESS_INFO")
        private ChangeRequestType type;
        @Schema(description = "요청 유형 라벨 — 「계좌 변경 (인플루언서)」 등", example = "사업자 정보")
        private String typeLabel;
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
        @Schema(description = "검토 대기 건수", example = "4")
        private long pendingCount;
        @Schema(description = "검토 대기 중 브랜드 요청 — 어드민 홈 「브랜드 N」(55 계획서 A3)", example = "2")
        private long brandPendingCount;
        @Schema(description = "검토 대기 중 인플루언서 요청 — 어드민 홈 「인플루언서 N」", example = "2")
        private long creatorPendingCount;
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
        @Schema(description = "행 성격 — REQUEST(요청 항목) · LIVE(현재 값 · 바뀌지 않음) · DERIVED(구분 변경에 따라 바뀜) · "
                + "DOCUMENT(서버가 값을 갖지 않음 — 서류로 운영자 확인) · CHECK(규칙 판정 — holderCheck)", example = "REQUEST")
        private String source;
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
    @Schema(description = "정산 계좌 예금주 대조(§16-3) — 인플루언서 건은 currentHolder · rule 을 쓴다")
    public static class HolderCheck {
        @Schema(description = "인플루언서 건 — 현재 예금주(= 본인 인증 실명)", example = "오예린")
        private String currentHolder;
        @Schema(description = "인플루언서 건 — 전환 후 예금주 규칙: REPRESENTATIVE_NAME · REAL_NAME", example = "REPRESENTATIVE_NAME")
        private String rule;
        @Schema(example = "(주)코코브라운")
        private String requestedHolder;
        @Schema(example = "(주)코코브라운")
        private String companyName;
        @Schema(description = "예금주 ≠ 상호이면 true", example = "false")
        private boolean mismatch;
    }

    @Getter
    @Builder
    @Schema(description = "서류와 대조할 값(C5b) — 인플루언서 건만. serverCheck 가 MATCH 인 행에만 「일치」를 그린다")
    public static class CompareItem {
        @Schema(example = "BIRTHDAY")
        private String key;
        @Schema(example = "생년월일")
        private String label;
        @Schema(example = "1995-08-12")
        private String value;
        @Schema(description = "서류의 날짜가 이 시각보다 앞서야 한다(개업일 · 폐업일)", nullable = true)
        private LocalDateTime mustBeBefore;
        @Schema(description = "MATCH · MISMATCH · null(서버가 확인하지 않음)", nullable = true)
        private String serverCheck;
    }

    @Getter
    @Builder
    @Schema(description = "「승인하면」 카드(C3a · C3b · C7b) — 검토 대기일 때만")
    public static class ApprovalImpact {
        @Schema(description = "적용 시점 — 정산 생성 시점 스냅샷이라 「다음 정산부터」", example = "NEXT_SETTLEMENT")
        private String appliesFrom;
        @Schema(example = "WITHHOLDING_3_3")
        private String payoutMethodBefore;
        @Schema(example = "TAX_INVOICE")
        private String payoutMethodAfter;
        @Schema(description = "기존 구분으로 끝나는 정산 — 지급 완료가 아닌 것")
        private List<RetainedSettlement> retainedSettlements;
        @Schema(description = "승인 후 정산 계좌 변경 필요 — 상호 명의 계좌를 받기 전(§55 B-9)에는 항상 false")
        private boolean accountChangeRequired;
        @Schema(example = "REPRESENTATIVE_NAME")
        private String accountHolderRuleAfter;
    }

    @Getter
    @Builder
    public static class RetainedSettlement {
        private Long settlementId;
        @Schema(example = "STL-2609-016")
        private String settlementNumber;
        @Schema(example = "REVIEWING")
        private String status;
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
        private Requester requester;
        @Schema(description = "브랜드 요청만 — requester.name 을 쓴다", example = "코코브라운", deprecated = true)
        private String brandName;
        @Schema(description = "브랜드 요청만 — requester.id 를 쓴다", example = "7", deprecated = true)
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
        @Schema(description = "브랜드 담당자 이름 스냅샷 — 인플루언서 요청은 null(FE 「인플루언서 본인」)", example = "김담당")
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
        @Schema(description = "인플루언서 건만 — 서류와 대조할 값(C5b)", nullable = true)
        private List<CompareItem> compareItems;
        @Schema(description = "인플루언서 건 · 검토 대기일 때만", nullable = true)
        private ApprovalImpact approvalImpact;
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
        private Requester requester;
        @Schema(description = "브랜드 요청만", example = "코코브라운", deprecated = true)
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
