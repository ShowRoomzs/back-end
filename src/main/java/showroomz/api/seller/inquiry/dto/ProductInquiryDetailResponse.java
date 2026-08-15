package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.entity.ProductInquiryHistory;
import showroomz.domain.inquiry.support.AnswerElapsedFormatter;
import showroomz.domain.inquiry.support.ProductInquiryStatusLabel;
import showroomz.domain.inquiry.type.InquiryActorType;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.global.utils.NicknameMasker;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 파트너센터 상품 문의 상세 (§23-3) — 문의 정보 / 문의 내용 / 브랜드 답변 3카드 + 우측 처리·이력.
 *
 * <p>작성자는 닉네임 마스킹만 담는다. 브랜드는 실명·연락처를 볼 수 없고 회원 상세 링크도 없다.
 * 운영자의 삭제 사유는 내부 기록이라 이 응답에 포함하지 않는다 (§23-5).
 */
@Getter
@Builder
@Schema(description = "파트너센터 상품 문의 상세 응답")
public class ProductInquiryDetailResponse {

    @Schema(description = "문의 ID", example = "71")
    private Long inquiryId;

    @Schema(description = "문의번호", example = "QNA-20260811-071")
    private String inquiryNumber;

    @Schema(description = "문의 유형 코드", example = "INGREDIENT_USAGE")
    private ProductInquiryType type;

    @Schema(description = "문의 유형 명", example = "성분·사용법")
    private String typeName;

    @Schema(description = "상품 ID — 상품 상세 링크용", example = "1024")
    private Long productId;

    @Schema(description = "상품명", example = "수분진정 세럼 30ml")
    private String productName;

    @Schema(description = "작성자 — 닉네임 마스킹. 실명·연락처·회원 상세 링크는 제공하지 않는다", example = "구****")
    private String writerName;

    @Schema(description = "비밀글 여부 — 작성자 지정 값이며 브랜드는 변경할 수 없다", example = "false")
    private boolean secret;

    @Schema(description = "공개여부 명", example = "공개")
    private String visibilityName;

    @Schema(description = "등록일시", example = "2026-08-11T09:12:00")
    private LocalDateTime createdAt;

    @Schema(description = "문의 내용 — 소비자 입력 · 250자 이내")
    private String content;

    @Schema(description = "첨부 사진 URL — 최대 3장")
    private List<String> imageUrls;

    @Schema(description = "브랜드 답변 — 미답변이면 null")
    private String answerContent;

    @Schema(description = "답변 등록 일시 — 미답변이면 null", example = "2026-08-10T16:05:00")
    private LocalDateTime answeredAt;

    @Schema(description = "답변 수정 일시 — 수정한 적 없으면 null. 등록 시각과 병기한다", example = "2026-08-11T09:30:00")
    private LocalDateTime answerModifiedAt;

    @Schema(description = "답변 소요 — 등록→답변까지 실제 걸린 시간. 미답변이면 null", example = "1시간 43분")
    private String answerElapsedText;

    @Schema(description = "답변 축", example = "ANSWERED")
    private InquiryStatus status;

    @Schema(description = "노출 축", example = "NORMAL")
    private InquiryExposureStatus exposureStatus;

    @Schema(description = "두 축을 합친 표시 상태", example = "답변완료")
    private String statusLabel;

    @Schema(description = "삭제 요청 — 요청한 적 없으면 null")
    private DeleteRequestInfo deleteRequest;

    @Schema(description = "처리 이력 — 최신순")
    private List<HistoryItem> history;

    @Schema(description = "답변 등록 가능 여부 — 미답변이며 삭제 요청·삭제 상태가 아닐 때", example = "true")
    private boolean canRegisterAnswer;

    @Schema(description = "답변 수정 가능 여부 (§23-4)", example = "false")
    private boolean canModifyAnswer;

    @Schema(description = "삭제 요청 가능 여부 — 검토 중·삭제 건에는 조작이 없다 (§23-5)", example = "true")
    private boolean canRequestDelete;

    @Schema(description = "현재 탭·필터 기준 이전 문의 ID", example = "70")
    private Long prevInquiryId;

    @Schema(description = "현재 탭·필터 기준 다음 문의 ID", example = "72")
    private Long nextInquiryId;

    @Getter
    @Builder
    @Schema(description = "삭제 요청과 그 결과 — 요청 취소는 불가하며, 반려되면 사유가 브랜드에게 전달된다")
    public static class DeleteRequestInfo {
        @Schema(description = "요청 사유 코드", example = "BRAND_COMPARISON")
        private ProductInquiryDeleteReason reason;
        @Schema(description = "요청 사유 명", example = "타 브랜드 비교·비방")
        private String reasonName;
        @Schema(description = "상세 설명 — 기타(직접 입력)일 때만 필수")
        private String detail;
        @Schema(example = "2026-08-09T15:02:00")
        private LocalDateTime requestedAt;
        @Schema(description = "운영자 처리 일시 — 검토 중이면 null", example = "2026-08-10T11:20:00")
        private LocalDateTime reviewedAt;
        @Schema(description = "운영자 반려 사유 — 반려된 경우에만 값이 있다")
        private String rejectReason;
        @Schema(description = "삭제 집행 일시 — 집행된 경우에만 값이 있다")
        private LocalDateTime deletedAt;
        @Schema(description = "운영자 검토 대기 중", example = "true")
        private boolean underReview;
        @Schema(description = "요청이 반려됨 — 상태는 요청 직전 값으로 복귀했다", example = "false")
        private boolean rejected;
    }

