package showroomz.api.seller.inquiry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.inquiry.dto.SellerInquiryListResponse;
import showroomz.api.seller.inquiry.dto.SellerInquirySearchCondition;
import showroomz.api.seller.inquiry.repository.SellerInquiryQueryRepository;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional
public class SellerInquiryService {

    private final ProductInquiryRepository productInquiryRepository;
    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final SellerInquiryQueryRepository sellerInquiryQueryRepository;

    @Transactional(readOnly = true)
    public SellerInquiryListResponse getMarketInquiries(String sellerEmail,
                                                        SellerInquirySearchCondition condition,
                                                        Pageable pageable) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_INFO));

        Long myMarketId = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA))
                .getId();

        return sellerInquiryQueryRepository.searchMarketInquiries(myMarketId, condition, pageable);
    }

    public void registerAnswer(String sellerEmail, Long inquiryId, String answerContent) {
        ProductInquiry inquiry = productInquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));

        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_INFO));

        Long myMarketId = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA))
                .getId();

        if (!inquiry.getProduct().getMarket().getId().equals(myMarketId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (inquiry.getStatus() == InquiryStatus.ANSWERED) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        inquiry.registerAnswer(answerContent);
    }
}
