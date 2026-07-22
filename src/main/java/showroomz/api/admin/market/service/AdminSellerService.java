package showroomz.api.admin.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.admin.market.DTO.AdminSellerDetailResponse;
import showroomz.api.admin.market.DTO.AdminSellerDetailResponse.ProcessingHistoryItem;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSellerService {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;

    public AdminSellerDetailResponse getSellerDetail(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (seller.getRoleType() != RoleType.SELLER) {
            throw new BusinessException(ErrorCode.ACCOUNT_ROLE_MISMATCH);
        }

        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        return AdminSellerDetailResponse.builder()
                .email(seller.getEmail())
                .marketName(market.getMarketName())
                .status(seller.getStatus().name())
                .businessType(seller.getBusinessType())
                .representativeName(seller.getRepresentativeName())
                .representativeContact(seller.getRepresentativeContact())
                .businessCompanyName(seller.getCompanyName())
                .businessRegistrationNumber(seller.getBusinessRegistrationNumber())
                .businessCategory(seller.getBusinessCondition())
                .businessAddress(seller.getBusinessAddress())
                .businessDetailAddress(seller.getDetailAddress())
                .taxEmail(seller.getTaxEmail())
                .businessLicenseImageUrl(seller.getBusinessLicenseImageUrl())
                .mailOrderLicenseImageUrl(seller.getMailOrderRegImageUrl())
                .mailOrderSalesNumber(seller.getMailOrderRegNumber())
                .settlementBankName(seller.getBankName())
                .accountHolderName(seller.getAccountHolder())
                .accountNumber(seller.getAccountNumber())
                .bankBookImageUrl(seller.getBankbookImageUrl())
                .applicationDate(seller.getCreatedAt())
                .processedDate(seller.getProcessedAt())
                .processingHistory(buildProcessingHistory(seller))
                .reviewMemo(seller.getReviewMemo())
                .build();
    }

    private List<ProcessingHistoryItem> buildProcessingHistory(Seller seller) {
        List<ProcessingHistoryItem> history = new ArrayList<>();

        history.add(ProcessingHistoryItem.builder()
                .type("APPLICATION_RECEIVED")
                .label("신청 접수")
                .processedAt(seller.getCreatedAt())
                .build());

        if (seller.getStatus() == SellerStatus.APPROVED && seller.getProcessedAt() != null) {
            history.add(ProcessingHistoryItem.builder()
                    .type("APPLICATION_APPROVED")
                    .label("신청 승인")
                    .processedAt(seller.getProcessedAt())
                    .build());
        } else if (seller.getStatus() == SellerStatus.REJECTED && seller.getProcessedAt() != null) {
            history.add(ProcessingHistoryItem.builder()
                    .type("APPLICATION_REJECTED")
                    .label("신청 반려")
                    .processedAt(seller.getProcessedAt())
                    .build());
        }

        return history;
    }
}
