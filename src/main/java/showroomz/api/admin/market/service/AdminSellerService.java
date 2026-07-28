package showroomz.api.admin.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.admin.market.DTO.AdminSellerDetailResponse;
import showroomz.api.admin.market.DTO.AdminSellerDetailResponse.ProcessingHistoryItem;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.history.entity.SellerApplicationHistory;
import showroomz.domain.history.repository.SellerApplicationHistoryRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.seller.entity.SellerApplication;
import showroomz.domain.member.seller.repository.SellerApplicationRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.RelativeTimeFormatter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSellerService {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final SellerApplicationHistoryRepository sellerApplicationHistoryRepository;

    public AdminSellerDetailResponse getSellerDetail(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (seller.getRoleType() != RoleType.SELLER) {
            throw new BusinessException(ErrorCode.ACCOUNT_ROLE_MISMATCH);
        }

        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        SellerApplication latestApplication = sellerApplicationRepository
                .findTopBySeller_IdOrderByCreatedAtDesc(sellerId)
                .orElse(null);

        boolean rejected = seller.getStatus() == SellerStatus.REJECTED;

        LocalDateTime applicationDate = latestApplication != null
                ? latestApplication.getCreatedAt()
                : seller.getCreatedAt();

        return AdminSellerDetailResponse.builder()
                .email(rejected ? null : seller.getEmail())
                .marketName(market.getMarketName())
                .status(seller.getStatus().name())
                .businessType(seller.getBusinessType())
                .representativeName(seller.getRepresentativeName())
                .representativeContact(seller.getRepresentativeContact())
                .businessCompanyName(seller.getCompanyName())
                .businessRegistrationNumber(rejected ? null : seller.getBusinessRegistrationNumber())
                .businessRegistrationNumberHash(rejected ? seller.getBusinessRegistrationNumber() : null)
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
                .applicationDate(applicationDate)
                .elapsedTime(RelativeTimeFormatter.formatElapsed(applicationDate))
                .processedDate(latestApplication != null
                        ? latestApplication.getProcessedAt()
                        : seller.getProcessedAt())
                .processorId(resolveProcessorId(sellerId, seller.getStatus()))
                .processingHistory(buildProcessingHistory(sellerId))
                .reviewMemo(seller.getReviewMemo())
                .build();
    }

    private Long resolveProcessorId(Long sellerId, SellerStatus status) {
        if (status != SellerStatus.APPROVED && status != SellerStatus.REJECTED) {
            return null;
        }

        List<SellerApplicationHistory> histories =
                sellerApplicationHistoryRepository.findBySellerIdAndNewStatusInOrderByCreatedAtDesc(
                        sellerId, List.of(SellerStatus.APPROVED, SellerStatus.REJECTED));

        if (histories.isEmpty()) {
            return null;
        }
        return histories.get(0).getProcessedBy();
    }

    private List<ProcessingHistoryItem> buildProcessingHistory(Long sellerId) {
        List<ProcessingHistoryItem> history = new ArrayList<>();

        List<SellerApplication> applications =
                sellerApplicationRepository.findBySeller_IdOrderByCreatedAtAsc(sellerId);

        for (SellerApplication application : applications) {
            history.add(ProcessingHistoryItem.builder()
                    .type("APPLICATION_RECEIVED")
                    .label("신청 접수")
                    .processedAt(application.getCreatedAt())
                    .build());
        }

        List<SellerApplicationHistory> statusHistories =
                sellerApplicationHistoryRepository.findBySellerIdOrderByCreatedAtAsc(sellerId);

        for (SellerApplicationHistory statusHistory : statusHistories) {
            if (statusHistory.getNewStatus() == SellerStatus.APPROVED) {
                history.add(ProcessingHistoryItem.builder()
                        .type("APPLICATION_APPROVED")
                        .label("신청 승인")
                        .processedAt(statusHistory.getCreatedAt())
                        .build());
            } else if (statusHistory.getNewStatus() == SellerStatus.REJECTED) {
                history.add(ProcessingHistoryItem.builder()
                        .type("APPLICATION_REJECTED")
                        .label("신청 반려")
                        .processedAt(statusHistory.getCreatedAt())
                        .build());
            }
        }

        history.sort((a, b) -> {
            if (a.getProcessedAt() == null && b.getProcessedAt() == null) {
                return 0;
            }
            if (a.getProcessedAt() == null) {
                return 1;
            }
            if (b.getProcessedAt() == null) {
                return -1;
            }
            return a.getProcessedAt().compareTo(b.getProcessedAt());
        });

        return history;
    }
}
