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
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.seller.entity.SellerApplication;
import showroomz.domain.member.seller.repository.SellerApplicationRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSellerService {

    private final SellerRepository sellerRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final SellerApplicationHistoryRepository sellerApplicationHistoryRepository;

    public AdminSellerDetailResponse getApplicationDetail(Long applicationId) {
        SellerApplication application = sellerApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        Seller seller = application.getSeller();
        if (seller.getRoleType() != RoleType.SELLER) {
            throw new BusinessException(ErrorCode.ACCOUNT_ROLE_MISMATCH);
        }

        boolean rejected = application.getStatus() == SellerStatus.REJECTED;

        return AdminSellerDetailResponse.builder()
                .applicationId(application.getId())
                .sellerId(seller.getId())
                .email(rejected ? null : seller.getEmail())
                .marketName(application.getMarketName())
                .status(application.getStatus().name())
                .rejectionReason(application.getRejectReason())
                .rejectionReasonDetail(application.getRejectReasonDetail())
                .businessType(application.getBusinessType())
                .representativeName(application.getRepresentativeName())
                .representativeContact(application.getRepresentativeContact())
                .businessCompanyName(application.getCompanyName())
                .businessRegistrationNumber(rejected ? null : application.getBusinessRegistrationNumber())
                .businessRegistrationNumberHash(rejected ? application.getBusinessRegistrationNumber() : null)
                .businessCategory(application.getBusinessCondition())
                .businessAddress(application.getBusinessAddress())
                .businessDetailAddress(application.getDetailAddress())
                .taxEmail(application.getTaxEmail())
                .businessLicenseImageUrl(application.getBusinessLicenseImageUrl())
                .mailOrderLicenseImageUrl(application.getMailOrderRegImageUrl())
                .mailOrderSalesNumber(application.getMailOrderRegNumber())
                .settlementBankName(application.getBankName())
                .accountHolderName(application.getAccountHolder())
                .accountNumber(application.getAccountNumber())
                .bankBookImageUrl(application.getBankbookImageUrl())
                .applicationDate(application.getCreatedAt())
                .processedDate(application.getProcessedAt())
                .processorLoginId(resolveProcessorLoginId(application))
                .processingHistory(buildProcessingHistory(seller.getId()))
                .reviewMemo(seller.getReviewMemo())
                .build();
    }

    private String resolveProcessorLoginId(SellerApplication application) {
        if (application.getStatus() != SellerStatus.APPROVED
                && application.getStatus() != SellerStatus.REJECTED) {
            return null;
        }

        Long processedBy = sellerApplicationHistoryRepository
                .findByApplication_IdOrderByCreatedAtAsc(application.getId())
                .stream()
                .filter(h -> h.getNewStatus() == SellerStatus.APPROVED
                        || h.getNewStatus() == SellerStatus.REJECTED)
                .reduce((first, second) -> second)
                .map(SellerApplicationHistory::getProcessedBy)
                .orElse(null);

        if (processedBy == null) {
            return null;
        }

        return sellerRepository.findById(processedBy)
                .map(Seller::getEmail)
                .orElse(null);
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
