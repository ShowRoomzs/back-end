package showroomz.api.admin.productinquiry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.entity.ProductInquiryHistory;
import showroomz.domain.inquiry.repository.ProductInquiryHistoryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.ProductInquiryHistoryType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 상품 문의 삭제 요청에 대한 운영자 판단 (§23-5) — 파트너센터(§23)의 짝이 되는 최소 창구다.
 * 브랜드는 삭제를 요청까지만 할 수 있고 집행·반려는 여기서 이뤄진다 (§23-6 ②).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminProductInquiryService {

    private final ProductInquiryRepository productInquiryRepository;
    private final ProductInquiryHistoryRepository productInquiryHistoryRepository;

    /** 삭제 집행 — 질문과 브랜드 답변이 함께 소비자 화면에서 내려간다. 원문은 보관된다. */
    public void executeDelete(Long inquiryId, String internalReason) {
        ProductInquiry inquiry = getInquiry(inquiryId);

        if (!inquiry.isDeleteRequested()) {
            throw new BusinessException(ErrorCode.INQUIRY_DELETE_NOT_REQUESTED);
        }

        inquiry.executeDelete(internalReason);
        productInquiryHistoryRepository.save(
                new ProductInquiryHistory(inquiry, ProductInquiryHistoryType.DELETE_EXECUTED, null));
    }

    /**
     * 삭제 요청 반려 — 노출 축만 정상으로 되돌린다.
     * 답변 축은 그대로 보존돼 있으므로 요청 직전 상태(답변대기/답변완료)로 정확히 복귀한다.
     * 별도 상태값을 만들지 않는 대신 처리 이력으로 브랜드가 결과를 인지한다.
     */
    public void rejectDeleteRequest(Long inquiryId, String rejectReason) {
        ProductInquiry inquiry = getInquiry(inquiryId);

        if (!inquiry.isDeleteRequested()) {
            throw new BusinessException(ErrorCode.INQUIRY_DELETE_NOT_REQUESTED);
        }

        inquiry.rejectDeleteRequest(rejectReason.trim());
        productInquiryHistoryRepository.save(
                new ProductInquiryHistory(inquiry, ProductInquiryHistoryType.DELETE_REJECTED, null));
    }

    private ProductInquiry getInquiry(Long inquiryId) {
        return productInquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));
    }
}
