package showroomz.api.admin.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.admin.market.DTO.AdminSellerDetailResponse;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

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
                .reviewMemo(seller.getReviewMemo())
                .build();
    }
}