    @Getter
    @Builder
    @Schema(description = "처리 이력 1건")
    public static class HistoryItem {
        @Schema(description = "이벤트 코드", example = "DELETE_REQUESTED")
        private String event;
        @Schema(description = "이벤트 명", example = "문의 삭제 요청")
        private String label;
        @Schema(description = "부가 문구 — 삭제 요청 사유 등", example = "타 브랜드 비교·비방")
        private String detail;
        @Schema(example = "2026-08-09T15:02:00")
        private LocalDateTime occurredAt;
        @Schema(description = "행위 주체", example = "BRAND")
        private InquiryActorType actorType;
        @Schema(description = "행위 주체 표기", example = "○○ 브랜드")
        private String actorLabel;
    }

    public static ProductInquiryDetailResponse of(ProductInquiry inquiry,
                                                  String inquiryNumber,
                                                  List<ProductInquiryHistory> histories,
                                                  Long prevInquiryId,
                                                  Long nextInquiryId) {
        String writerName = NicknameMasker.mask(inquiry.getUser().getNickname());
        String brandName = inquiry.getProduct().getMarket().getMarketName();

        return ProductInquiryDetailResponse.builder()
                .inquiryId(inquiry.getId())
                .inquiryNumber(inquiryNumber)
                .type(inquiry.getType())
                .typeName(inquiry.getType().getDescription())
                .productId(inquiry.getProduct().getProductId())
                .productName(inquiry.getProduct().getName())
                .writerName(writerName)
                .secret(inquiry.isSecret())
                .visibilityName(inquiry.isSecret() ? "비밀글" : "공개")
                .createdAt(inquiry.getCreatedAt())
                .content(inquiry.getContent())
                .imageUrls(inquiry.getImageUrls())
                .answerContent(inquiry.getAnswerContent())
                .answeredAt(inquiry.getAnsweredAt())
                .answerModifiedAt(inquiry.getAnswerModifiedAt())
                .answerElapsedText(AnswerElapsedFormatter.format(inquiry.getCreatedAt(), inquiry.getAnsweredAt()))
                .status(inquiry.getStatus())
                .exposureStatus(inquiry.getExposureStatus())
                .statusLabel(ProductInquiryStatusLabel.of(inquiry.getStatus(), inquiry.getExposureStatus()))
                .deleteRequest(toDeleteRequestInfo(inquiry))
                .history(toHistoryItems(histories, writerName, brandName))
                .canRegisterAnswer(!inquiry.isAnswered() && inquiry.getExposureStatus() == InquiryExposureStatus.NORMAL)
                .canModifyAnswer(inquiry.isAnswered() && inquiry.getExposureStatus() == InquiryExposureStatus.NORMAL)
                .canRequestDelete(inquiry.getExposureStatus() == InquiryExposureStatus.NORMAL)
                .prevInquiryId(prevInquiryId)
                .nextInquiryId(nextInquiryId)
                .build();
    }

    private static DeleteRequestInfo toDeleteRequestInfo(ProductInquiry inquiry) {
        if (inquiry.getDeleteRequestedAt() == null) {
            return null;
        }
        boolean underReview = inquiry.isDeleteRequested();
        boolean rejected = !underReview && !inquiry.isDeleted() && inquiry.getDeleteReviewedAt() != null;

        return DeleteRequestInfo.builder()
                .reason(inquiry.getDeleteRequestReason())
                .reasonName(inquiry.getDeleteRequestReason() != null
                        ? inquiry.getDeleteRequestReason().getDescription() : null)
                .detail(inquiry.getDeleteRequestDetail())
                .requestedAt(inquiry.getDeleteRequestedAt())
                .reviewedAt(inquiry.getDeleteReviewedAt())
                .rejectReason(inquiry.getDeleteRejectReason())
                .deletedAt(inquiry.getDeletedAt())
                .underReview(underReview)
                .rejected(rejected)
                .build();
    }

    private static List<HistoryItem> toHistoryItems(List<ProductInquiryHistory> histories,
                                                    String writerName, String brandName) {
        return histories.stream()
                .map(history -> HistoryItem.builder()
                        .event(history.getHistoryType().name())
                        .label(history.getHistoryType().getDescription())
                        .detail(history.getDetail())
                        .occurredAt(history.getCreatedAt())
                        .actorType(history.getActorType())
                        .actorLabel(resolveActorLabel(history.getActorType(), writerName, brandName))
                        .build())
                .toList();
    }

    private static String resolveActorLabel(InquiryActorType actorType, String writerName, String brandName) {
        return switch (actorType) {
            case CONSUMER -> writerName;
            case BRAND -> brandName;
            case OPERATOR -> InquiryActorType.OPERATOR.getDescription();
        };
    }
}
