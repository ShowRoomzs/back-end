package showroomz.api.admin.productinquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.type.InquiryActorType;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryRejectReason;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.global.dto.PaginationInfo;

import java.time.LocalDateTime;
import java.util.List;

/** 어드민 상품 문의 모니터링 응답·요청 스키마 (§18). 운영자는 답변하지 않고 부적절 게시물만 걸러낸다. */
public class AdminProductInquiryDto {

    @Getter
    @Builder
    @Schema(description = "목록 행 (§18-2 컬럼 7종)")
    public static class ListItem {
        @Schema(description = "문의 ID", example = "58")
        private Long inquiryId;
        @Schema(description = "문의 유형 코드", example = "INGREDIENT_USAGE")
        private ProductInquiryType type;
        @Schema(description = "문의 유형 명 — 점 없는 중립 배지", example = "성분·사용법")
        private String typeName;
        @Schema(description = "질문 — 목록은 한 줄 말줄임으로 표시한다")
        private String content;
        @Schema(description = "상품명", example = "글로우 세럼 앰플 30ml")
        private String productName;
        @Schema(description = "브랜드명", example = "글로우랩")
        private String brandName;
        @Schema(example = "2026-07-16T10:05:00")
        private LocalDateTime createdAt;
        @Schema(description = "답변일 — 미답변이면 null", example = "2026-07-16T13:40:00")
        private LocalDateTime answeredAt;
        @Schema(description = "답변 축", example = "ANSWERED")
        private InquiryStatus status;
        @Schema(description = "노출 축", example = "NORMAL")
        private InquiryExposureStatus exposureStatus;
        @Schema(description = "두 축을 합친 표시 상태 — 맨 뒷열", example = "답변완료")
        private String statusLabel;
    }

    @Getter
    @Builder
    @Schema(description = "상태 탭 건수 — 유형·검색어 필터는 반영하고 상태 조건만 제외한 값")
    public static class StatusCounts {
        @Schema(example = "6")
        private long all;
        @Schema(example = "1")
        private long waiting;
        @Schema(example = "3")
        private long answered;
        @Schema(description = "운영자가 봐야 할 유일한 수치", example = "1")
        private long deleteRequested;
        @Schema(example = "1")
        private long deleted;
    }

    @Getter
    @Builder
    @Schema(description = "목록 응답")
    public static class ListResponse {
        private List<ListItem> content;
        private PaginationInfo pageInfo;
        @Schema(description = "상태 탭 건수 — 툴바의 `총 N건`도 이 값 기준이다")
        private StatusCounts statusCounts;
        @Schema(description = "전체 삭제 요청 건수 — 탭·필터와 무관. 툴바의 `삭제 요청 N건`", example = "1")
        private long deleteRequestedCount;
    }

    @Getter
    @Builder
    @Schema(description = "GNB 배지용 삭제 요청 건수 — 운영자가 봐야 할 유일한 수치 (§18-2)")
    public static class SummaryResponse {
        @Schema(example = "1")
        private long deleteRequestedCount;
    }

    @Getter
    @Builder
    @Schema(description = "문의 유형 필터 옵션 (§18-2-1)")
    public static class TypeOption {
        @Schema(example = "OPTION")
        private ProductInquiryType code;
        @Schema(example = "옵션")
        private String label;
    }

    @Getter
    @Builder
    @Schema(description = "삭제 요청 카드 — 요청이 있는 건에만 존재한다 (§18-3, §18-7)")
    public static class DeleteRequestInfo {
        @Schema(description = "요청 사유 코드", example = "BRAND_COMPARISON")
        private ProductInquiryDeleteReason reason;
        @Schema(description = "요청 사유 명", example = "타 브랜드 비교·비방")
        private String reasonName;
        @Schema(description = "요청 상세 설명")
        private String detail;
        @Schema(description = "요청 브랜드명", example = "무드코스메틱")
        private String requesterBrandName;
        @Schema(example = "2026-07-15T16:02:00")
        private LocalDateTime requestedAt;
        @Schema(description = "운영자 검토 대기 중", example = "true")
        private boolean underReview;
        @Schema(description = "반려됨 — 문의는 게시 유지, 상태는 요청 직전으로 복귀했다", example = "false")
        private boolean rejected;
        @Schema(description = "반려 사유 코드 — 반려된 경우에만 값이 있다")
        private ProductInquiryRejectReason rejectReasonType;
        @Schema(description = "반려 사유 명")
        private String rejectReasonName;
        @Schema(description = "반려 상세 사유")
        private String rejectReasonDetail;
        @Schema(description = "반려 처리 일시")
        private LocalDateTime rejectedAt;
        @Schema(description = "반려 처리 운영자명")
        private String rejectedByName;
    }

