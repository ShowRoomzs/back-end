package showroomz.api.admin.productinquiry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.productinquiry.dto.AdminProductInquiryDeleteDecision;
import showroomz.api.admin.productinquiry.dto.AdminProductInquiryDto;
import showroomz.api.admin.productinquiry.repository.AdminProductInquiryQueryRepository;
import showroomz.api.admin.productinquiry.type.AdminProductInquiryStatusFilter;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.entity.ProductInquiryHistory;
import showroomz.domain.inquiry.repository.ProductInquiryHistoryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.support.ProductInquiryNumber;
import showroomz.domain.inquiry.support.ProductInquiryStatusLabel;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.ProductInquiryAdminDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryHistoryType;
import showroomz.domain.inquiry.type.ProductInquiryRejectReason;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 상품 문의 모니터링 (§18) — 운영자는 답변하지 않는다. 답변 주체는 브랜드이고,
 * 운영자는 부적절 게시물을 걸러내는 역할만 한다(삭제 집행 · 삭제 요청 반려).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminProductInquiryService {

    private static final String UNKNOWN_OPERATOR = "운영자";

    private final ProductInquiryRepository productInquiryRepository;
    private final ProductInquiryHistoryRepository productInquiryHistoryRepository;
    private final AdminProductInquiryQueryRepository adminProductInquiryQueryRepository;
    private final SellerRepository sellerRepository;

    @Transactional(readOnly = true)
    public AdminProductInquiryDto.ListResponse getList(AdminProductInquiryStatusFilter statusFilter,
                                                        ProductInquiryType type, String keyword,
                                                        Pageable pageable) {
        String normalizedKeyword = normalize(keyword);
        Page<ProductInquiry> page = adminProductInquiryQueryRepository.search(
                statusFilter, type, normalizedKeyword, pageable);

        List<AdminProductInquiryDto.ListItem> content = page.getContent().stream()
                .map(this::toListItem)
                .toList();

        return AdminProductInquiryDto.ListResponse.builder()
                .content(content)
                .pageInfo(new PaginationInfo(page))
                .statusCounts(buildStatusCounts(type, normalizedKeyword))
                .deleteRequestedCount(adminProductInquiryQueryRepository.countAllDeleteRequested())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminProductInquiryDto.SummaryResponse getSummary() {
        return AdminProductInquiryDto.SummaryResponse.builder()
                .deleteRequestedCount(adminProductInquiryQueryRepository.countAllDeleteRequested())
                .build();
    }

    /** 문의 유형 필터 옵션 (§18-2-1) */
    public List<AdminProductInquiryDto.TypeOption> getTypeOptions() {
        return Arrays.stream(ProductInquiryType.values())
                .map(type -> AdminProductInquiryDto.TypeOption.builder()
                        .code(type)
                        .label(type.getDescription())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminProductInquiryDto.DetailResponse getDetail(Long inquiryId, AdminProductInquiryStatusFilter statusFilter,
                                                            ProductInquiryType type, String keyword) {
        ProductInquiry inquiry = getInquiry(inquiryId);

        List<Long> orderedIds = adminProductInquiryQueryRepository.findOrderedIds(
                statusFilter, type, normalize(keyword));
        int index = orderedIds.indexOf(inquiryId);
        Long prevId = index > 0 ? orderedIds.get(index - 1) : null;
        Long nextId = (index >= 0 && index < orderedIds.size() - 1) ? orderedIds.get(index + 1) : null;

        List<ProductInquiryHistory> histories =
                productInquiryHistoryRepository.findByInquiry_IdOrderByCreatedAtDescIdDesc(inquiryId);

        return toDetailResponse(inquiry, prevId, nextId, histories);
    }

    /**
     * 삭제 집행 (§18-5) — 삭제 요청 유무와 무관하게 답변대기·답변완료 어느 상태에서든 직접 집행할 수 있다.
     * 질문과 브랜드 답변이 함께 소비자·브랜드 화면에서 내려가고 원문은 내부 기록으로 보관된다.
     */
    public void executeDelete(Long inquiryId, Long operatorId, AdminProductInquiryDeleteDecision.ExecuteRequest request) {
        ProductInquiry inquiry = getInquiry(inquiryId);

        if (inquiry.isDeleted()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_DELETED);
        }

        ProductInquiryAdminDeleteReason reason = request.getReason();
        String detail = normalize(request.getDetail());
        if (reason.requiresDetail() && detail == null) {
            throw new BusinessException(ErrorCode.INQUIRY_DELETE_REASON_DETAIL_REQUIRED);
        }

        inquiry.executeDelete(reason, detail, operatorId);
        productInquiryHistoryRepository.save(new ProductInquiryHistory(
                inquiry, ProductInquiryHistoryType.DELETE_EXECUTED, reason.getDescription(), operatorId));
    }

    /**
     * 삭제 요청 반려 (§18-6) — 삭제 요청이 있을 때만 성립한다(기각할 대상이 없으면 버튼 자체가 없다).
     * 노출 축만 정상으로 되돌리므로 답변 축이 보존돼 있어 요청 직전 상태로 정확히 복귀한다.
     */
    public void rejectDeleteRequest(Long inquiryId, Long operatorId, AdminProductInquiryDeleteDecision.RejectRequest request) {
        ProductInquiry inquiry = getInquiry(inquiryId);

        if (!inquiry.isDeleteRequested()) {
            throw new BusinessException(ErrorCode.INQUIRY_DELETE_NOT_REQUESTED);
        }

        ProductInquiryRejectReason reason = request.getReason();
        String detail = normalize(request.getDetail());
        if (reason.requiresDetail() && detail == null) {
            throw new BusinessException(ErrorCode.INQUIRY_DELETE_REASON_DETAIL_REQUIRED);
        }

        inquiry.rejectDeleteRequest(reason, detail, operatorId);
        productInquiryHistoryRepository.save(new ProductInquiryHistory(
                inquiry, ProductInquiryHistoryType.DELETE_REJECTED, reason.getDescription(), operatorId));
    }

    private AdminProductInquiryDto.ListItem toListItem(ProductInquiry inquiry) {
        return AdminProductInquiryDto.ListItem.builder()
                .inquiryId(inquiry.getId())
                .type(inquiry.getType())
                .typeName(inquiry.getType().getDescription())
                .content(inquiry.getContent())
                .productName(inquiry.getProduct().getName())
                .brandName(inquiry.getProduct().getMarket().getMarketName())
                .createdAt(inquiry.getCreatedAt())
                .answeredAt(inquiry.getAnsweredAt())
                .status(inquiry.getStatus())
                .exposureStatus(inquiry.getExposureStatus())
                .statusLabel(ProductInquiryStatusLabel.of(inquiry.getStatus(), inquiry.getExposureStatus()))
                .build();
    }

    private AdminProductInquiryDto.StatusCounts buildStatusCounts(ProductInquiryType type, String keyword) {
        Map<AdminProductInquiryStatusFilter, Long> counts =
                adminProductInquiryQueryRepository.countByStatus(type, keyword);

        return AdminProductInquiryDto.StatusCounts.builder()
                .all(counts.get(AdminProductInquiryStatusFilter.ALL))
                .waiting(counts.get(AdminProductInquiryStatusFilter.WAITING))
                .answered(counts.get(AdminProductInquiryStatusFilter.ANSWERED))
                .deleteRequested(counts.get(AdminProductInquiryStatusFilter.DELETE_REQUESTED))
                .deleted(counts.get(AdminProductInquiryStatusFilter.DELETED))
                .build();
    }

    private AdminProductInquiryDto.DetailResponse toDetailResponse(ProductInquiry inquiry, Long prevId, Long nextId,
                                                                    List<ProductInquiryHistory> histories) {
        Users user = inquiry.getUser();
        String brandName = inquiry.getProduct().getMarket().getMarketName();
        String writerName = writerName(user);

        return AdminProductInquiryDto.DetailResponse.builder()
                .inquiryId(inquiry.getId())
                .inquiryNumber(ProductInquiryNumber.of(inquiry, productInquiryRepository))
                .type(inquiry.getType())
                .typeName(inquiry.getType().getDescription())
                .productId(inquiry.getProduct().getProductId())
                .productName(inquiry.getProduct().getName())
                .marketId(inquiry.getProduct().getMarket().getId())
                .brandName(brandName)
                .userId(user.getId())
                .writerName(writerName)
                .secret(inquiry.isSecret())
                .visibilityName(inquiry.isSecret() ? "비밀글" : "공개")
                .createdAt(inquiry.getCreatedAt())
                .content(inquiry.getContent())
                // 지연 로딩 컬렉션(@ElementCollection)이라 트랜잭션 안에서 복사해 넘긴다 —
                // 원본을 그대로 실으면 직렬화 시점에 세션이 닫혀 응답 쓰기가 실패한다.
                .imageUrls(List.copyOf(inquiry.getImageUrls()))
                .answerContent(inquiry.getAnswerContent())
                .answeredAt(inquiry.getAnsweredAt())
                .answerModifiedAt(inquiry.getAnswerModifiedAt())
                .answererName(inquiry.isAnswered() ? brandName : null)
                .status(inquiry.getStatus())
                .exposureStatus(inquiry.getExposureStatus())
                .statusLabel(ProductInquiryStatusLabel.of(inquiry.getStatus(), inquiry.getExposureStatus()))
                .deleteRequest(toDeleteRequestInfo(inquiry, brandName))
                .processingMeta(toProcessingMeta(inquiry, brandName))
                .history(toHistoryItems(histories, writerName, brandName))
                .canExecuteDelete(!inquiry.isDeleted())
                .canReject(inquiry.isDeleteRequested())
                .prevInquiryId(prevId)
                .nextInquiryId(nextId)
                .build();
    }

    private AdminProductInquiryDto.DeleteRequestInfo toDeleteRequestInfo(ProductInquiry inquiry, String brandName) {
        if (inquiry.getDeleteRequestedAt() == null) {
            return null;
        }
        boolean underReview = inquiry.isDeleteRequested();
        boolean rejected = !underReview && !inquiry.isDeleted() && inquiry.getDeleteReviewedAt() != null;

        return AdminProductInquiryDto.DeleteRequestInfo.builder()
                .reason(inquiry.getDeleteRequestReason())
                .reasonName(inquiry.getDeleteRequestReason() != null
                        ? inquiry.getDeleteRequestReason().getDescription() : null)
                .detail(inquiry.getDeleteRequestDetail())
                .requesterBrandName(brandName)
                .requestedAt(inquiry.getDeleteRequestedAt())
                .underReview(underReview)
                .rejected(rejected)
                .rejectReasonType(rejected ? inquiry.getDeleteRejectReasonType() : null)
                .rejectReasonName(rejected && inquiry.getDeleteRejectReasonType() != null
                        ? inquiry.getDeleteRejectReasonType().getDescription() : null)
                .rejectReasonDetail(rejected ? inquiry.getDeleteRejectReasonDetail() : null)
                .rejectedAt(rejected ? inquiry.getDeleteReviewedAt() : null)
                .rejectedByName(rejected ? resolveOperatorName(inquiry.getDeleteProcessedBy()) : null)
                .build();
    }

    /**
     * 우측 처리 패널 메타 — 상태와 무관하게 같은 레이아웃을 쓰되(§18-4),
     * 표시하는 항목군은 현재 상태에 맞는 하나만 고른다: 답변 그룹 / 삭제 요청 그룹 / 삭제 그룹.
     */
    private AdminProductInquiryDto.ProcessingMeta toProcessingMeta(ProductInquiry inquiry, String brandName) {
        AdminProductInquiryDto.ProcessingMeta.ProcessingMetaBuilder builder =
                AdminProductInquiryDto.ProcessingMeta.builder().createdAt(inquiry.getCreatedAt());

        if (inquiry.isDeleted()) {
            return builder
                    .deletedAt(inquiry.getDeletedAt())
                    .processedByName(resolveOperatorName(inquiry.getDeleteProcessedBy()))
                    .deleteReasonName(inquiry.getDeleteReasonType() != null
                            ? inquiry.getDeleteReasonType().getDescription() : null)
                    .deleteReasonDetail(inquiry.getDeleteReasonDetail())
                    .build();
        }
        if (inquiry.isDeleteRequested()) {
            return builder
                    .deleteRequestedAt(inquiry.getDeleteRequestedAt())
                    .deleteRequesterName(brandName)
                    .build();
        }
        if (inquiry.isAnswered()) {
            return builder
                    .answeredAt(inquiry.getAnsweredAt())
                    .answererName(brandName)
                    .build();
        }
        return builder.build();
    }

    /** 어드민 화면 전용 이력 라벨 — 브랜드·소비자 화면과 문구가 다르다(§18-3 wireframe) */
    private List<AdminProductInquiryDto.HistoryItem> toHistoryItems(List<ProductInquiryHistory> histories,
                                                                     String writerName, String brandName) {
        return histories.stream()
                .map(history -> AdminProductInquiryDto.HistoryItem.builder()
                        .event(history.getHistoryType().name())
                        .label(adminHistoryLabel(history.getHistoryType()))
                        .detail(history.getDetail())
                        .occurredAt(history.getCreatedAt())
                        .actorType(history.getActorType())
                        .actorLabel(resolveActorLabel(history, writerName, brandName))
                        .build())
                .toList();
    }

    private String adminHistoryLabel(ProductInquiryHistoryType type) {
        return switch (type) {
            case REGISTERED -> "문의 등록";
            case ANSWERED -> "브랜드 답변 등록";
            case ANSWER_MODIFIED -> "브랜드 답변 수정";
            case DELETE_REQUESTED -> "삭제 요청 접수";
            case DELETE_REJECTED -> "삭제 요청 반려";
            case DELETE_EXECUTED -> "삭제 처리";
        };
    }

    private String resolveActorLabel(ProductInquiryHistory history, String writerName, String brandName) {
        return switch (history.getActorType()) {
            case CONSUMER -> "소비자(" + writerName + ")";
            case BRAND -> "브랜드(" + brandName + ")";
            case OPERATOR -> "운영자(" + resolveOperatorName(history.getActorId()) + ")";
        };
    }

    private String resolveOperatorName(Long operatorId) {
        if (operatorId == null) {
            return UNKNOWN_OPERATOR;
        }
        return sellerRepository.findById(operatorId).map(Seller::getName).orElse(UNKNOWN_OPERATOR);
    }

    private String writerName(Users user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getNickname();
    }

    private String normalize(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }

    private ProductInquiry getInquiry(Long inquiryId) {
        return productInquiryRepository.findByIdWithUserAndProduct(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));
    }
}
