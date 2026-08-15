package showroomz.api.seller.inquiry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.inquiry.dto.ProductInquiryDetailResponse;
import showroomz.api.seller.inquiry.dto.SellerInquiryDeleteRequest;
import showroomz.api.seller.inquiry.dto.SellerInquiryDto;
import showroomz.api.seller.inquiry.dto.SellerInquiryListResponse;
import showroomz.api.seller.inquiry.dto.SellerInquirySearchCondition;
import showroomz.api.seller.inquiry.repository.SellerInquiryQueryRepository;
import showroomz.api.seller.inquiry.type.InquiryVisibility;
import showroomz.api.seller.inquiry.type.SellerInquiryStatusFilter;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.entity.ProductInquiryHistory;
import showroomz.domain.inquiry.repository.ProductInquiryHistoryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.support.ProductInquiryNumber;
import showroomz.domain.inquiry.type.ProductInquiryDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryHistoryType;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;
import java.util.Map;

/**
 * 파트너센터 문의 관리 (§23) — 파트너센터는 답변의 유일한 작성 창구다.
 * 브랜드가 직접 할 수 있는 것은 답변 등록·수정과 삭제 <b>요청</b>까지이고,
 * 삭제 집행과 반려 판단은 운영자 몫이다 (§23-6).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SellerInquiryService {

    private final ProductInquiryRepository productInquiryRepository;
    private final ProductInquiryHistoryRepository productInquiryHistoryRepository;
    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final SellerInquiryQueryRepository sellerInquiryQueryRepository;

    @Transactional(readOnly = true)
    public SellerInquiryListResponse getMarketInquiries(
            String sellerEmail,
            SellerInquirySearchCondition condition,
            PagingRequest pagingRequest) {
        Long marketId = getMyMarketId(sellerEmail);
        Pageable pageable = pagingRequest.toPageable();

        Page<ProductInquiry> page = sellerInquiryQueryRepository.search(marketId, condition, pageable);

        Map<SellerInquiryStatusFilter, Long> statusCounts = sellerInquiryQueryRepository.countByStatus(marketId);
        Map<ProductInquiryType, Long> typeCounts = sellerInquiryQueryRepository.countByType(marketId);
        Map<InquiryVisibility, Long> visibilityCounts = sellerInquiryQueryRepository.countByVisibility(marketId);

        return SellerInquiryListResponse.builder()
                .totalCount(page.getTotalElements())
                .waitingCount(statusCounts.get(SellerInquiryStatusFilter.WAITING))
                .content(page.getContent().stream().map(SellerInquiryDto::from).toList())
                .pageInfo(new PaginationInfo(page))
                .statusCounts(SellerInquiryListResponse.StatusCounts.builder()
                        .all(statusCounts.get(SellerInquiryStatusFilter.ALL))
                        .waiting(statusCounts.get(SellerInquiryStatusFilter.WAITING))
                        .answered(statusCounts.get(SellerInquiryStatusFilter.ANSWERED))
                        .deleteRequested(statusCounts.get(SellerInquiryStatusFilter.DELETE_REQUESTED))
                        .deleted(statusCounts.get(SellerInquiryStatusFilter.DELETED))
                        .build())
                .typeCounts(typeCounts.entrySet().stream()
                        .map(entry -> SellerInquiryListResponse.FilterCount.builder()
                                .code(entry.getKey().name())
                                .label(entry.getKey().getDescription())
                                .count(entry.getValue())
                                .build())
                        .toList())
                .visibilityCounts(visibilityCounts.entrySet().stream()
                        .map(entry -> SellerInquiryListResponse.FilterCount.builder()
                                .code(entry.getKey().name())
                                .label(entry.getKey().getDescription())
                                .count(entry.getValue())
                                .build())
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public ProductInquiryDetailResponse getInquiryDetail(String sellerEmail, Long inquiryId,
                                                         SellerInquirySearchCondition condition) {
        Long marketId = getMyMarketId(sellerEmail);
        ProductInquiry inquiry = getMyInquiry(inquiryId, marketId);

        List<Long> orderedIds = sellerInquiryQueryRepository.findOrderedIds(marketId, condition);
        int index = orderedIds.indexOf(inquiryId);
        Long prevId = index > 0 ? orderedIds.get(index - 1) : null;
        Long nextId = (index >= 0 && index < orderedIds.size() - 1) ? orderedIds.get(index + 1) : null;

        List<ProductInquiryHistory> histories =
                productInquiryHistoryRepository.findByInquiry_IdOrderByCreatedAtDescIdDesc(inquiryId);

        return ProductInquiryDetailResponse.of(inquiry, inquiryNumber(inquiry), histories, prevId, nextId);
    }

    /** 답변 등록 (§23-4) — 등록 즉시 공개 콘텐츠로 전환된다. */
    public void registerAnswer(String sellerEmail, Long inquiryId, String answerContent) {
        ProductInquiry inquiry = getMyInquiry(inquiryId, getMyMarketId(sellerEmail));

        if (inquiry.isAnswered()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
        if (inquiry.isDeleteRequested()) {
            throw new BusinessException(ErrorCode.INQUIRY_UNDER_DELETE_REVIEW);
        }
        if (inquiry.isDeleted()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_DELETED);
        }

        inquiry.registerAnswer(answerContent.trim());
        productInquiryHistoryRepository.save(
                new ProductInquiryHistory(inquiry, ProductInquiryHistoryType.ANSWERED, null));
    }

    /**
     * 답변 수정 (§23-4) — 공개 콘텐츠라 잘못된 안내를 고칠 경로가 필요하다.
     * 대신 조용한 수정은 막는다: 수정 시각을 남기고 처리 이력에도 기록한다.
     */
    public void modifyAnswer(String sellerEmail, Long inquiryId, String answerContent) {
        ProductInquiry inquiry = getMyInquiry(inquiryId, getMyMarketId(sellerEmail));

        if (!inquiry.isAnswered()) {
            throw new BusinessException(ErrorCode.INQUIRY_NOT_ANSWERED);
        }
        if (inquiry.isDeleteRequested()) {
            throw new BusinessException(ErrorCode.INQUIRY_UNDER_DELETE_REVIEW);
        }
        if (inquiry.isDeleted()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_DELETED);
        }

        inquiry.modifyAnswer(answerContent.trim());
        productInquiryHistoryRepository.save(
                new ProductInquiryHistory(inquiry, ProductInquiryHistoryType.ANSWER_MODIFIED, null));
    }

    /**
     * 문의 삭제 요청 (§23-5) — 답변대기·답변완료 어느 쪽에서도 가능하다.
     * 요청해도 문의는 계속 게시되고, 요청 취소는 불가하다. 반려된 건은 사유를 보완해 재요청할 수 있다.
     */
    public void requestDelete(String sellerEmail, Long inquiryId, SellerInquiryDeleteRequest request) {
        ProductInquiry inquiry = getMyInquiry(inquiryId, getMyMarketId(sellerEmail));

        if (inquiry.isDeleteRequested()) {
            throw new BusinessException(ErrorCode.INQUIRY_DELETE_ALREADY_REQUESTED);
        }
        if (inquiry.isDeleted()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_DELETED);
        }

        ProductInquiryDeleteReason reason = request.getReason();
        String detail = request.getDetail() != null ? request.getDetail().trim() : null;
        if (reason.requiresDetail() && (detail == null || detail.isEmpty())) {
            throw new BusinessException(ErrorCode.INQUIRY_DELETE_REASON_DETAIL_REQUIRED);
        }

        inquiry.requestDelete(reason, detail);
        productInquiryHistoryRepository.save(new ProductInquiryHistory(
                inquiry, ProductInquiryHistoryType.DELETE_REQUESTED, reason.getDescription()));
    }

    private ProductInquiry getMyInquiry(Long inquiryId, Long marketId) {
        ProductInquiry inquiry = productInquiryRepository.findByIdWithUserAndProduct(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));

        if (!inquiry.getProduct().getMarket().getId().equals(marketId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return inquiry;
    }

    private Long getMyMarketId(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_INFO));

        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA))
                .getId();
    }

    private String inquiryNumber(ProductInquiry inquiry) {
        return ProductInquiryNumber.of(inquiry, productInquiryRepository);
    }
}