    @Getter
    @Builder
    @Schema(description = "우측 처리 패널 메타 — 현재 상태와 무관하게 같은 레이아웃, 표시 항목만 상태별로 달라진다 (§18-4)")
    public static class ProcessingMeta {
        @Schema(example = "2026-07-16T10:05:00")
        private LocalDateTime createdAt;
        @Schema(description = "답변일시 — 답변완료 상태일 때만 값이 있다")
        private LocalDateTime answeredAt;
        @Schema(description = "답변자(브랜드명) — 답변완료 상태일 때만 값이 있다", example = "글로우랩")
        private String answererName;
        @Schema(description = "삭제 요청일시 — 삭제 요청 상태일 때만 값이 있다")
        private LocalDateTime deleteRequestedAt;
        @Schema(description = "요청자(브랜드명) — 삭제 요청 상태일 때만 값이 있다", example = "무드코스메틱")
        private String deleteRequesterName;
        @Schema(description = "삭제일시 — 삭제 상태일 때만 값이 있다")
        private LocalDateTime deletedAt;
        @Schema(description = "처리자(운영자명) — 삭제 상태일 때만 값이 있다", example = "김운영")
        private String processedByName;
        @Schema(description = "삭제 사유 명 — 삭제 상태일 때만 값이 있다. 내부 기록용")
        private String deleteReasonName;
        @Schema(description = "삭제 상세 사유 — 삭제 상태일 때만 값이 있다. 내부 기록용")
        private String deleteReasonDetail;
    }

    @Getter
    @Builder
    @Schema(description = "처리 이력 1건 — 최신순")
    public static class HistoryItem {
        @Schema(example = "DELETE_EXECUTED")
        private String event;
        @Schema(description = "어드민 화면 전용 라벨", example = "삭제 처리")
        private String label;
        @Schema(description = "부가 문구 — 삭제 요청 사유 등")
        private String detail;
        @Schema(example = "2026-07-08T16:30:00")
        private LocalDateTime occurredAt;
        @Schema(description = "행위 주체", example = "OPERATOR")
        private InquiryActorType actorType;
        @Schema(description = "행위 주체 표기 — `역할(이름)`", example = "운영자(김운영)")
        private String actorLabel;
    }

    @Getter
    @Builder
    @Schema(description = "상세 응답 (§18-3) — 3카드(문의 정보 · 문의 내용 · 브랜드 답변) + 삭제 요청 카드(있는 경우) + 우측 처리·이력")
    public static class DetailResponse {
        @Schema(example = "58")
        private Long inquiryId;
        @Schema(description = "문의번호", example = "QNA-20260716-058")
        private String inquiryNumber;
        @Schema(example = "INGREDIENT_USAGE")
        private ProductInquiryType type;
        @Schema(example = "성분·사용법")
        private String typeName;
        @Schema(description = "상품 ID — 상품 상세 링크용", example = "1024")
        private Long productId;
        @Schema(example = "글로우 세럼 앰플 30ml")
        private String productName;
        @Schema(description = "마켓(브랜드) ID — 브랜드 상세 링크용", example = "12")
        private Long marketId;
        @Schema(example = "글로우랩")
        private String brandName;
        @Schema(description = "작성자 회원 ID — 회원 상세 링크용", example = "301")
        private Long userId;
        @Schema(description = "작성자명 — 실명 우선, 없으면 닉네임. 마스킹하지 않는다", example = "한서준")
        private String writerName;
        @Schema(description = "비밀글 여부", example = "false")
        private boolean secret;
        @Schema(description = "공개여부 명", example = "공개")
        private String visibilityName;
        @Schema(example = "2026-07-16T10:05:00")
        private LocalDateTime createdAt;
        @Schema(description = "문의 내용 — 소비자 입력 · 250자 이내")
        private String content;
        @Schema(description = "첨부 사진 URL — 최대 3장")
        private List<String> imageUrls;
        @Schema(description = "브랜드 답변 — 미답변이면 null")
        private String answerContent;
        @Schema(description = "답변일시 — 미답변이면 null")
        private LocalDateTime answeredAt;
        @Schema(description = "답변 수정일시 — 수정한 적 없으면 null")
        private LocalDateTime answerModifiedAt;
        @Schema(description = "답변자(브랜드명) — 미답변이면 null", example = "글로우랩")
        private String answererName;
        @Schema(description = "답변 축", example = "ANSWERED")
        private InquiryStatus status;
        @Schema(description = "노출 축", example = "NORMAL")
        private InquiryExposureStatus exposureStatus;
        @Schema(description = "두 축을 합친 표시 상태", example = "답변완료")
        private String statusLabel;
        @Schema(description = "삭제 요청 카드 — 요청이 없으면 null")
        private DeleteRequestInfo deleteRequest;
        @Schema(description = "우측 처리 패널 메타")
        private ProcessingMeta processingMeta;
        @Schema(description = "처리 이력 — 최신순")
        private List<HistoryItem> history;
        @Schema(description = "삭제 집행 가능 여부 — 삭제된 건이 아니면 언제나 가능(요청 유무와 무관)", example = "true")
        private boolean canExecuteDelete;
        @Schema(description = "반려 가능 여부 — 삭제 요청이 있을 때만 성립한다 (§18-4)", example = "false")
        private boolean canReject;
        @Schema(description = "현재 탭·필터 기준 이전 문의 ID")
        private Long prevInquiryId;
        @Schema(description = "현재 탭·필터 기준 다음 문의 ID")
        private Long nextInquiryId;
    }
}
